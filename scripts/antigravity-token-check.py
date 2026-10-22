#!/usr/bin/env python3
"""Check whether an Antigravity Google token is accepted by the cloudcode-pa v1internal API.

Prints only the HTTP status and the error reason of each call. Never prints tokens, client values,
project ids or response bodies. Tokens are read from the environment, never from arguments.

  OOT_AGY_ACCESS_TOKEN=...  python3 scripts/antigravity-token-check.py
  OOT_AGY_REFRESH_TOKEN=... python3 scripts/antigravity-token-check.py   # also needs the client id/secret:
      OOT_ANTIGRAVITY_CLIENT_ID / OOT_ANTIGRAVITY_CLIENT_SECRET in the environment, or in the
      properties file that OOT_OAUTH_PROPS points to (same file the Gradle build reads).
"""
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

TOKEN_URL = "https://oauth2.googleapis.com/token"
LOAD_CODE_ASSIST_URL = "https://cloudcode-pa.googleapis.com/v1internal:loadCodeAssist"
SUMMARY_URL = "https://daily-cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary"
METADATA = {"metadata": {"ideType": "ANTIGRAVITY", "platform": "PLATFORM_UNSPECIFIED", "pluginType": "GEMINI"}}


def props_value(key):
    value = os.environ.get(key, "").strip()
    if value:
        return value
    path = os.environ.get("OOT_OAUTH_PROPS", "").strip()
    if not path:
        return ""
    try:
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith(("#", "!")) and "=" in line:
                    k, v = line.split("=", 1)
                    if k.strip() == key:
                        return v.strip()
    except OSError:
        sys.exit("OOT_OAUTH_PROPS does not point to a readable file")
    return ""


def scrub(text):
    # Belt and braces: never echo anything that looks like a token or a client secret.
    text = re.sub(r"(ya29\.|1//|GOCSPX-)[\w\-.]+", "<redacted>", text)
    text = re.sub(r"[A-Za-z0-9_\-]{40,}", "<redacted>", text)
    return text[:200]


def reason(body):
    try:
        data = json.loads(body)
    except ValueError:
        return "non-JSON body"
    err = data.get("error")
    if isinstance(err, dict):
        parts = [str(err.get("status") or ""), str(err.get("message") or "")]
        for detail in err.get("details") or []:
            if isinstance(detail, dict) and detail.get("reason"):
                parts.append(str(detail["reason"]))
        return scrub(" | ".join(p for p in parts if p))
    if isinstance(err, str):  # OAuth token endpoint style
        return scrub(" | ".join(p for p in [err, str(data.get("error_description") or "")] if p))
    return ""


def call(name, url, data, headers):
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            status, body = resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        status, body = e.code, e.read().decode("utf-8", "replace")
    except urllib.error.URLError as e:
        print(f"{name}: network error ({scrub(str(e.reason))})")
        return None, ""
    line = f"{name}: HTTP {status}"
    if status >= 400:
        line += f" - {reason(body) or 'no error reason'}"
    print(line)
    return status, body


def main():
    access = os.environ.get("OOT_AGY_ACCESS_TOKEN", "").strip()
    refresh = os.environ.get("OOT_AGY_REFRESH_TOKEN", "").strip()
    if not access and not refresh:
        sys.exit("set OOT_AGY_ACCESS_TOKEN or OOT_AGY_REFRESH_TOKEN")

    if not access:
        client_id = props_value("OOT_ANTIGRAVITY_CLIENT_ID")
        client_secret = props_value("OOT_ANTIGRAVITY_CLIENT_SECRET")
        if not client_id or not client_secret:
            sys.exit("a refresh token needs OOT_ANTIGRAVITY_CLIENT_ID and OOT_ANTIGRAVITY_CLIENT_SECRET (env or OOT_OAUTH_PROPS)")
        form = urllib.parse.urlencode({
            "grant_type": "refresh_token", "refresh_token": refresh,
            "client_id": client_id, "client_secret": client_secret,
        }).encode()
        status, body = call("token refresh", TOKEN_URL, form, {"Content-Type": "application/x-www-form-urlencoded"})
        if status != 200:
            return 1
        access = json.loads(body).get("access_token", "")
        if not access:
            print("token refresh: no access_token in the answer")
            return 1

    headers = {"Authorization": f"Bearer {access}", "Content-Type": "application/json", "User-Agent": "oot-token-check"}
    status, body = call("loadCodeAssist", LOAD_CODE_ASSIST_URL, json.dumps(METADATA).encode(), headers)
    project = None
    if status == 200:
        try:
            raw = json.loads(body).get("cloudaicompanionProject")
            project = raw.get("id") if isinstance(raw, dict) else raw
        except ValueError:
            pass
        print(f"loadCodeAssist: project {'present' if project else 'absent'}")
    summary_body = json.dumps({"project": project} if project else {}).encode()
    status2, _ = call("retrieveUserQuotaSummary", SUMMARY_URL, summary_body, headers)
    return 0 if status == 200 and status2 == 200 else 1


if __name__ == "__main__":
    sys.exit(main())
