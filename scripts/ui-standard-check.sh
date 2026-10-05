#!/usr/bin/env bash
# Netra UI standard check. Fails the build when a known UI regression comes back.
# Rules (see CONTRIBUTING.md "UI standard"):
#  1. No developer or internal wording in text the user can see.
#  2. Filter chip and tab labels stay on one line (maxLines = 1).
set -u
SRC=app/src/main/java
fail=0
BANNED='Score weight|TRUTH ENGINE|ABSOLUTE TRUTH|Game State Error|HAL Nodes|Smart Search|Global Search|ISPPE |IBRS2|IDHMSE|CORE_ENGINE|RAW TELEMETRY'
hits=$(grep -rnE "\"[^\"]*(${BANNED})[^\"]*\"" "$SRC" --include=*.kt | grep -vE 'Log\.|LoggingManager|//' || true)
if [ -n "$hits" ]; then
  echo "UI standard rule 1 failed: developer or internal wording in user-visible text:"; echo "$hits"; fail=1
fi
# Rule 2: a FilterChip or Tab label Text must carry maxLines.
bad=$(grep -rnE '(label|text) = \{ Text\("[^"]*"[^}]*\) *\},?$' "$SRC" --include=*.kt | grep -E 'FilterChip|Tab\(' | grep -v maxLines || true)
if [ -n "$bad" ]; then echo "UI standard rule 2 failed: label without maxLines = 1:"; echo "$bad"; fail=1; fi
[ $fail -eq 0 ] && echo "UI standard check passed"
exit $fail
