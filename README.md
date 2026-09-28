# Clavier Tuner

**The first free and open-source app made for professional piano tuning.**

Clavier Tuner measures the piano in front of you and tunes it from that
measurement: as accurate as the devices you know, and open for anyone to
check how. It runs on an Android phone, listens through the microphone with
every effect switched off, and keeps everything on the phone — no account,
no server, nothing uploaded.

Made by [clavierhaus.at](https://clavierhaus.at), Vienna, by a piano tuner
who wanted to see what his instruments actually do.

## What it does

- **Measures your piano in two minutes.** Strike a few keys, single strings,
  mutes in. From those strings the app fits the inharmonicity of the whole
  compass and computes a target for every note *before* you tune the first
  one — the stretch comes from this piano, not from a table.
- **Reads the way a tuner listens.** For every note the app picks the
  partial a tuner would listen to in that register, synthesises its target,
  and reads the string against it by phase, to a tenth of a cent. The
  display shows the reading as measured, 47 times a second: a string that
  wobbles is shown wobbling, a false beat is shown as what it is. Nothing is
  smoothed into a verdict.
- **Full Spectrum.** Every partial the string has, each read by phase on a
  cents axis against the string's own model. A single string reads
  congruent; a stray partial is a false beat, and you see which.
- **Follows the key.** Strike a note and the screen is on it; the reading
  turns green at a match. One note, one motion, Done.
- **Keeps the tuning.** Continue tomorrow where you stopped: the session is
  saved in one sealed file under a key that never leaves the phone.
- **One screen, landscape, no wizards.** Drawn for a phone lying on the
  plate: near-black, one orange, light blue only for targets, green only for
  a match.

Pitch raises are out of scope by design.

## How it measures

The chain is the one every proven tuning device uses, written down and
testable:

1. The note is an input. The app never guesses what you struck from the
   spectrum alone; it identifies the key by fitting a string — fundamental
   and inharmonicity — to the peaks it hears (`tuning/KeyIdentifier`).
2. The partial is chosen by register (`measure/PartialMap`).
3. The target is synthesised from the reference and the instrument's
   inharmonicity curve (`tuning/InharmonicityCurve`, `tuning/TuningSession`).
4. The reading is the phase of that partial against the target
   (`measure/PhaseReader`), gated on the partial being present.
5. Done measures the string from the last strike — every partial, and
   the fitted inharmonicity — and keeps it (`measure/StringMeasure`).

Raw 48 kHz PCM, unprocessed; no third-party DSP; the same code on every
platform. The core tests run against synthetic strings; key identification
and the curve were judged on recordings of five pianos.

## FOSS and Pro

This repository is the whole engine and the free app. Clavier Tuner Pro,
from clavierhaus, is built on exactly this core and adds what a working
technician's day needs: sampling anywhere on the compass, the unison display
that reads all strings of a note partial by partial, and the customer
workbook. The free app measures its inharmonicity from the temperament
octave A3–A4 and six strings across the compass, which is what it needs to
tune the whole piano.

## Build

Android SDK and JDK 17; `local.properties` pointing at the SDK.

```
./gradlew :shared:testAndroidHostTest     # the core tests, JVM only
./gradlew :androidApp:assembleDebug
./scripts/deploy.sh                       # pull, test, build, install on the connected phone
```

`shared/` is Kotlin Multiplatform with an iOS stub; only the Android app
exists today.

## Contributing

- **Languages.** The app's words live in `i18n/strings.tsv`
  (`scripts/i18n.py --check` refuses holes). English and German today.
- **Words.** The app speaks the technician's words; `docs/GLOSSARY.md`
  decides each one once, in English and German, with its source. A new
  word on a screen cites its row there.
- **Measurement.** Every decision about what is measured and how is written
  down under `docs/` (in preparation) with the recordings it was judged on;
  a change to the measurement is a change to that document in the same
  commit.
- **Issues** with a recording attached are worth ten without.

## Licence

Apache License 2.0 today (see `LICENSE`). The licence for the public
release is being settled and may change before it.
