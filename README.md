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

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="100%" alt="Tuning G3 on its sixth partial: the strobe">
</p>
<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="49%" alt="Sampling the piano: a single string measured">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="49%" alt="The stretch computed from this piano">
</p>
<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="49%" alt="The hub">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="49%" alt="Settings">
</p>

## What it does

- **Sets A4 on the string you choose.** The first page sets the pitch: tap
  − or +, or slide a finger along the strip under them. Strike each string of
  A4 alone and its partials stand as strobes over their buttons: a clean
  string's strobes move together and stand still together, a false partial
  beats on its own. Take the cleanest string as the reference; a tap on a
  partial's button takes it out of the strobe and another brings it back.
- **Measures first.** Strike single strings, mutes in, any keys up to G6 in
  any order: eight are the minimum, 10 to 15 spread up to G6 give the best result.
  From those strings the app measures the inharmonicity of the piano and
  computes where every note belongs before you tune the first one. The
  stretch comes from this instrument, not from a table. Each key says
  "measuring …" until more sound no longer changes the measurement, then
  "measured"; Accept turns grey once a key is kept.
- **Follows the key.** Strike a note and the screen is on it — the key is
  identified by fitting a string to what the microphone hears, not guessed
  from the loudest peak. Measured on recordings of six pianos (Bösendorfer
  225 and 290, Steinway B and D, Kawai, Samick): 49 of 49 keys of a walk
  across the range, 169 of 173 on the largest set of recordings.
- **Tunes by the strobe.** For every note the app picks the partial a tuner
  would listen to in that register, synthesises its target, and shows the
  string against it as a strobe, to a tenth of a cent, 47 readings a second.
  The string's other partials stand beside it, each in its own band: a clean
  string is one moving pattern that comes to a standstill, a false partial
  is the one out of step. A partial you take out stays out for the note.
- **Shows the tuning at a glance.** A line at the top says what this tuning
  is tuned to — A4, the temperament octave, the octave type of bass, middle
  and treble. A tap on it opens exactly those settings.
- **Knows when the note is in.** When the strobe stands still inside the
  tolerance you set, the app records the note and the keyboard shows the
  tuning as it stands. No Done to tap.
- **Keeps the session.** One sealed file on the phone, under a key that never
  leaves it. Continue tomorrow where you stopped.
- **Carries your choices as settings.** Octave type and width per register,
  each register offering the types its strings sound — bass 4:2, 6:3, 10:5;
  middle 2:1, 4:2; treble 2:1, 4:2, the double octave 4:1 and the twelfth 3:1; set
  out as bass 6:3, middle 4:2, treble 4:1. The bass's type is beatless at the
  lowest key, the treble's at C8, the middle's next to the temperament octave;
  every octave between moves from one to the other key by key, so the tuning
  curve has no break anywhere. Also the temperament's reach and the match window. The range is the 88 keys, A0 to
  C8. The measurement decides the rest.
- **Out of the way when you want it.** The phone's Back and Home buttons show
  everywhere but on the tuning screen, which has the whole screen; Settings ›
  App › Display switches them off for a full screen everywhere.

**Out of scope by design:** pitch raises — a piano far from pitch needs one
first. Unisons are tuned by ear.

## New in 0.7.2

- **What the strobe shows is chosen beside it** on the tuning screen, three
  lines, one active: *All partials*, *Limit to* 4 (more or fewer with − and
  +), or *One strobe, averaged*. This replaces *Number of strobes* and *Octave
  partial* in Settings, and the note on screen takes the choice at once.

## New in 0.7.1

- **Every strobe stands where the note as played puts it,** from its first
  reading: a strobe other than the one being tuned stands at the tuned
  strobe's target times the ratio between the two partials as read. No strobe
  is placed by a model and then moved. *Strobe smoothing* (Settings › Tuning ›
  Reading) sets how far back that ratio is averaged.
- **The piano's size recommends the bass's octave type:** the window behind
  the tuning line opens after sampling, with a switch for a grand of 210 cm
  (6′11″) or longer — on, bass 10:5; off, 6:3. Every type stays yours.

## New in 0.7

- **A new tuning curve, with no borders.** The bass's octave type is beatless
  at the lowest key, the treble's at C8, the midrange's next to the
  temperament octave; every octave between moves key by key from one to the
  other, and the extra stretch with it. Until 0.6 each region's type held
  whole up to its edge, which could leave a few keys of the low tenor several
  cents flat of their neighbours.
- **The temperament octave is stretched too:** the octave below A4 is a
  beatless octave of the midrange's type, divided equally. It was 2:1 by the
  fundamentals, which is narrower on a piano with much inharmonicity.
- **One smooth inharmonicity line** through the measured strings, with no cut
  at the lowest plain string. On the recordings we have, strings not measured
  are predicted about twice as closely as before.
- The targets have changed: a tuning saved with 0.6 is best begun again.
- **The key struck is caught sooner,** about 0.17 s after the strike instead of
  0.26 s, and **the strobe's row stands at once:** every band the note offers
  is there from the start and fades in over 0.3 s as its partial is read.
  Settings › Strobe › *Number of strobes* shows all of them or the lowest few,
  the one being tuned always among them; *Strobes appear together* holds the
  row until every band is read.
- **Each register offers only the octave types its strings sound:** bass 4:2,
  6:3, 10:5; middle 2:1, 4:2; treble 2:1, 4:2, 4:1, 3:1.
- **Sampling needs eight strings besides A4,** six until now: enough on a
  concert grand, not on a spinet.

## New in 0.6

- **One band, if you want it.** Settings › Strobe › Bands: *All partials*, as
  before and still the default, or *Octave partial*: one band in every
  register, the partial on which the note meets its octave by the octave type
  you set (4:2 on the upper note: partial 2; 6:3: partial 3; 2:1, 4:1, 3:1:
  partial 1; in A3–A4, partial 2). The note is read and kept on that band
  alone. A tap on a partial below still adds it beside the band.

## New in 0.5

- **Progress counted the sampling as tuning.** Keys only sampled stood green
  and in the line "as tuned". Now only what you tuned counts.
- **Settings in piano words**, in English and German.
- **"Too short" in the top octave while sampling.** Sampling now stops at G6;
  above it a string cannot be measured reliably. Tuning up there is as before.
- **"Partial 1" beside the note** is gone.
- **Which microphone?** Settings › App › About says whether the phone gives
  the app its unprocessed microphone, or processes the sound itself.

## New in 0.4

What testers found in 0.3, and what changed:

- **Words broken or cut on smaller phones.** On a Motorola edge 50 neo the
  word beside the caught key stood letter by letter, and the last line of
  the sampling instructions was missing. Every screen is now drawn and
  checked on five phone sizes, in English and German and at the largest
  system text size: a button's word never breaks, and no text is cut.
- **Sampling instructions that named keys.** They now say only what to do:
  single strings, any keys, any order; eight at least, 10 to 15 for the best
  result; let each ring until it says measured, then Accept.
- **A strobe on the sampling page nobody needed.** A sample is measured, not
  tuned; the sampling page has no strobe any more. A4 keeps its strobe, now
  with each partial's number under its name.
- **The bells are gone for good,** and with them the "anchor" line beside
  the partial.

## New in 0.3

What testers found irritating in 0.1 and 0.2, and what changed:

- **The treble stood sharp.** Against another tuning system the treble came
  out just under 4 cents high, C7 about 11. The treble's octave was set out
  as 4:2; it is now the double octave 4:1, which met a concert grand as its
  tuner left it within half a cent. 4:2 and the new 3:1 remain choices.
- **No way out of the app.** Back, Home and the app switcher were hidden on
  every screen. They now show outside the tuning screen; Back on the start
  page closes the app.
- **"Done" while sampling was unclear.** Each key now says whether it is
  still being measured, and Accept greys once it has taken.
- **The bells.** The bells beside the strobe moved where the strobe of the
  same partials stood still. They are gone; the strobe shows every partial.
- **Small things.** The screen stays on while tuning; the strobe comes up
  with the strike; the word "listening" is gone.

## How it measures

The chain every proven tuning device uses, written down and testable:

1. **The key** is identified by fitting a string — fundamental and
   inharmonicity — to the peaks the microphone hears (`tuning/KeyIdentifier`).
2. **The partial** is chosen by register (`measure/PartialMap`).
3. **The target** is synthesised from the reference and the instrument's
   measured inharmonicity (`tuning/InharmonicityCurve`, `tuning/TuningSession`):
   octaves chained outward from the temperament, each blended by key from the
   middle's octave type to the outer one, each partial where this piano's
   strings put it; the inharmonicity is one smooth line through the strings
   measured.
4. **The reading** is the phase of that partial against the target
   (`measure/PhaseReader`), shown only while the partial stands over the noise
   beside it. The clean partials are read together, a partial that beats on
   its own is named and left out (`measure/CleanReading`, `measure/BeatMeasure`).
5. **The string** is measured from the strike — every partial, and the fitted
   inharmonicity — and kept (`measure/StringMeasure`).

Raw 48 kHz PCM, the microphone unprocessed; no third-party DSP; the same code
on every platform. The engine is judged against recordings: the core tests
run on synthetic strings, the recording tests on recordings of real pianos. The
engine (`shared/`) and the screens (`ui/`) are a Kotlin Multiplatform library
with an iOS stub; only the Android app exists today.

## Install

- **F-Droid**: *Clavier Tuner* (`at.clavierhaus.claviertuner`). A new
  version reaches F-Droid some days after its GitHub release, once F-Droid
  has rebuilt it.
- **GitHub releases**: the signed APK of every version, the same bytes
  F-Droid builds and verifies.

The build is reproducible: F-Droid rebuilds every release from the tagged
commit in its own environment and publishes it only when the bytes match.

Android 8.0 (API 26) or later. The app needs the microphone and nothing else.

## Contributing

- **Issues** with a recording attached are worth ten without. The engine
  hears nothing but the microphone, and the app records nothing — so a
  recording made beside it (a recorder app, 48 kHz, uncompressed, every
  effect off) is the only way anyone else can hear what it heard.
- **Languages.** Every word the app shows lives in `i18n/strings.tsv`, one
  column per language; `scripts/i18n.py --check` refuses holes.
- **Words.** The app speaks the technician's words; `docs/GLOSSARY.md`
  decides each one once, with its source.
- **Measurement.** A change to what is measured or how is a change to the
  documents it is judged by, in the same commit, with the recording that
  called for it.

## Licence

Apache License 2.0 — see `LICENSE`. The typeface is DejaVu Serif (Bitstream
Vera licence); the settings icon is from Material Design icons (Apache 2.0).
