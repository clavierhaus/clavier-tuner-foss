# The probe: a recording played in place of the microphone

A tuner is not always at a piano, and a fault seen once at the instrument
cannot be chased from memory. The probe lets a recording be fed to the app
exactly where the microphone's buffers would arrive, so the tuning screen
reads the recording as it would read the string.

## Why it proves anything

`FeedableAudioSource` **is** an `AudioSource` — the whole platform contract
for audio input, four methods wide. Everything above that line is the shared
engine: the tap, the phase readers, the key identifier, the clean-partial
reading, the targets, the screen. None of it is stubbed, mocked or branched
for the probe, and none of it can tell where the buffers came from. What the
probe shows is what the piano would show, or the probe is broken.

Two things are deliberately kept honest:

- **Real time.** Buffers are handed over at the recording's own speed. The
  strike detector, the reader's 150 ms memory and the settling skip are all
  written in seconds; a file poured in as fast as the processor allows would
  be read quite differently from the same file played at its own speed.
- **No resampling.** A file at another sample rate is refused by name rather
  than converted. A resampler would be one more thing standing between the
  recording and the reading, and every set of recordings here is already at 48 kHz.

## Using it

Switch on *Play button on the tuning screen* under Settings → **Probe**. A
triangle then sits beside the recording button: it opens the file dialog and
plays straight into the engine, so the scope, the partials, One partial and
Done all stay where they are while a recording runs. It turns into a square
and the recording's name; tapping it again gives the microphone back.

Settings → **Probe** → Input → *A recording…* does the same from the settings.
Any WAV the phone can reach will do: the app's own recordings, Downloads,
anything pushed with `adb push … /sdcard/Download/`. 48 kHz, 16-bit PCM or
32-bit float, mono or stereo (the left channel is taken).

The recording repeats with a second of silence between passes, so every pass
begins with a clean onset and the strike detector sees a strike rather than
a splice. Go back to the tuning screen and watch it as if a string were
sounding. *Microphone* puts it back.

The source is shown on the same tab, and the tuning screen behaves in every
other respect as usual — including the red record button, which will happily
record the fed audio.

**Leave it on Microphone at the instrument.** The probe is a workshop tool,
not a feature, and a tuner who forgets it is tuning to yesterday's piano.

## What it is for

- Reproducing something seen at the piano, with the recording that showed it.
- Watching a change to the engine against a known string before it is carried
  to an instrument — the same recording before and after, which the piano can
  never give twice.
- Reading a recording on the screen instead of in a table, which is how a
  tuner judges whether a number is usable.

Its limit: a recording cannot answer a question about the microphone itself,
the room, or the phone's own position. Those still need the piano.
