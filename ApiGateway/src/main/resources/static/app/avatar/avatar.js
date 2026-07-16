// ---- Avatar: 3D-tilted 4:3 profile card + glassmorphic upload/crop modal ----
(function () {
  function initials(name) {
    var p = String(name || '').trim().split(/\s+/);
    var s = ((p[0] || '')[0] || '') + ((p[1] || '')[0] || '');
    return s.toUpperCase() || '?';
  }
  function e(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
    return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }

  // Render a downward-sideways tilted 4:3 avatar card.
  // opts: { src, name, editable } — no src shows the default iconized face (a "+" when editable).
  window.avatarCard = function (opts) {
    opts = opts || {};
    var face = opts.src
      ? '<img class="av-img" src="' + e(opts.src) + '" alt="' + e(opts.name || 'avatar') + '">'
      : '<span class="av-fallback">' + (opts.editable
          ? '<i data-lucide="plus"></i>'
          : '<span class="av-initials">' + e(initials(opts.name)) + '</span>') + '</span>';
    return '<div class="avatar-card' + (opts.editable ? ' editable' : '') + '">'
      + '<div class="av-face">' + face + '</div>'
      + (opts.name ? '<div class="av-name">' + e(opts.name) + '</div>' : '')
      + '</div>';
  };

  // Glassmorphic upload + crop-to-4:3 modal. onSave receives a JPEG data-URL (or null if cleared).
  // opts: { current, onSave }
  window.openAvatarUpload = function (opts) {
    opts = opts || {};
    var VP_W = 320, VP_H = 240, OUT_W = 640, OUT_H = 480;   // 4:3
    var img = null, zoom = 1, base = 1, off = { x: 0, y: 0 };
    var drag = null;

    var back = document.createElement('div');
    back.className = 'avatar-modal-bg';
    back.innerHTML =
      '<div class="avatar-modal glass">' +
        '<h2>Profile picture</h2>' +
        '<p class="subtle">Drag to reposition, zoom to fit the 4:3 frame.</p>' +
        '<div class="crop-stage">' +
          '<canvas class="crop-canvas" width="' + VP_W + '" height="' + VP_H + '"></canvas>' +
          '<div class="crop-empty"><i data-lucide="image-plus"></i><span>Choose an image</span></div>' +
        '</div>' +
        '<input type="range" class="crop-zoom" min="1" max="4" step="0.01" value="1" disabled>' +
        '<input type="file" accept="image/*" class="crop-file" hidden>' +
        '<div class="avatar-modal-actions">' +
          '<button class="btn secondary" data-act="pick">Choose file</button>' +
          (opts.current ? '<button class="btn danger" data-act="remove">Remove</button>' : '') +
          '<span style="flex:1"></span>' +
          '<button class="btn ghost" data-act="cancel">Cancel</button>' +
          '<button class="btn" data-act="save" disabled>Save</button>' +
        '</div>' +
      '</div>';
    document.body.appendChild(back);
    if (window.lucide) lucide.createIcons({ root: back });

    var canvas = back.querySelector('.crop-canvas');
    var ctx = canvas.getContext('2d');
    var file = back.querySelector('.crop-file');
    var zoomEl = back.querySelector('.crop-zoom');
    var emptyEl = back.querySelector('.crop-empty');
    var saveBtn = back.querySelector('[data-act="save"]');

    function clamp() {
      var dw = img.naturalWidth * base * zoom, dh = img.naturalHeight * base * zoom;
      off.x = Math.min(0, Math.max(VP_W - dw, off.x));
      off.y = Math.min(0, Math.max(VP_H - dh, off.y));
    }
    function draw() {
      ctx.clearRect(0, 0, VP_W, VP_H);
      if (!img) return;
      var dw = img.naturalWidth * base * zoom, dh = img.naturalHeight * base * zoom;
      ctx.drawImage(img, off.x, off.y, dw, dh);
    }
    function loadFile(f) {
      if (!f) return;
      var r = new FileReader();
      r.onload = function () {
        var im = new Image();
        im.onload = function () {
          img = im;
          base = Math.max(VP_W / im.naturalWidth, VP_H / im.naturalHeight); // cover
          zoom = 1; off = { x: (VP_W - im.naturalWidth * base) / 2, y: (VP_H - im.naturalHeight * base) / 2 };
          zoomEl.value = 1; zoomEl.disabled = false; saveBtn.disabled = false;
          emptyEl.style.display = 'none';
          draw();
        };
        im.src = r.result;
      };
      r.readAsDataURL(f);
    }

    // Interactions
    canvas.addEventListener('pointerdown', function (ev) {
      if (!img) return;
      drag = { x: ev.clientX, y: ev.clientY, ox: off.x, oy: off.y };
      canvas.setPointerCapture(ev.pointerId);
    });
    canvas.addEventListener('pointermove', function (ev) {
      if (!drag) return;
      off.x = drag.ox + (ev.clientX - drag.x);
      off.y = drag.oy + (ev.clientY - drag.y);
      clamp(); draw();
    });
    canvas.addEventListener('pointerup', function () { drag = null; });
    zoomEl.addEventListener('input', function () {
      if (!img) return;
      var cx = VP_W / 2, cy = VP_H / 2;
      var prev = zoom; zoom = parseFloat(zoomEl.value);
      // keep the viewport centre anchored while zooming
      off.x = cx - (cx - off.x) * (zoom / prev);
      off.y = cy - (cy - off.y) * (zoom / prev);
      clamp(); draw();
    });

    back.querySelector('[data-act="pick"]').onclick = function () { file.click(); };
    file.onchange = function () { loadFile(file.files[0]); };
    back.querySelector('[data-act="cancel"]').onclick = close;
    back.addEventListener('click', function (ev) { if (ev.target === back) close(); });
    var removeBtn = back.querySelector('[data-act="remove"]');
    if (removeBtn) removeBtn.onclick = function () { if (opts.onSave) opts.onSave(null); close(); };

    saveBtn.onclick = function () {
      if (!img) return;
      var out = document.createElement('canvas');
      out.width = OUT_W; out.height = OUT_H;
      var octx = out.getContext('2d');
      var scale = OUT_W / VP_W;
      var dw = img.naturalWidth * base * zoom, dh = img.naturalHeight * base * zoom;
      octx.drawImage(img, off.x * scale, off.y * scale, dw * scale, dh * scale);
      if (opts.onSave) opts.onSave(out.toDataURL('image/jpeg', 0.85));
      close();
    };

    function close() { back.remove(); }
    // Seed with the current picture so re-opening shows it (as a starting frame).
    if (opts.current) loadFile(null);
  };
})();
