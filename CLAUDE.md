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
- Build: each service has `.mvn/jvm.config` with `--enable-native-access=ALL-UNNAMED` so `mvnw.cmd` works on Java 25. Use `mvnw package -DskipTests` (produces JAR). Launch via `java -jar target/*.jar`, not `spring-boot:run`.
- Launcher: double-click `SchoolHub-Manager.cmd` or run `setup/SchoolHub-Manager.ps1`

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
Use `openGlassModal(opts)` for modals; the layered glass is the standard. **All helpers auto-detect stack depth — callers only need `frost: true` to declare a semantic floor.**

### Auto-stacking (depth detected from `stack.length` at open time)
| Depth at open | Auto class   | With `frost: true` floor | Visual                  |
|---------------|-------------|--------------------------|-------------------------|
| 0 (first)     | (none) clear | `frost`                  | See-through vs frosted  |
| 1 (second)    | `frost`      | `frost`                  | Blurs + darkens beneath |
| 2+ (third+)   | `frost-2`    | `frost-2`                | Deeper blur + darker    |

Auto-stacking **only upgrades, never downgrades** from the floor. `frost: true` means "never clearer than Layer 2."

### Semantic floor — when to set `frost: true`
Set `frost: true` on modals that conceptually sit **above** page content regardless of stack state:
- **Notifications, slide-out panels, overlays** — they are never "see-through glass over the page"
- **Do NOT set** on dialogs, forms, confirms, alerts — they auto-detect correctly

### Layer definitions
1. **Layer 1** (first modal over the page) = see-through glass, causes NO darkening. The page IS the backdrop.
2. **Layer 2** = frosted glass that blurs + slightly darkens the layer behind. Use `frost: true` as a floor.
3. **Layer 3+** (frost-2) = deeper blur + darker. Auto-applied when two or more modals are already open.

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
| `static/app/modal/modal.js` | `openGlassModal(opts)` — layered glass modal system with auto-stacking + frost floor |
| `static/app/modal/modal.css` | Glass panels (layer-1 / frost), notification + pwd modals |
| `static/app/avatar/avatar.js` | `avatarCard()` + `openAvatarUpload()` (drag/zoom crop to 4:3) |
| `static/app/tiltstack/tiltstack.js` | `renderTiltStack()` / `highlightTiltCard()` — side-scrolling tilt cards |
| `static/css/style.css` | Palette, `--glass-*` recipe, bento, tilt/pw/activity styles |
