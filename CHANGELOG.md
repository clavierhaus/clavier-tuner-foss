# Changelog — Clavier Tuner (foss)

What changed for the tuner and for the engine, newest first. Dates are the
day the change was made at the instrument or on the recordings; commits
carry the detail. Kept from 27 September 2026 on; the earlier entries were
reconstructed from the commit history that day.

## Unreleased

### 2026-09-30
- The partials table gains a **cents** column to a hundredth: 0.1 Hz is
  0.39 c at the fundamental and 0.08 c at C#7, so the Hz column alone had
  five times the resolution at the bottom of the table as at the top.
- A partial whose reading has expired loses the verdict on it too: a "false"
  call can no longer outlive the reading behind it. The hold is derived from
  seconds and the hop, not written as a count.
- A reading stands for half a second after its partial was last read, then
  goes: numbers no longer sit on the screen looking live seconds after the
  sound has died. Found at the Samick, where it made the screen unusable as
  a measuring instrument.
- A partial called false stays false until its spread has clearly fallen
  (0.5 c out, 0.3 c back in): the list under the bell no longer flickers at
  the line. It reads "partials 1 3 5 · not 4".
- Reading memory as a setting (Settings → Precision: 0.15 / 0.3 / 0.6 / 1.2 s):
  how far back the bell looks; every reader takes it. The tuner's control
  over the averaging — a noisy room, a string that wanders.
- Octave width per region has a second value at the region's far end (the
  lowest key; C8 for the treble), interpolated by key from the near end's:
  a bass tuned wider the lower it goes is one setting, not a step. Unset,
  nothing changes. Settings → Stretch, "… at the lowest key".
- Recordings: the studies Stage 1 (one string per key) and Stage 2 (the
  other strings of the plain trichords) for a piano investigated in stages.

### 2026-09-28
- The reading on the string's clean partials (`measure/CleanReading`): a
  measured string is read on partials 1–8 together — present, steady, in
  agreement with the partial being tuned — level-weighted; a partial that
  beats on its own is left out and named under the bell ("partials 2 4 5 ·
  3 beats"). An unmeasured string is read on its partial alone, or, when
  that one beats, on the steadiest other. Settings → Precision switch, on by
  default; a partial tapped under Partials is read alone. A measured string's
  partial targets come from its own table, not the B model.
- `docs/GLOSSARY.md`: the words the app uses, decided once — what was wrong
  and what replaces it, the trade's words kept, our own coinages named as
  such, screen by screen what changes; sources Reblitz, TuneLab 3.2,
  PianoMeter, PTG usage. The English pass over the tables follows it.
- Languages as infrastructure: catalogues (FOSS's table and Pro's, looked up
  the same way, each carrying a language whole or not at all), the regional
  chain de-CH → de → en, `Strings.missing()` for "not available yet",
  gettext .po export and import in `scripts/i18n.py`, a version per table.
- Record Strikes: the piano *Samick 172*; the study *Every string* — left,
  centre and right of every key alone, then the key as it stands, only the
  strings the key has (the two- and three-string floors); the study *Drift*
  — the A's and C's as they stand, sixteen keys.

### 2026-09-27
- The microphone is a pump: `audio/AudioTap` opens the source and fans every
  buffer out to its taps. The pre-September analysis chain (`PartialMonitor`,
  `PartialAnalyzer`, Yin, PreciseF0, Goertzel, BeatRate, Scope, PartialLevel,
  LiveIdentification, UnisonState) and its 54 tests are deleted; nothing the
  app reads came from it. Core tests 132 → 81.
- `UnisonSync.State.f1Hz`: the note's fundamental as placed on the strike that
  named it.

### 2026-09-25
- The key struck is identified by fitting a string (fundamental and B) to
  the peaks heard, from every key of the compass as a start
  (`KeyIdentifier` replaces `NoteDetector`). Below C7: 225 169/173, Steinway
  D 38/40, Bösendorfer 290 34/34, Steinway B 73/73, Kawai upright 54/55.
- Following the key: from 8 hops after the strike, not once the level has
  fallen 30 dB, and after half a second only a lower key may replace the one
  named. What sounded before the strike is background.
- Sampling is the tuner's choice: any key struck is caught and shown green,
  Accept keeps it, a key struck again replaces its sample, Done opens at A4
  plus the app's set. No prescribed notes, no steady gate, no arrows.

### 2026-09-22
- The tuning engine: the note is an input, the partial is chosen by register,
  the target is synthesised, the reading is the phase of that partial against
  the target (`PhaseReader`: ±25 c band, 150 ms memory, restart on a strike,
  presence gate). The finder only says where.
- A new tuning runs hub → sampling → tuning. `InharmonicityCurve` (ln B
  linear per segment, cut at the wound/plain floor) gives every note a
  target before any note is tuned; a tuned partner is the check beside the
  target, never the source.
- Full Spectrum: every shown partial read by its own phase reader on a cents
  axis against the string's model.
- Unison sync in core: partials 1–8 of a unison each read by phase, steady
  or beating, best strike, string-alone check.
- Follow the key on the new engine; the sampling screen; languages: every
  word the app shows in `i18n/strings.tsv` (English, German).
- Licence file: Apache License 2.0.

### 2026-09-21
- Bass tuning by the link partial below the temperament octave, down to the
  instrument's lowest key; recording button on the tuning screen (Settings).

### 2026-09-18 – 20
- Temperament first: FOSS finishes A3–A4 before any note outside it; the
  session then walks down the plain wire and up to C8.
- The tuning screen of docs/SCREEN.md: one note, the beat as motion.

### 2026-09-16
- Measured display: every reading shown as measured, 47 a second; the bell
  follows within 20 ms. No median.
- Continue last tuning: a sealed session file (AES-256-GCM under an Android
  Keystore key), versioned format.
- The hub on the clavierhaus stencil: concert grand photograph, Continue /
  New Tuning.
- Record Strikes: the wobble study protocol (single strings, two pianos).

### 2026-09-15
- Tuning session from A4 down, single strings; partial targets from the
  string itself; the readout view (blue target bell, orange live bells,
  green within 0.1 Hz); settings model with persistence.

### 2026-09-14
- Repository created: the open foundation.
