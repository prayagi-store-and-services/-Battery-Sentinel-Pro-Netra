# Solar Monitoring Integration Plan — Battery Sentinel Pro Nethra

## Status
Planning and provider-documentation review only. No solar provider is enabled in the app yet. A provider becomes **Supported** only after an authorized, credential-backed smoke test confirms real account/plant/device data and field timestamps. Public API documentation alone is not proof that a particular user's account can connect.

## Non-negotiable rules
- Never display fabricated, sample, cached-demo, or guessed telemetry as live data.
- If a field is absent, malformed, stale beyond its configured freshness window, or rejected by the provider, show `Unavailable` (and optionally the last successful value clearly marked with its timestamp).
- Never call a provider Supported until authentication, plant/device discovery, telemetry retrieval, unit normalization, timestamp validation, and error handling pass tests.
- Prefer official OAuth or official token-based authorization. Do not collect provider passwords if an approved authorization flow exists.
- Keep provider secrets out of logs, crash reports, analytics, source control, and Android resources. Use Android Keystore-backed encrypted storage for any user-scoped tokens. Never embed developer AppSecrets in the APK; use a secured backend if the provider's protocol requires confidential application credentials.
- Do not bypass provider approval, rate limits, account region restrictions, or terms. Do not scrape private web dashboards or reverse-engineer undocumented endpoints.
- Solar monitoring is read-only in the first release. No inverter control or dispatch commands.
- Integrate data through the existing Netra Central Unit / capability registry; do not create a parallel telemetry authority.
- Solar telemetry must not be represented as phone battery telemetry. Keep solar system, inverter battery, grid, and phone battery values distinct.

## Official provider documentation review (2026-10-02)

| Provider | Official evidence reviewed | What the evidence establishes | Release status |
|---|---|---|---|
| SOLARMAN / IGEN | https://doc.solarmanpv.com/en/Device%20interface/3.3Real-time%20device%20data ; https://doc.solarmanpv.com/en/Public%20Information/12Account%20and%20Capabilities ; https://helpcenter.solarmanpv.com/portal/en/kb/articles/i-want-to-open-api-how-can-i-open-api | Documented current device data endpoint, token-based authorization, point names/values/units/timestamps, historical data and device/plant APIs. API activation/review and App ID/Secret may be required; service can be paid depending on account/plant count. | Candidate only — live account/credential test required |
| Sungrow iSolarCloud | https://developer-api.isolarcloud.com/ | Official developer portal documents monitoring, live data and OAuth 2.0; account registration/application approval and AppKey are required. | Candidate only — access approval and live account test required |
| DeyeCloud | https://developer.deyecloud.com/ | Official developer portal advertises DeyeCloud OpenAPI and developer application/credential management. | Candidate only — exact endpoints, scopes and live account test required |
| GoodWe | https://openapi.goodwe.com/ | Official OpenAPI platform documents device/plant telemetry and partner licensing models. | Candidate only — authorization and live account test required |
| Growatt | https://openapi.growatt.com/ ; https://oss.growatt.com/login?lang=en | Official login/OSS portals found, but this review did not establish sufficient public API documentation for a compliant integration. | Not listed as supported; do not implement until official API docs and access are confirmed |

This is a documentation review, not a successful API call. No live endpoint, account, inverter, or returned telemetry has been verified in this repository change. Do not show these candidate providers in the production “Supported Brands” picker yet. They may appear in a developer-only integration status screen as “Verification pending” only if clearly separated from supported providers.

## Required provider verification protocol
For each provider, record:
1. Official API base URL, documentation URL, region/data-center rules, API version and current terms.
2. Authentication type and required permissions/scopes; whether app approval, installer authorization or a paid plan is required.
3. Whether user credentials can be avoided using OAuth/authorization code or provider-supported token flow.
4. With an authorized test account, request token/authorize; list plants/sites; list devices; request live telemetry; request historical telemetry if supported.
5. Save sanitized evidence only: HTTP status, provider error code, field names, units, collection timestamp, server timestamp and request duration. Never save secrets, full tokens, passwords or personal data.
6. Confirm at least one real inverter's non-empty readings and timestamps. Validate offline device, missing field, expired token, rate limit, network loss and account with no plants.
7. Confirm units and semantics with provider docs; do not infer a field from its name alone.
8. Mark provider Supported only after all checks pass and tests are green. Otherwise mark “Access required”, “Unsupported” or “Verification failed” with a reason.

## Data model and UI
- New Solar section, separate from Home/Battery/Monitoring/Devices/Settings and integrated with the existing navigation conventions.
- Provider picker contains only providers with verified adapters in production.
- Connect/disconnect, connection health, selected plant/site and selected inverter.
- Metric cards show only returned, validated values: current PV power (W/kW), energy today (kWh), lifetime energy (kWh), inverter status, solar/battery/grid/load power and battery state only when the provider actually supplies each metric.
- Every metric carries source/provider, unit, collection timestamp, received timestamp and freshness state.
- A last-known reading must be visibly labeled “Last updated …”; never style stale data as live.
- Missing field: “Unavailable”. API failure: show connection error and retry affordance without replacing real data with demo values.
- Provider polling obeys documented quotas and app lifecycle/battery policy; use bounded backoff and WorkManager only for provider-appropriate background refresh, never a one-second polling loop.
- Logs are sanitized and integrated with existing central logging/capability registry.

## Security and architecture
- Define a provider-neutral SolarTelemetry model plus one adapter per provider.
- Keep parsing, validation, unit conversion and freshness validation in provider adapters/repository; publish normalized solar state through the existing central architecture.
- Use HTTPS with normal platform certificate validation. No custom trust-all TLS.
- Store user authorization tokens encrypted with Android Keystore-backed encryption; redact Authorization headers and credentials in all logs.
- If an API requires confidential AppSecret, route it through a minimal secured backend; do not ship the secret in the APK or ask the end user to paste a company developer secret.
- Provide disconnect/revoke and delete locally stored provider credentials.
- No undocumented endpoints, dashboard scraping, hidden APIs or credential-sharing shortcuts.

## Acceptance criteria
- Production provider list includes only integrations that passed the full live verification protocol.
- Unit tests cover successful responses, empty plants/devices, absent fields, bad units, malformed values, stale timestamps, token expiry, rate limiting, offline state and provider errors.
- Instrumentation/UI tests prove no sample/demo values are displayed.
- Network tests verify no credentials are emitted to logs or crash reports.
- App build and full existing tests pass; CodeQL and repository release gates remain unchanged.
- Release notes explicitly distinguish tested provider/account combinations from providers whose access is still pending.

## Next implementation sequence
1. Audit navigation, settings, capability registry, persistence and existing network/security patterns.
2. Implement provider-neutral models, repository contracts, freshness/validation, and tests with fixture responses.
3. Verify provider access with authorized test accounts before enabling each adapter.
4. Implement only adapters with sufficient official documentation and approved credentials.
5. Add Solar UI and connection management, then run full tests and CI.
6. Do not label the feature complete until at least one authorized provider has returned real data end-to-end.
