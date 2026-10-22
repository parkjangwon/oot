#!/usr/bin/env bash
# Checks how the build resolves the Antigravity OAuth client values, using FAKE values only.
# Prints pass/fail per case; never prints a BuildConfig value.
set -euo pipefail

cd "$(dirname "$0")/.."
gradle_cmd="${GRADLE:-./gradlew}"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
fail=0
bc="app/build/generated/source/buildConfig/debug/org/parkjw/apps/oot/BuildConfig.java"

printf 'OOT_ANTIGRAVITY_CLIENT_ID=props-id.fake\nOOT_ANTIGRAVITY_CLIENT_SECRET=props-secret-fake\n' > "$tmp/test.oauth.properties"

# Real values (CI secrets, a developer's env) must not leak into these cases.
gen() { env -u OOT_ANTIGRAVITY_CLIENT_ID -u OOT_ANTIGRAVITY_CLIENT_SECRET -u OOT_OAUTH_PROPS "$@" \
  "$gradle_cmd" -q :app:generateDebugBuildConfig --rerun-tasks >/dev/null 2>&1; }

field() { grep -c "String ${1} = \"${2}\";" "$bc" || true; }

check() {
  local name="$1" got="$2"
  if [[ "$got" == "1" ]]; then echo "ok $name"; else echo "FAIL $name"; fail=1; fi
}

gen
check "no values -> empty id" "$(field ANTIGRAVITY_CLIENT_ID '')"
check "no values -> empty secret" "$(field ANTIGRAVITY_CLIENT_SECRET '')"

gen OOT_OAUTH_PROPS="$tmp/test.oauth.properties"
check "props file -> id" "$(field ANTIGRAVITY_CLIENT_ID 'props-id.fake')"
check "props file -> secret" "$(field ANTIGRAVITY_CLIENT_SECRET 'props-secret-fake')"

gen OOT_OAUTH_PROPS="$tmp/test.oauth.properties" OOT_ANTIGRAVITY_CLIENT_ID=env-id.fake
check "env beats props file" "$(field ANTIGRAVITY_CLIENT_ID 'env-id.fake')"
check "props file fills the rest" "$(field ANTIGRAVITY_CLIENT_SECRET 'props-secret-fake')"

if gen OOT_OAUTH_PROPS="$tmp/missing.properties"; then echo "FAIL missing props file must fail the build"; fail=1
else echo "ok missing props file fails the build"; fi

gen  # leave the default (empty) BuildConfig behind
exit "$fail"
