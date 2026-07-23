# Auto-Stacking Modal System — Implementation Plan

**Date:** 2026-07-23  
**Status:** pending reasoner approval  
**Scope:** Frontend-only (CSS + JS, no backend changes)

---

## Analysis

### Current state
`openGlassModal(opts)` requires callers to manually pass `frost: true` to get a frosted backdrop and panel. The `stack` array already tracks open modals in order, but it's only used for `Escape` key handling — not for visual layering. The depth information is sitting right there, unused.

### The gap
Callers must remember to pass `frost: true` (and 9 call sites do), and there's no Layer 3 (frost-2) for triple-stacked modals. The design rules in CLAUDE.md already say "Layer 1 = clear (no darken) over the page, Layer 2+ = frost (blur+darken) over another modal" — the code just doesn't enforce it automatically.

### Design principle
The `stack.length` at the moment `openGlassModal` is called tells us the depth BEFORE adding the new modal:
- `stack.length === 0` → first modal over the page → Layer 1 (clear glass)
- `stack.length === 1` → one modal already open → Layer 2 (frost)
- `stack.length >= 2` → two+ modals already open → Layer 3+ (deeper frost / frost-2)

This means when the notification panel is opened directly from the page (no other modal open), it will be Layer 1 → **clear glass**. This aligns with the design rules that say "the page is ground level, only modals are layers." The notification panel only darkens when stacked over another modal — which is the correct progressive-darkening behavior.

---

## Implementation plan

### 1. CSS: New Layer 3 tokens (style.css, lines 933-957)

Add frost-2 tokens to both themes. Values are interpolated between frost (Layer 2) and a further step:

**`:root` (light theme) — add after line 941:**
```css
--glass-bg-frost2: color-mix(in srgb, var(--card) 84%, transparent);
--glass-blur-frost2: blur(36px) saturate(140%);
--glass-backdrop-bg-frost2: rgba(2, 18, 30, .56);
--glass-backdrop-blur-frost2: blur(14px) saturate(110%);
```

**`[data-theme="dusk"]` (dark theme) — add after line 954:**
```css
--glass-bg-frost2: linear-gradient(135deg, rgba(210,218,230,.10), rgba(110,118,134,.04) 45%, rgba(60,66,80,.12)), color-mix(in srgb, #111520 78%, transparent);
--glass-blur-frost2: blur(38px) saturate(145%);
--glass-backdrop-bg-frost2: rgba(0, 0, 0, .62);
--glass-backdrop-blur-frost2: blur(14px) saturate(125%);
```

### 2. CSS: New frost-2 selectors (modal.css)

Add after the existing `.glass-modal-bg.frost` block (after line 17):
```css
/* Layer 3+: deeper frost — more darkening + more blur */
.glass-modal-bg.frost-2 {
  background: var(--glass-backdrop-bg-frost2);
  backdrop-filter: var(--glass-backdrop-blur-frost2);
  -webkit-backdrop-filter: var(--glass-backdrop-blur-frost2);
}
```

Add after the existing `.glass-panel.frost` block (after line 61):
```css
/* Frost-2 panel (layer 3+): densest, least see-through */
.glass-panel.frost-2 {
  background: var(--glass-bg-frost2);
  backdrop-filter: var(--glass-blur-frost2);
  -webkit-backdrop-filter: var(--glass-blur-frost2);
}
```

### 3. JS: Auto-detect depth in openGlassModal (modal.js)

Replace the manual `frost` check with stack-length-based auto-detection:

**Change `openGlassModal` (lines 10-16):**
```js
// BEFORE:
window.openGlassModal = function (opts) {
  opts = opts || {};
  var bg = document.createElement('div');
  bg.className = 'glass-modal-bg' + (opts.frost ? ' frost' : '');
  var panel = document.createElement('div');
  panel.className = 'glass-panel' + (opts.frost ? ' frost' : '') + (opts.className ? ' ' + opts.className : '');

// AFTER:
window.openGlassModal = function (opts) {
  opts = opts || {};
  // Auto-detect depth: stack.length tells us what's already open
  // 0 = first modal → clear (Layer 1), 1 = over another → frost (Layer 2), 2+ = deeper frost (Layer 3+)
  var depth = stack.length;
  var frostClass = '';
  if (depth === 1) frostClass = ' frost';
  else if (depth >= 2) frostClass = ' frost-2';
  var bg = document.createElement('div');
  bg.className = 'glass-modal-bg' + frostClass;
  var panel = document.createElement('div');
  panel.className = 'glass-panel' + frostClass + (opts.className ? ' ' + opts.className : '');
```

**Remove `frost` forwarding from `glassConfirm` (line 57):**
```js
// BEFORE:
frost: !!opts.frost,
// AFTER:
// (remove the line entirely — auto-detection handles it)
```

**Remove `frost` forwarding from `glassAlert` (line 76):**
```js
// BEFORE:
frost: !!opts.frost,
// AFTER:
// (remove the line entirely)
```

### 4. JS/HTML: Remove all manual `frost` params from call sites

| File | Line | Change |
|------|------|--------|
| `static/app/calendar/calendar.js` | 76 | Remove `frost: true,` |
| `static/js/app.js` | 203 | Remove `frost: true,` |
| `static/js/dashboards.js` | 161 | Remove `frost: true,` |
| `static/js/dashboards.js` | 357 | Remove `frost: true,` |
| `static/js/dashboards.js` | 1056 | Remove `frost: false,` |
| `static/js/dashboards.js` | 1057 | Remove `frost: true,` |
| `static/js/dashboards.js` | 1714 | Remove `frost: true` from glassConfirm call |
| `static/js/dashboards.js` | 2003 | Remove `frost: true,` |
| `static/js/dashboards.js` | 3482 | Remove `frost: true,` |
| `static/js/dashboards.js` | 3547 | Remove `frost: true,` |
| `static/app.html` | 96 | Remove `frost: false,` |

**Total: 11 call sites across 4 files.**

### 5. Update the demo/test buttons (dashboards.js lines 1055-1059)

The "glass" and "frost" demo buttons currently use `frost: false` and `frost: true` to demonstrate the two layers. After auto-detection:
- **glass button:** Opens Layer 1 (clear) as before, just without the explicit `frost: false`
- **frost button:** Needs to actually stack — open the Layer 1 modal first, then open the frost modal on top

Update:
```js
const demos = {
  glass: () => openGlassModal({ html: '<h2>Layer-1 glass</h2><p class="subtle">See-through, no page darkening.</p><div class="glass-actions"><button class="btn" onclick="this.closest(\'.glass-modal-bg\').remove()">Close</button></div>' }),
  frost: () => { demos.glass(); openGlassModal({ html: '<h2>Layer-2 frost</h2><p class="subtle">Blurs + darkens what is behind.</p><div class="glass-actions"><button class="btn" onclick="this.closest(\'.glass-modal-bg\').remove()">Close</button></div>' }); },
  confirm: () => glassConfirm('This is the glass confirm dialog.', { title: 'Confirm', okText: 'OK' }),
  calendar: () => window.openDualCalendar && window.openDualCalendar({}),
};
```

### 6. Copy static files to target

After all source changes:
```bash
cp -r ApiGateway/src/main/resources/static/* ApiGateway/target/classes/static/
```

---

## Affected files summary

| File | Type | Action |
|------|------|--------|
| `ApiGateway/src/main/resources/static/css/style.css` | CSS | Add 8 new `--glass-*-frost2` tokens |
| `ApiGateway/src/main/resources/static/app/modal/modal.css` | CSS | Add `.glass-modal-bg.frost-2` + `.glass-panel.frost-2` selectors |
| `ApiGateway/src/main/resources/static/app/modal/modal.js` | JS | Replace manual frost with auto-depth; remove frost from glassConfirm/glassAlert |
| `ApiGateway/src/main/resources/static/js/app.js` | JS | Remove `frost: true` at line 203 |
| `ApiGateway/src/main/resources/static/js/dashboards.js` | JS | Remove 8 `frost: true/false` params; update demo buttons |
| `ApiGateway/src/main/resources/static/app/calendar/calendar.js` | JS | Remove `frost: true` at line 76 |
| `ApiGateway/src/main/resources/static/app.html` | HTML | Remove `frost: false` at line 96 |

---

## Verification

1. **Layer 1 (first modal):** Open notification bell — should be clear glass, no page darkening
2. **Layer 2 (stacked):** From within a modal, trigger another (e.g., change password while notification panel is open, or click "Generate code" from staff codes modal) → should get frost backdrop + frosted panel
3. **Layer 3+ (triple stack):** Open notification panel, then open calendar picker over it, then trigger a glassConfirm from within → should get deepest frost
4. **Theme toggle:** Switch light/dusk — all 3 layers should use correct theme tokens
5. **Escape key:** Should close topmost modal; depth auto-recalculates for the newly-revealed modal
6. **Glass test page:** Demo buttons should demonstrate auto-stacking correctly

---

## Tradeoffs

- **Pro:** Zero caller awareness needed — just call `openGlassModal({...})`
- **Pro:** Progressive darkening is automatic and correct by construction
- **Pro:** Adding a 4th layer in the future is trivial (add `frost-3` tokens + one `else if` branch)
- **Con:** The notification panel changes from always-frost to only frost when stacked. This aligns with the design rules but changes visual behavior when opened directly from the page. Users who liked the frosted notification backdrop will notice it's now clear — but this is the correct behavior per the design spec.
- **Con:** Triple-stacked modals are rare, so Layer 3 CSS may see limited use. But it costs almost nothing to add.

---

## No backend impact

This is purely frontend. No Java controllers, services, entities, or API endpoints are affected. Confirmed via grep: zero references to `openGlassModal`, `glassConfirm`, `glassAlert`, or `frost` in any `.java` file.
