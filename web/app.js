'use strict';

// ── Real API integration ─────────────────────────────────────────────────────
const API_BASE = '';   // same origin — nginx proxies /api/ → Node.js

let sseSource = null;

/**
 * SSE 연결 — Android 앱이 스캔을 완료하면 즉시 결과를 수신한다.
 * 연결 끊기면 3초 후 자동 재연결.
 */
function connectSSE() {
  if (sseSource) sseSource.close();

  sseSource = new EventSource(API_BASE + '/api/report/stream');

  sseSource.onmessage = (e) => {
    if (!e.data || e.data.trim().startsWith(':')) return;  // ping
    try {
      const report = JSON.parse(e.data);
      setAndroidReady(report);
      // 스캔 화면이 열려 있지 않으면 즉시 결과 표시
      if (state.screen === 'home' || state.screen === 'result') {
        showReportFromAndroid(report);
      }
    } catch (_) {}
  };

  sseSource.onerror = () => {
    sseSource.close();
    setTimeout(connectSSE, 3000);
  };
}

/**
 * Android ScanReport JSON을 결과 화면에 표시한다.
 * 시뮬레이션 showResult()와 동일 렌더링, 단 데이터 출처가 실제 앱.
 */
let _lastAndroidReport = null;

function setAndroidReady(report) {
  _lastAndroidReport = report;
  const waiting = document.getElementById('android-waiting');
  const badge   = document.getElementById('android-badge');
  const btn     = document.getElementById('btn-view-android');
  if (waiting) waiting.style.display = 'none';
  if (badge) {
    const level = { SAFE:'안전', INTEREST:'관심', CAUTION:'주의', DANGER:'위험', CRITICAL:'매우 위험' };
    badge.textContent = `📱 Android 실측 — 위험도 ${report.riskScore}/100 (${level[report.riskLevel] ?? report.riskLevel})`;
    badge.style.display = 'block';
  }
  if (btn) btn.style.display = 'block';
}

function showReportFromAndroid(report) {
  const levelMap = {
    SAFE:     { ko: '안전',     color: '#4CAF50', desc: '탐지된 위협 없음. 안전한 환경으로 판단됨' },
    INTEREST: { ko: '관심',     color: '#8BC34A', desc: '경미한 의심 징후. 추가 확인 권장' },
    CAUTION:  { ko: '주의',     color: '#FFC107', desc: '복수의 의심 징후 감지. 주의 깊게 점검 필요' },
    DANGER:   { ko: '위험',     color: '#FF9800', desc: '강한 의심 징후. 즉각적인 점검 권장' },
    CRITICAL: { ko: '매우 위험', color: '#F44336', desc: '몰래카메라 의심 강함. 즉시 신고 및 점검 권장' },
  };

  const score = report.riskScore ?? 0;
  const level = levelMap[report.riskLevel] ?? levelMap.SAFE;

  // Score ring
  const arc = document.getElementById('score-arc');
  const offset = 314 - (score / 100) * 314;
  arc.style.stroke = level.color;
  arc.style.strokeDashoffset = offset;

  // Animated counter
  const scoreEl = document.getElementById('score-num');
  let cur = 0;
  const step = Math.max(1, Math.ceil(score / 30));
  const counter = setInterval(() => {
    cur = Math.min(cur + step, score);
    scoreEl.textContent = cur;
    if (cur >= score) clearInterval(counter);
  }, 40);
  scoreEl.style.color = level.color;

  document.getElementById('score-level').textContent = report.riskLevel ?? 'SAFE';
  document.getElementById('score-label-ko').textContent = level.ko;
  document.getElementById('score-label-ko').style.color = level.color;
  document.getElementById('score-desc').textContent = level.desc;

  // Correction factor
  const corrRow = document.getElementById('correction-row');
  const factor = report.correctionFactor ?? 1.0;
  const factorLabels = { 0.7: '신뢰도 하향 보정 (단일 레이어)', 1.2: '교차 확인 가중 보정', 1.5: '전 레이어 양성 — 고위험 보정' };
  const factorKey = Math.round(factor * 10) / 10;
  if (factor !== 1.0) {
    corrRow.classList.add('visible');
    corrRow.textContent = `보정 계수: ×${factor.toFixed(1)} — ${factorLabels[factorKey] ?? ''} | 소요: ${((report.durationMs ?? 0) / 1000).toFixed(1)}s`;
  } else {
    corrRow.classList.remove('visible');
  }

  // Source badge
  corrRow.classList.add('visible');
  corrRow.textContent = `📱 Android 실측 데이터 | 보정 계수: ×${factor.toFixed(1)} | 소요: ${((report.durationMs ?? 0) / 1000).toFixed(1)}s`;

  // Layer breakdown
  const layersEl = document.getElementById('result-layers');
  layersEl.innerHTML = '';
  const layerColors = { WIFI: '#29B6F6', LENS: '#AB47BC', IR: '#CE93D8', MAGNETIC: '#66BB6A' };
  const layerIcons  = { WIFI: '📡', LENS: '🔍', IR: '☀️', MAGNETIC: '🧲' };

  const layerResults = report.layerResults ?? {};
  Object.entries(layerResults).forEach(([type, layer]) => {
    const color = layerColors[type] ?? '#9AA0A6';
    const icon  = layerIcons[type]  ?? '●';
    const contribution = Math.round((layer.weight ?? 0) * (layer.score ?? 0));
    const row = document.createElement('div');
    row.className = 'result-layer-row';
    row.innerHTML = `
      <div class="result-layer-icon">${icon}</div>
      <div class="result-layer-info">
        <div class="result-layer-name">${layer.labelKo ?? type}</div>
        <div class="result-layer-note">가중 기여: +${contribution}점 (${layer.status ?? '—'})</div>
      </div>
      <div class="result-layer-score" style="color:${color}">${layer.score ?? 0}</div>
      <div class="result-layer-bar-wrap">
        <div class="result-layer-bar">
          <div class="result-layer-bar-fill" style="width:${layer.score ?? 0}%;background:${color}"></div>
        </div>
      </div>`;
    layersEl.appendChild(row);
  });

  // Device list
  const devicesEl = document.getElementById('result-devices');
  const devices = report.devices ?? [];
  if (devices.length > 0) {
    devicesEl.innerHTML = `<div class="devices-title">발견된 기기 (${devices.length})</div>`;
    devices.forEach(d => {
      const riskClass = (d.riskScore ?? 0) > 50 ? 'high' : (d.riskScore ?? 0) > 20 ? 'med' : 'low';
      const riskLabel = (d.riskScore ?? 0) > 50 ? '고위험' : (d.riskScore ?? 0) > 20 ? '주의' : '정상';
      const icon = d.isCamera ? '📷' : d.deviceType === 'ROUTER' ? '📡' : '📱';
      const row = document.createElement('div');
      row.className = 'device-row';
      row.innerHTML = `
        <div class="device-icon">${icon}</div>
        <div class="device-info">
          <div class="device-ip">${d.ip}</div>
          <div class="device-vendor">${d.vendor ?? d.hostname ?? d.mac ?? '알 수 없음'}</div>
        </div>
        <div class="device-risk ${riskClass}">${riskLabel}</div>`;
      devicesEl.appendChild(row);
    });
  } else {
    devicesEl.innerHTML = '';
  }

  showScreen('result');
}

// ── Risk scoring (mirrors CalculateRiskUseCase.kt) ──────────────────────────
const WEIGHTS = { wifi: 0.50, lens: 0.20, magnetic: 0.15 };
const WEIGHTS_NO_WIFI = { wifi: 0.00, lens: 0.45, magnetic: 0.25 };
const CORRECTION = [1.0, 0.7, 1.2, 1.5];

const RISK_LEVELS = [
  { id: 'SAFE',     ko: '안전',     min: 0,  max: 20,  color: '#4CAF50', desc: '탐지된 위협 없음. 안전한 환경으로 판단됨' },
  { id: 'INTEREST', ko: '관심',     min: 21, max: 40,  color: '#8BC34A', desc: '경미한 의심 징후. 추가 확인 권장' },
  { id: 'CAUTION',  ko: '주의',     min: 41, max: 60,  color: '#FFC107', desc: '복수의 의심 징후 감지. 주의 깊게 점검 필요' },
  { id: 'DANGER',   ko: '위험',     min: 61, max: 80,  color: '#FF9800', desc: '강한 의심 징후. 즉각적인 점검 권장' },
  { id: 'CRITICAL', ko: '매우 위험', min: 81, max: 100, color: '#F44336', desc: '몰래카메라 의심 강함. 즉시 신고 및 점검 권장' },
];

function getRiskLevel(score) {
  return RISK_LEVELS.find(r => score >= r.min && score <= r.max) || RISK_LEVELS[0];
}

function calculateRisk(layers) {
  const hasWifi = layers.some(l => l.id === 'wifi' && l.score > 0);
  const w = hasWifi ? WEIGHTS : WEIGHTS_NO_WIFI;
  const weighted = layers.reduce((sum, l) => sum + (w[l.id] || 0) * l.score, 0);
  const positiveCount = layers.filter(l => l.score > 0).length;
  const factor = CORRECTION[Math.min(positiveCount, 3)];
  return { score: Math.min(100, Math.round(weighted * factor)), factor };
}

// ── Simulated device database ────────────────────────────────────────────────
const VENDORS = [
  { name: 'Samsung Electronics',   icon: '📱', risk: 0  },
  { name: 'Apple, Inc.',            icon: '🍎', risk: 0  },
  { name: 'ASUS',                   icon: '💻', risk: 5  },
  { name: 'TP-Link Technologies',   icon: '📡', risk: 5  },
  { name: 'Unknown / No OUI',       icon: '❓', risk: 30 },
  { name: 'Hikvision Digital Tech', icon: '📷', risk: 80 },
  { name: 'Dahua Technology',       icon: '📹', risk: 75 },
  { name: 'Reolink Innovation',     icon: '📹', risk: 70 },
  { name: 'Tuya Smart',             icon: '🔌', risk: 55 },
];

function randomIp() {
  return `192.168.1.${Math.floor(Math.random() * 200) + 10}`;
}

function simulateWifiDevices(suspicious = false) {
  const n = 3 + Math.floor(Math.random() * 5);
  const devices = [];
  for (let i = 0; i < n; i++) {
    let pool = suspicious && i === n - 1
      ? VENDORS.filter(v => v.risk > 50)
      : VENDORS.filter(v => v.risk <= 30);
    const v = pool[Math.floor(Math.random() * pool.length)];
    devices.push({ ip: randomIp(), vendor: v.name, icon: v.icon, risk: v.risk });
  }
  return devices;
}

// ── App state ────────────────────────────────────────────────────────────────
let state = {
  screen: 'home',
  scanMode: 'quick',
  scanStart: 0,
  timerInterval: null,
  cameraStream: null,
  motionListener: null,
  results: { wifi: null, lens: null, magnetic: null },
  devices: [],
};

// ── Screen navigation ────────────────────────────────────────────────────────
function showScreen(id) {
  const current = document.querySelector('.screen.active');
  if (current) {
    current.classList.add('slide-out');
    setTimeout(() => current.classList.remove('active', 'slide-out'), 250);
  }
  const next = document.getElementById('screen-' + id);
  setTimeout(() => next.classList.add('active'), 10);
  state.screen = id;
}

// ── Utility ──────────────────────────────────────────────────────────────────
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
function rand(min, max) { return min + Math.floor(Math.random() * (max - min + 1)); }
function lerp(a, b, t) { return a + (b - a) * t; }

function setProgress(fillId, pct) {
  document.getElementById(fillId).style.width = pct + '%';
}

function setStatus(id, text) {
  document.getElementById(id).textContent = text;
}

function setCardState(id, st) {
  document.getElementById(id).dataset.state = st;
}

function setCheck(id, cls) {
  const el = document.getElementById(id);
  el.className = 'layer-check ' + cls;
}

function addLog(containerId, text, type = 'info') {
  const log = document.getElementById(containerId);
  const entry = document.createElement('div');
  entry.className = 'log-entry';
  const dot = document.createElement('div');
  dot.className = 'log-dot ' + type;
  const span = document.createElement('span');
  span.textContent = text;
  entry.appendChild(dot);
  entry.appendChild(span);
  log.appendChild(entry);
}

// ── Timer ────────────────────────────────────────────────────────────────────
function startTimer() {
  state.scanStart = Date.now();
  state.timerInterval = setInterval(() => {
    const s = Math.floor((Date.now() - state.scanStart) / 1000);
    document.getElementById('scan-timer').textContent = s + 's';
  }, 500);
}

function stopTimer() {
  clearInterval(state.timerInterval);
}

// ── WiFi Layer (simulation) ──────────────────────────────────────────────────
async function runWifiLayer() {
  setCardState('card-wifi', 'active');
  setStatus('wifi-status', '네트워크 스캔 중...');
  addLog('wifi-log', 'ARP 테이블 파싱 중...', 'info');
  setProgress('wifi-fill', 10);
  await sleep(600);

  // Simulate device discovery
  const suspicious = Math.random() < 0.3; // 30% chance of suspicious device
  const devices = simulateWifiDevices(suspicious);
  state.devices = devices;

  let maxRisk = 0;
  for (let i = 0; i < devices.length; i++) {
    await sleep(400 + rand(0, 300));
    const d = devices[i];
    addLog('wifi-log', `${d.ip} — ${d.vendor}`, d.risk > 50 ? 'warn' : 'ok');
    setProgress('wifi-fill', 10 + Math.round((i + 1) / devices.length * 80));
    if (d.risk > maxRisk) maxRisk = d.risk;
  }

  await sleep(400);
  addLog('wifi-log', `mDNS + SSDP 탐색 완료 (${devices.length}개 기기)`, 'info');
  setProgress('wifi-fill', 100);

  const score = maxRisk;
  setStatus('wifi-status', score > 50 ? `⚠ 의심 기기 감지 (점수 ${score})` : `${devices.length}개 기기 발견 — 이상 없음`);
  setCardState('card-wifi', 'done');
  setCheck('wifi-check', score > 30 ? 'warn' : 'done');

  return score;
}

// ── Camera / Lens Layer ──────────────────────────────────────────────────────
async function runLensLayer() {
  setCardState('card-lens', 'active');
  setStatus('lens-status', '카메라 권한 요청 중...');
  setProgress('lens-fill', 5);

  let score = 0;

  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'environment', width: 320, height: 240 }
    });
    state.cameraStream = stream;

    const video = document.getElementById('cam-video');
    const canvas = document.getElementById('cam-canvas');
    const ctx = canvas.getContext('2d');
    const wrap = document.getElementById('camera-wrap');

    video.srcObject = stream;
    wrap.classList.remove('hidden');
    canvas.width = 320;
    canvas.height = 240;

    setStatus('lens-status', '역반사 분석 중...');
    addLog('lens-log', '카메라 스트림 활성화', 'info');
    setProgress('lens-fill', 20);
    await sleep(800);

    // Analyze frames for bright spots (retroreflection simulation)
    let brightSpots = 0;
    for (let frame = 0; frame < 8; frame++) {
      await sleep(500);
      ctx.drawImage(video, 0, 0, 320, 240);
      const imgData = ctx.getImageData(0, 0, 320, 240);
      const data = imgData.data;

      // Count pixels brighter than threshold (simple retroreflection heuristic)
      let bright = 0;
      for (let i = 0; i < data.length; i += 4) {
        if (data[i] > 230 && data[i+1] > 230 && data[i+2] > 230) bright++;
      }
      const brightRatio = bright / (320 * 240);

      // Highlight bright regions
      for (let i = 0; i < data.length; i += 4) {
        if (data[i] > 230 && data[i+1] > 230 && data[i+2] > 230) {
          data[i] = 171; data[i+1] = 71; data[i+2] = 188; // purple overlay
        }
      }
      ctx.putImageData(imgData, 0, 0);

      if (brightRatio > 0.005) brightSpots++;
      setProgress('lens-fill', 20 + Math.round((frame + 1) / 8 * 70));
    }

    score = Math.min(80, brightSpots * 12);
    addLog('lens-log', `${brightSpots}개 밝은 반사점 감지`, brightSpots > 3 ? 'warn' : 'ok');

    // Stop camera
    stream.getTracks().forEach(t => t.stop());
    state.cameraStream = null;

  } catch (e) {
    // Camera unavailable — simulation mode
    addLog('lens-log', '카메라 사용 불가 — 시뮬레이션 모드', 'info');
    for (let i = 1; i <= 5; i++) {
      await sleep(600);
      setProgress('lens-fill', i * 18);
    }
    score = rand(0, 20); // low simulated score
  }

  setProgress('lens-fill', 100);
  setStatus('lens-status', score > 30 ? `⚠ 반사 이상 감지 (점수 ${score})` : '반사 이상 없음');
  setCardState('card-lens', 'done');
  setCheck('lens-check', score > 30 ? 'warn' : 'done');

  return score;
}

// ── Magnetic Layer ───────────────────────────────────────────────────────────
async function runMagneticLayer() {
  setCardState('card-mag', 'active');
  setStatus('mag-status', '자기장 센서 초기화...');
  setProgress('mag-fill', 5);

  const meter = document.getElementById('mag-meter');
  const bar = document.getElementById('mag-bar');
  const needle = document.getElementById('mag-needle');
  const reading = document.getElementById('mag-reading');
  meter.classList.add('visible');

  let score = 0;
  let hasMotion = false;
  let readings = [];

  // Try DeviceMotion API (requires permission on iOS)
  const hasSensor = typeof DeviceMotionEvent !== 'undefined'
    && typeof DeviceMotionEvent.requestPermission === 'function';

  if (hasSensor) {
    try {
      const perm = await DeviceMotionEvent.requestPermission();
      if (perm === 'granted') hasMotion = true;
    } catch (e) {}
  } else if (window.DeviceMotionEvent) {
    hasMotion = true;
  }

  if (hasMotion) {
    addLog('mag-log', '자기장 센서 활성화', 'info');
    const listener = (e) => {
      const a = e.accelerationIncludingGravity;
      if (a) {
        const mag = Math.sqrt(a.x ** 2 + a.y ** 2 + a.z ** 2).toFixed(1);
        readings.push(parseFloat(mag));
        reading.textContent = mag + ' µT';
        const pct = Math.min(100, (parseFloat(mag) / 50) * 100);
        bar.style.width = pct + '%';
        needle.style.left = pct + '%';
      }
    };
    window.addEventListener('devicemotion', listener);
    state.motionListener = listener;

    for (let i = 0; i < 8; i++) {
      await sleep(600);
      setProgress('mag-fill', 10 + i * 11);
    }

    window.removeEventListener('devicemotion', listener);
    state.motionListener = null;

    if (readings.length > 0) {
      const avg = readings.reduce((a, b) => a + b, 0) / readings.length;
      const variance = readings.reduce((a, b) => a + (b - avg) ** 2, 0) / readings.length;
      score = Math.min(80, Math.round(variance * 2));
    }
  } else {
    // Simulation
    addLog('mag-log', '센서 없음 — 시뮬레이션 모드', 'info');
    let simVal = 20;
    for (let i = 0; i < 10; i++) {
      await sleep(400);
      simVal += rand(-4, 6);
      simVal = Math.max(10, Math.min(60, simVal));
      reading.textContent = simVal.toFixed(1) + ' µT';
      const pct = (simVal / 60) * 100;
      bar.style.width = pct + '%';
      needle.style.left = pct + '%';
      setProgress('mag-fill', 10 + i * 9);
    }
    score = rand(0, 15);
  }

  setProgress('mag-fill', 100);
  setStatus('mag-status', score > 30 ? `⚠ 자기장 이상 감지 (점수 ${score})` : '자기장 정상 범위');
  addLog('mag-log', score > 30 ? `이상 변동 감지 — 편차 높음` : `측정값 정상 범위`, score > 30 ? 'warn' : 'ok');
  setCardState('card-mag', 'done');
  setCheck('mag-check', score > 30 ? 'warn' : 'done');

  return score;
}

// ── Full scan orchestration ──────────────────────────────────────────────────
async function runScan(mode) {
  state.scanMode = mode;
  state.results = { wifi: 0, lens: 0, magnetic: 0 };
  state.devices = [];

  // Reset UI
  ['wifi', 'lens', 'mag'].forEach(id => {
    document.getElementById(id + '-fill').style.width = '0%';
    document.getElementById('card-' + (id === 'mag' ? 'mag' : id)).dataset.state = 'idle';
    document.getElementById(id === 'mag' ? 'mag-status' : id + '-status').textContent = '대기 중';
    document.getElementById(id === 'mag' ? 'mag-log' : id + '-log').innerHTML = '';
    document.getElementById(id === 'mag' ? 'mag-check' : id + '-check').className = 'layer-check';
  });
  document.getElementById('cam-video').srcObject = null;
  document.getElementById('camera-wrap').classList.add('hidden');
  document.getElementById('mag-meter').classList.remove('visible');
  document.getElementById('scan-overall').style.width = '0%';

  showScreen('scan');
  await sleep(200);
  startTimer();

  // Run layers
  document.getElementById('scan-phase').textContent = 'Layer 1: Wi-Fi 네트워크 스캔 중...';
  state.results.wifi = await runWifiLayer();
  setProgress('scan-overall', 40);

  if (mode !== 'wifi') {
    document.getElementById('scan-phase').textContent = 'Layer 2: 렌즈 감지 분석 중...';
    state.results.lens = await runLensLayer();
    setProgress('scan-overall', 72);

    document.getElementById('scan-phase').textContent = 'Layer 3: 자기장 측정 중...';
    state.results.magnetic = await runMagneticLayer();
    setProgress('scan-overall', 100);
  } else {
    setProgress('scan-overall', 100);
  }

  stopTimer();
  document.getElementById('scan-phase').textContent = '스캔 완료 — 결과 분석 중...';
  await sleep(600);

  showResult();
}

// ── Result screen ────────────────────────────────────────────────────────────
function showResult() {
  const layers = [
    { id: 'wifi',     label: 'Wi-Fi 네트워크', icon: '📡', score: state.results.wifi,     color: '#29B6F6' },
    { id: 'lens',     label: '렌즈 감지',      icon: '🔍', score: state.results.lens,     color: '#AB47BC' },
    { id: 'magnetic', label: '자기장 (EMF)',    icon: '🧲', score: state.results.magnetic, color: '#66BB6A' },
  ].filter(l => l.score !== null && l.score !== undefined);

  const { score, factor } = calculateRisk(layers);
  const level = getRiskLevel(score);

  // Score ring
  const arc = document.getElementById('score-arc');
  const circumference = 314;
  const offset = circumference - (score / 100) * circumference;
  arc.style.stroke = level.color;
  arc.style.strokeDashoffset = offset;

  // Animated score counter
  const scoreEl = document.getElementById('score-num');
  let cur = 0;
  const step = Math.ceil(score / 30);
  const counter = setInterval(() => {
    cur = Math.min(cur + step, score);
    scoreEl.textContent = cur;
    if (cur >= score) clearInterval(counter);
  }, 40);

  scoreEl.style.color = level.color;
  document.getElementById('score-level').textContent = level.id;
  document.getElementById('score-label-ko').textContent = level.ko;
  document.getElementById('score-label-ko').style.color = level.color;
  document.getElementById('score-desc').textContent = level.desc;

  // Correction factor
  const corrRow = document.getElementById('correction-row');
  const positives = layers.filter(l => l.score > 0).length;
  if (positives > 0) {
    corrRow.classList.add('visible');
    const factorLabels = { 0.7: '신뢰도 하향 보정 (단일 레이어)', 1.2: '교차 확인 가중 보정', 1.5: '전 레이어 양성 — 고위험 보정' };
    corrRow.textContent = `보정 계수: ×${factor.toFixed(1)} — ${factorLabels[factor] || ''}`;
  } else {
    corrRow.classList.remove('visible');
  }

  // Layer breakdown
  const layersEl = document.getElementById('result-layers');
  layersEl.innerHTML = '';
  const wt = state.results.wifi > 0 ? WEIGHTS : WEIGHTS_NO_WIFI;

  layers.forEach(l => {
    const contribution = Math.round((wt[l.id] || 0) * l.score);
    const row = document.createElement('div');
    row.className = 'result-layer-row';
    row.innerHTML = `
      <div class="result-layer-icon">${l.icon}</div>
      <div class="result-layer-info">
        <div class="result-layer-name">${l.label}</div>
        <div class="result-layer-note">가중 기여: +${contribution}점</div>
      </div>
      <div class="result-layer-score" style="color:${l.color}">${l.score}</div>
      <div class="result-layer-bar-wrap">
        <div class="result-layer-bar">
          <div class="result-layer-bar-fill" style="width:${l.score}%;background:${l.color}"></div>
        </div>
      </div>`;
    layersEl.appendChild(row);
  });

  // Device list
  const devicesEl = document.getElementById('result-devices');
  if (state.devices.length > 0) {
    devicesEl.innerHTML = `<div class="devices-title">발견된 기기 (${state.devices.length})</div>`;
    state.devices.forEach(d => {
      const riskClass = d.risk > 50 ? 'high' : d.risk > 20 ? 'med' : 'low';
      const riskLabel = d.risk > 50 ? '고위험' : d.risk > 20 ? '주의' : '정상';
      const row = document.createElement('div');
      row.className = 'device-row';
      row.innerHTML = `
        <div class="device-icon">${d.icon}</div>
        <div class="device-info">
          <div class="device-ip">${d.ip}</div>
          <div class="device-vendor">${d.vendor}</div>
        </div>
        <div class="device-risk ${riskClass}">${riskLabel}</div>`;
      devicesEl.appendChild(row);
    });
  } else {
    devicesEl.innerHTML = '';
  }

  showScreen('result');
}

// ── Event listeners ──────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {

  // SSE 연결 시작 — Android 앱 결과 실시간 수신
  connectSSE();

  // 최신 결과 즉시 fetch (페이지 로드 시 이미 스캔된 결과 표시)
  fetch(API_BASE + '/api/report/latest')
    .then(r => r.ok ? r.json() : null)
    .then(report => {
      if (report) {
        setAndroidReady(report);
      }
    })
    .catch(() => {});

  document.getElementById('btn-quick-scan').addEventListener('click', () => {
    runScan('quick');
  });

  document.querySelectorAll('.sub-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const mode = btn.dataset.mode;
      runScan(mode === 'full' ? 'full' : mode === 'wifi' ? 'wifi' : 'quick');
    });
  });

  document.getElementById('btn-cancel').addEventListener('click', () => {
    stopTimer();
    if (state.cameraStream) {
      state.cameraStream.getTracks().forEach(t => t.stop());
      state.cameraStream = null;
    }
    if (state.motionListener) {
      window.removeEventListener('devicemotion', state.motionListener);
      state.motionListener = null;
    }
    showScreen('home');
  });

  document.getElementById('btn-home').addEventListener('click', () => {
    showScreen('home');
  });

  document.getElementById('btn-rescan').addEventListener('click', () => {
    runScan(state.scanMode);
  });

  document.getElementById('btn-view-android').addEventListener('click', () => {
    if (_lastAndroidReport) showReportFromAndroid(_lastAndroidReport);
  });
});
