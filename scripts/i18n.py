#!/usr/bin/env python3
"""The words of an app, from a table into code, and out to translators.

  scripts/i18n.py                 writes the Kotlin table from i18n/strings.tsv
  scripts/i18n.py --check         exits 1 if a key lacks a language, has a bad
                                  argument, or the generated file is out of date
  scripts/i18n.py --export        writes i18n/languages/<catalog>.pot (the English
                                  template) and <catalog>.<lang>.po per language
  scripts/i18n.py --import FILE   takes a translated .po back into the table as a
                                  whole column; refuses one with holes

One table per catalogue, every language side by side, the code never holds a
word. FOSS runs it as is; Pro runs the same script on its own table:

  core/scripts/i18n.py --table i18n/strings.tsv \\
      --out pro/src/commonMain/kotlin/at/clavierhaus/unisonmaster/pro/ProStringsTable.kt \\
      --object ProStringsTable --package at.clavierhaus.unisonmaster.pro --catalog clavier-tuner-pro

The table's header may carry "# version: 2026-09-28" — the day its English
last changed, kept apart from the app's version; it goes into the .po headers
so a translator knows which English a file was made from.

A base language is whole — the check refuses a hole in it. A regional tag
(de-AT, de-CH) holds only the words that differ from its base (docs/I18N.md):
its cells may stay empty and fall to the base at run time, so the check allows
a gap in a regional column (and its base must stand beside it), and --import
takes a regional .po with untranslated rows back as those empty cells.

The order of work (docs/I18N.md): the glossary decides the English, the
German pass follows it, and only then are .po files exported — a translator
who starts on pre-glossary English has every rename undo their work.
"""
import re, sys, pathlib, datetime

ROOT = pathlib.Path.cwd()
args = sys.argv[1:]

def opt(name, default):
    if name in args:
        i = args.index(name); v = args[i + 1]; del args[i:i + 2]; return v
    return default

TABLE = pathlib.Path(opt("--table", "i18n/strings.tsv"))
OUT = pathlib.Path(opt("--out", "shared/src/commonMain/kotlin/at/clavierhaus/unisonmaster/i18n/StringsTable.kt"))
OBJECT = opt("--object", "StringsTable")
PACKAGE = opt("--package", "at.clavierhaus.unisonmaster.i18n")
CATALOG = opt("--catalog", "clavier-tuner")
PO_DIR = pathlib.Path(opt("--po-dir", str(TABLE.parent / "languages")))
IMPORT = opt("--import", None)


def load():
    text = TABLE.read_text(encoding="utf-8")
    version = (re.search(r"^#\s*version:\s*(\S+)", text, re.M) or [None, "unversioned"])[1]
    rows = [l.rstrip("\n") for l in text.splitlines() if l.strip() and not l.startswith("#")]
    header = rows[0].split("\t")
    assert header[0] == "key", "first column must be 'key'"
    langs = header[1:]
    assert langs and langs[0] == "en", "English must be the first language"
    # a regional column (de-AT) carries only where it differs from its base (de); a base language is whole
    problems = [f"{l}: a regional column needs its base {l.split('-')[0]} beside it"
                for l in langs if "-" in l and l.split("-")[0] not in langs]
    table = {}
    for l in rows[1:]:
        cells = l.split("\t")
        if len(cells) != len(header):
            problems.append(f"{cells[0]}: {len(cells) - 1} columns for {len(langs)} languages"); continue
        key, texts = cells[0], cells[1:]
        if not re.fullmatch(r"[a-z][a-zA-Z0-9]*(\.[a-z][a-zA-Z0-9]*)+", key):
            problems.append(f"{key}: not a dotted key")
        if key in table: problems.append(f"{key}: twice")
        args_ = {frozenset(re.findall(r"\{(\d+)\}", t)) for t in texts if t.strip()}
        if len(args_) > 1: problems.append(f"{key}: the languages do not use the same arguments")
        for lang, t in zip(langs, texts):
            if not t.strip() and "-" not in lang: problems.append(f"{key}: no {lang}")
        table[key] = texts
    return version, langs, table, problems


def kotlin(version, langs, table):
    def esc(s): return s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    lines = [f"package {PACKAGE}", ""]
    if PACKAGE != "at.clavierhaus.unisonmaster.i18n":
        lines += ["import at.clavierhaus.unisonmaster.i18n.Catalog", ""]
    lines += [f"// GENERATED from {TABLE.as_posix()} by scripts/i18n.py — edit the table, not this file.", "",
              "/** Every key of the table, as a constant: the compiler catches a key that is not there. */",
              f"object {'K' if OBJECT == 'StringsTable' else OBJECT.replace('StringsTable', 'K')} {{"]
    for key in table:
        lines.append(f'    const val {key.replace(".", "_")} = "{key}"')
    lines += ["}", "", f"{'internal ' if OBJECT == 'StringsTable' else ''}object {OBJECT} : Catalog {{",
              f'    override val name = "{CATALOG}"',
              f'    override val version = "{version}"',
              "    override val languages: List<String> = listOf(" + ", ".join(f'"{l}"' for l in langs) + ")",
              "    override val table: Map<String, Array<String>> = mapOf("]
    for key, texts in table.items():
        lines.append(f'        "{key}" to arrayOf(' + ", ".join(f'"{esc(t)}"' for t in texts) + "),")
    lines += ["    )", "}", ""]
    return "\n".join(lines)


def po_escape(s): return s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")

def po_header(version, lang):
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M+0000")
    return ('msgid ""\nmsgstr ""\n'
            f'"Project-Id-Version: {CATALOG} {version}\\n"\n'
            f'"POT-Creation-Date: {now}\\n"\n'
            f'"Language: {lang}\\n"\n'
            '"MIME-Version: 1.0\\n"\n"Content-Type: text/plain; charset=UTF-8\\n"\n'
            '"Content-Transfer-Encoding: 8bit\\n"\n'
            '"X-Note: msgctxt is the key; msgid the English; {0} {1} are arguments and stay as they are.\\n"\n\n')

def export(version, langs, table):
    PO_DIR.mkdir(parents=True, exist_ok=True)
    pot = po_header(version, "")
    for key, texts in table.items():
        pot += f'msgctxt "{key}"\nmsgid "{po_escape(texts[0])}"\nmsgstr ""\n\n'
    (PO_DIR / f"{CATALOG}.pot").write_text(pot, encoding="utf-8")
    for i, lang in enumerate(langs[1:], 1):
        po = po_header(version, lang)
        for key, texts in table.items():
            po += f'msgctxt "{key}"\nmsgid "{po_escape(texts[0])}"\nmsgstr "{po_escape(texts[i])}"\n\n'
        (PO_DIR / f"{CATALOG}.{lang}.po").write_text(po, encoding="utf-8")
    print(f"i18n: exported {CATALOG}.pot and {len(langs) - 1} .po files to {PO_DIR}")

def po_unescape(s):
    return re.sub(r'\\(.)', lambda m: {"n": "\n", "t": "\t"}.get(m.group(1), m.group(1)), s)

def po_parse(path):
    text = pathlib.Path(path).read_text(encoding="utf-8")
    lang = (re.search(r'"Language: ([\w-]+)\\n"', text) or [None, None])[1]
    entries = {}
    for m in re.finditer(r'msgctxt "([^"]+)"\nmsgid "((?:[^"\\]|\\.)*)"\nmsgstr "((?:[^"\\]|\\.)*)"', text):
        key, en, tr = m.groups()
        entries[key] = (po_unescape(en), po_unescape(tr))
    return lang, entries

def import_po(path, langs, table):
    lang, entries = po_parse(path)
    if not lang: sys.exit("i18n: the .po has no Language header")
    regional = "-" in lang          # a regional column is sparse: what it leaves empty falls to its base
    holes = [k for k in table if not regional and not entries.get(k, ("", ""))[1].strip()]
    stale = [k for k in table if k in entries and entries[k][0] != table[k][0]]
    if holes or stale:
        for k in holes: print(f"i18n: {k}: no {lang}")
        for k in stale: print(f"i18n: {k}: translated from an older English")
        sys.exit(f"i18n: {path} not taken — a language is whole or absent")
    bad = [k for k in table if entries.get(k, ("", ""))[1].strip()
           and set(re.findall(r"\{(\d+)\}", entries[k][1])) != set(re.findall(r"\{(\d+)\}", table[k][0]))]
    if bad:
        for k in bad: print(f"i18n: {k}: the arguments differ")
        sys.exit(1)
    col = langs.index(lang) if lang in langs else None
    lines = TABLE.read_text(encoding="utf-8").splitlines()
    out = []
    for l in lines:
        if l.startswith("#") or not l.strip(): out.append(l); continue
        cells = l.split("\t")
        if cells[0] == "key":
            if col is None: cells.append(lang)
        else:
            v = (entries.get(cells[0]) or (None, ""))[1] if regional else entries[cells[0]][1]
            if col is None: cells.append(v)
            else: cells[col + 1] = v
        out.append("\t".join(cells))
    TABLE.write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"i18n: {lang} taken into {TABLE} ({len(table)} keys); now run scripts/i18n.py")


if __name__ == "__main__":
    version, langs, table, problems = load()
    if IMPORT:
        import_po(IMPORT, langs, table); sys.exit(0)
    if problems:
        print("\n".join("i18n: " + p for p in problems)); sys.exit(1)
    code = kotlin(version, langs, table)
    if "--export" in args:
        export(version, langs, table)
    elif "--check" in args:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != code:
            print(f"i18n: {OUT} is out of date — run scripts/i18n.py"); sys.exit(1)
        print(f"i18n: {CATALOG} {version}: {len(table)} keys, {len(langs)} languages, up to date")
    else:
        OUT.write_text(code, encoding="utf-8")
        print(f"i18n: {CATALOG} {version}: wrote {len(table)} keys × {len(langs)} languages")
