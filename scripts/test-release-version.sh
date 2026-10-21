#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
gradle_cmd="${GRADLE:-./gradlew}"
check="scripts/check-release-tag.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
fail=0

printf 'org.gradle.jvmargs=-Xmx2g\nversion=1.1.0\n' > "$tmp/gradle.properties"

expect() {
  local want="$1" tag="$2"
  if "$check" "$tag" "$tmp/gradle.properties" >/dev/null 2>&1; then got=pass; else got=fail; fi
  if [[ "$got" == "$want" ]]; then
    echo "ok tag $tag -> $got"
  else
    echo "FAIL tag $tag: want $want, got $got"
    fail=1
  fi
}

expect pass v1.1.0
expect fail v1.1.1
expect fail v1.0.9
expect fail 1.1.0
expect fail v1.1
expect fail v1.1.0-rc1
expect fail ""

for pair in 0.3.1:301 0.4.0:400 0.10.2:1002 1.1.0:10100 1.2.3:10203; do
  v="${pair%%:*}"
  want="${pair##*:}"
  got="$($gradle_cmd -q printVersion -Pversion="$v" | sed -n 's/^versionCode=//p')"
  if [[ "$got" == "$want" ]]; then
    echo "ok versionCode $v -> $got"
  else
    echo "FAIL versionCode $v: want $want, got '$got'"
    fail=1
  fi
done

if $gradle_cmd -q printVersion -Pversion=0.100.0 >/dev/null 2>&1; then
  echo "FAIL version 0.100.0 should be rejected"
  fail=1
else
  echo "ok version 0.100.0 rejected"
fi

exit "$fail"
