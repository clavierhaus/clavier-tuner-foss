#!/usr/bin/env python3
"""appstore.py -- Clavier Tuner's App Store version, from the files in this repository, and its submission to App Review.

Peter, 23 Nov 2026, 22:35: "prepare an actual release in the app store". Release tooling, as release-ios.sh is: it reads
no recording and measures nothing. Run on the X1 after a build of this version is on TestFlight. In order:

  1. the App Store version of this version (androidApp's versionName): found, or made, on the iPhone platform; released
     by hand after approval (MANUAL), so nothing goes on sale before Peter says so;
  2. the app's name, subtitle and privacy policy URL, and the version's description, keywords, promotional text, support
     and marketing URLs, in every language under fastlane/metadata/ios/<locale>/ (en-US, de-DE, fr-FR);
  3. the categories (Music, then Utilities), the copyright, and the content rights (no third-party content);
  4. the newest build of this version that Apple has processed, attached to it;
  5. the contact for App Review, taken from TestFlight's Beta App Review contact, with notes on how to review an app that
     needs a piano;
  6. the screenshots under fastlane/screenshots/ios/<locale>/*.png, by their size: iPhone large (2868x1320, 2796x1290),
     iPhone medium (2622x1206, 2556x1179) and iPad 13" (2752x2064, 2732x2048), either way round; a set is replaced whole;
  7. what only App Store Connect's own pages can do, said by name: the price (free), App Privacy, the age rating, and
     the EU trader status;
  8. with --submit, and only then: the version to App Review.

Every step reads before it writes, so a second run changes only what differs. The key: ASC_KEY (the .p8), ASC_KEY_ID and
ASC_ISSUER from the environment; without them the newest AuthKey_<ID>.p8 in Clavierhaus/Apple beside the repositories, or in ~/Downloads, as testflight_ready.py does.

    python3 scripts/appstore.py            the version prepared, nothing submitted
    python3 scripts/appstore.py --submit   and sent to App Review

Unverified until its first run against App Store Connect (23 Nov 2026): the API's field names are Apple's of that year.
"""
import argparse
import glob
import hashlib
import json
import os
import struct
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
from testflight import Asc, why  # noqa: E402  the same token and calls as TestFlight's

sys.stdout.reconfigure(line_buffering=True)
ISSUER = "4aff3636-5757-45f5-b9f5-58987d53bb80"  # the team's, as testflight_ready.py has it
BUNDLE = "at.clavierhaus.claviertuner"
META = os.path.join(ROOT, "fastlane", "metadata", "ios")
SHOTS = os.path.join(ROOT, "fastlane", "screenshots", "ios")
LOCALES = ["en-US", "de-DE", "fr-FR"]
PRIMARY_CATEGORY, SECONDARY_CATEGORY = "MUSIC", "UTILITIES"
COPYRIGHT = "2026 Clavierhaus GmbH"
REVIEW_NOTES = (
    "Clavier Tuner tunes a piano: it listens through the microphone to single strings struck on a real piano. Without a "
    "piano, a tone generator or a recording of a piano note at A4 (440 Hz) shows the first screen working: New Tuning, "
    "then the strobe stands still when the sound is at the pitch shown. Help in the app (the gear, then Help) explains "
    "every step. No account, no network, no data collected."
)
# (width, height) in pixels -> App Store Connect's display type; both orientations
SIZES = {}
for w, h in [(2868, 1320), (2796, 1290)]:
    SIZES[(w, h)] = SIZES[(h, w)] = "APP_IPHONE_67"
# the medium Dynamic Island group (iPhone 14 Pro to 17), the one App Store Connect names as required (screenshot
# specifications, read 23 Nov 2026); its API name is taken to be APP_IPHONE_61, unverified until the first upload
for w, h in [(2622, 1206), (2556, 1179)]:
    SIZES[(w, h)] = SIZES[(h, w)] = "APP_IPHONE_61"
for w, h in [(2752, 2064), (2732, 2048)]:
    SIZES[(w, h)] = SIZES[(h, w)] = "APP_IPAD_PRO_3GEN_129"
EDITABLE = {"PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED", "INVALID_BINARY"}


def ok(msg):
    print(f"  ok {msg}")


def say(msg):
    print(f"  .. {msg}")


def read(locale, name):
    p = os.path.join(META, locale, name)
    return open(p, encoding="utf-8").read().strip() if os.path.isfile(p) else None


def version_name():
    for line in open(os.path.join(ROOT, "androidApp", "build.gradle.kts"), encoding="utf-8"):
        if "versionName = \"" in line:
            return line.split('"')[1]
    sys.exit("  x  no versionName in androidApp/build.gradle.kts")


def png_size(path):
    with open(path, "rb") as f:
        head = f.read(24)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        sys.exit(f"  x  {path}: not a PNG")
    return struct.unpack(">II", head[16:24])


def key():
    k = os.environ.get("ASC_KEY")
    if not k:
        # beside the repositories first (/home/erweitert/Clavierhaus/Apple on the X1), then ~/Downloads (Peter, 25 Nov
        # 2026: secrets are not kept as hidden files in his home)
        beside = os.path.join(os.path.dirname(ROOT), "Clavierhaus", "Apple", "AuthKey_*.p8")
        found = sorted(glob.glob(beside), key=os.path.getmtime) or \
            sorted(glob.glob(os.path.expanduser("~/Downloads/AuthKey_*.p8")), key=os.path.getmtime)
        if not found:
            sys.exit("  x  no App Store Connect key: set ASC_KEY to the .p8, or keep AuthKey_<ID>.p8 in Clavierhaus/Apple beside the repositories")
        k = found[-1]
    kid = os.environ.get("ASC_KEY_ID") or os.path.basename(k)[len("AuthKey_"):-len(".p8")]
    return Asc(k, kid, os.environ.get("ASC_ISSUER", ISSUER))


def patch_if(asc, kind, oid, current, wanted):
    """PATCH only the attributes that differ; returns the names changed."""
    diff = {k: v for k, v in wanted.items() if v is not None and current.get(k) != v}
    if diff:
        asc.ok("PATCH", f"/v1/{kind}/{oid}", data={"data": {"type": kind, "id": oid, "attributes": diff}})
    return sorted(diff)


def the_version(asc, app, version):
    versions = asc.ok("GET", f"/v1/apps/{app}/appStoreVersions", {"filter[platform]": "IOS", "limit": 50})["data"]
    for v in versions:
        if v["attributes"]["versionString"] == version:
            return v
    editable = [v for v in versions if v["attributes"].get("appStoreState") in EDITABLE]
    if editable:  # the one version Apple lets be edited, renamed to this one
        v = editable[0]
        patch_if(asc, "appStoreVersions", v["id"], v["attributes"], {"versionString": version})
        say(f"version {v['attributes']['versionString']} renamed {version}")
        return asc.ok("GET", f"/v1/appStoreVersions/{v['id']}")["data"]
    return asc.ok("POST", "/v1/appStoreVersions", data={"data": {
        "type": "appStoreVersions",
        "attributes": {"platform": "IOS", "versionString": version, "copyright": COPYRIGHT, "releaseType": "MANUAL"},
        "relationships": {"app": {"data": {"type": "apps", "id": app}}}}})["data"]


def localizations(asc, parent_kind, parent_id, kind, rel, fields):
    """One localization per locale with its files: made where missing, patched where it differs."""
    have = {l["attributes"]["locale"]: l for l in asc.ok("GET", f"/v1/{parent_kind}/{parent_id}/{kind}", {"limit": 50})["data"]}
    out = {}
    for loc in LOCALES:
        wanted = {attr: read(loc, f) for attr, f in fields.items()}
        if not any(wanted.values()):
            continue
        if loc in have:
            changed = patch_if(asc, kind, have[loc]["id"], have[loc]["attributes"], wanted)
            out[loc] = have[loc]["id"]
            ok(f"{kind} {loc}: " + (", ".join(changed) if changed else "as it was"))
        else:
            made = asc.ok("POST", f"/v1/{kind}", data={"data": {
                "type": kind, "attributes": dict({"locale": loc}, **{k: v for k, v in wanted.items() if v}),
                "relationships": {rel: {"data": {"type": parent_kind, "id": parent_id}}}}})["data"]
            out[loc] = made["id"]
            ok(f"{kind} {loc}: made")
    return out


def screenshots(asc, version_locs):
    for loc in sorted(os.listdir(SHOTS)) if os.path.isdir(SHOTS) else []:
        files = sorted(glob.glob(os.path.join(SHOTS, loc, "*.png")))
        if not files:
            continue
        if loc not in version_locs:
            sys.exit(f"  x  screenshots for {loc}, but no description for it under {META}")
        by_type = {}
        for f in files:
            size = png_size(f)
            kind = SIZES.get(size)
            if not kind:
                sys.exit(f"  x  {f}: {size[0]}x{size[1]} is no size App Store Connect takes here "
                         f"(iPhone 2868x1320, 2796x1290, 2622x1206 or 2556x1179, iPad 2752x2064 or 2732x2048, either way round)")
            by_type.setdefault(kind, []).append(f)
        sets = {s["attributes"]["screenshotDisplayType"]: s["id"] for s in asc.ok(
            "GET", f"/v1/appStoreVersionLocalizations/{version_locs[loc]}/appScreenshotSets", {"limit": 50})["data"]}
        for kind, paths in by_type.items():
            if len(paths) > 10:
                sys.exit(f"  x  {loc} {kind}: {len(paths)} screenshots, App Store Connect takes 10")
            sid = sets.get(kind) or asc.ok("POST", "/v1/appScreenshotSets", data={"data": {
                "type": "appScreenshotSets", "attributes": {"screenshotDisplayType": kind},
                "relationships": {"appStoreVersionLocalization": {"data": {
                    "type": "appStoreVersionLocalizations", "id": version_locs[loc]}}}}})["data"]["id"]
            old = asc.ok("GET", f"/v1/appScreenshotSets/{sid}/appScreenshots", {"limit": 50})["data"]
            same = [(s["attributes"].get("fileName"), (s["attributes"].get("sourceFileChecksum") or "")) for s in old]
            mine = [(os.path.basename(p), hashlib.md5(open(p, "rb").read()).hexdigest()) for p in paths]
            if same == mine:
                ok(f"screenshots {loc} {kind}: {len(paths)}, as they were")
                continue
            for s in old:
                asc.ok("DELETE", f"/v1/appScreenshots/{s['id']}")
            for p in paths:
                upload(asc, sid, p)
            ok(f"screenshots {loc} {kind}: {len(paths)} uploaded")


def upload(asc, set_id, path):
    data = open(path, "rb").read()
    shot = asc.ok("POST", "/v1/appScreenshots", data={"data": {
        "type": "appScreenshots", "attributes": {"fileName": os.path.basename(path), "fileSize": len(data)},
        "relationships": {"appScreenshotSet": {"data": {"type": "appScreenshotSets", "id": set_id}}}}})["data"]
    for op in shot["attributes"]["uploadOperations"]:
        part = data[op["offset"]:op["offset"] + op["length"]]
        req = urllib.request.Request(op["url"], method=op["method"], data=part)
        for h in op.get("requestHeaders", []):
            req.add_header(h["name"], h["value"])
        with urllib.request.urlopen(req, timeout=120) as r:
            if r.status >= 300:
                sys.exit(f"  x  {path}: upload part answered {r.status}")
    asc.ok("PATCH", f"/v1/appScreenshots/{shot['id']}", data={"data": {
        "type": "appScreenshots", "id": shot["id"],
        "attributes": {"uploaded": True, "sourceFileChecksum": hashlib.md5(data).hexdigest()}}})


def main():
    a = argparse.ArgumentParser()
    a.add_argument("--submit", action="store_true", help="send the version to App Review once everything is in place")
    o = a.parse_args()
    version = version_name()
    for loc in LOCALES:
        for f, n in [("name.txt", 30), ("subtitle.txt", 30), ("keywords.txt", 100), ("promotional_text.txt", 170),
                     ("description.txt", 4000)]:
            t = read(loc, f)
            if t is None:
                sys.exit(f"  x  {META}/{loc}/{f} missing")
            if len(t) > n:
                sys.exit(f"  x  {loc}/{f}: {len(t)} characters, App Store Connect takes {n}")
    asc = key()

    apps = asc.ok("GET", "/v1/apps", {"filter[bundleId]": BUNDLE})["data"]
    if not apps:
        sys.exit(f"  x  no app for {BUNDLE} in App Store Connect")
    app = apps[0]
    ok(f"app {app['attributes']['name']} ({app['id']}), version {version}")

    # 1. the version
    v = the_version(asc, app["id"], version)
    state = v["attributes"].get("appStoreState")
    if state not in EDITABLE:
        sys.exit(f"  x  version {version} stands {state}: nothing can be changed or submitted now")
    changed = patch_if(asc, "appStoreVersions", v["id"], v["attributes"], {"copyright": COPYRIGHT, "releaseType": "MANUAL"})
    ok(f"version {version} ({state})" + (": " + ", ".join(changed) if changed else ""))

    # 2. the words, app-wide and the version's
    infos = asc.ok("GET", f"/v1/apps/{app['id']}/appInfos")["data"]
    info = next((i for i in infos if (i["attributes"].get("appStoreState") or i["attributes"].get("state")) != "READY_FOR_SALE"),
                infos[0])
    localizations(asc, "appInfos", info["id"], "appInfoLocalizations", "appInfo",
                  {"name": "name.txt", "subtitle": "subtitle.txt", "privacyPolicyUrl": "privacy_url.txt"})
    version_locs = localizations(asc, "appStoreVersions", v["id"], "appStoreVersionLocalizations", "appStoreVersion",
                                 {"description": "description.txt", "keywords": "keywords.txt",
                                  "promotionalText": "promotional_text.txt", "supportUrl": "support_url.txt",
                                  "marketingUrl": "marketing_url.txt"})

    # 3. the categories and the content rights
    asc.ok("PATCH", f"/v1/appInfos/{info['id']}", data={"data": {"type": "appInfos", "id": info["id"], "relationships": {
        "primaryCategory": {"data": {"type": "appCategories", "id": PRIMARY_CATEGORY}},
        "secondaryCategory": {"data": {"type": "appCategories", "id": SECONDARY_CATEGORY}}}}})
    ok(f"categories {PRIMARY_CATEGORY}, {SECONDARY_CATEGORY}")
    patch_if(asc, "apps", app["id"], app["attributes"], {"contentRightsDeclaration": "DOES_NOT_USE_THIRD_PARTY_CONTENT"})
    ok("content rights: no third-party content")

    # 4. the build
    builds = asc.ok("GET", "/v1/builds", {"filter[app]": app["id"], "filter[preReleaseVersion.version]": version,
                                         "filter[processingState]": "VALID", "sort": "-uploadedDate", "limit": 1})["data"]
    if not builds:
        sys.exit(f"  x  no processed build of {version} on TestFlight: run ./scripts/release-ios.sh first")
    b = builds[0]
    asc.ok("PATCH", f"/v1/appStoreVersions/{v['id']}/relationships/build", data={"data": {"type": "builds", "id": b["id"]}})
    ok(f"build {b['attributes']['version']} attached")

    # 5. App Review's contact and notes, from TestFlight's
    status, beta = asc.call("GET", f"/v1/apps/{app['id']}/betaAppReviewDetail")
    contact = {k: ((beta.get("data") or {}).get("attributes") or {}).get(k) for k in
               ("contactFirstName", "contactLastName", "contactPhone", "contactEmail")} if status < 400 else {}
    wanted = dict(contact, demoAccountRequired=False, notes=REVIEW_NOTES)
    status, rd = asc.call("GET", f"/v1/appStoreVersions/{v['id']}/appStoreReviewDetail")
    if status < 400 and rd.get("data"):
        patch_if(asc, "appStoreReviewDetails", rd["data"]["id"], rd["data"]["attributes"], wanted)
    else:
        asc.ok("POST", "/v1/appStoreReviewDetails", data={"data": {
            "type": "appStoreReviewDetails", "attributes": {k: v2 for k, v2 in wanted.items() if v2 is not None},
            "relationships": {"appStoreVersion": {"data": {"type": "appStoreVersions", "id": v["id"]}}}}})
    if not all(contact.values()):
        say("App Review's contact is incomplete: App Store Connect › the version › App Review Information")
    ok("App Review: contact and notes")

    # 6. the screenshots
    screenshots(asc, version_locs)
    if not glob.glob(os.path.join(SHOTS, "en-US", "*.png")):
        say(f"no screenshots under {SHOTS}/en-US: the iPhone's are required, and the iPad's, the app running on both")

    # 7. what only App Store Connect's pages can do
    print("\n  In App Store Connect, by hand, once:")
    for line in ["Business › your trader status for the EU (the DSA): the company's address, phone and email, shown on the store",
                 "the app › Pricing and Availability: Free, all countries",
                 "the app › App Privacy: Data Not Collected",
                 "the app › App Information › Age Rating: every answer None/No (4+)"]:
        print(f"   - {line}")

    # 8. App Review
    if not o.submit:
        print(f"\n  Version {version} prepared. Once the list above is done: python3 scripts/appstore.py --submit")
        return
    status, sub = asc.call("POST", "/v1/reviewSubmissions", data={"data": {
        "type": "reviewSubmissions", "attributes": {"platform": "IOS"},
        "relationships": {"app": {"data": {"type": "apps", "id": app["id"]}}}}})
    if status >= 400:
        sys.exit(f"  x  App Review refused a submission: {why(sub)}")
    sid = sub["data"]["id"]
    asc.ok("POST", "/v1/reviewSubmissionItems", data={"data": {"type": "reviewSubmissionItems", "relationships": {
        "reviewSubmission": {"data": {"type": "reviewSubmissions", "id": sid}},
        "appStoreVersion": {"data": {"type": "appStoreVersions", "id": v["id"]}}}}})
    status, done = asc.call("PATCH", f"/v1/reviewSubmissions/{sid}", data={"data": {
        "type": "reviewSubmissions", "id": sid, "attributes": {"submitted": True}}})
    if status >= 400:
        sys.exit(f"  x  not submitted; App Store Connect says what is missing:\n     {why(done)}")
    ok(f"version {version} sent to App Review; released by hand after approval")


if __name__ == "__main__":
    main()
