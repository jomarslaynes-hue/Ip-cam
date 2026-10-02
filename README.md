# DroidCam Pro - Android IP Camera & HDMI Webcam for OBS Studio

A professional-grade Android application that turns your smartphone into a broadcast-quality camera for **OBS Studio**, **vMix**, and live production (e.g. Church live streaming, events, podcasts).

---

## 🚀 Key Features

* **Camera2 / CameraX Engine**: Ultra crisp 720p, 1080p, and 4K UHD sensor streaming with selectable 24, 30, and 60 FPS.
* **Low-Latency IP Streaming**: High-speed MJPEG & WebRTC stream server embedded directly inside the Android phone (`http://PHONE-IP:8080/stream`).
* **Browser Remote Control**: Control zoom, exposure, white balance, flash/torch, and lens from any computer or tablet on the local network (`http://PHONE-IP:8080/control`).
* **Clean HDMI Output Mode**: Pure 100% full-screen viewfinder without UI overlays for devices with **USB-C DisplayPort Alt Mode** feeding into an HDMI capture card.
* **USB UVC Webcam Analysis**: Automated detection of Android 14+ native USB Webcam Mode with fallback recommendations.
* **Broadcast Audio**: 48kHz audio streaming with microphone toggle, mute, and live VU meter.
* **Android Foreground Service**: Continuous background streaming with Wi-Fi locks and WakeLocks that keep streaming alive during screen locks.
* **Security & Isolation**: Optional HTTP Basic Auth, Token verification, and local subnet whitelist.

---

## 🛠️ How to Build and Run in Android Studio

1. Open **Android Studio** (Hedgehog, Iguana, Koala, or Ladybug).
2. Choose **File -> Open** and select this project directory.
3. Allow Gradle to synchronize dependencies.
4. Connect your Android device via USB with **USB Debugging** enabled (Developer Options).
5. Click **Run 'app'** (Shift + F10).
6. Grant **Camera** and **Audio** permissions when prompted.
