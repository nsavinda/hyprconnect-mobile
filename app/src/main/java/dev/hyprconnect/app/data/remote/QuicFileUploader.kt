package dev.hyprconnect.app.data.remote

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import dev.hyprconnect.app.data.local.CertificateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Uploads files over HTTPS using OkHttp with the daemon's pinned certificate.
 *
 * Uses the CertificateStore's BKS keystore (which already contains the daemon cert
 * from pairing) as the trust store, so no manual CA installation is needed.
 */
@Singleton
class QuicFileUploader @Inject constructor(
    private val certificateStore: CertificateStore
) {
    private val TAG = "QuicFileUploader"

    private val client: OkHttpClient by lazy {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(certificateStore.getKeyStore())
        val trustManager = tmf.trustManagers.first() as X509TrustManager

        val sslContext = SSLContext.getInstance("TLSv1.3")
        sslContext.init(null, tmf.trustManagers, SecureRandom())

        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true } // daemon uses IP, not hostname
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    data class UploadResult(
        val success: Boolean,
        val bytesReceived: Long = 0,
        val sha256: String? = null,
        val error: String? = null
    )

    /**
     * Upload a file via HTTPS to the daemon.
     *
     * @param host         daemon IP address
     * @param quicPort     daemon upload port (typically 17540)
     * @param uploadToken  one-time token from file.offer response
     * @param uri          content URI of the file to upload
     * @param fileSize     known file size in bytes
     * @param contentResolver for opening the URI stream
     * @param onProgress   called with bytes sent so far
     * @return UploadResult with success/failure and server response
     */
    suspend fun upload(
        host: String,
        quicPort: Int,
        uploadToken: String,
        uri: Uri,
        fileSize: Long,
        contentResolver: ContentResolver,
        onProgress: ((bytesSent: Long) -> Unit)? = null
    ): UploadResult = withContext(Dispatchers.IO) {
        val url = "https://$host:$quicPort/upload/$uploadToken"
        Log.d(TAG, "Starting HTTPS upload to $url ($fileSize bytes)")

        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = fileSize

            override fun writeTo(sink: BufferedSink) {
                contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(256 * 1024)
                    var bytesSent = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        sink.write(buffer, 0, read)
                        bytesSent += read
                        onProgress?.invoke(bytesSent)
                    }
                } ?: throw IllegalStateException("Cannot open file URI")
            }
        }

        val request = Request.Builder()
            .url(url)
            .put(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "Upload response: status=${response.code} protocol=${response.protocol} body=$responseBody")

                if (response.isSuccessful) {
                    parseUploadResponse(responseBody)
                } else {
                    UploadResult(false, error = "HTTP ${response.code}: $responseBody")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTPS upload failed: ${e.message}", e)
            UploadResult(false, error = e.message ?: "Upload failed")
        }
    }

    private fun parseUploadResponse(body: String): UploadResult {
        return try {
            val bytesReceived = Regex("\"bytes_received\"\\s*:\\s*(\\d+)")
                .find(body)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val sha256 = Regex("\"sha256\"\\s*:\\s*\"([a-f0-9]+)\"")
                .find(body)?.groupValues?.get(1)
            UploadResult(success = true, bytesReceived = bytesReceived, sha256 = sha256)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse upload response: $body", e)
            UploadResult(success = true, bytesReceived = 0)
        }
    }
}
