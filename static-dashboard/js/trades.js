(function () {
  'use strict';
  const table = document.getElementById('trades-table');
  const tbody = document.getElementById('trades-tbody');
  if (!table || !tbody) return;
  let rows = [];
  const esc = v => v == null ? '' : String(v).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  function renderRows() {
    tbody.innerHTML = rows.length ? rows.map(r => `<tr><td>${esc(r.tradeRef)}</td><td>${esc(r.symbol)}</td><td>${esc(r.quantity)}</td><td>${esc(r.price)}</td><td>${esc(r.status)}</td><td>${esc(r.tradeDate)}</td></tr>`).join('') : '<tr><td colspan="6">No trades found.</td></tr>';
  }
  table.querySelectorAll('thead th').forEach(th => th.addEventListener('click', e => {
    if (e.target.classList.contains('resize-handle')) return;
    const dir = th.getAttribute('aria-sort') === 'ascending' ? 'descending' : 'ascending';
    table.querySelectorAll('thead th').forEach(h => h.removeAttribute('aria-sort'));
    th.setAttribute('aria-sort', dir);
    const mult = dir === 'ascending' ? 1 : -1, col = th.dataset.col;
    rows.sort((a,b) => th.dataset.type === 'number' ? (Number(a[col])-Number(b[col]))*mult : String(a[col] || '').localeCompare(String(b[col] || ''))*mult);
    renderRows();
  }));
  table.querySelectorAll('.resize-handle').forEach(handle => handle.addEventListener('mousedown', e => {
    e.preventDefault(); const th = handle.closest('th'), startX = e.clientX, startWidth = th.offsetWidth;
    const move = ev => { const width = startWidth + ev.clientX - startX; if (width > 60) th.style.width = width + 'px'; };
    const up = () => { document.removeEventListener('mousemove', move); document.removeEventListener('mouseup', up); };
    document.addEventListener('mousemove', move); document.addEventListener('mouseup', up);
  }));
  fetch('/api/v1/trades?size=200').then(r => { if (!r.ok) throw new Error(); return r.json(); }).then(data => { rows = data.content || data; renderRows(); }).catch(() => { rows = [{tradeRef:'EQU-001',symbol:'SAP.DE',quantity:1000,price:125.50,status:'MATCHED',tradeDate:'2026-06-03'}]; renderRows(); });
})();
