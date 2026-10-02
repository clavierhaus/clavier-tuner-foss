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
   page with a name of its own.
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

### `TuningPicture` (`shared/.../picture/`, the first picture; built, tested, not yet drawn)

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
  tab with more pages, each with a label. The sub-tab row appears only where a
  tab has more than one page; left/right and swipe step through them.

`OnePicture` (the scale-down of a reference design) is retired with these;
`Pane` replaces it.

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
