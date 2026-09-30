# Working rules

Invariants of this engine. They are not preferences and not style: each one
was decided at an instrument, and each has already been broken once at a cost.
A change that violates one of these is wrong even when its tests pass, even
when it makes a number look better, and even when it was asked for — say so
and stop rather than ship it.

Read this before changing the engine or the tuning screen. Add to it when a
rule is decided; never quietly drop one.

---

## 1. A bell is not pitch dependent

The partial display shows **one string seen several ways**, not several
pitches to tune. Where the string sits in pitch moves every bell together and
changes nothing about their relationship. Therefore:

> **Every shown partial must move together. A change that moves one bell
> relative to the others is wrong unless the string moved it.**

On a clean string the bells stand one above the other, whatever the pitch.
A failure to stack has exactly three causes, and telling them apart is what
the display is *for*:

| what you see | what it is |
|---|---|
| all bells displaced together | the string is not at pitch — the ordinary case, and not a failure |
| one bell displaced, **standing still** | the app's model of that string is wrong. Our fault. |
| one bell that **will not settle** | that partial beats on its own — a false string, seen the same second it is heard |

Motion is the discriminator, and `measure/CleanReading` already measures it:
a partial's spread over the strike is what separates the second row from the
third.

*Broken 30 September 2026.* A guard placed one partial by a fitted series
while its neighbours kept their measured places — it moved a single bell, on
its own, for a reason no string had. It rested on a claim asserted as physics
and never measured: that partial 2 cannot sit below twice partial 1. A real
string's fundamental runs about a cent sharp of the stiff-string model (on the
Samick's F3: every partial within 0.13 c of the fit, the fundamental +0.99 c),
so partial 2 sits 0.3 c *below* twice partial 1 on a perfectly clean string.
Targets taken from the string's own partials stack to 0.000 c; from the fit,
to 1.13 c.

## 2. The partials are a live measurement and are not stored

The partial ratios used as targets come from **the strike being heard** and
are discarded with it. `f1` and `B` persist, because the curve is built from
them and they are a fitted scalar; a per-note partial *table* does not.

A stored table lets a strike from an earlier session — or from the sampling
pass, taken while a partial was swinging — furnish today's targets, and the
screen then shows agreement or disagreement that has nothing to do with the
string now sounding. It pulls wool over the tuner's eyes.

*Decided 30 September 2026, at the instrument, after a table stored during
sampling put F3's partial 2 several cents out and drove the tuning screen's
targets for the rest of the day.*

## 3. A reading must not outlive its sound

A number on screen means a partial is sounding now. When the partial is gone
the number goes, and so does any verdict resting on it.

*Broken 30 September 2026: readings stood for seconds after the string had
died, which corrupted two analyses before it was found.*

## 4. The presence gate is a ratio, and not a knob

The partial's power against its own sidebands, level-independent by
construction. It does not become a setting.

## 5. A tuned partner is the check, never the source

A note's target is computed; a partner that happens to be tuned stands beside
it as a check. Targets are never read off another string.

## 6. There is never an undamped neighbour in these recordings

When a partial splits or wobbles, it is the string. This has been proposed as
an explanation and refuted more than once; do not raise it again without a
recording that shows it.

## 7. Physics is measured here, not asserted

No claim about how a string behaves enters the code or the docs without a
measurement behind it, and a claim that turns out wrong is withdrawn in
writing with the number that killed it. Rule 1 was broken precisely by
skipping this step.

## 8. The line between the two apps

FOSS shows what the microphone says. Pro says what to do about it. Data —
corpora, maps, widths as tuned — never crosses into foss.
