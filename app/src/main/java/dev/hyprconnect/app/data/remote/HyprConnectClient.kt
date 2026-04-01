package dev.hyprconnect.app.data.remote

import android.util.Log
import dev.hyprconnect.app.data.local.CertificateStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.*

@Singleton
class HyprConnectClient @Inject constructor(
    private val certificateStore: CertificateStore
) {
    private val TAG = "HyprConnectClient"
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    private var socket: SSLSocket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _incomingRequests = MutableSharedFlow<JsonRpcRequest>(extraBufferCapacity = 64)
    val incomingRequests: SharedFlow<JsonRpcRequest> = _incomingRequests.asSharedFlow()

    private val _incomingMessages = MutableSharedFlow<JsonRpcMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<JsonRpcMessage> = _incomingMessages.asSharedFlow()

    suspend fun connect(host: String, port: Int, trustAll: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Connecting to $host:$port (trustAll=$trustAll)")
        try {
            val sslContext = SSLContext.getInstance("TLSv1.3")
            
            val trustManagers = if (trustAll) {
                Log.w(TAG, "Using InsecureSkipVerify (trustAll=true)")
                arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
            } else {
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).algorithm)
                tmf.init(certificateStore.getKeyStore())
                tmf.trustManagers
            }

            val customKeyManager = object : X509ExtendedKeyManager() {
                private val alias = certificateStore.getAlias()
                
                override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String {
                    Log.d(TAG, "chooseClientAlias: keyType=${keyType?.joinToString()}, issuers=${issuers?.size ?: 0}. Returning $alias")
                    return alias
                }
                
                override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?): String {
                    Log.d(TAG, "chooseEngineClientAlias. Returning $alias")
                    return alias
                }
                
                override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> = arrayOf(alias)
                override fun getCertificateChain(alias: String?): Array<X509Certificate> {
                    val cert = certificateStore.getSelfCertificate()
                    Log.d(TAG, "getCertificateChain for alias $alias. Subject: ${cert.subjectX500Principal}")
                    return arrayOf(cert)
                }
                
                override fun getPrivateKey(alias: String?): PrivateKey {
                    Log.d(TAG, "getPrivateKey for alias $alias")
                    return certificateStore.getSelfPrivateKey()
                }
                
                override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
                override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
                override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String? = null
            }

            sslContext.init(arrayOf(customKeyManager), trustManagers, SecureRandom())
            
            val factory = sslContext.socketFactory
            val rawSocket = factory.createSocket(host, port) as SSLSocket
            rawSocket.enabledProtocols = arrayOf("TLSv1.3")
            
            // Go/Daemon often requires a client to start the handshake immediately
            Log.d(TAG, "Starting TLS 1.3 handshake...")
            rawSocket.startHandshake()
            
            val session = rawSocket.session
            Log.d(TAG, "Handshake successful. Session valid: ${session.isValid}")
            Log.d(TAG, "Local Certificates presented: ${session.localCertificates?.size ?: 0}")
            if (session.localCertificates != null) {
                val localCert = session.localCertificates[0] as X509Certificate
                Log.d(TAG, "Local Cert Subject: ${localCert.subjectX500Principal}")
            }
            
            socket = rawSocket
            reader = BufferedReader(InputStreamReader(rawSocket.inputStream))
            writer = BufferedWriter(OutputStreamWriter(rawSocket.outputStream))

            startListening()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed to $host:$port: ${e.message}", e)
            false
        }
    }

    fun getPeerCertificate(): X509Certificate? {
        return try {
            val certs = socket?.session?.peerCertificates
            Log.d(TAG, "Peer certs found: ${certs?.size ?: 0}")
            certs?.firstOrNull() as? X509Certificate
        } catch (e: Exception) {
            Log.e(TAG, "Error getting peer certificate: ${e.message}")
            null
        }
    }

    private fun startListening() {
        scope.launch {
            try {
                while (isActive) {
                    val line = reader?.readLine() ?: break
                    Log.d(TAG, "Received: $line")
                    try {
                        val message = json.decodeFromString<JsonRpcMessage>(line)
                        Log.d(TAG, "Decoded message, emitting to flow. Subscribers: ${_incomingMessages.subscriptionCount.value}")
                        _incomingMessages.emit(message)
                        
                        if (message.isRequest()) {
                            val request = JsonRpcRequest(
                                jsonrpc = message.jsonrpc,
                                method = message.method!!,
                                params = message.params,
                                id = message.id
                            )
                            Log.d(TAG, "Emitting request: ${request.method}. Subscribers: ${_incomingRequests.subscriptionCount.value}")
                            _incomingRequests.emit(request)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse message: $line", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Read error: ${e.message}")
            } finally {
                disconnect()
            }
        }
    }

    suspend fun sendRequest(request: JsonRpcRequest): Boolean = withContext(Dispatchers.IO) {
        try {
            val line = json.encodeToString(request)
            Log.d(TAG, "Sending request: $line")
            writer?.write(line)
            writer?.newLine()
            writer?.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Send request failed: ${e.message}")
            false
        }
    }

    suspend fun sendResponse(response: JsonRpcResponse): Boolean = withContext(Dispatchers.IO) {
        try {
            val line = json.encodeToString(response)
            Log.d(TAG, "Sending response: $line")
            writer?.write(line)
            writer?.newLine()
            writer?.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Send response failed: ${e.message}")
            false
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting client...")
        try {
            socket?.close()
            reader?.close()
            writer?.close()
            socket = null
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error: ${e.message}")
        }
    }
}
