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
