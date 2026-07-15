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

## Key files
| File | Purpose |
|------|---------|
| `static/js/app.js` | Auth guard, API helper, utilities, theme toggle, dynamic island, overlay dismiss, bell |
| `static/js/dashboards.js` | All role dashboards, activity monitor/dot graph |
| `static/css/style.css` | All styles (palette, glass, modals, island, toggle, activity panel) |
| `static/app/drawer/drawer.js` | macOS-style floating drawer state machine |
| `static/app/drawer/drawer.css` | Drawer chrome gradient, stages, logout styling |
