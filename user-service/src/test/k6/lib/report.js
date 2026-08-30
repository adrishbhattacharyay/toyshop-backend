/**
 * Shared handleSummary helper — produces both JSON and HTML reports.
 *
 * Usage in any test file:
 *   import { htmlReport } from './lib/report.js';
 *   export function handleSummary(data) { return htmlReport(data, 'my-test'); }
 *
 * Outputs:
 *   reports/<testName>-summary.json
 *   reports/<testName>-report.html
 */
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

/**
 * @param {Object} data   - k6 summary data object
 * @param {string} name   - test name used in filenames and the HTML title
 * @returns {Object}      - k6 handleSummary return value
 */
export function htmlReport(data, name) {
  const reportDir = __ENV.REPORT_DIR || 'reports';
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');

  return {
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
    [`${reportDir}/${name}-summary.json`]: JSON.stringify(data, null, 2),
    [`${reportDir}/${name}-report.html`]: buildHtml(data, name, timestamp),
  };
}

// ---------------------------------------------------------------------------
// Internal HTML builder — no external dependencies
// ---------------------------------------------------------------------------
function buildHtml(data, title, timestamp) {
  const metrics = data.metrics || {};

  // Collect threshold pass/fail status
  const thresholdRows = Object.entries(metrics)
    .filter(([, m]) => m.thresholds && Object.keys(m.thresholds).length > 0)
    .flatMap(([metricName, m]) =>
      Object.entries(m.thresholds).map(([expr, t]) => ({
        metric: metricName,
        expr,
        ok: !t.ok === false ? t.ok : true,
      }))
    );

  const allPassed = thresholdRows.every((r) => r.ok);

  // Key HTTP metrics
  const reqDuration = metrics['http_req_duration'] || {};
  const reqFailed = metrics['http_req_failed'] || {};
  const reqCount = metrics['http_reqs'] || {};

  const p95 = reqDuration.values ? reqDuration.values['p(95)'] : null;
  const p99 = reqDuration.values ? reqDuration.values['p(99)'] : null;
  const avgDur = reqDuration.values ? reqDuration.values['avg'] : null;
  const maxDur = reqDuration.values ? reqDuration.values['max'] : null;
  const failRate = reqFailed.values ? (reqFailed.values.rate * 100).toFixed(2) : null;
  const totalReqs = reqCount.values ? reqCount.values.count : null;
  const rps = reqCount.values ? reqCount.values.rate.toFixed(2) : null;

  const statusColor = allPassed ? '#22c55e' : '#ef4444';
  const statusText = allPassed ? '✅ ALL THRESHOLDS PASSED' : '❌ SOME THRESHOLDS FAILED';

  const thresholdTableRows = thresholdRows
    .map(
      (r) => `
      <tr>
        <td>${r.metric}</td>
        <td><code>${r.expr}</code></td>
        <td style="color:${r.ok ? '#22c55e' : '#ef4444'};font-weight:bold">${r.ok ? 'PASS' : 'FAIL'}</td>
      </tr>`
    )
    .join('');

  // All custom metrics
  const customMetricRows = Object.entries(metrics)
    .filter(([, m]) => m.type === 'trend' || m.type === 'rate' || m.type === 'counter')
    .map(([name, m]) => {
      const vals = m.values || {};
      if (m.type === 'trend') {
        return `<tr>
          <td>${name}</td>
          <td>${m.type}</td>
          <td>${fmt(vals.avg)} ms</td>
          <td>${fmt(vals['p(95)'])} ms</td>
          <td>${fmt(vals['p(99)'])} ms</td>
          <td>${fmt(vals.max)} ms</td>
        </tr>`;
      }
      if (m.type === 'rate') {
        return `<tr>
          <td>${name}</td>
          <td>${m.type}</td>
          <td colspan="4">${(vals.rate * 100).toFixed(2)}%</td>
        </tr>`;
      }
      return `<tr>
        <td>${name}</td>
        <td>${m.type}</td>
        <td colspan="4">${vals.count || 0}</td>
      </tr>`;
    })
    .join('');

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>k6 Report — ${title}</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
           background: #0f172a; color: #e2e8f0; padding: 2rem; }
    h1 { font-size: 1.8rem; margin-bottom: 0.25rem; }
    .subtitle { color: #94a3b8; font-size: 0.9rem; margin-bottom: 2rem; }
    .status-banner { background: ${statusColor}22; border: 2px solid ${statusColor};
                     border-radius: 8px; padding: 1rem 1.5rem; margin-bottom: 2rem;
                     font-size: 1.1rem; font-weight: bold; color: ${statusColor}; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
            gap: 1rem; margin-bottom: 2rem; }
    .card { background: #1e293b; border-radius: 8px; padding: 1.25rem; }
    .card .label { font-size: 0.75rem; color: #94a3b8; text-transform: uppercase;
                   letter-spacing: 0.05em; margin-bottom: 0.5rem; }
    .card .value { font-size: 1.6rem; font-weight: bold; }
    .card .unit { font-size: 0.8rem; color: #94a3b8; }
    section { margin-bottom: 2rem; }
    h2 { font-size: 1.1rem; margin-bottom: 0.75rem; color: #94a3b8;
         text-transform: uppercase; letter-spacing: 0.05em; }
    table { width: 100%; border-collapse: collapse; background: #1e293b;
            border-radius: 8px; overflow: hidden; }
    th { background: #334155; padding: 0.75rem 1rem; text-align: left;
         font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.05em; }
    td { padding: 0.65rem 1rem; border-top: 1px solid #334155; font-size: 0.9rem; }
    tr:hover td { background: #263148; }
    code { background: #334155; padding: 0.1rem 0.4rem; border-radius: 4px;
           font-size: 0.85rem; }
    footer { margin-top: 3rem; color: #475569; font-size: 0.8rem; text-align: center; }
  </style>
</head>
<body>
  <h1>k6 Performance Report</h1>
  <p class="subtitle">${title} &nbsp;·&nbsp; ${timestamp}</p>

  <div class="status-banner">${statusText}</div>

  <div class="grid">
    <div class="card">
      <div class="label">Total Requests</div>
      <div class="value">${totalReqs ?? '—'}</div>
    </div>
    <div class="card">
      <div class="label">Req/s</div>
      <div class="value">${rps ?? '—'}</div>
    </div>
    <div class="card">
      <div class="label">Avg Duration</div>
      <div class="value">${fmt(avgDur)}</div>
      <div class="unit">ms</div>
    </div>
    <div class="card">
      <div class="label">p(95) Duration</div>
      <div class="value">${fmt(p95)}</div>
      <div class="unit">ms</div>
    </div>
    <div class="card">
      <div class="label">p(99) Duration</div>
      <div class="value">${fmt(p99)}</div>
      <div class="unit">ms</div>
    </div>
    <div class="card">
      <div class="label">Max Duration</div>
      <div class="value">${fmt(maxDur)}</div>
      <div class="unit">ms</div>
    </div>
    <div class="card">
      <div class="label">Error Rate</div>
      <div class="value" style="color:${parseFloat(failRate) > 1 ? '#ef4444' : '#22c55e'}">${failRate ?? '—'}%</div>
    </div>
  </div>

  <section>
    <h2>Thresholds</h2>
    <table>
      <thead><tr><th>Metric</th><th>Expression</th><th>Result</th></tr></thead>
      <tbody>${thresholdTableRows || '<tr><td colspan="3">No thresholds defined</td></tr>'}</tbody>
    </table>
  </section>

  <section>
    <h2>Custom Metrics</h2>
    <table>
      <thead>
        <tr><th>Metric</th><th>Type</th><th>Avg</th><th>p(95)</th><th>p(99)</th><th>Max</th></tr>
      </thead>
      <tbody>${customMetricRows || '<tr><td colspan="6">No custom metrics</td></tr>'}</tbody>
    </table>
  </section>

  <footer>Generated by k6 · ToyShop Performance Suite</footer>
</body>
</html>`;
}

function fmt(val) {
  if (val === null || val === undefined) return '—';
  return Math.round(val).toString();
}
