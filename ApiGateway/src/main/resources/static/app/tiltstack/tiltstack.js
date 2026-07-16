// ---- TiltStack: a side-scrolling row of glass cards on the shared 3D tilt (picture-1 angle) ----
// Each card shows a 4:3 avatar (image or iconized fallback). Hover raises the card and fades
// its name in above; drag or scroll moves the row sideways.
(function () {
  function initials(name) {
    var p = String(name || '').trim().split(/\s+/);
    return (((p[0] || '')[0] || '') + ((p[1] || '')[0] || '')).toUpperCase() || '?';
  }
  function e(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
    return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }

  // items: [{ id, name, subtitle, avatar, status }]
  // opts: { onClick(item), showStatus, statusBadge(status)->cssClass, emptyText, highlightId }
  window.renderTiltStack = function (container, items, opts) {
    opts = opts || {};
    if (!items || !items.length) {
      container.innerHTML = '<div class="tilt-empty">' + e(opts.emptyText || 'Nothing to show yet.') + '</div>';
      return;
    }
    container.innerHTML = '<div class="tilt-stack">' + items.map(function (it) {
      var face = it.avatar
        ? '<img class="tc-img" src="' + e(it.avatar) + '" alt="' + e(it.name) + '">'
        : '<span class="tc-fallback">' + e(initials(it.name)) + '</span>';
      var badge = (opts.showStatus && it.status)
        ? '<span class="tc-status badge ' + (opts.statusBadge ? opts.statusBadge(it.status) : '') + '">' + e(it.status) + '</span>'
        : '';
      var hot = (opts.highlightId != null && String(opts.highlightId) === String(it.id)) ? ' spotlight' : '';
      return '<div class="tilt-card' + hot + '" data-id="' + e(it.id) + '" tabindex="0">'
        + '<div class="tc-name">' + e(it.name) + (it.subtitle ? '<span class="tc-sub">' + e(it.subtitle) + '</span>' : '') + '</div>'
        + '<div class="tc-face">' + face + badge + '</div>'
        + '</div>';
    }).join('') + '</div>';
    if (window.lucide) lucide.createIcons({ root: container });

    var strip = container.querySelector('.tilt-stack');

    // Drag-to-scroll (pointer), plus native wheel/trackpad horizontal scroll.
    var drag = null;
    strip.addEventListener('pointerdown', function (ev) {
      drag = { x: ev.clientX, left: strip.scrollLeft, moved: false };
    });
    strip.addEventListener('pointermove', function (ev) {
      if (!drag) return;
      var dx = ev.clientX - drag.x;
      if (Math.abs(dx) > 3) drag.moved = true;
      strip.scrollLeft = drag.left - dx;
    });
    function endDrag() { setTimeout(function () { if (drag) drag.wasMoved = drag.moved; drag = null; }, 0); }
    strip.addEventListener('pointerup', endDrag);
    strip.addEventListener('pointerleave', endDrag);
    strip.addEventListener('wheel', function (ev) {
      if (Math.abs(ev.deltaY) > Math.abs(ev.deltaX)) { strip.scrollLeft += ev.deltaY; ev.preventDefault(); }
    }, { passive: false });

    if (opts.onClick) {
      strip.querySelectorAll('.tilt-card').forEach(function (card) {
        function fire() {
          if (drag && drag.moved) return;      // ignore the click that ends a drag
          var it = items.find(function (x) { return String(x.id) === card.dataset.id; });
          if (it) opts.onClick(it);
        }
        card.addEventListener('click', fire);
        card.addEventListener('keydown', function (ev) { if (ev.key === 'Enter') fire(); });
      });
    }
  };

  // Bring a card to the centre and put it in the raised "hover" state (name shown) — used when a
  // search result or table row is clicked. No coloured highlight; it reads like you hovered it.
  window.highlightTiltCard = function (container, id) {
    var card = container.querySelector('.tilt-card[data-id="' + CSS.escape(String(id)) + '"]');
    if (!card) return;
    container.querySelectorAll('.tilt-card.spotlight').forEach(function (c) { c.classList.remove('spotlight'); });
    card.classList.add('spotlight');
    card.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
  };
})();
