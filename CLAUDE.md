# SchoolHub — Agent Rules

## Context recovery (read this first)
- Use `git log --oneline` and `CHANGELOG.md` to understand recent state.
- Use `git diff` to see what changed — never re-read full files for context.
- `git diff HEAD~1 -- <file>` shows last commit's changes to a single file.

## Project
- Path: `C:/Users/Deus/Desktop/Springworld/SchoolHub refix/`
- DB: `SchoolManagementtester` (Postgres, user: postgres/postgres)
- Login: DeusSA@gmail.com / admin123 (Platform Owner)
- Services: Auth(9001), Tenant(9002), School(9003), Gateway(9000)
- Static files: copy `src/main/resources/static/` → `target/classes/static/` to deploy UI changes (no restart needed for static files)

## Conventions
- RoleCode for routing, not display name (PLATFORM_OWNER → "Platform Owner")
- Light theme stays flat/pastel; dark theme is the premium one
- Pure CSS, no libraries. Glassmorphism with edge gloss. Chrome/metallic gradients.
- Discuss before executing. Log decisions in CHANGELOG.md.
- Commit after every meaningful change.
- Everything must be theme-sensitive — drive it off CSS tokens, never hardcode a colour/glass.
- Section pages (not the dashboard/home) show their name + description in the hero; home keeps "Welcome {name}".
- Cards use the shared picture-1 tilt (`--card-tilt`); hover/selection raises the card + reveals bare tilted letters (no upright label card).
- PLATFORM_OWNER login is padlock-only by design — do not relax it (see the design memory).

## Design rules — GLASS MODAL SYSTEM (general, apply everywhere)
Use `openGlassModal({frost})` for modals; the layered glass is the standard.
1. **Layer 1** (first modal over the page) = see-through glass, causes NO darkening.
2. **Layer 2** (e.g. notifications) = frosted glass that blurs + slightly darkens the layer behind.
3. **Layer 3+** = adds more blur + darkening.
4. More modals stacked ⇒ progressively darker + blurrier (each `.frost` backdrop composites on its own).

**Glass surface look (theme-sensitive, one recipe — the `--glass-*` tokens in style.css):**
- **Light theme** = frosted **glass-white** (the activity-widget look).
- **Dark theme** = **glossy silver** (metallic sheen), not flat dark.
Every glass surface (modals, avatar modal, activity widget) reads from `--glass-bg`, `--glass-bg-frost`, `--glass-border`, `--glass-sheen`, `--glass-blur`, `--glass-shadow`. Never hardcode a glass; extend the tokens.

## Key files
| File | Purpose |
|------|---------|
| `static/js/app.js` | Auth guard, API helper, utilities, theme toggle, dynamic island, overlay dismiss, bell |
| `static/js/dashboards.js` | All role dashboards, activity monitor/dot graph |
| `static/css/style.css` | All styles (palette, glass, modals, island, toggle, activity panel) |
| `static/app/drawer/drawer.js` | macOS-style floating drawer state machine + trigger icon |
| `static/app/drawer/drawer.css` | Drawer chrome gradient, stages, logout styling |
| `static/app/modal/modal.js` | `openGlassModal({frost})` — the layered glass modal system |
| `static/app/modal/modal.css` | Glass panels (layer-1 / frost), notification + pwd modals |
| `static/app/avatar/avatar.js` | `avatarCard()` + `openAvatarUpload()` (drag/zoom crop to 4:3) |
| `static/app/tiltstack/tiltstack.js` | `renderTiltStack()` / `highlightTiltCard()` — side-scrolling tilt cards |
| `static/css/style.css` | Palette, `--glass-*` recipe, bento, tilt/pw/activity styles |
