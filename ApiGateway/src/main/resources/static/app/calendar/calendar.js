// ---- Dual From/To calendar (two months in one glass modal) ----
// window.openDualCalendar({ from, to, onApply(fromISO, toISO) })
// The "To" can't be earlier than "From" and vice-versa (out-of-range days are disabled).
(function () {
  var MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  var WD = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

  function iso(d) { return d ? d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0') : null; }
  function day0(d) { return new Date(d.getFullYear(), d.getMonth(), d.getDate()); }
  function same(a, b) { return a && b && a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate(); }
  function nice(d) { return d ? d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) : '…'; }

  // Render one month into `host`. cfg: { view:{y,m}, selected, min, max, onPick(date), onNav() }
  function renderMonth(host, cfg) {
    var y = cfg.view.y, m = cfg.view.m;
    var startWd = (new Date(y, m, 1).getDay() + 6) % 7;   // week starts Monday
    var days = new Date(y, m + 1, 0).getDate();
    var prev = new Date(y, m, 0).getDate();

    var cells = '';
    for (var i = 0; i < startWd; i++) cells += '<span class="cal-day muted-day">' + (prev - startWd + 1 + i) + '</span>';
    for (var d = 1; d <= days; d++) {
      var date = new Date(y, m, d);
      var off = (cfg.min && date < day0(cfg.min)) || (cfg.max && date > day0(cfg.max));
      var sel = same(date, cfg.selected);
      cells += '<button type="button" class="cal-day' + (sel ? ' sel' : '') + (off ? ' disabled' : '') + '" data-d="' + d + '"' + (off ? ' disabled' : '') + '>' + d + '</button>';
    }
    var trail = (7 - (startWd + days) % 7) % 7;
    for (var t = 1; t <= trail; t++) cells += '<span class="cal-day muted-day">' + t + '</span>';

    host.innerHTML =
      '<div class="cal-head">'
      + '<button type="button" class="cal-nav" data-nav="pm" aria-label="Previous month">&lsaquo;</button>'
      + '<div class="cal-title-wrap" title="Scroll to change month">'
      +   '<button type="button" class="cal-chev" data-nav="py" aria-label="Previous year">&#9650;</button>'
      +   '<span class="cal-title">' + MONTHS[m] + ' ' + y + '</span>'
      +   '<button type="button" class="cal-chev" data-nav="ny" aria-label="Next year">&#9660;</button>'
      + '</div>'
      + '<button type="button" class="cal-nav" data-nav="nm" aria-label="Next month">&rsaquo;</button>'
      + '</div>'
      + '<div class="cal-grid cal-wd">' + WD.map(function (w) { return '<span class="cal-wdh">' + w + '</span>'; }).join('') + '</div>'
      + '<div class="cal-grid cal-days">' + cells + '</div>';

    function stepMonth(n) { cfg.view.m += n; while (cfg.view.m < 0) { cfg.view.m += 12; cfg.view.y--; } while (cfg.view.m > 11) { cfg.view.m -= 12; cfg.view.y++; } cfg.onNav(); }
    host.querySelector('[data-nav="pm"]').onclick = function () { stepMonth(-1); };
    host.querySelector('[data-nav="nm"]').onclick = function () { stepMonth(1); };
    host.querySelector('[data-nav="py"]').onclick = function () { cfg.view.y--; cfg.onNav(); };
    host.querySelector('[data-nav="ny"]').onclick = function () { cfg.view.y++; cfg.onNav(); };
    host.querySelector('.cal-title-wrap').addEventListener('wheel', function (e) { e.preventDefault(); stepMonth(e.deltaY < 0 ? -1 : 1); }, { passive: false });
    host.querySelectorAll('.cal-day[data-d]:not(.disabled)').forEach(function (b) {
      b.onclick = function () { cfg.onPick(new Date(y, m, parseInt(b.dataset.d, 10))); };
    });
  }

  window.openDualCalendar = function (opts) {
    opts = opts || {};
    var fromDate = opts.from ? new Date(opts.from) : null;
    var toDate = opts.to ? new Date(opts.to) : null;
    var base = fromDate || new Date();
    var fromView = { y: base.getFullYear(), m: base.getMonth() };
    var tbase = toDate || fromDate || new Date();
    var toView = { y: tbase.getFullYear(), m: tbase.getMonth() };

    var ctrl = openGlassModal({
      frost: true,                       // opens over the notification panel → stacks/darkens (rule 2/3)
      className: 'dual-cal-modal',
      html: '<div class="dual-cal">'
        + '<div class="cal-col"><div class="cal-label">From</div><div class="cal-pane" id="calFrom"></div></div>'
        + '<div class="cal-gap"></div>'
        + '<div class="cal-col"><div class="cal-label">To</div><div class="cal-pane" id="calTo"></div></div>'
        + '</div>'
        + '<div class="dual-cal-foot"><span class="muted" id="calRange"></span>'
        + '<div class="glass-actions">'
        + '<button class="btn ghost" data-x="clear">Clear</button>'
        + '<button class="btn ghost" data-x="cancel">Cancel</button>'
        + '<button class="btn" data-x="apply">Apply</button>'
        + '</div></div>'
    });
    var panel = ctrl.panel;
    var fromHost = panel.querySelector('#calFrom'), toHost = panel.querySelector('#calTo');

    function draw() {
      renderMonth(fromHost, {
        view: fromView, selected: fromDate, max: toDate,          // From can't be after To
        onPick: function (d) { fromDate = d; if (toDate && toDate < fromDate) toDate = null; draw(); }, onNav: draw
      });
      renderMonth(toHost, {
        view: toView, selected: toDate, min: fromDate,            // To can't be before From
        onPick: function (d) { toDate = d; if (fromDate && fromDate > toDate) fromDate = null; draw(); }, onNav: draw
      });
      panel.querySelector('#calRange').textContent = 'From' + nice(fromDate) + '  to  ' + nice(toDate);
    }

    panel.querySelector('[data-x="cancel"]').onclick = ctrl.close;
    panel.querySelector('[data-x="clear"]').onclick = function () { fromDate = null; toDate = null; draw(); };
    panel.querySelector('[data-x="apply"]').onclick = function () { if (opts.onApply) opts.onApply(iso(fromDate), iso(toDate)); ctrl.close(); };
    draw();
  };
})();
