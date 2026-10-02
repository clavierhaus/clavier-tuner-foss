# The aural sequence: the screen walks a tuner's order

A tuner learning to tune by ear follows a sequence — the temperament by
fourths and fifths, say — and an ETD that walks the keyboard chromatically
is in his way. *Aural sequence* on the hub lets the tuning screen walk any
order instead: the arrows step along the sequence, the status line names the
step and what the file says about it, and the reading is the engine's own,
unchanged. The student tunes the interval by ear, strikes the note, and the
scale says whether the ear landed. The key struck is **not followed** while a
sequence runs — the sequence names the note — which is also how TuneLab did
it ("no automatic note switching in aural sequence mode", TuneLab Pro
manuals 4.0 and 5.1, *Special Functions*). TuneLab's is the only such feature
we know of in an ETD, and it orders the notes and nothing more; so does this.

## The file

One note per line, scientific pitch (`A4`, `F#3`, `A#1`); anything after the
note on the same line is shown with it; lines starting with `#` and blank lines
are skipped. A line that is not a note refuses the whole file with its line
number — a sequence with one wrong step teaches the wrong thing. A TuneLab
sequence file (bare notes) works as it is.

    # 4ths and 5ths — an order, not a method
    A4   the reference
    A3   octave below A4
    D4   4th up from A3
    G3   5th down from D4

One sequence ships, *4ths and 5ths*: the temperament F3–F4 by fourths and
fifths, A4 first. It is an order, not a method, and every line is meant to be
changed. *A file of your own…* opens the phone's file dialog for any text file
it can reach — the Downloads folder, a file pushed over `adb`, a file shared
from another app.

## Where the targets come from

The sequence runs on the tuning as it stands: the session continued from the
hub, or a fresh one from A4 when there is none. Every target is the session's
own — the reference, the temperament octave, the octaves on the curve — exactly
as it would be without the sequence. Nothing is computed for the sequence's
sake; `AuralSequence` holds no measurement.

## Not yet

The thing TuneLab could not do and we could: the beat rate of the interval
being tuned, read from the two coincident partials as the microphone hears
them, beside the ear instead of in place of it. That is a measurement and
belongs in the engine when it comes; the sequence does not wait for it.
