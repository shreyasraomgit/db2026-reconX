// TICKET-ADV104 / ADV105 — live EventSource feed with safe bounded rendering.
(function () {
  'use strict';
  const feed = document.getElementById('trade-feed');
  const badge = document.getElementById('sse-status');
  if (!feed) return;
  let sse;
  const escapeHtml = value => String(value == null ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  const quantity = new Intl.NumberFormat('en-US');
  const price = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 4 });
  function setStatus(text) { if (badge) badge.textContent = text; }
  function prependTradeRow(trade) {
    const status = { MATCHED: 'matched', BREAK: 'break', UNMATCHED: 'break' }[trade.status] || 'pending';
    const card = document.createElement('article');
    card.className = 'trade-card trade-card--' + status + ' trade-card--new';
    card.innerHTML = '<strong>' + escapeHtml(trade.tradeRef) + '</strong> ' +
      '<span>' + escapeHtml(trade.symbol) + '</span> ' +
      '<span>qty=' + quantity.format(trade.qty != null ? trade.qty : (trade.quantity || 0)) + '</span> ' +
      '<span>price=' + price.format(trade.price || 0) + '</span> ' +
      '<span>[' + escapeHtml(trade.status) + ']</span>';
    feed.prepend(card);
    setTimeout(() => card.classList.remove('trade-card--new'), 500);
    while (feed.children.length > 50) feed.lastElementChild.remove();
  }
  function connect() {
    sse = new EventSource('/api/v1/trades/stream');
    sse.onopen = () => setStatus('Live');
    sse.onmessage = event => { try { prependTradeRow(JSON.parse(event.data)); } catch (_) { /* ignore invalid event */ } };
    sse.onerror = () => setStatus('Reconnecting…'); // EventSource reconnects itself.
  }
  window.addEventListener('beforeunload', () => { if (sse) sse.close(); });
  connect();
})();
