# Glossary — the words the app uses, decided once

The app is written for piano technicians, so it speaks their words. This
table decides each word once; the app's tables (`i18n/strings.tsv` in both
repositories), the READMEs and the technician-facing docs follow it, and a
new word on a screen cites its row here in the same commit. Engineering
docs may keep engineering words for what no technician names (a phase
reader is a phase reader), but wherever a technician has a word, theirs.

Sources: **R** = Reblitz, *Piano Servicing, Tuning, and Rebuilding* (glossary
and chapters 6–7, the trade's standard text); **TL** = TuneLab Piano Tuner
manual 3.2; **PM** = PianoMeter's published pages; **PTG** = usage in the
Piano Technicians Guild's publications; **ours** = a coinage of this
project, where the trade has no word because the thing is new. German
column: to be reviewed by a German-speaking technician (Peter) before the
German pass; a word marked ? is a proposal.

## 1. Words that were wrong, and what replaces them

| Was | Is (English) | German | Source | Why |
|---|---|---|---|---|
| corpus | recordings | Aufnahmen | R has no such word; "Korpus" is the piano's case in German | a set of recordings is recordings |
| take | recording | Aufnahme | — | film word |
| hub | home | Start | — | ours, unexplained |
| New Tuning | (gone; a tuning starts from a piano) | — | 28 Sept | |
| sampling, "Sampling your piano" | measuring, "Measuring your piano" | messen, "Ihr Klavier wird gemessen" | TL "measure inharmonicity", PM "measure the piano" | what the technician does; "sample" is the statistician's word |
| sample (a string) | measure (a string); a measured string | eine gemessene Saite | TL, PM | |
| listening partial | the partial being tuned | der gestimmte Teilton | TL "partial number for each note" | |
| Full Spectrum | Partials | Teiltöne | TL's "spectrum display" is a different picture (energy over frequency); ours shows partials read by phase | the button shows partials, so it says so |
| Tune Unison | Unisons | Chöre | R "unison"; German "Chor" is the set of strings of one note | |
| strings alone / as it stands | muted / unmuted | mit Keil / ohne Keil ? | R "mute"; TL "muting" | what the technician did |
| wedge | mute, wedge mute; the felt strip is the temperament strip | Stimmkeil; Dämpfstreifen (Filzstreifen) | R "wedge mute", "temperament strip", "tuning mute" | |
| Customer care | Customers | Kunden | — | |
| ledger | history | Verlauf | — | what a technician keeps for a piano |
| regimen | checklist | Checkliste | R uses "checklist for quick reference" | a list of steps to follow or not |
| Record Strikes | Recordings | Aufnahmen | — | the screen records; "strike" is the physicist's word for what a technician calls playing a note |
| strike (noun, a struck note) | note played, a blow (test blow is the trade's) | Anschlag; Probeschlag | R "test blows" | in the app's prose: "play the key" |
| stretch (the computed tuning) | tuning curve; stretch stays for the amount | Stimmkurve; Spreizung | TL "tuning curve", "stretch"; PM "stretch" | both are the trade's; the curve is the thing, stretch is its size |
| target (a note's) | offset from ET stays internal; on screen the note's pitch is "the target" — keep | Zielton ? | TL "offset", "desired pitch" | "target" is understood; do not say "offset" to a technician |
| the tuner's width | the octave's width | Oktavbreite ? | TL/PM: octaves "wide"/"narrow" | |
| inventory | Pianos | Klaviere | PM "piano files"; a dealer's inventory is stock | "the pianos you look after" |
| lowest unwound string | lowest plain string | tiefste blanke Saite | R "wound string", "plain wire"; TL marks the same place as "highest wound string" | say either, never "unwound" |
| compass | range | Tonumfang | Peter, 27 Oct 2026 | a nautical instrument, not a piano word; the only place it stands, so it is never used again |
| curve breaks (settings) | struts (each as the first note above it) | Streben ? (Gussrahmen-Streben) | R "plate strut" | what the technician sees |

## 2. Words the trade owns, kept as they are

| Word | German | Source | Note |
|---|---|---|---|
| partial (1st, 2nd …); fundamental | Teilton; Grundton | R, TL, PM | never "harmonic" for a piano string |
| coincident partials | koinzidierende Teiltöne ? | R | the pair an interval is tuned by |
| inharmonicity; inharmonicity constant (B) | Inharmonizität; Inharmonizitätskonstante | R, TL | |
| beats, beats per second; beatless | Schwebungen; schwebungsfrei | R | |
| false beat, false-beating string | unsaubere Saite (the string); Schwebung der Saite in sich | R "false beats", PTG "false string" | a string that beats alone |
| wild string | — | PTG usage | a string whose false beat is bad enough to be a defect |
| unison; monochord / bichord / trichord unison | Chor; einchörig / zweichörig / dreichörig | R | "three-string unison" in prose is fine; the field says trichord |
| temperament; temperament octave; equal temperament (ET) | Temperatur; Temperaturoktave; gleichstufige Stimmung | R | |
| cent | Cent | R | |
| pitch; pitch standard (A4 = 440) | Tonhöhe; Kammerton | R | "reference" on the app's hub is the pitch standard: say A4 |
| pitch raise; over-pull | Stimmung anheben; Überziehen | R, TL "over-pull" | |
| octave types 2:1, 4:2, 6:3, 8:4, 10:5; wide / narrow | Oktavtypen …; weit / eng | TL, PM, PTG | |
| stretch | Spreizung | TL, PM | the amount; see §1 |
| tuning curve | Stimmkurve | TL | |
| tuning lever (not "hammer") | Stimmhammer | R "tuning lever" | note the German is the hammer |
| tuning pin, pinblock, hitch pin, agraffe, bridge (bass, long/treble), plate, plate strut, soundboard | Stimmwirbel, Stimmstock, Anhangstift, Agraffe, Steg (Bass-, Diskantsteg), Gussrahmen, Strebe, Resonanzboden | R | |
| bass, tenor, treble; the break (bass break, tenor break) | Bass, Tenor, Diskant; Übergang / Bruch ? | R, TL "split-scale break" | regions of the range |
| wound string; plain (wire) string | umsponnene Saite; blanke Saite | R | |
| stringing scale, scale | Mensur | R | the piano's scale, as the record calls it |
| range (A0–C8) | Tonumfang | R "88-key"; Peter, 27 Oct 2026 | the keys from the lowest to the highest |
| speaking length | klingende Länge | R | |
| electronic tuning device (ETD) | elektronisches Stimmgerät | R | |
| tuning file | Stimmdatei | TL, PM | what other devices call a piano's saved tuning; ours is the piano's record |
| test blow | Probeschlag | R | the hard blow that shows whether a pin has set |
| setting the pin / setting the string | Wirbel setzen | R | |
| regulating; voicing (tone regulating) | Regulierung; Intonation | R | |
| aural tuning; tuning by ear | Gehörstimmung; nach Gehör | R | |
| mute; wedge mute; temperament strip; Papps mute | Stimmkeil; Dämpfstreifen; — | R | |

## 3. Our own words, kept because the thing is new — and said as such

| Word | German | What it names |
|---|---|---|
| reading | Anzeige / Messwert ? | what the display shows for the partial being tuned: its deviation, as measured, 47 times a second |
| the partial's own beat (own beat) | Eigenschwebung ? | a partial that beats on a string alone — a false beat, named per partial |
| string record | Saitenprotokoll ? | what one measured string leaves behind: partials, offsets, false beats |
| the piano's fingerprint | — (prose only) | the partial ratios of all its strings, which only new wire changes |
| measure while tuning | beim Stimmen messen | the minimal checklist: every note tuned is a note measured |

## 4. Screen by screen, what changes

Home: *Continue tuning · Pianos · Unisons*. Pianos: *Customers … · Piano ·
Scale · Checklist · History*; Settings tabs: *Temperament · Piano · About ·
Customers · License*; the measuring screen: *Measuring your piano — play any
key; Keep; Done*; the tuning screen's button: *Partials*; the unison screen:
*Unisons — muted string / unmuted*; Recordings (was Record Strikes): the
studies *Every string · Drift · Range · Reference · Wobble*.

## 5. Not decided yet

"Accept" vs "Keep" for the measured string *(settled 12 Oct 2026: Accept — §6)*; "Progress" for the keyboard
picture — decided so far (28 Sept): *Verlauf* is History, so the keyboard
picture is *Fortschritt* in German and keeps "Progress" in English until a
better word is found; the German for the coinages in §3. These are settled when the screen
that shows them is next touched, and the row is added here first.

## 6. 12 October 2026 — the two answers to a value

Peter, 12 Oct 2026: the button names its act; the other one says *Cancel*.

| ours | the word | German | source | note |
|---|---|---|---|---|
| Accept | taking a measured value: a sample, a reading | Übernehmen ? | Peter, 12 Oct 2026 | settles §5; *Keep* is retired from the measuring screen (§4) |
| Cancel | the other answer: what was caught is dropped and a new strike is wanted | Abbrechen | Peter, 12 Oct 2026 | on the sampling page it drops the strike's catch and its sample; on a window it leaves things as they were |
| the act's own name | a confirming button says what it does: *Set A4 again*, *Save*, *Add entry* | — | Peter, 12 Oct 2026 | never *OK*, never *Keep* |

## 7. 14 October 2026 — the German words, decided

Peter, 14 Oct 2026, for the German-first localisation. These settle the German
column wherever an earlier row carries a "?" for the same thing.

| ours | Deutsch | note |
|---|---|---|
| address | *Sie, Ihr, Ihre* | explanations address the tuner with *Sie*; short orders stay in the infinitive (*Taste anschlagen*) |
| note names | A1, A#1, C4 … **H** for B | only the screen; files and the corpus keep B (`noteName` vs `Notes.name`) |
| sampling, to sample, a sample | *Vermessen*, *messen*, *Messpunkt* | not *Sampling*: on Clavio it was read as recording sounds for playback |
| Continue Tuning / New Tuning | *Stimmung fortsetzen* / *Neue Stimmung* | the home screen's doors |
| match window | *Toleranz* | |
| reading memory | *Mittelungszeit* | |
| the curve's breaks | *Übergänge*; *Bassübergang*, *Tenorübergang* | a break by the place it stands |
| bells; the anchor line | *Glockenkurven*; *Vertikalachse* | |
| Accept / Cancel | *Übernehmen* / *Abbrechen* | §6 |
| Progress | *Fortschritt* | §5 |

