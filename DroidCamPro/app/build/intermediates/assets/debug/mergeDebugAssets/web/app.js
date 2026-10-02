// DroidCam Pro Web Remote Controller Script
const host = window.location.host;
const zoomSlider = document.getElementById('zoomSlider');
const zoomValue = document.getElementById('zoomValue');
const exposureSlider = document.getElementById('exposureSlider');
const exposureValue = document.getElementById('exposureValue');
const qualitySlider = document.getElementById('qualitySlider');
const qualityValue = document.getElementById('qualityValue');
const btnTorch = document.getElementById('btnTorch');
const torchText = document.getElementById('torchText');
const btnRefreshStream = document.getElementById('btnRefreshStream');
const cameraPreview = document.getElementById('cameraPreview');

let torchState = false;

// Update OBS URLs dynamically
document.getElementById('obsMjpegUrl').textContent = 'http://' + host + '/stream';
document.getElementById('obsBrowserUrl').textContent = 'http://' + host + '/obs';

// Zoom Controller
zoomSlider.addEventListener('input', (e) => {
  const zoom = e.target.value;
  zoomValue.textContent = zoom + 'x';
  fetch('/api/control?zoom=' + zoom, { method: 'POST' });
});

// Exposure Controller
exposureSlider.addEventListener('input', (e) => {
  const ev = e.target.value;
  exposureValue.textContent = (ev > 0 ? '+' : '') + ev + ' EV';
  fetch('/api/control?exposure=' + ev, { method: 'POST' });
});

// Quality Controller
qualitySlider.addEventListener('input', (e) => {
  const q = e.target.value;
  qualityValue.textContent = q + '%';
  fetch('/api/control?quality=' + q, { method: 'POST' });
});

// Torch toggle
btnTorch.addEventListener('click', () => {
  torchState = !torchState;
  torchText.textContent = 'Torch: ' + (torchState ? 'ON' : 'OFF');
  fetch('/api/control?torch=' + torchState, { method: 'POST' });
});

// Reconnect button
btnRefreshStream.addEventListener('click', () => {
  cameraPreview.src = '/stream?t=' + Date.now();
});

// Poll status
setInterval(async () => {
  try {
    const res = await fetch('/api/status');
    const data = await res.json();
    document.getElementById('clientCountBadge').textContent = 'Clients: ' + data.connectedClients;
    document.getElementById('resolutionBadge').textContent = data.resolution + ' @ ' + data.fps + ' FPS';
  } catch (err) {}
}, 2000);
