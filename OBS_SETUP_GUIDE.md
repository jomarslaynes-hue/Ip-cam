# OBS Studio Integration Guide

## Method 1: OBS Browser Source (Recommended)

1. Open OBS Studio.
2. In the **Sources** panel, click **+** and select **Browser**.
3. Name it `DroidCam Pro - Phone`.
4. Configure the settings:
   - **URL**: `http://<YOUR-PHONE-IP>:8080/obs`
   - **Width**: `1920` (or `1280` for 720p, `3840` for 4K)
   - **Height**: `1080` (or `720`, `2160`)
   - **FPS**: `30` (or `60`)
   - **Custom CSS**: `body { background-color: rgba(0, 0, 0, 0); margin: 0px auto; overflow: hidden; }`
   - Check **Shutdown source when not visible**.
5. Click **OK**. Your phone feed is now live in OBS!

---

## Method 2: OBS Media Source (MJPEG Direct)

1. In OBS, click **+** and select **Media Source**.
2. Uncheck **Local File**.
3. Set **Input**: `http://<YOUR-PHONE-IP>:8080/stream`
4. Set **Input Format**: `mjpeg`
5. Check **Use hardware decoding when available**.
6. Set **Network Buffering**: `1 MB` (for ultra-low latency).
7. Click **OK**.

---

## Method 3: HDMI Capture Card (Zero Network Latency)

1. Verify your phone supports **USB-C DisplayPort Alt Mode** (e.g. Samsung Galaxy S series, Pixel 8+).
2. Connect: **Phone -> USB-C to HDMI Cable -> HDMI Capture Card (Cam Link 4K) -> PC USB 3.0**.
3. On the phone app, tap the **HDMI Clean Feed** button. The screen turns into an uncompressed full-screen preview.
4. In OBS, click **+** and select **Video Capture Device**.
5. Select your HDMI capture card.
