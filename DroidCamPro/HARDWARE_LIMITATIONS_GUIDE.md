# Hardware Limitations & Workflow Comparison

### Workflow A: IP Camera (Wi-Fi / LAN)
* **Works on**: 100% of Android phones running Android 8.0+.
* **Latency**: ~50ms (WebRTC) to ~120ms (MJPEG).
* **Setup**: Connect both phone and OBS PC to the same 5GHz Wi-Fi or USB tethering network.
* **Pros**: Wireless freedom, multi-camera support, works on any budget smartphone.

### Workflow B: HDMI Clean Feed (USB-C DisplayPort Alt Mode)
* **Works on**: Flagships with USB 3.2 controller (Samsung Galaxy S8-S24, Note series, Google Pixel 8/9).
* **Does NOT work on**: Budget Android phones (e.g. Moto G, Redmi Note, Galaxy A series) which use USB 2.0 and lack the physical high-speed DisplayPort differential pairs on the USB-C connector.
* **Latency**: < 10ms (Hardware HDMI video signal).

### Workflow C: Native USB UVC Webcam Mode
* **Works on**: Android 14+ devices supporting the `DeviceAsWebcam` HAL implementation.
* **Does NOT work on**: Android 13 and below without specialized custom kernel/root patches.
* **Workaround**: Use USB Tethering mode. It turns the USB cable into a virtual Gigabit Ethernet link, letting you stream `http://192.168.42.129:8080/stream` directly over the USB cable with zero Wi-Fi interference!
