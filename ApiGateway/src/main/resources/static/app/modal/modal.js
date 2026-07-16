// ---- Layered glass modal system ----
// Layer 1 (clear): see-through refractive glass; does NOT darken the page.
// Layer 2+ (frost): frosted glass that blurs + darkens everything behind it. Because each
// frosted backdrop composites over the one below, stacking N of them gets progressively
// darker/blurrier on its own — no manual bookkeeping needed.
(function () {
  var stack = [];

  // opts: { frost, className, html, dismissable, onClose }
  window.openGlassModal = function (opts) {
    opts = opts || {};
    var bg = document.createElement('div');
    bg.className = 'glass-modal-bg' + (opts.frost ? ' frost' : '');
    var panel = document.createElement('div');
    panel.className = 'glass-panel' + (opts.frost ? ' frost' : '') + (opts.className ? ' ' + opts.className : '');
    panel.innerHTML = opts.html || '';
    bg.appendChild(panel);
    document.body.appendChild(bg);
    if (window.lucide) lucide.createIcons({ root: panel });

    var entry = { bg: bg, panel: panel };
    stack.push(entry);

    function close() {
      var i = stack.indexOf(entry);
      if (i < 0) return;              // already closed
      stack.splice(i, 1);
      bg.classList.add('closing');
      setTimeout(function () { bg.remove(); }, 180);
      if (opts.onClose) opts.onClose();
    }
    entry.close = close;

    if (opts.dismissable !== false) {
      bg.addEventListener('click', function (e) { if (e.target === bg) close(); });
    }
    // let it transition in
    requestAnimationFrame(function () { bg.classList.add('show'); });
    return { close: close, panel: panel };
  };

  // Escape closes the topmost modal.
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && stack.length) stack[stack.length - 1].close();
  });
})();
