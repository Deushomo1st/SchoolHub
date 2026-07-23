// ---- Layered glass modal system ----
// Layer 1 (clear): see-through refractive glass; does NOT darken the page.
// Layer 2+ (frost): frosted glass that blurs + darkens everything behind it. Because each
// frosted backdrop composites over the one below, stacking N of them gets progressively
// darker/blurrier on its own — no manual bookkeeping needed.
//
// Auto-stacking: depth is detected from `stack.length` at open time.
//   0 = first modal → clear (layer 1)
//   1 = second modal → frost (layer 2)
//   2+ = third+ modal → frost-2 (layer 3+)
// Manual `opts.frost` still works as an override but triggers a console warning.
(function () {
  var stack = [];

  // opts: { frost, className, html, dismissable, onClose }
  window.openGlassModal = function (opts) {
    opts = opts || {};

    // Auto-detect depth from current stack (before we push onto it)
    var depth = stack.length;  // 0=first, 1=second, 2+=third+
    var frostClass = '';
    if (depth >= 2) frostClass = ' frost-2';
    else if (depth === 1) frostClass = ' frost';
    // depth 0 = clear (no class)

    // Manual frost override (backward compat)
    if (opts.frost !== undefined) {
      console.warn('[modal] manual opts.frost is deprecated — stacking depth auto-detected from stack.length. Depth=' + depth);
      if (opts.frost === true && frostClass === '') frostClass = ' frost'; // force at least frost
      if (opts.frost === false) frostClass = ''; // force clear
    }

    var bg = document.createElement('div');
    bg.className = 'glass-modal-bg' + frostClass;
    var panel = document.createElement('div');
    panel.className = 'glass-panel' + frostClass + (opts.className ? ' ' + opts.className : '');
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

  function esc(s) { return String(s == null ? '' : s).replace(/[&<>\"']/g, function (c) {
    return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '\"': '&quot;', \"'\": '&#39;' }[c]; }); }

  // Glass replacements for native confirm()/alert(). Both return a Promise.
  // opts: { title, okText, cancelText, danger, frost }
  window.glassConfirm = function (message, opts) {
    opts = opts || {};
    return new Promise(function (resolve) {
      var answered = false;
      // Auto-stacking: depth detected by openGlassModal; opts.frost is an override.
      var frostOpt = {};
      if (opts.frost !== undefined) {
        console.warn('[modal] manual opts.frost is deprecated in glassConfirm — stacking depth auto-detected');
        frostOpt.frost = opts.frost;
      }
      var ctrl = openGlassModal(Object.assign({
        className: 'confirm-glass-panel',
        html: '<h2>' + esc(opts.title || 'Are you sure?') + '</h2>'
          + '<p class="subtle">' + esc(message || '') + '</p>'
          + '<div class="glass-actions">'
          + '<button class="btn ghost" data-x="no">' + esc(opts.cancelText || 'Cancel') + '</button>'
          + '<button class="btn ' + (opts.danger ? 'danger' : '') + '" data-x="yes">' + esc(opts.okText || 'Confirm') + '</button>'
          + '</div>',
        onClose: function () { if (!answered) { answered = true; resolve(false); } }
      }, frostOpt));
      ctrl.panel.querySelector('[data-x="no"]').onclick = function () { answered = true; resolve(false); ctrl.close(); };
      ctrl.panel.querySelector('[data-x="yes"]').onclick = function () { answered = true; resolve(true); ctrl.close(); };
    });
  };

  window.glassAlert = function (message, opts) {
    opts = opts || {};
    return new Promise(function (resolve) {
      // Auto-stacking: depth detected by openGlassModal; opts.frost is an override.
      var frostOpt = {};
      if (opts.frost !== undefined) {
        console.warn('[modal] manual opts.frost is deprecated in glassAlert — stacking depth auto-detected');
        frostOpt.frost = opts.frost;
      }
      var ctrl = openGlassModal(Object.assign({
        className: 'confirm-glass-panel',
        html: '<h2>' + esc(opts.title || 'Notice') + '</h2>'
          + '<p class="subtle" style="white-space:pre-line">' + esc(message || '') + '</p>'
          + '<div class="glass-actions"><button class="btn" data-x="ok">' + esc(opts.okText || 'OK') + '</button></div>',
        onClose: function () { resolve(); }
      }, frostOpt));
      ctrl.panel.querySelector('[data-x="ok"]').onclick = function () { ctrl.close(); };
    });
  };
})();
