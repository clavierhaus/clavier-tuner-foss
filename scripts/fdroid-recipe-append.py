#!/usr/bin/env python3
"""fdroid-recipe-append.py <recipe.yml> <versionName> <versionCode> <commitSha> <reference.yml>

Adds a build block for one version to an F-Droid recipe in fdroiddata, in the shape of the
block in this repository's reference recipe (fdroid/<appid>.yml): the three version fields
set, any `disable:` line dropped, everything else of the shape kept. The recipe keeps every
published version, so an existing block for this versionCode is REPLACED and the others are
left as they are -- a global edit over the file rewrote every block to the new version once
(gnubg, 1.0.2: "Builds has non-unique elements"). CurrentVersion / CurrentVersionCode follow;
AllowedAPKSigningKeys and Binaries are taken from the reference when the recipe lacks them
(the first submission) and left alone otherwise. Idempotent: run twice, one block.

Written with ruamel-free plain PyYAML; the file is loaded and dumped whole, which loses the
reference's comments in the fdroiddata copy -- that copy is F-Droid's, and carries none.
"""
import sys
import yaml


def main(recipe, version, code, sha, reference):
    code = int(code)
    with open(reference) as f:
        ref = yaml.safe_load(f)
    with open(recipe) as f:
        data = yaml.safe_load(f) or {}
    shape = dict(ref["Builds"][-1])
    shape.pop("disable", None)
    shape["versionName"] = version
    shape["versionCode"] = code
    shape["commit"] = str(sha)          # a hash of digits only would otherwise become a number
    builds = [b for b in data.get("Builds", []) if int(b.get("versionCode", -1)) != code]
    builds.append(shape)
    builds.sort(key=lambda b: int(b["versionCode"]))
    data["Builds"] = builds
    for key in ("AllowedAPKSigningKeys", "Binaries"):
        if key in ref and key not in data:
            data[key] = str(ref[key])
    if "AllowedAPKSigningKeys" in data:
        data["AllowedAPKSigningKeys"] = str(data["AllowedAPKSigningKeys"])
    for b in builds:
        b["commit"] = str(b["commit"])
        # F-Droid's schema wants the string 'yes' in gradle:, and YAML 1.1 reads a bare yes as
        # True (the fork's CI, 10 Oct 2026: "'yes' was expected"); said as the string wherever it
        # came in as the boolean
        if "gradle" in b:
            b["gradle"] = ["yes" if g is True else g for g in b["gradle"]]
    data["CurrentVersion"] = version
    data["CurrentVersionCode"] = code
    codes = [int(b["versionCode"]) for b in builds]
    assert len(codes) == len(set(codes)), "duplicate versionCode after append"
    with open(recipe, "w") as f:
        yaml.safe_dump(data, f, sort_keys=False, allow_unicode=True, default_flow_style=False, width=120)
    print(f"{recipe}: build {version} ({code}) at {sha[:12]}; {len(builds)} build block(s)")


if __name__ == "__main__":
    if len(sys.argv) != 6:
        sys.exit(__doc__)
    main(*sys.argv[1:])
