# Clavier Tuner

**A free, open-source piano tuning app, made by a piano tuner.**

Clavier Tuner measures the piano in front of you and tunes it from that
measurement. It listens through the phone's microphone with every effect
switched off, reads each string by phase against a target built from this
piano's own inharmonicity, and keeps everything on the phone — no account, no
server, nothing uploaded. As accurate as the devices you know, and open for
anyone to check how.

Made by [clavierhaus.at](https://clavierhaus.at), Vienna, by a piano tuner who
wanted to see what his instruments actually do.

## What it does

- **Measures first.** Strike a handful of keys, single strings, mutes in. From
  those strings the app measures the inharmonicity of the piano and computes
  where every note belongs before you tune the first one. The stretch comes
  from this instrument, not from a table.
- **Follows the key.** Strike a note and the screen is on it — the key is
  identified by fitting a string to what the microphone hears, not guessed
  from the loudest peak. Measured on recordings of six pianos (Bösendorfer
  225 and 290, Steinway B and D, Kawai, Samick): 49 of 49 keys of a walk
  across the compass, 169 of 173 on the largest corpus.
- **Reads the way a tuner listens.** For every note the app picks the partial
  a tuner would listen to in that register, synthesises its target, and shows
  the string against it as a strobe, to a tenth of a cent, 47 readings a
  second. A string that wobbles is shown wobbling; nothing is smoothed into a
  verdict.
- **Shows every partial.** Beside the strobe stand the bells: each partial of
  the string read on its own against the string's own model. A clean string
  reads as one stack; a false beat is seen for what it is.
- **Knows when the note is in.** When the strobe stands still inside the
  tolerance you set, the app records the note and the keyboard shows the
  tuning as it stands. No Done to tap.
- **Keeps the session.** One sealed file on the phone, under a key that never
  leaves it. Continue tomorrow where you stopped.
- **Carries your choices as settings.** Octave type and width per register,
  the compass, the wound/plain break, the temperament's reach, the match
  window. The measurement decides the rest.

**Out of scope by design:** pitch raises — a piano far from pitch needs one
first. Unisons are tuned by ear.

## How it measures

The chain every proven tuning device uses, written down and testable:

1. **The key** is identified by fitting a string — fundamental and
   inharmonicity — to the peaks the microphone hears (`tuning/KeyIdentifier`).
2. **The partial** is chosen by register (`measure/PartialMap`).
3. **The target** is synthesised from the reference and the instrument's
   measured inharmonicity (`tuning/InharmonicityCurve`, `tuning/TuningSession`):
   octaves of the chosen type chained outward from the temperament, each
   partial where this piano's strings put it.
4. **The reading** is the phase of that partial against the target
   (`measure/PhaseReader`), shown only while the partial stands over the noise
   beside it. The clean partials are read together, a partial that beats on
   its own is named and left out (`measure/CleanReading`, `measure/BeatMeasure`).
5. **The string** is measured from the strike — every partial, and the fitted
   inharmonicity — and kept (`measure/StringMeasure`).

Raw 48 kHz PCM, the microphone unprocessed; no third-party DSP; the same code
on every platform. The engine is judged against recordings: the core tests
run on synthetic strings, the corpus tests on real ones, and the findings of
every piano day are written down with the recording they came from. The
engine (`shared/`) and the screens (`ui/`) are a Kotlin Multiplatform library
with an iOS stub; only the Android app exists today.

## Clavier Tuner Pro

The free app and Clavier Tuner Pro are one tuner: the same engine, the same
screens, the same measurement. Pro adds the book a working technician keeps —
pianos and customers, a tuning saved to a piano and its history, the unison
display that reads all strings of a note partial by partial, the recorder,
letters and the business side. Nothing of the tuning is held back from the
free app.

## Install

- **F-Droid**: *Clavier Tuner* (`at.clavierhaus.claviertuner`) — once the
  first release is through F-Droid's review.
- **GitHub releases**: the signed APK of every version, the same bytes
  F-Droid builds and verifies.

Android 8.0 (API 26) or later. The app needs the microphone and nothing else.

## Build

Android SDK (platform 37, build-tools 36.0.0) and a JDK 17 or later;
`local.properties` pointing at the SDK.

```
./gradlew :shared:testAndroidHostTest     # the core tests, JVM only
./gradlew :androidApp:assembleDebug       # the app, with the commit in its version
./gradlew :androidApp:assembleRelease     # the release build, reproducible
./scripts/deploy.sh                       # pull, test, build, install on the connected phone
```

The release is reproducible and F-Droid verifies it: `scripts/release-fdroid.sh`
builds a tagged commit twice in independent worktrees, refuses to publish
unless the two unsigned APKs are identical, signs the APK and publishes it,
and hands the recipe to F-Droid, which rebuilds it in its own environment and
compares the bytes. `scripts/fdroid-env-check.sh` says whether a machine meets
F-Droid's requirements for that.

## Contributing

- **Issues** with a recording attached are worth ten without. The app records
  nothing by itself; a phone recorder at 48 kHz with every effect off, or the
  Pro recorder, gives the engine what it needs.
- **Languages.** The app's words live in `i18n/strings.tsv`
  (`scripts/i18n.py --check` refuses holes). English and German today; the
  app follows the phone's language.
- **Words.** The app speaks the technician's words; `docs/GLOSSARY.md`
  decides each one once, in English and German, with its source.
- **Measurement.** A change to what is measured or how is a change to the
  documents it is judged by, in the same commit, with the recording that
  called for it.

## Licence

Apache License 2.0 — see `LICENSE`. The typeface is DejaVu Serif (Bitstream
Vera licence); the settings icon is from Material Design icons (Apache 2.0).
