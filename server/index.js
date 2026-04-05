'use strict';

const express = require('express');
const cors = require('cors');

const app = express();
const PORT = process.env.PORT || 3000;

// ── In-memory store ──────────────────────────────────────────────────────────
let latestReport = null;          // 가장 최근 ScanReport JSON
const sseClients = new Set();     // 연결된 SSE 클라이언트 (웹 브라우저)

// ── Middleware ───────────────────────────────────────────────────────────────
app.use(cors());
app.use(express.json({ limit: '2mb' }));

// ── POST /api/report  (Android → Server) ────────────────────────────────────
// Android 앱이 스캔 완료 후 ScanReport JSON을 전송한다.
app.post('/api/report', (req, res) => {
  const report = req.body;

  if (!report || !report.id) {
    return res.status(400).json({ error: 'Invalid report: missing id' });
  }

  report.receivedAt = Date.now();
  latestReport = report;

  console.log(`[report] id=${report.id} score=${report.riskScore} devices=${report.devices?.length ?? 0}`);

  // SSE로 연결된 모든 웹 클라이언트에게 즉시 push
  const payload = `data: ${JSON.stringify(report)}\n\n`;
  for (const client of sseClients) {
    try { client.write(payload); } catch (_) { sseClients.delete(client); }
  }

  res.json({ ok: true, clientCount: sseClients.size });
});

// ── GET /api/report/latest  (Web → 최신 결과 polling) ───────────────────────
app.get('/api/report/latest', (req, res) => {
  if (!latestReport) {
    return res.status(404).json({ error: 'No report yet' });
  }
  res.json(latestReport);
});

// ── GET /api/report/stream  (SSE — 실시간 push to Web) ──────────────────────
app.get('/api/report/stream', (req, res) => {
  res.writeHead(200, {
    'Content-Type':  'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection':    'keep-alive',
    'X-Accel-Buffering': 'no',       // nginx 버퍼링 비활성화
  });

  // 연결 직후 최신 리포트가 있으면 즉시 전송
  if (latestReport) {
    res.write(`data: ${JSON.stringify(latestReport)}\n\n`);
  } else {
    res.write(': waiting for Android scan\n\n');
  }

  sseClients.add(res);
  console.log(`[sse] client connected (total: ${sseClients.size})`);

  // keep-alive ping (30초마다)
  const ping = setInterval(() => {
    try { res.write(': ping\n\n'); } catch (_) { clearInterval(ping); }
  }, 30_000);

  req.on('close', () => {
    clearInterval(ping);
    sseClients.delete(res);
    console.log(`[sse] client disconnected (total: ${sseClients.size})`);
  });
});

// ── GET /api/health ──────────────────────────────────────────────────────────
app.get('/api/health', (req, res) => {
  res.json({ ok: true, clients: sseClients.size, hasReport: !!latestReport });
});

// ── Start ────────────────────────────────────────────────────────────────────
app.listen(PORT, '0.0.0.0', () => {
  console.log(`SearCam API server listening on :${PORT}`);
});
