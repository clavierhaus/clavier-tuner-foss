# Changelog — Clavier Tuner (foss)

What changed for the tuner and for the engine, newest first. Dates are the
day the change was made at the instrument or on the recordings; commits
carry the detail. Kept from 27 September 2026 on; the earlier entries were
reconstructed from the commit history that day.

## Unreleased

### 2026-10-04
- **The stretch is one tap from the tuning screen.** A tap on the "partial N"
  line of the picture opens Settings at the stretch page of the region the
  note is in — wound, bass, middle or treble — the octave type the tuner is
  living with never three taps deep (the F3 affair's lesson).
- **Settings without scroll.** Four tabs by what they are for — Tuning,
  Stretch, Piano, App — each with named pages on a second strip (◀ ▶, or a
  swipe) where it has more than one; a page is two columns of rows that say
  their name and a control, with one line at the foot saying what the page
  means. Nothing scrolls, nothing is cut. The stretch has a page per region;
  the probe and the recording button live under App; About and Licence are
  one page. The Temperament tab that held everything is gone.

### 2026-10-03
- **The anchor is a trusted reading.** A partial standing less than 25 dB over
  the noise beside it does not anchor the bells, however narrow its last half
  second: at the floor a reading wanders slowly too, and Peter's recording of
  17:45 showed partial 4, at the floor, anchoring every needle 1.5 c off the
  line. The line moves to the strongest partial still well over the floor
  ("bells on 1"), and the needles stand on it.
- **Every bell has a width: how well its partial is read.** The spread of the
  partial's last half second of readings, measured live, cleared at a strike,
  gone with the sound. A loud partial is a needle; a partial at the room's
  floor is a wide, low bell whose wandering says "I am quiet", not "the string
  moved". A bell much wider than the narrowest neither anchors the line nor
  vetoes "on" — a needle outranks a wide bell. Measured on the Samick's F3:
  the loudest partial 0.01–0.17 c wide, the loud ones under half a cent, the
  floor-huggers 0.5–1.4 c.
- **A4 leaves the settings.** It is the piano's, set at the hub from a single
  string when the session starts; the stepper under Temperament showed the same
  value in a second place and confused. The note picker reads as the keyboard
  does: bass at the top left, top treble at the bottom right.
- **The orange settings tab is the open one.** The Temperament tab carried a
  fixed accent and stayed orange whichever tab was open; now orange marks the
  active tab and nothing else.
- **The note name is the picture's one control; the arrows are gone.** The
  screen follows the key struck by default. A tap on the note name opens the
  compass as a keyboard — one row per octave, bass at the bottom, nothing
  scrolls — and the key chosen there holds, with a thin line under the name
  saying so; a tap on the name then hands the choice back to the strike. The
  ◀ F3 ▶ stepper in the bottom row is removed.
- **The clean partials are read against the strike's own series.** Once the
  strike has spoken (1.5 s in) every partial's target comes from the string now
  sounding; the clean-partials reading kept the model's placing for the whole
  strike, so when the partial being tuned died away and another stood in, the
  reading carried the model's error at that partial — 8 c on F3-C-s1 through
  the probe as partial 1 stood in for partial 4. The readings now follow the
  series without the readers restarting: a target that moves within the band is
  a change of reference, and what was read is said against the new one.
- **A partial decaying onto the room's floor is no longer called false.** The
  false-beat verdict is the spread of a partial's readings over two seconds;
  as a partial fades to within 15 dB of the noise beside it, the floor's phase
  scatters the reading by a cent and more, and on the Samick's clean F3 the
  engine called 22 partials false over nine single-string takes, every one of
  them past 4.6 s into the strike — the red-violet bells and chips Peter saw on
  a string with no false beat. Now a reading counts towards the spread only
  while the partial stands 25 dB over the noise beside it (measured: the
  largest clean spread is 0.22–0.30 c there, 0.27–0.52 c at 20 dB); a partial
  read only nearer the floor is shown, but neither counted in the combined
  reading nor accused, and a verdict expires with the readings that made it.
  A real false beat on a sounding partial (C5's fundamental, 2 Oct, 2 c of
  spread at 33 dB over the noise) is named as before.
- **A reading from before the strike is never shown after it.** The reader
  resets its memory at a strike and skips the settle; the hop in which the skip
  ends could still hold no fresh rate, and in that hop the reading held from
  before the strike — the room's — passed as a reading of the string, for one
  hop. On a synthetic F3 in a room floor that one hop spread partial 8's window
  to 0.97 c and had a clean string called false. Until the memory holds a rate
  read after the strike, the reader says it is settling.
- **The tuning screen is a split window.** Left, the tuning: the note, the
  partial, the linear scale at half its former width with its gate. Right,
  nothing but the bells on their centre line, ±6 c so a bell's movement is seen
  undistorted, the partial numbers in a row above. The frequency table is gone
  (its place is the unisons, when they come). The partials are one row of chips
  that always fits, each an equal share of the width; a partial measured false
  is red-violet, and a tap on it takes it out of the bells. No cap on how many
  partials may be shown.
- **Every partial of a sounding string is read, on every note.** Until now the
  partials beyond the listened one were read only in the Full Spectrum mode,
  and at most six of them; with the modes gone the one picture of a sounding
  string stood empty (3 Oct, 06:54, the first picture seen). The engine now
  reads every partial the strike shows, up to the highest the setting offers,
  each with its level from the finder, and a reader is remade only when its
  target leaves the band it reads in, so the bells do not swing when the
  strike's own series arrives. One state word: the header's, and it is the
  picture's — *near* inside the gate, *matches* only when held.

### 2026-10-02 (evening)
- **The tuning screen is one picture.** Bells and scale together, in place of
  the Tuning and Partials modes. The bells stand on one steady anchor line —
  each at its distance from the anchor partial's own reading, so a clean string
  stacks at zero wherever the pitch is and only a false or off-series partial
  leaves the line; the partial numbers in a row above so labels never overprint;
  the readout table beside; the linear ±30 c scale with its gate across the
  bottom. *Near* inside the gate, *on* after half a second held, the scale and
  the note washing green. A partial the engine measures false is red-violet.
  The picture is computed in `shared/picture` and tested; the view only draws it
  (`docs/UI.md`). The bottom row keeps the arrows, the partials to tap, Progress
  and Done; the Partials / One partial button is gone with the modes. Not yet in
  the picture: the beat band — its place is decided in the next round.
- `Pane` and `FitText`: the first two primitives of the screens — placement by
  fractions of the pane, and text that steps its size down instead of being
  cut. `LawWallsTest` counts every scroll and cut in the source and only ever
  lets the count fall.

### 2026-10-01 (evening)
- The tuning scale is **linear, ±30 cents**, a tick every five and a label
  every ten. No magnification anywhere on it: one cent is the same width
  wherever it falls, which is the only way the screen can answer "how far is
  that" the same way twice. Two magnified versions were tried at the piano in
  one hour and both were unusable — the first gave a third of the screen to the
  two tenths of a cent of the match window, so the pointer slammed end to end on
  a string that stood in tune; the second widened that third to ±2 c but kept
  log segments reaching ±100 c, a semitone on a tuning scale. The match window
  stays where the measurement puts it and is drawn at the width it really has.
- **The match window is drawn as a gate on the scale**: a post at each edge, feet
  pointing inward, the fill between them and the figure beside the right post,
  all of it turning green with the pointer. A band that only changes colour says
  the same thing at a tenth of a cent as at one and four tenths; the gate says
  where inside the window the string stands.
- The match window is **±1.5 cents**, a tenth of the scale either side of the
  target, so the green band is an area to aim at instead of the hairline ±0.2 c
  drew on a thirty-cent scale. The floor is still the measurement's — a string's
  partials repeat within 0.04–0.11 c, so a window under a tenth of a cent promises
  what the reading cannot repeat — but above that floor the window is the tuner's
  tolerance, and Peter set it at the piano. Settings steps it 0.2–5.0 c in tenths
  and the band on the scale always draws at its true width.
- Leaving **Partials** is a button called *Tuning*, as leaving Progress already
  was. It read *One partial* — the name of a mode, not of a destination — so the
  way back did not look like a way back. Both buttons now say where they go.
- **The tuning screen works in cents, not Hertz.** The scale under the band was
  a Hertz axis with its magnified middle third set by a match window in Hertz, so
  a tenth of a Hertz — one cent at F3 — was a twelfth of a cent at C7, and the
  same picture meant forty different musical distances across the compass. At the
  top of the piano it magnified a twelfth of a cent into a visible swing: a
  precision no measurement here has. The axis is now ±0.2 c in the middle third,
  ±2 c in the next, ±10 c to the edge, one meaning everywhere on the keyboard.
- **The match window is in cents and is set by what the measurement resolves.**
  `matchHz` becomes `matchCents`, default **0.2 c** — about twice the 0.04–0.11 c
  within which one string's partials repeat from strike to strike (30 Sept, F3,
  three strikes on each of three strings). Green now means the string is there,
  not that the reading twitched. Settings → Precision says so.

### 2026-10-01 (evening)
- **Reverted within the hour: taking the reference from `measurements[A4]`.** It
  replaced a reference that was knowably wrong (the 440 default) with one nobody
  can vouch for: on the Samick session it found 439.6 Hz, a figure Peter had
  never seen and 0.7 cents from the 439.418 the corpus measures across nine
  single-string takes. A reference from an unidentified strike is not a
  measurement — it only looks like one, which is worse. **Where the reference
  comes from is still open**; it is chosen deliberately or it is not a reference.
- **The tuning screen always shows the A4 it is working from.** Every figure on
  that screen is derived from it — the target, the cents, the octave and its
  width — and it was the one number never shown. Four rounds at the piano on
  1 October went into deducing it from where the bells landed.

### 2026-10-01 (evening)
- `scripts/deploy.sh` is silent when the build works and complete when it does
  not. It ran gradle with `-q`, which says almost nothing about a compile
  failure, and `set -e` then stopped the run where it stood, so nothing could be
  analysed from what reached the screen. A failure now prints the lines that
  name the fault, counts the compiler errors, and keeps the whole output in a
  file to be read or pasted whole. Success still says nothing.

### 2026-10-01
- **The tuning screen says when the A4 reference is assumed.** 440 Hz is where
  the reference starts, not where a piano stands: a session that never measured
  the A4 string tuned to that assumption silently, and on an instrument at
  439.4 Hz that puts every target 2.3 cents sharp of the piano — more than a
  tuner works to. The origin line now reads "A4 440.0 Hz assumed, not measured",
  in orange, in front of the numbers it governs.
- **Progress draws the curve this piano asks for**, in blue, unbroken across the
  whole compass: every key's target computed from the inharmonicity of the
  strings that were sampled. It was being computed for every note and shown for
  none — the screen drew only how far the measured notes stand from equal
  temperament, a result and not a target, with a gap wherever two measured notes
  were more than three semitones apart. Both lines now share one scale, so the
  tuning can be seen lying on the curve or departing from it.
- The operating manual and the working rules move to clavier-tuner-pro, where
  they are kept for both repositories: they name Pro's features and this
  repository is published, while the engine they govern lives here and is
  mirrored into Pro. A session working on the engine fetches `CLAUDE.md` and
  `docs/RULES.md` from there before touching it.
- `CLAUDE.md`: the operating manual, binding on both repositories. Its Core
  Directive — **the only truth is a verifiable, reproducible measurement from an
  uncompressed, unaltered source of sound** — is the law the rest follow from:
  no lossy audio or processing anywhere in the chain, a measurement is
  reproducible or it is not one, the corpus is evidence and is never edited, and
  where the ear and a measurement disagree the measurement is the suspect. With
  it: the verification order, the questions to answer before changing anything,
  build and test discipline, and a standing orders ledger of the ten failures of
  30 September and 1 October that produced these rules.
- **The partials shown on the tuning screen are measured from the strike you are
  hearing** (RULES.md 2). Every strike, the string's own partials are read from
  the ring and become the targets, anchored on the partial being tuned; they are
  discarded with the strike. The stored per-note partial table leaves the target
  path entirely — f1 and B stay, since the curve is built from them. Until today
  a table measured during sampling, hours earlier and possibly on another
  string, furnished the targets of the string now sounding, and a clean string's
  bells would not stand on one line. Measured on the 30 Sept corpus: one
  string's ratios repeat from strike to strike within 0.04–0.11 cents over all
  twelve partials, so deriving them afresh costs nothing in steadiness.
- `docs/RULES.md` gains the two standing laws decided at the instrument:
  **measurement has the primary role and modelling never overrides it** — no
  guard, threshold, cap or fallback decides in advance what the piano may
  produce, and a partial the strike produces is reported with its level, not
  dropped — and **foss is the measurement foundation**: the engine is
  byte-identical in both repositories, every engine change starts here and is
  propagated into Pro, never the reverse.

### 2026-09-30
- The probe gains a **play button on the tuning screen**, beside the recording
  button: it opens the file dialog and plays straight into the engine, so the
  scope, the partials and the Done button stay where they are while a recording
  runs. Switched on under Settings → Probe, off by default.
- **The probe** (Settings → Probe, docs/PROBE.md): a recording played in place
  of the microphone. `FeedableAudioSource` *is* an `AudioSource`, so the tap,
  the readers, the identifier and the screen cannot tell where the buffers came
  from — what the probe shows is what the piano would show. Real time, no
  resampling, the file repeating with a second of silence between passes so
  each begins with a clean onset. Any WAV the phone can reach, 48 kHz, 16-bit
  PCM or 32-bit float. For chasing at the desk what was seen at the instrument.
- `docs/RULES.md`: the engine's invariants, written down because the one that
  was broken today existed only in conversation — a bell is not pitch
  dependent, the partials are a live measurement and are not stored, a reading
  does not outlive its sound, the gate is a ratio and not a knob, physics is
  measured and not asserted. Each rule carries what it cost when it was broken.
- Reverted within the hour: a guard that placed a measured partial by the
  string's fitted inharmonicity when the stored reading disagreed with the fit.
  It rested on a claim that is false — that partial 2 cannot sit below twice
  partial 1 — and the Samick's own clean F3 does exactly that, by 0.3 c,
  because a real string's fundamental runs about a cent sharp of the
  stiff-string model (measured: every other partial within 0.13 c, the
  fundamental +0.99). The guard then substituted a fitted series for good
  measurements and broke the one thing the partial display is for: on a clean
  string the bells must stand one above the other, and they do so only when
  each partial is read against that same string's own partials. Targets from
  the string's partials: spread 0.000 c. From the fit: 1.13 c.
- **Every partial on the tuning screen is placed from the partial being tuned.**
  Its target comes from the interval it must make; the note's place on the curve
  and a measured string's own table are different numbers, and the screen was
  taking the fundamental from one and the rest from another. The row being tuned
  could therefore disagree with the bell directly above it — on the Samick's F3,
  697.0 against 697.77 — and the partials of one clean string scattered over
  2.5 cents. Anchored, the same string's partials stand together within 0.24 c.
  The partials' cents were also measured against the bare harmonic k·f1, which
  no piano string sits on (F3 stands 11.7 c above it at partial 8), so every
  upper partial of a clean string read sharp.
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
