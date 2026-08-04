// Recon page — trigger a run, list results for that job, resolve a break.
// Same fetch/demo-fallback convention as trades.js.
(function () {
  'use strict';

  const form      = document.getElementById('recon-run-form');
  const runStatus = document.getElementById('run-status');
  const tbody     = document.getElementById('recon-tbody');
  if (!form || !tbody) return;

  let rows = []; // canonical data array, mirrors trades.js's pattern

  function esc(v) {
    return v == null ? '' : String(v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function renderRows() {
    if (!rows.length) {
      tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:2rem;opacity:0.5">No breaks for this job.</td></tr>';
      return;
    }
    tbody.innerHTML = rows.map(function (r) {
      return '<tr data-id="' + esc(r.id) + '">' +
        '<td>' + esc(r.id)              + '</td>' +
        '<td>' + esc(r.tradeId)         + '</td>' +
        '<td>' + esc(r.discrepancyType) + '</td>' +
        '<td>' + esc(r.status)          + '</td>' +
        '<td>' + esc(r.detectedAt)      + '</td>' +
        '<td>' + (r.status === 'RESOLVED'
                    ? '&mdash;'
                    : '<button type="button" class="resolve-btn" data-id="' + esc(r.id) + '">Resolve</button>') +
        '</td></tr>';
    }).join('');
  }

  function loadResults(jobId) {
    fetch('/api/v1/recon/jobs/' + encodeURIComponent(jobId) + '/results')
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) {
        rows = data;
        renderRows();
      })
      .catch(function () {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:2rem;opacity:0.5">Could not load results.</td></tr>';
      });
  }

  tbody.addEventListener('click', function (e) {
    if (!e.target.classList.contains('resolve-btn')) return;
    var id = e.target.dataset.id;
    fetch('/api/v1/recon/results/' + encodeURIComponent(id) + '/resolve', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ note: 'Resolved from static dashboard' })
    })
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (updated) {
        rows = rows.map(function (r) { return r.id === updated.id ? updated : r; });
        renderRows();
      })
      .catch(function () {
        runStatus.textContent = 'Could not resolve break ' + id + '.';
      });
  });

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    var from = document.getElementById('from-date').value;
    var to   = document.getElementById('to-date').value;
    runStatus.textContent = 'Running recon job…';

    fetch('/api/v1/recon/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ from: from, to: to })
    })
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (job) {
        runStatus.textContent = 'Job ' + job.jobId + ' — ' + job.status;
        loadResults(job.jobId);
      })
      .catch(function () {
        runStatus.textContent = 'Could not reach the recon API — showing demo data.';
        rows = [
          { id: 1, tradeId: 101, discrepancyType: 'PRICE_MISMATCH',    status: 'OPEN',     detectedAt: '2026-06-03T10:00:00Z' },
          { id: 2, tradeId: 102, discrepancyType: 'MISSING_EXTERNAL',  status: 'OPEN',     detectedAt: '2026-06-03T10:05:00Z' },
          { id: 3, tradeId: 103, discrepancyType: 'QUANTITY_MISMATCH', status: 'RESOLVED', detectedAt: '2026-06-02T09:00:00Z' },
        ];
        renderRows();
      });
  });
})();
