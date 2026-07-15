// ---- macOS-style floating drawer state machine ----
(function() {
  var drawer = document.getElementById('floatingDrawer');
  var trigger = document.getElementById('drawerTrigger');
  if (!drawer || !trigger) return;
  var stage = 0, timer = null, clickOpened = false;

  function measureStage2Width() {
    var maxW = 0;
    drawer.querySelectorAll('.drawer-item span').forEach(function(s) {
      var w = s.scrollWidth;
      if (w > maxW) maxW = w;
    });
    return Math.max(116, 20 + 10 + maxW + 28);
  }

  function setStage(s) {
    stage = s;
    drawer.classList.remove('stage1', 'stage2');
    if (s >= 1) drawer.classList.add('stage1');
    if (s >= 2) {
      drawer.classList.add('stage2');
      drawer.style.width = measureStage2Width() + 'px';
    } else {
      drawer.style.width = '';
    }
  }

  // Public API: allow external code to fully reset the drawer
  window.dismissDrawer = function() {
    clearTimeout(timer);
    clickOpened = false;
    setStage(0);
  };

  drawer.addEventListener('mouseenter', function() {
    clearTimeout(timer);
    if (clickOpened) return;
    if (window.lucide) {
      var body = drawer.querySelector('.drawer-body');
      if (body) lucide.createIcons({ root: body });
    }
    if (stage === 0) setStage(1);
  });

  trigger.addEventListener('click', function(e) {
    e.stopPropagation();
    clearTimeout(timer);
    if (stage === 0) {
      clickOpened = true;
      setStage(2);
    } else {
      clickOpened = false;
      setStage(0);
    }
  });

  drawer.addEventListener('mouseleave', function() {
    if (clickOpened) return;
    if (stage >= 2) {
      setStage(1);
      timer = setTimeout(function() { setStage(0); }, 2000);
    } else {
      timer = setTimeout(function() { setStage(0); }, 150);
    }
  });

  var iconsDone = false;
  var obs = new MutationObserver(function() {
    if (!iconsDone && window.lucide && (drawer.classList.contains('stage1') || drawer.classList.contains('stage2'))) {
      var body = drawer.querySelector('.drawer-body');
      if (body && body.offsetHeight > 0) {
        lucide.createIcons({ attrs: { class: 'lucide' }, root: body });
        iconsDone = true;
      }
    }
  });
  obs.observe(drawer, { attributes: true, attributeFilter: ['class'] });
})();

// ---- Public API: rebuild drawer items for any role ----
window.rebuildDrawer = function(items) {
  var colIcons = document.querySelector('.drawer-body .col-icons');
  if (!colIcons) return;
  colIcons.innerHTML = '';
  items.forEach(function(item) {
    var div = document.createElement('div');
    div.className = 'drawer-item';
    div.dataset.nav = item.id;
    div.innerHTML = '<i data-lucide="' + item.icon + '"></i><span>' + item.label + '</span>';
    colIcons.appendChild(div);
  });
  if (typeof window.dismissDrawer === 'function') window.dismissDrawer();
  if (window.lucide) lucide.createIcons({ root: colIcons });
};
