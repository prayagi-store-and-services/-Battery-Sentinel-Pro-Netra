## UI standard (all Netra apps)

Every screen follows these rules. The `UI standard check` workflow (`scripts/ui-standard-check.sh`) fails a pull request that breaks the rules it can test by text search.

1. One header, 56 dp: app name, installed version, date and time. Nothing else.
2. Only the header and the footer (bottom bar) stay fixed. Everything else scrolls with the page.
3. One line per tab or chip label (`maxLines = 1`). No letter-by-letter wrapping; long values wrap on the right.
4. No overlays or see-through panels over content.
5. No developer or internal wording in text the user sees (no module codes, "score weight", "telemetry", "session id").
6. The same fact is shown once on a screen.
7. Light and dark theme both readable; no dark card on a light screen.
8. If a value has no evidence, show "Unavailable". Never invent data.

The check script tests rules 3 (filter chip and tab labels) and 5 (a list of banned internal words). The rest are a review checklist: tick them in the pull request.

## Security checklist (all Netra apps and sites)

Every new product and every release is checked against these 20 points before it ships. Each point gets one status in the pull request: FIXED (what changed), ALREADY covered (name the file or pull request as evidence) or NOT APPLICABLE (say why, for example "static page, no server"). No point is marked covered without evidence. Netra apps keep data on the phone and have no login server, so several points are usually not applicable. They apply in full the moment a product adds a server, an account or a cloud call.

1. SQL injection: no SQL built by joining user text. Use Room queries with parameters.
2. XSS: every user or network value put into a page goes through an escape that also handles quotes. No eval, no document.write, no WebView with JavaScript for outside content.
3. CSRF: any server action that changes data needs an anti-forgery token or same-site protection.
4. File upload: check type, size and content of any file the user supplies. Never run it.
5. SSRF: never fetch a URL that comes from user input. Fixed, listed addresses only.
6. Broken object-level authorization: a user can only read or change their own records. Check ownership on every record id.
7. Rate limiting: limit repeated sign-in, send and submit actions on any server.
8. Passwords and PINs: salted, slow hash (PBKDF2, bcrypt or argon2). Never plain text, never a fast hash alone.
9. Multi-factor sign-in for any account that protects personal data.
10. Permissions are enforced on the server, not only hidden in the screen. Android components are not exported unless needed and are protected by a permission.
11. Row-level security on any shared database: the database itself refuses other people's rows.
12. Token-signing secrets (JWT) are long, random and kept on the server only.
13. API keys and secrets stay on the server. Never in the app, a page or the repository.
14. No sign-in tokens in browser local storage. Use secure, HttpOnly cookies on a server.
15. No default or shared credentials, anywhere.
16. CORS allows only the exact sites that need it. Never a wildcard with credentials.
17. Webhooks verify the sender's signature before acting.
18. No source maps and no debug symbols published with a release.
19. No personal data in logs: no names, voice text, locations, PINs or tokens. Log events, not content.
20. Dependencies are kept current. Open security alerts are read every day and fixed or dated. Alerts are never dismissed to make a list look clean.
