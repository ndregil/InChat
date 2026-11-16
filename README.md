# InChat

A modern XMPP client for Android.

## Features

- **Video and audio calls**
- **Chat rooms (MUC)**: join and manage group chats
- **Search public rooms**
- **Room moderation**: manage participants and room settings
- **Encrypted messages**
- **Whispered messages** in chat bubbles
- **Chat translation**
- **Location sharing**
- **Client version check** for your contacts
- Background message notifications
- Modern UI built with Jetpack Compose

## Download

Get the latest APK from the [Releases](https://github.com/ndregil/InChat/releases) page.

You can also use [Obtainium](https://github.com/ImranR98/Obtainium) to get automatic update notifications. Just paste this repository's link.

Requirements: Android 7.0 (API 24) or newer.

## Permissions

| Permission | Purpose |
|------------|---------|
| Internet | Connect to the XMPP server |
| Notifications | Show incoming messages |
| Camera | Video calls and sending photos |
| Microphone | Audio and video calls |
| Location | Share your location |
| Foreground service | Keep the connection alive |
| Ignore battery optimizations | Prevent the system from killing the connection |

## Building from source

Requirements: JDK 17 and the Android SDK.

```
git clone https://github.com/ndregil/InChat.git
cd InChat
cp .env.example .env
./gradlew assembleDebug
```

For a signed release build, create your own keystore and set:

```
export KEYSTORE_PATH=/path/to/keystore.jks
export STORE_PASSWORD="..."
export KEY_PASSWORD="..."
./gradlew assembleRelease
```

The key alias used is `upload`.

## Tech stack

Kotlin, Jetpack Compose, Room, Smack (XMPP), OkHttp, Retrofit, Moshi, Coil, CameraX, WorkManager.

## License

Released under the GPL-3.0 license. See the [LICENSE](LICENSE) file.
