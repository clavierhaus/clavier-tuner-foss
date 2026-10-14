# The screens: how the UI is built

For whoever opens `ui/` or `androidApp/` for the first time. The engine is
described in the Pro repository's `docs/ENGINE.md` and in the source under
`shared/`; this file is about everything above the engine: what a screen is
allowed to be, which layer decides what, and how a change is checked without
a piano.

## The laws of the screen

Four rules bind every screen. They are checked, not remembered.

1. **Relative, never absolute.** A control sits at a fraction of its pane. No
   screen is drawn for one device and scaled.
2. **Nothing scrolls.** A page that has more than fits splits into a second
   page with a name of its own. *Except Help* (Peter, 12 Oct 2026: "For all
   stuff under Help, that rule does not apply" — its documents will be longer
   than a page): the Help tab's text wraps and scrolls, drawn by `HelpSteps`
   in `ui/HelpParts.kt`, the one file `LawWallsTest` exempts by name.
3. **No text is ever cut.** No clipping, no ellipsis. A page's content is
   budgeted for the smallest supported pane at the smallest legible size;
   type that steps down is a safety net that must never fire on a supported
   pane.
4. **The screen shows what the string is doing now.** Every number on it is
   read from the strike that is sounding (CLAUDE.md, Rule 3). Nothing is
   remembered from an earlier strike, nothing is computed a second time beside
   the engine's own reading, and nothing freezes.

Rules 2 and 3 are enforced by `LawWallsTest` (`shared/src/androidHostTest`),
which reads the source of `ui/` and `androidApp/` and counts scroll modifiers,
ellipses and one-line caps. Each file's count is held in the test; it may only
go down, and lowering it is part of the commit that mends the file. The test
runs on the JVM and needs no device. Landscape only: the app does not try to
be both orientations.

## Where the screens live (4 Oct 2026)

One tuner, two editions: the screens — `TunerHub`, `BasicHub`,
`TunerSettingsScreen`, `RecordStrikesScreen`, `SessionRecorder`,
`PrefsStore` — live in the `ui` library (package
`at.clavierhaus.unisonmaster.app`), and both apps' `MainActivity` mount them.
Nothing is copied by hand any more. Pro adds the book beside them: its own
tabs and pages go into the settings screen through `extraPianoPages` and
`extraTabs`, its own door — *Pianos* — into the hub through `TunerHub(extra)`,
after *New Tuning*; its own screens hang off that door. The hub's words are
written once, in `TunerHub`; an app only says what happens behind each door.
A tuning feature is built here, once.

The unison screen is Pro's (Peter, 4 Oct 2026, 15:26: "a USP for Pro
only") and lives in Pro, not here. The free app has no *The Unisons* door.

The recorder is Pro's (blueprint §18.5, 4 Oct 2026): the red button on the
tuning and unison screens, the play button, the *Recordings* door and the
`RecordStrikesScreen` behind it — the reference recording belongs to the
piano's record, which only Pro keeps. The screen and `SessionRecorder` stay
in this library (built once) and the free app does not mount them:
`recorder = null`, `onPickRecording = null`, no `onRecordings`. The probe
(App · Probe) is not playback: it feeds the engine a recording where the
microphone's buffers arrive (`PROBE.md`) and stays in both editions.

## Piano Sampling (4 Oct 2026)

The start of a tuning — the hub's A4 and the sampling — is one page,
`SamplingPage` (Peter's word): left, *Piano Sampling* / *Retrieve the
piano's inharmonicity* and the orders in a box; right, A4 (live, then set,
green), the temperament octave's low note (C3…A3, a setting no longer
pinned), the key caught from the last strike with Accept, the count with
Done; along the bottom `SamplingKeyboard`, the whole compass. A tap on a
sampled key offers Strike again or Remove (`TuningController.forgetSample`);
on A4, setting it again starts the sampling over. No spectrum, no partials.

## The corners of every screen (4 Oct 2026)

Blueprint §18–§19, Peter's word. `ui/Corners.kt` draws them, every screen
the same, at the same inset from the pane's edge, in the screen's root box
before any padding of its own:

- **Top left: the gear, alone.** It opens the settings from anywhere. On
  the settings screen it stands orange and a tap on it closes them again,
  back to the screen they were opened from — the gear is the second hub.
- **Top right: the name and the home button.** *clavier tuner*, lower-case,
  the *vie* in orange, the edition's mark in script after it where there is
  one (`Edition.mark`, set once by the app); then a house, drawn in strokes,
  which leads to the hub from everywhere. On the hub it stands dim.
- **No back arrow anywhere.** The Android back gesture still returns to
  where a screen was opened from; it is not an arrow on the screen.

A screen's own header keeps to the band between the corners
(`CORNER_LEFT_WIDTH` … `CORNER_RIGHT_WIDTH`, `CORNER_BAND` high): the tuning
screen's state word and Pro's two buttons, the unison screen's note and
Listen, the record screen's title, the settings' title. The hub's name and
gear of before, `ClavierhausTitle`, `SettingsGear` and `BackArrow`, are
gone with the arrows.

## Four layers, one direction

```
shared/   engine        measurement: readers, identifier, session, curve
shared/   picture       what a screen shows, as plain data, computed once
ui/       parts         Compose: draws a picture, holds no logic
androidApp/ screens     wiring: navigation, permissions, the microphone
```

Data flows downward only. A *picture* is a small immutable class in
`shared/.../picture/` built from the engine's own state flows: it says, for
one screen, exactly what is to be drawn — which partial anchors the bells,
where each bell stands relative to that anchor, which partials are counted,
whether the reading is near or on — and nothing about how. It is pure Kotlin
and is tested on the JVM with synthetic strikes, so a display rule is proved
before any Compose code exists. The Compose part receives the picture and a
pane and draws; it never reads the controller directly. The screen in
`androidApp/` connects the two and nothing else.

The reason is the law above: if two places can put a number on the screen,
one of them is wrong. A picture is the one place.

### `TuningPicture` (`shared/.../picture/`, the first picture; drawn by `TuningPictureView` since 3 Oct)

```kotlin
class TuningPicture(
    val note: String,            // "F3"
    val listened: Int,           // the partial being tuned
    val anchor: Int,             // the partial the bells stand on: steady, counted, named on screen
    val bells: List<Bell>,       // every partial over the noise, up to Partials.MAX
    val pointerCents: Double?,   // the listened partial against its target; null while nothing sounds
    val state: State,            // SILENT, SOUNDING, NEAR, ON
    val falseMarked: Set<Int>,   // measured false on this strike: red-violet, filled
    val dropped: Set<Int>,       // taken out by the tuner: red-violet, outlined, dimmed
) {
    class Bell(val k: Int, val offsetCents: Double, val errorCents: Double, val level: Double, val counted: Boolean)
    enum class State { SILENT, SOUNDING, NEAR, ON }
}
```

Built by `TuningPicture.Builder` from a `Frame` — the engine's readings of one
moment, as plain values, so the picture and its tests need no controller. The
app maps `TuningController`'s flows to a frame at every reading. Its rules,
each with a test in `TuningPictureTest`, and each test shown to fail when its
rule is taken out:

- the anchor is the listened partial while it is counted; when it is marked
  false or dropped, the anchor moves to the strongest counted partial and
  stays there until that one is lost (hysteresis: a move needs the new anchor
  to be the strongest for `ANCHOR_HOLD_S`, so the line never flaps);
- a bell's offset is its reading against the anchor's reading, not against a
  target — on a clean string every bell stands at zero wherever the pitch is;
- `NEAR` is the pointer inside the match window; `ON` is `NEAR` held for
  `HOLD_S` (0.5 s) by every counted bell; a dropped partial cannot veto `ON`;
- the picture dies with its sound: when the engine's reading expires, the
  picture is `SILENT` and the bells are gone. Nothing is held.
- **a bell has a width** (3 Oct): how well its partial is read — the robust
  spread (median absolute deviation × 1.4826) of its last half second of
  shown readings, kept by the controller per shown partial (`ReadingSpread`),
  cleared at a strike, dropped with the partial. A loud partial is a needle
  (0.01–0.3 c on the Samick's F3), a partial at the room's floor a wide, low
  bell (0.5–1.4 c of spread, ±2–3 c of wander). A bell more than
  `WIDE_FACTOR` (3×) wider than the narrowest counted one, and over
  `WIDE_CENTS` (0.5 c), is *wide*: it does not anchor the line and does not
  veto "on" — a needle outranks a wide bell. The picture says only the width;
  it does not say why a bell is wide (the floor, a false string and three
  strings struck together all widen it, and width and depth over the floor
  do not tell them apart on the Samick session).
- **the anchor is a trusted reading** (3 Oct, evening): a partial standing
  less than `ANCHOR_DEPTH_DB` (25 dB, the depth `CleanReading` trusts a
  reading at) over the noise beside it does not anchor the line, however
  narrow its last half second — at the floor a reading wanders slowly as
  well as fast, and a narrow bell can stand a cent from where it was. On
  F3-C-s1 the anchor leaves partial 4 for partial 1 at 4.5 s, when partial 4
  falls under 25 dB; the needles then stand within the string's own drift of
  the line (partial 5 against partial 1 moves 0.4–0.6 c over the strike,
  measured independently of the engine — the string, not the picture).

**The one control on the picture** is the note name (3 Oct 2026). The screen
follows the key struck by default (`TunerSettings.autoNote`). A tap on the
name opens `NotePicker` — the session's compass as a keyboard, one row per
octave, nothing scrolls — and the key chosen there holds: the setting goes
off and a thin line under the name says the key is the tuner's. A tap on the
name then gives the choice back to the strike. The arrows that stepped the
note a semitone at a time are gone with the stepper: they were clutter beside
a screen that already knows which key was struck.

Later pictures follow the same shape: `ProgressPicture`, `UnisonPicture`,
`SettingsPage`.

## The three primitives of `ui/`

Everything in `ui/` is built from three things, so the laws hold by
construction rather than by review.

- **`Pane`** — a box whose children are placed by fractions of its width and
  height. It is the only way to position anything; `dp` offsets do not appear
  in a screen. There are three pane classes — narrow phone (20:9), wide phone
  (16:9), tablet (16:10) — chosen by the aspect ratio of the window; each is
  relative inside, and a screen gives one arrangement per class it supports.
  Only the 20:9 class is drawn in the first round; the others are
  arrangements, not redrawings.
- **`FitText`** — text that is measured before it is drawn and steps its size
  down in whole steps until it fits its box. It never clips and never
  ellipsises; `maxLines` is not a parameter. A debug build logs when it had
  to step down on a supported pane, because that is a budget error, not a
  layout feature.
- **`Page`** — a settings page with a row budget: it takes at most the rows
  that fit the smallest pane at minimum size, and a tab with more rows is a
  tab with more pages, each with a label. The page row appears only where a
  tab has more than one page; a tap or a swipe turns the page.

`OnePicture` (the scale-down of a reference design) is retired with these;
`Pane` replaces it.

## Settings: tabs, pages, two columns (4 Oct 2026)

`SettingsScreen` (`ui/SettingsParts.kt`) writes the tabs as a line of serif
words, the open one orange, as the hub writes its doors — no pills; a
hairline; under a tab with more than one page, its pages as a line of small
words, the open one orange and underlined (Peter's choice of 4 Oct, option A
of Pro's `wireframes/2026-10-04`; the pill strips and their ◀ ▶ went with
it). A tap or a swipe turns the page. A `SettingsPage` is two columns of rows and one line
at the foot; a row (`StepperRow`, `ChoiceRow`, `SwitchRow`, `ReadOnlyRow`,
`ActionRow`) is a name and a control, 40 dp, nothing under the name — the
page's note says the rest, as on the camera's LCD. Nothing scrolls: the
budget is five rows and a header per column on the reference pane (260 dp
under a sub-tab row), and a context that needs more is two named pages.
The open tab and the open page are the orange ones. foss's tabs: Tuning
(Temperament · Reading), Stretch (Bass · Middle · Treble · Breaks — one
bass, wound and plain alike, since 4 Oct 2026, its width sliding from an
octave above the plain-wire floor down to the lowest key), Piano
(Compass), App (Language · Probe · About); Pro adds its own from the same
parts. A4 is not a setting.

## Settings: the (i) behind the entry (14 Oct 2026)

This supersedes the line at the foot of each page described above. Peter: the
small print at the bottom of the settings did not say which entry it belonged
to. An explanation now stands behind its entry as a round (i): right after a
row's name, or after a section's header when it speaks for the whole section.
A tap opens a window with the entry's name and the text. A tap anywhere outside
the window closes it, and so does Back. The ring is orange while its window is
open. `SettingsPage` no longer has a note. Every row except `ActionRow` and
`SettingsSection` take `info`, and `InfoButton` draws the ring and the window.
No explanation stands on a settings page. A page note that spoke of two rows
was split between them (Compass: lowest key, lowest unwound string). Help
pages have no foot line either. Quick start's pitch-raise line became the last
line of its A4 section.

## Help: an index on the left, the topic on the right (14 Oct 2026)

Peter: "The left half will become an index for various help topics." Help is one
page. Its left half is the index, one line per topic, with the open topic orange
and bold. Its right half is the open topic's full text in one column. A new topic
opens at its top. A− A+ step the text of both halves. Quick start is the first
topic, and its four sections now stand in one column on the right. A topic is a
`HelpTopic(title, sections)`, and an edition adds its own through
`TunerSettingsScreen(extraHelpTopics = …)`. Both halves wrap and scroll, under
Help's exemption.

## Settings: three scopes

| scope | lives in | examples |
|---|---|---|
| **App** — the tuner and the device | `TunerSettings`, this device | language, display, match window, reading memory, probe, licences |
| **Piano** — the instrument's facts | the piano's record (Pro) | compass, lowest key, first plain wire, breaks, the stretch settled for it |
| **This tuning** — today's choices | the session | A4 for this visit, pitch raise, temperament octave, a deviation from the piano's stretch |

A value belongs to one scope and is shown in one place. The workflow holds
the *moments* (A4 at the start of a session, the piano at the hub); settings
hold the *values*; the tuning screen reaches the stretch page in one tap.

## Colours

| meaning | colour | where |
|---|---|---|
| target, static | `Brand.TARGET_BLUE` | target bell outline, target figures |
| sounding, not on | `Brand.ORANGE` | live bells, pointer, state word |
| on | `Brand.GO_GREEN` | the wash, the gate, the state word |
| measured false on this strike | `Brand.FALSE_VIOLET` `#C040F0` | that partial's bell, its button, the status line — nowhere else |

Violet filled: false but counted. Violet outlined: dropped by the tuner. A
dashed outline or a struck number carries the cue without colour.

## Checking a change without a piano

- `./gradlew :shared:testAndroidHostTest` — the engine, the pictures, and the
  law walls. Green before any push.
- The probe (`docs/PROBE.md`): any 48 kHz recording played where the
  microphone's buffers arrive, so a screen is judged on the same strike by
  everyone. The corpus takes in the Pro repository are the reference strikes.
- `ui/` and `androidApp/` compile only with the Android SDK. A change there is
  unverified until it builds on a machine that has one, and its commit says so.

## Where things go

- A new reading or measurement: the engine (`shared/`), with its test.
- A new rule about what a screen shows: the picture (`shared/.../picture/`),
  with its test, before any drawing.
- A new way of drawing: `ui/`, built from `Pane`, `FitText`, `Page`.
- Navigation, permissions, files, the microphone: `androidApp/`.
- Words the user reads: `i18n/strings.tsv`, then `scripts/i18n.py`.
