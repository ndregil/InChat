# InChat

Klien XMPP modern untuk Android.

## Fitur

- **Panggilan video dan audio**
- **Chat room (MUC)**: gabung dan kelola grup chat
- **Cari room publik**
- **Moderasi room**: kelola peserta dan pengaturan room
- **Pesan terenkripsi**
- **Pesan bisik (whisper)** dalam bubble chat
- **Terjemahan chat**
- **Berbagi lokasi**
- **Cek versi klien** lawan bicara
- Notifikasi pesan di latar belakang
- Antarmuka modern dengan Jetpack Compose

## Unduh

Unduh APK terbaru di halaman [Releases](https://github.com/ndregil/InChat/releases).

Anda juga bisa memakai [Obtainium](https://github.com/ImranR98/Obtainium) agar update otomatis terdeteksi. Cukup tempelkan link repo ini.

Persyaratan: Android 7.0 (API 24) atau lebih baru.

## Izin yang diminta

| Izin | Kegunaan |
|------|----------|
| Internet | Terhubung ke server XMPP |
| Notifikasi | Menampilkan pesan masuk |
| Kamera | Panggilan video dan kirim foto |
| Mikrofon | Panggilan audio dan video |
| Lokasi | Berbagi lokasi |
| Foreground service | Menjaga koneksi tetap aktif |
| Abaikan optimasi baterai | Mencegah koneksi dimatikan sistem |

## Membangun dari source

Kebutuhan: JDK 17 dan Android SDK.

```
git clone https://github.com/ndregil/InChat.git
cd InChat
cp .env.example .env
./gradlew assembleDebug
```

Untuk build release yang ditandatangani, siapkan keystore sendiri lalu atur:

```
export KEYSTORE_PATH=/path/ke/keystore.jks
export STORE_PASSWORD="..."
export KEY_PASSWORD="..."
./gradlew assembleRelease
```

Alias key yang dipakai: `upload`.

## Teknologi

Kotlin, Jetpack Compose, Room, Smack (XMPP), OkHttp, Retrofit, Moshi, Coil, CameraX, WorkManager.

## Lisensi

Dirilis di bawah lisensi GPL-3.0. Lihat file [LICENSE](LICENSE).
