# HyprConnect Android App

Android companion for the HyprConnect desktop daemon. Syncs notifications, clipboard, battery, and files between phone and desktop.

## Architecture

**Language:** Kotlin | **UI:** Jetpack Compose + Material 3 | **DI:** Hilt
**Pattern:** Clean Architecture (domain/data/ui layers) + MVVM
**Min SDK:** 26 | **Target SDK:** 35 | **Namespace:** `dev.hyprconnect.app`

## Project Structure

```
app/src/main/java/dev/hyprconnect/app/
  MainActivity.kt                    # Entry activity, starts foreground service
  HyprConnectApplication.kt         # @HiltAndroidApp
  data/
    local/
      CertificateStore.kt           # BKS keystore, self-signed RSA 2048 certs
      SettingsDataStore.kt           # DataStore preferences
    remote/
      HyprConnectClient.kt          # TLS 1.3 socket + JSON-RPC 2.0 client
      JsonRpcMessage.kt             # JSON-RPC message types
      DeviceDiscovery.kt            # mDNS/NsdManager discovery
    repository/
      DeviceRepositoryImpl.kt       # Device operations implementation
      SettingsRepositoryImpl.kt     # Settings operations implementation
  di/
    AppModule.kt                    # Hilt: CertificateStore, SettingsDataStore
    RepositoryModule.kt             # Hilt: repository bindings
  domain/
    model/
      Device.kt                     # Device data class (id, name, host, port, type, status, battery)
      DeviceStatus.kt               # Sealed class: Connected/Disconnected/Connecting/Pairing/Error
      PluginConfig.kt               # Feature toggle flags
    repository/
      DeviceRepository.kt           # Interface
      SettingsRepository.kt         # Interface
    usecase/
      DiscoverDevicesUseCase.kt
      PairDeviceUseCase.kt
      SendClipboardUseCase.kt
      SyncNotificationsUseCase.kt
  service/
    HyprConnectService.kt           # Foreground service: pairing, battery updates (60s), clipboard
    HyprNotificationListenerService.kt  # NotificationListenerService -> notification.push
    ClipboardMonitor.kt             # Clipboard change monitor (500ms rate limit)
  ui/
    navigation/NavGraph.kt          # Routes: home, settings, pairing/{id}, device/{id}, file_transfer
    home/                           # Paired + discovered device lists
    pairing/                        # SAS 6-digit verification
    device/                         # Device detail + quick actions
    settings/                       # Device name, sync toggles
    filetransfer/                   # File transfer progress
    components/                     # DeviceCard, VerificationCodeDisplay
    theme/                          # Material 3 Color/Theme/Type
  util/
    Constants.kt                    # Default port (17539), service type
    Extensions.kt                   # Kotlin extensions
```

## Communication

### Network (Phone <-> Daemon)
- **TLS 1.3** mutual authentication over TCP
- **JSON-RPC 2.0** newline-delimited messages
- **mDNS discovery:** `_hyprconnect._tcp.` via Android NsdManager

### Key JSON-RPC Methods
**Phone sends:** `pair.request`, `clipboard.set`, `battery.update`, `notification.push`, `notification.dismiss`, `file.offer`
**Phone receives:** `pair.approved`, `clipboard.set`

### Pairing
- Initial connect with InsecureSkipVerify
- SAS code: `SHA256(sorted_fingerprints) % 1,000,000`
- On approval: store daemon cert in BKS keystore, reconnect with mutual TLS

## Key Dependencies

- **Compose BOM** 2024.10.01, Material 3, Navigation Compose 2.8.3
- **Hilt** 2.52 (DI)
- **BouncyCastle** 1.78.1 (TLS certificates)
- **kotlinx-serialization-json** 1.7.3
- **DataStore Preferences** 1.1.1
- **Accompanist Permissions** 0.36.0

## Build

```sh
./gradlew assembleDebug     # Debug build
./gradlew assembleRelease   # Release build
./gradlew test              # Unit tests
```

## Permissions

INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE,
POST_NOTIFICATIONS, BIND_NOTIFICATION_LISTENER_SERVICE, READ_MEDIA_*, RECEIVE_BOOT_COMPLETED, WAKE_LOCK

## Data Storage

- **Keystore:** `filesDir/hyprconnect_v2.bks` (BKS format, BouncyCastle)
- **Preferences:** DataStore at `filesDir/settings.preferences_pb`
- **Settings keys:** device_name, notification_sync, clipboard_sync, file_transfer, media_control, battery_reporting

## Companion Daemon

Go daemon at `/home/nirmal/Projects/elixir-craft/hyprconnect/hyprconnect` — see its CLAUDE.md for details.
