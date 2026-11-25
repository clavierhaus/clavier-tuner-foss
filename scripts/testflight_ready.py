#!/usr/bin/env python3
"""testflight_ready.py -- is App Store Connect ready for an upload, before the Mac is started?

Run by release-ios.sh on the X1 before it starts the build (Peter, 22 Nov 2026, 22:12, after build 104 was built, uploaded and
processed and only then refused for external testing by a missing Beta App Description: "Shit like that burns my money").
It measures nothing and changes nothing: it reads App Store Connect with the same key the jobs use and says, by name, every
field Apple will refuse the build for, so a missing one costs no Mac minute.

Checked: the app exists for the bundle; Test Information has a Beta App Description and a feedback email in some
language; the Beta App Review contact (first and last name, phone, email) is filled; an external group exists (else the
public link gets nothing, said, not refused).

The key: ASC_KEY (the .p8), ASC_KEY_ID and ASC_ISSUER from the environment; without them the newest AuthKey_<ID>.p8 in
Clavierhaus/Apple beside the repositories (or ~/Downloads), its ID from its name, and the team's issuer.

    testflight_ready.py --bundle-id at.clavierhaus.claviertuner
"""
import argparse
import glob
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from testflight import Asc  # noqa: E402  the same token and calls as the job's

ISSUER = "4aff3636-5757-45f5-b9f5-58987d53bb80"  # the team's, the same for every key (docs: ios-account, 8 Nov 2026)


def main():
    a = argparse.ArgumentParser()
    a.add_argument("--bundle-id", required=True)
    o = a.parse_args()
    key = os.environ.get("ASC_KEY")
    if not key:
        # beside the repositories first (/home/erweitert/Clavierhaus/Apple on the X1), then ~/Downloads (Peter, 25 Nov
        # 2026: secrets are not kept as hidden files in his home)
        beside = os.path.join(os.path.dirname(ROOT), "Clavierhaus", "Apple", "AuthKey_*.p8")
        found = sorted(glob.glob(beside), key=os.path.getmtime) or \
            sorted(glob.glob(os.path.expanduser("~/Downloads/AuthKey_*.p8")), key=os.path.getmtime)
        if not found:
            sys.exit("  x  no App Store Connect key: set ASC_KEY to the .p8, or keep AuthKey_<ID>.p8 in Clavierhaus/Apple beside the repositories")
        key = found[-1]
    key_id = os.environ.get("ASC_KEY_ID") or os.path.basename(key)[len("AuthKey_"):-len(".p8")]
    asc = Asc(key, key_id, os.environ.get("ASC_ISSUER", ISSUER))

    apps = asc.ok("GET", "/v1/apps", {"filter[bundleId]": o.bundle_id})["data"]
    if not apps:
        sys.exit(f"  x  no app for {o.bundle_id} in App Store Connect: Apps › + › New App first")
    app = apps[0]["id"]
    missing = []

    locs = asc.ok("GET", f"/v1/apps/{app}/betaAppLocalizations")["data"]
    if not any((l["attributes"].get("description") or "").strip() for l in locs):
        missing.append("Test Information › Beta App Description")
    if not any((l["attributes"].get("feedbackEmail") or "").strip() for l in locs):
        missing.append("Test Information › Feedback Email")

    status, body = asc.call("GET", f"/v1/apps/{app}/betaAppReviewDetail")
    d = (body.get("data") or {}).get("attributes", {}) if status < 400 else {}
    for field, name in [("contactFirstName", "first name"), ("contactLastName", "last name"),
                        ("contactPhone", "phone"), ("contactEmail", "email")]:
        if not (d.get(field) or "").strip():
            missing.append(f"Test Information › Beta App Review contact {name}")

    if missing:
        sys.exit("  x  App Store Connect will refuse external testing; fill in first:\n" +
                 "\n".join(f"       - {m}" for m in missing))

    groups = asc.ok("GET", "/v1/betaGroups", {"filter[app]": app})["data"]
    external = [g["attributes"]["name"] for g in groups if not g["attributes"].get("isInternalGroup")]
    print(f"  ok App Store Connect ready: key {key_id}, " +
          (f"external group {', '.join(external)}" if external else "no external group yet: the public link gets nothing"))


if __name__ == "__main__":
    main()
