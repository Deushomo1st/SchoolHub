# Changelog

## 2026-07-15 — UI stabilisation

- Fixed infinite login redirect loop (auth guard now skips public pages)
- Restored missing utility functions: saveSession, requireAuth, hideMsg, showMsg, getUser, logout
- Universal overlay dismiss: notifications, date picker, password modal, confirm modals, drawer — all close on outside click
- Notification bell now fetches real data from /api/v1/notifications with read/unread badges
- Logout button styled red in drawer footer
- Confirm sign-out modal: removed double-blur, now single glassmorphic panel with blur + brightness darken
- Dynamic island styled: iPhone-style black pill, top-center, silver blade edges
- Theme toggle styled: black pill bottom-right with sun/moon icon
- Activity monitor: removed transparent glass wrapper, time scales moved vertical on right side, title at top of chart
- Added CLAUDE.md with agent rules, conventions, and git-based context recovery strategy
