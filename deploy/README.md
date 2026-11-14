# Existing static website on Railway

The root Dockerfile serves only existing `docs/` content with NGINX. No Android
build, backend API, database, secret, telemetry or page change is added.
`PORT` is supplied by Railway (8080 locally); the official image substitutes only
that variable into the server template. `/healthz` is a static readiness response.
Unknown URLs return 404 rather than masking missing files. Existing canonical,
privacy and update links remain unchanged. GitHub Pages stays in place.

Connect only the canonical BSPN repository after this PR's exact-head checks pass.
Railway detects the root Dockerfile. Deploy one service in the existing free trial,
then generate its Railway HTTPS domain and target the detected port. Never upgrade,
add a card/token, alter repository access, or deploy the Android root via Railpack.
Runtime consumes trial credit; $5/30 days is not permanent free hosting. Check the
live account allowance before deploying and stop when the free allowance ends.

CI builds the actual container, verifies a non-default PORT, byte-compares existing
HTML/JS/icon files and checks that application source, .env and .git are not served.
Local container execution must be marked NOT RUN when Docker is unavailable.

Sources:
- https://docs.railway.com/builds/dockerfiles
- https://docs.railway.com/pricing/free-trial
- https://hub.docker.com/_/nginx
