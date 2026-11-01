#!/usr/bin/env python3
"""Build the bundled offline threat-intel database for Apps Manager.

Fetches the AssoEchap/stalkerware-indicators `ioc.yaml` (public, reputable,
updated since 2019 - package names + signing-cert SHA-1 of known stalkerware /
watchware Android apps), merges a small hand-curated seed list, and writes
`app/src/main/res/raw/threat_intel_bundled.json` in the app's own schema.

Run before cutting a release so the offline (no-network) source stays fresh:
    python3 tools/build_threat_intel.py

The on-device updater (RemoteFeedFetcher) can refresh the same source at
runtime; this script only refreshes the *bundled* copy.
"""

import datetime
import json
import os
import sys
import urllib.request

import yaml

IOC_URL = (
    "https://raw.githubusercontent.com/AssoEchap/stalkerware-indicators"
    "/master/ioc.yaml"
)
WATCHWARE_URL = (
    "https://raw.githubusercontent.com/AssoEchap/stalkerware-indicators"
    "/master/watchware.yaml"
)
SOURCE_NAME = "stalkerware-indicators"
SOURCE_URL = "https://github.com/AssoEchap/stalkerware-indicators"

# Hand-curated seeds from public malware reports (package -> (severity, label)).
# Keep this list tiny; the YAML feed is the main source.
SEEDS = {
    # STAR Labs, 2026-10-08: Novinarya banking/crypto stealer + its loader.
    "ir.novinarya": ("malware", "Novinarya stealer"),
    "net.swiftnova.bridge": ("malware", "Novinarya loader"),
}

OUT_PATH = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app",
    "src",
    "main",
    "res",
    "raw",
    "threat_intel_bundled.json",
)


def fetch_yaml(url):
    req = urllib.request.Request(url, headers={"User-Agent": "apps-manager-build/1.0"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read().decode("utf-8")


def main():
    print("Fetching", IOC_URL)
    raw = fetch_yaml(IOC_URL)
    doc = yaml.safe_load(raw)
    print("Parsed %d IOC entries" % len(doc))
    print("Fetching", WATCHWARE_URL)
    watchware = yaml.safe_load(fetch_yaml(WATCHWARE_URL)) or []
    print("Parsed %d watchware entries" % len(watchware))
    doc = list(doc) + list(watchware)

    entries = {}  # package -> dict
    certs = {}  # sha1 -> dict

    def add_package(pkg, severity, label):
        pkg = pkg.strip()
        if not pkg:
            return
        # Keep the strongest severity seen for a package (malware > suspect).
        prev = entries.get(pkg)
        if prev is None or (prev["severity"] == "suspect" and severity == "malware"):
            entries[pkg] = {
                "package": pkg,
                "severity": severity,
                "label": label,
                "source": SOURCE_NAME,
            }

    def add_cert(sha1, severity, label):
        sha1 = sha1.strip().upper()
        if not sha1:
            return
        prev = certs.get(sha1)
        if prev is None or (prev["severity"] == "suspect" and severity == "malware"):
            certs[sha1] = {
                "sha1": sha1,
                "severity": severity,
                "label": label,
                "source": SOURCE_NAME,
            }

    for item in doc:
        if not isinstance(item, dict):
            continue
        kind = str(item.get("type", "")).lower()
        severity = "malware" if kind == "stalkerware" else "suspect"
        label = str(item.get("name", "unknown"))
        for pkg in item.get("packages") or []:
            add_package(str(pkg), severity, label)
        for cert in item.get("certificates") or []:
            add_cert(str(cert), severity, label)

    for pkg, (severity, label) in SEEDS.items():
        add_package(pkg, severity, label + " (seed)")

    # Keep only plausible Android package names.
    import re

    pkg_re = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$")
    entries = {k: v for k, v in entries.items() if pkg_re.match(k)}

    bundle = {
        "schema": 1,
        "generated_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "sources": [
            {
                "name": SOURCE_NAME,
                "url": SOURCE_URL,
                "feed": IOC_URL,
                "note": "Package names + signing-cert SHA-1 of known stalkerware/watchware apps",
            },
            {
                "name": "hand-curated seeds",
                "url": "",
                "note": "Small seed list from public malware reports, see tools/build_threat_intel.py",
            },
        ],
        "entries": sorted(entries.values(), key=lambda e: e["package"]),
        "certs": sorted(certs.values(), key=lambda c: c["sha1"]),
    }

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w", encoding="utf-8") as f:
        json.dump(bundle, f, ensure_ascii=False, indent=1)
    print(
        "Wrote %s: %d packages, %d certs"
        % (OUT_PATH, len(bundle["entries"]), len(bundle["certs"]))
    )


if __name__ == "__main__":
    sys.exit(main())
