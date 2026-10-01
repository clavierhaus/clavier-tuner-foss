# CLAUDE.md — Clavier Tuner

Binding on all work in this repository and in `clavier-tuner-pro`. Every order
extends to its evident intent; orders bind classes of action, not the single
incident that produced them; and orders compose — no action is permitted if any
active order forbids it in spirit.

Read this before touching the engine, the research scripts, or the docs.

---

## The Core Directive

> **The only truth is a verifiable, reproducible measurement from an
> uncompressed, unaltered source of sound** — the device's microphone, or a file
> in an uncompressed format.

Nothing else is evidence. Not a model's prediction. Not a number read off a
screen. Not a figure recalled from an earlier session, a doc, or this
conversation. Not inference from how strings "should" behave. Not an expected
result from a work order. Where the two disagree, **the recording wins and the
model is wrong**, every time, without exception and without negotiation.

What follows from it, and is not negotiable:

- **No lossy audio ever enters the chain.** No mp3, no AAC, no Opus, no
  resampling, no normalisation, no AGC, no noise suppression, no "cleaning up" a
  take. The recorder uses `MediaRecorder.AudioSource.UNPROCESSED`; a file at the
  wrong sample rate is refused by name, never converted. A processing step
  between the string and the reading is a lie with a timestamp on it.
- **A measurement is reproducible or it is not a measurement.** One strike is an
  anecdote. A rate that does not repeat across strikes is not a rate. State the
  window, the method, and the corpus file, so the number can be recomputed by
  someone who does not trust you.
- **The corpus is evidence and is never edited.** Do not re-split, re-record,
  "correct", normalise or regenerate a take to make an analysis work. If the
  take cannot answer the question, say so and ask for a new recording.
- **Peter's ear is an instrument, and a better one than the code.** When his
  hearing and a measurement disagree, the measurement is the suspect. That has
  been true every time it has come up.

---

## The Measurement Laws

The full statements, with what each cost when it was broken, are in
`docs/RULES.md`. They are law, not guidance. In brief:

0. **Measurement has the primary role; modelling never overrides it.** No guard,
   threshold, cap or fallback decides in advance what the piano is allowed to
   produce. A partial the strike produces is reported — with its level and its
   confidence — never silently dropped.
0b. **FOSS is the measurement foundation.** The engine is byte-identical in both
   repositories. Every engine change starts here and is propagated into Pro,
   never the reverse, and never edited in place in Pro's `core/`.
1. **A bell is not pitch dependent.** The partial display shows one string seen
   several ways. Every shown partial moves together; a change that moves one
   bell relative to the others is wrong unless the string moved it.
2. **The partials are a live measurement and are not stored.** Partial ratios
   come from the strike being heard and are discarded with it. `f1` and `B`
   persist, because the curve is built from them.
3. **A reading must not outlive its sound.**
4. **The presence gate is a ratio, and not a knob.**
5. **A tuned partner is the check, never the source.**
6. **There is never an undamped neighbour in these recordings.**
7. **Physics is measured here, not asserted.**
8. **FOSS shows what the microphone says; Pro says what to do about it.** Data —
   corpora, maps, widths as tuned — never crosses into foss.

---

## Verification Discipline

**Never speculate — exhaust the resources.** When something is unclear, in this
order:

1. **The recordings.** `data/recordings/` in Pro. Nearly every question asked in
   this project so far was already answerable from a file on disk.
2. **The engine source**, read this session — not recalled, not inferred.
3. **The repository history** (`git log`, `git diff`), and the dated sections of
   the docs.
4. **The probe** (`docs/PROBE.md`): feed a recording where the microphone's
   buffers arrive and watch the engine read it. This is how a fault seen at the
   instrument is chased without the instrument.
5. **Peter** — for facts only he has: what he heard, what the piano is doing,
   what the screen showed.

Hypotheses are stated as an ordered list, each paired with the concrete check
that would kill it. Narrating a series of guesses is forbidden.

**Before asserting that a file, symbol, constant or function exists**, read it in
source in this session. Verifying that a type exists does not verify its
members: before new code names a field, a method or an enum constant, that
member's declaration is read.

**Before stating a number**, compute it in this session or cite the dated doc
section it comes from.

---

## Before Changing Anything

Answer these. Silence on any of them means the work does not proceed.

1. **What measurement says this change is needed?** Name the recording, the
   script and the figure. "The screen looks wrong" is an observation, not a
   measurement.
2. **Which law does this change touch?** If it adds a guard, a cap, a threshold,
   a fallback or a stored value, it is probably forbidden — say which law allows
   it before writing it.
3. **What would the result look like if the change is wrong?** Write the check
   first. A test that passes before the fix is applied is worthless; verify that
   it fails without it.
4. **Is this the engine?** Then it starts in foss, is tested here, and is
   propagated to Pro.

---

## Build and Test Discipline

- Core tests: the JVM suite must be green before any push. Currently 96.
- **The cloud sandbox has no Android SDK and compiles none of `androidApp` or
  `ui`.** A change to those modules is *unverified* until it builds on the X1 or
  in CI. Say so plainly in the message that ships it; never imply a build that
  did not happen. Two broken APKs reached Peter this way on 30 September.
- `./scripts/sync-core.sh` after every engine change, then push both repos.
- `BasicHub.kt` and `SessionRecorder.kt` are byte-identical between the repos.
  Verify by `diff`, not by memory.
- Commit messages are one long descriptive line saying *why*, ending with the
  Co-Authored-By and Claude-Session lines.
- **Docs get dated sections. History is never rewritten.** A wrong claim is
  withdrawn in writing, in place, with the number that killed it.

---

## Working with Peter

He is a tuner, not a command-line interpreter. Never ask him to cat, paste or
hunt for files — fetch them from GitHub. Short answers; no epic sentences; no
wasted quota. Tell a tuner what to do in brief words.

The repositories are the work: Claude commits and pushes; the X1 pulls, syncs,
builds, installs.

Abstraction, not specification. The Bösendorfer 225 is one piano; the Steinway B
is the abstraction test; a Wurlitzer spinet and a Bösendorfer Imperial must both
work, because the physics between them does not change.

---

## Standing Orders Ledger

Rules born from specific failures. Each entry names what went wrong, so the
rule cannot be argued away by someone who did not live through it.

**2026-09-30 — A reading outlived its sound.** Partial values stood on screen
for seconds after the string had decayed. Two analyses were built on frozen
numbers and both had to be withdrawn. → Rule 3.

**2026-09-30 — Physics asserted, not measured.** "Partial 2 cannot sit below
twice partial 1" was stated as fact and a guard built on it, which then
overwrote good measurements with a fitted series and broke the bell stacking.
The claim is false: a real string's fundamental runs about a cent sharp of the
stiff-string model, and this piano's clean F3 does exactly that. → Rule 7, and
the Core Directive.

**2026-09-30 — A model used as the yardstick for a measurement.** Twice in one
evening a stored value was called "several cents wrong" by comparing it against
the *harmonic series* instead of against the string. Against the string the
error was 0.44 c. → The Core Directive.

**2026-09-30 — A prediction stated before the data, and wrong.** "E6 will be
false on all three strings." It was false on one. Predictions are allowed, but
they are recorded as predictions and their failure is recorded too.

**2026-09-30 — A vacuous test.** A regression test passed with the fix reverted.
Always verify that a new test fails without its fix. → Before Changing
Anything, §3.

**2026-09-30 — Two broken APKs shipped.** Both were errors in `androidApp`,
which this sandbox cannot compile. Each cost Peter a build cycle. → Build
Discipline.

**2026-09-30 — A stored table furnished live targets.** F3's partial table,
measured during sampling hours earlier, drove the tuning screen's targets all
day, and no clean string's bells could stand on one line. → Rule 2.

**2026-10-01 — Caps decided what the piano may produce.** Three research
scripts stopped at 8 or 10 partials. F3 has a real, strong twelfth partial that
every earlier table was blind to. The engine was never at fault. → Rule 0.

**2026-10-01 — A search window crossed into the neighbouring partial.** A fixed
±150 cent band spans 332 Hz at the eleventh partial while F3's partials stand
174 Hz apart, so partials 10 and 11 were measured as one peak. Bounds are taken
from the string, never from a constant. → Rule 0.

**2026-10-01 — A verdict reached without the number that decides it.** A split
was reported as an audible false beat without ever measuring how loud that
partial is within the note. It stands 44 dB below the fundamental and cannot be
heard. → Rule 0, pointing the other way.

---

## The Spirit Binds, Not the Letter

Finding a reading under which an action is technically permitted while its
intent is defeated is itself a violation. Before each commit:

> **Would Peter, reading this, see his order honoured or lawyered?**
