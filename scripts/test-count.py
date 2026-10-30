#!/usr/bin/env python3
"""Count what the test runs actually did, from the JUnit XML Gradle writes, and say it.

A green build says nothing about how many tests ran: on 4 Oct four recordings tests counted
green while reading nothing. This prints tests, skipped and failed per module and in all,
and adds them to the CI run's summary page when there is one. Usage: test-count.py <dir>...
"""
import glob, os, re, sys

rows, total = [], [0, 0, 0]
for root in sys.argv[1:] or ["."]:
    t = s = f = 0
    for p in glob.glob(os.path.join(root, "build/test-results/**/*.xml"), recursive=True):
        m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', open(p).read())
        if m:
            t += int(m[1]); s += int(m[2]); f += int(m[3]) + int(m[4])
    rows.append((root, t, s, f))
    total = [total[0] + t, total[1] + s, total[2] + f]
lines = ["| module | tests | skipped | failed |", "|---|---|---|---|"]
lines += [f"| {r} | {t} | {s} | {f} |" for r, t, s, f in rows]
lines.append(f"| all | {total[0]} | {total[1]} | {total[2]} |")
print("\n".join(lines))
summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary:
    with open(summary, "a") as out:
        out.write("\n".join(lines) + "\n")
if total[0] == 0:
    sys.exit("no test results found: nothing ran")
