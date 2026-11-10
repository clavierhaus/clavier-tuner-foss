#!/usr/bin/env python3
"""testflight.py -- what the testers read, and the build to the testers, once Apple has the upload.

Run by the testflight job of .github/workflows/build.yml after the upload (Peter, 10 Nov 2026: "Could you do this as a
release-ios.sh in foss as soon as new things happen?"). It reads no recording and measures nothing: it is release
tooling, as release.sh is. In order:

  1. waits until App Store Connect has processed the build just uploaded (its version and build number), up to
     --wait minutes; a build Apple refuses (FAILED, INVALID) stops here;
  2. writes What to Test from <notes>/<locale>.txt for every such file (en-US is required); the file's first line must be
     the version, so notes left from an earlier version are refused rather than shown;
  3. gives the build to every beta group that does not take every build by itself, and submits it to Beta App Review
     when an external group is among them and it has not been submitted yet.

Only the Python standard library and openssl: the App Store Connect token is signed by openssl (ES256) and its DER
signature turned into the raw r||s the token wants. ASC_API overrides the address, for a test against a stand-in.

    testflight.py --key AuthKey_X.p8 --key-id X --issuer Y --bundle-id at.clavierhaus.claviertuner \
                  --version 0.8.0 --build 171 --notes fastlane/metadata/ios/testflight
"""
import argparse
import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = os.environ.get("ASC_API", "https://api.appstoreconnect.apple.com")
WHAT_TO_TEST_MAX = 4000


def b64url(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def der_to_raw(der: bytes) -> bytes:
    """ECDSA-Sig-Value ::= SEQUENCE { r INTEGER, s INTEGER } -> r||s, 32 bytes each."""
    def length(i):
        n = der[i]
        if n < 0x80:
            return n, i + 1
        k = n & 0x7F
        return int.from_bytes(der[i + 1:i + 1 + k], "big"), i + 1 + k
    assert der[0] == 0x30, "not a DER sequence"
    _, i = length(1)
    out = b""
    for _ in range(2):
        assert der[i] == 0x02, "not a DER integer"
        n, i = length(i + 1)
        v = der[i:i + n].lstrip(b"\x00")
        assert len(v) <= 32, "integer longer than 32 bytes"
        out += v.rjust(32, b"\x00")
        i += n
    return out


class Asc:
    def __init__(self, key, key_id, issuer):
        self.key, self.key_id, self.issuer = key, key_id, issuer

    def token(self):
        # a fresh token per request: Apple accepts at most twenty minutes, and the wait for processing is longer
        now = int(time.time())
        head = b64url(json.dumps({"alg": "ES256", "kid": self.key_id, "typ": "JWT"}).encode())
        body = b64url(json.dumps({"iss": self.issuer, "iat": now, "exp": now + 600, "aud": "appstoreconnect-v1"}).encode())
        signing = f"{head}.{body}".encode()
        der = subprocess.run(["openssl", "dgst", "-sha256", "-sign", self.key], input=signing, capture_output=True, check=True).stdout
        return f"{head}.{body}.{b64url(der_to_raw(der))}"

    def call(self, method, path, params=None, data=None):
        url = API + path + ("?" + urllib.parse.urlencode(params) if params else "")
        req = urllib.request.Request(url, method=method, data=json.dumps(data).encode() if data is not None else None)
        req.add_header("Authorization", "Bearer " + self.token())
        if data is not None:
            req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                raw = r.read()
                return r.status, (json.loads(raw) if raw else {})
        except urllib.error.HTTPError as e:
            raw = e.read()
            try:
                return e.code, json.loads(raw)
            except ValueError:
                return e.code, {"errors": [{"detail": raw.decode(errors="replace")[:300]}]}

    def ok(self, method, path, params=None, data=None):
        status, body = self.call(method, path, params, data)
        if status >= 400:
            sys.exit(f"  x  {method} {path}: {status} {why(body)}")
        return body


def why(body):
    return "; ".join(e.get("detail") or e.get("title") or str(e) for e in body.get("errors", [])) or json.dumps(body)[:300]


def notes(directory, version):
    found = {}
    for name in sorted(os.listdir(directory)):
        if not name.endswith(".txt"):
            continue
        text = open(os.path.join(directory, name), encoding="utf-8").read().strip()
        first = text.splitlines()[0].strip() if text else ""
        if first != version:
            sys.exit(f"  x  {name} is for '{first}', the build is {version}: notes of another version are not sent")
        if len(text) > WHAT_TO_TEST_MAX:
            sys.exit(f"  x  {name}: {len(text)} characters, TestFlight takes {WHAT_TO_TEST_MAX}")
        found[name[:-4]] = text
    if "en-US" not in found:
        sys.exit(f"  x  {directory}/en-US.txt missing")
    return found


def main():
    a = argparse.ArgumentParser()
    a.add_argument("--key", required=True)
    a.add_argument("--key-id", required=True)
    a.add_argument("--issuer", required=True)
    a.add_argument("--bundle-id", required=True)
    a.add_argument("--version", required=True)
    a.add_argument("--build", required=True)
    a.add_argument("--notes", required=True)
    a.add_argument("--wait", type=float, default=45, help="minutes to wait for Apple's processing")
    a.add_argument("--poll", type=float, default=30, help="seconds between two looks")
    o = a.parse_args()
    texts = notes(o.notes, o.version)       # refused before anything is asked of Apple
    asc = Asc(o.key, o.key_id, o.issuer)

    apps = asc.ok("GET", "/v1/apps", {"filter[bundleId]": o.bundle_id})["data"]
    if not apps:
        sys.exit(f"  x  no app {o.bundle_id} in App Store Connect")
    app = apps[0]["id"]

    # 1. the build, once processed
    deadline = time.time() + o.wait * 60
    build = None
    while True:
        found = asc.ok("GET", "/v1/builds", {"filter[app]": app, "filter[version]": o.build,
                                              "filter[preReleaseVersion.version]": o.version, "limit": 1})["data"]
        state = found[0]["attributes"].get("processingState") if found else "not there yet"
        if state == "VALID":
            build = found[0]["id"]
            break
        if state in ("FAILED", "INVALID"):
            sys.exit(f"  x  Apple's processing of {o.version} ({o.build}): {state}")
        if time.time() > deadline:
            sys.exit(f"  x  {o.version} ({o.build}) still {state} after {o.wait:g} minutes: uploaded, notes not set")
        print(f"  .. {o.version} ({o.build}): {state}", flush=True)
        time.sleep(o.poll)
    print(f"  ok {o.version} ({o.build}) processed")

    # 2. What to Test, per language
    have = {x["attributes"]["locale"]: x["id"] for x in asc.ok("GET", f"/v1/builds/{build}/betaBuildLocalizations")["data"]}
    for locale, text in texts.items():
        if locale in have:
            asc.ok("PATCH", f"/v1/betaBuildLocalizations/{have[locale]}",
                   data={"data": {"type": "betaBuildLocalizations", "id": have[locale], "attributes": {"whatToTest": text}}})
        else:
            asc.ok("POST", "/v1/betaBuildLocalizations",
                   data={"data": {"type": "betaBuildLocalizations", "attributes": {"locale": locale, "whatToTest": text},
                                  "relationships": {"build": {"data": {"type": "builds", "id": build}}}}})
        print(f"  ok What to Test, {locale}")

    # 3. the testers
    groups = asc.ok("GET", "/v1/betaGroups", {"filter[app]": app, "limit": 200})["data"]
    external = False
    for g in groups:
        at = g["attributes"]
        if at.get("isInternalGroup") and at.get("hasAccessToAllBuilds"):
            print(f"  ok {at['name']}: takes every build by itself")
            continue
        asc.ok("POST", f"/v1/betaGroups/{g['id']}/relationships/builds", data={"data": [{"type": "builds", "id": build}]})
        print(f"  ok {at['name']}: build given")
        external |= not at.get("isInternalGroup")
    if not groups:
        print("  .. no tester group in App Store Connect: the build waits there")
    if external:
        sent = asc.ok("GET", "/v1/betaAppReviewSubmissions", {"filter[build]": build})["data"]
        if sent:
            print(f"  ok Beta App Review: {sent[0]['attributes'].get('betaReviewState', 'submitted')}")
        else:
            asc.ok("POST", "/v1/betaAppReviewSubmissions",
                   data={"data": {"type": "betaAppReviewSubmissions", "relationships": {"build": {"data": {"type": "builds", "id": build}}}}})
            print("  ok Beta App Review: submitted (external testers get it once Apple approves)")


if __name__ == "__main__":
    main()
