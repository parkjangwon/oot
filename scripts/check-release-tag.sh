#!/usr/bin/env bash
set -euo pipefail

tag="${1:-${GITHUB_REF_NAME:-}}"
props="${2:-gradle.properties}"

if ! [[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "::error::Invalid tag format ($tag). Expected vX.Y.Z (e.g. v1.1.0)." >&2
  exit 1
fi

declared="$(sed -n 's/^version=[[:space:]]*\([^[:space:]]*\)[[:space:]]*$/\1/p' "$props" | tail -n 1)"
if [[ -z "$declared" ]]; then
  echo "::error::Could not find version in $props." >&2
  exit 1
fi

if [[ "${tag#v}" != "$declared" ]]; then
  echo "::error::Tag ($tag) does not match app version ($declared). Merge the version bump PR to main first, then tag that commit." >&2
  exit 1
fi

echo "Tag ($tag) matches app version ($declared)."
