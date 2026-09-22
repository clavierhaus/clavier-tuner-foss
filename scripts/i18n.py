#!/usr/bin/env python3
"""The translations, from i18n/strings.tsv into code.

  scripts/i18n.py           writes shared/.../i18n/StringsTable.kt
  scripts/i18n.py --check   exits 1 if a key lacks a language, has a bad
                            argument, or the generated file is out of date

One table, all languages side by side; the code never holds a word.
"""
import re, sys, pathlib
ROOT = pathlib.Path(__file__).resolve().parent.parent
TSV = ROOT / "i18n" / "strings.tsv"
OUT = ROOT / "shared/src/commonMain/kotlin/at/clavierhaus/unisonmaster/i18n/StringsTable.kt"

def load():
    rows = [l.rstrip("\n") for l in TSV.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")]
    header = rows[0].split("\t")
    assert header[0] == "key", "first column must be 'key'"
    langs = header[1:]
    table = {}
    problems = []
    for l in rows[1:]:
        cells = l.split("\t")
        if len(cells) != len(header):
            problems.append(f"{cells[0]}: {len(cells) - 1} columns for {len(langs)} languages"); continue
        key, texts = cells[0], cells[1:]
        if not re.fullmatch(r"[a-z][a-zA-Z0-9]*(\.[a-z][a-zA-Z0-9]*)+", key):
            problems.append(f"{key}: not a dotted key")
        if key in table: problems.append(f"{key}: twice")
        args = {frozenset(re.findall(r"\{(\d+)\}", t)) for t in texts}
        if len(args) > 1: problems.append(f"{key}: the languages do not use the same arguments")
        for lang, t in zip(langs, texts):
            if not t.strip(): problems.append(f"{key}: no {lang}")
        table[key] = texts
    return langs, table, problems

def kotlin(langs, table):
    def esc(s): return s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    lines = ["package at.clavierhaus.unisonmaster.i18n", "",
             "// GENERATED from i18n/strings.tsv by scripts/i18n.py — edit the table, not this file.", "",
             "/** Every key of the table, as a constant: the compiler catches a key that is not there. */",
             "object K {"]
    for key in table:
        lines.append(f'    const val {key.replace(".", "_")} = "{key}"')
    lines += ["}", "", "internal object StringsTable {",
              "    val languages: List<String> = listOf(" + ", ".join(f'"{l}"' for l in langs) + ")",
              "    val table: Map<String, Array<String>> = mapOf("]
    for key, texts in table.items():
        lines.append(f'        "{key}" to arrayOf(' + ", ".join(f'"{esc(t)}"' for t in texts) + "),")
    lines += ["    )", "}", ""]
    return "\n".join(lines)

if __name__ == "__main__":
    langs, table, problems = load()
    if problems:
        print("\n".join("i18n: " + p for p in problems)); sys.exit(1)
    code = kotlin(langs, table)
    if "--check" in sys.argv:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != code:
            print(f"i18n: {OUT.relative_to(ROOT)} is out of date — run scripts/i18n.py"); sys.exit(1)
        print(f"i18n: {len(table)} keys, {len(langs)} languages, up to date")
    else:
        OUT.write_text(code, encoding="utf-8")
        print(f"i18n: wrote {len(table)} keys × {len(langs)} languages")
