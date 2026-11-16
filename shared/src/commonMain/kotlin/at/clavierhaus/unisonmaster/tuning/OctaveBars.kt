package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.settings.TunerSettings

/**
 * The top of the range by the octave below, an experiment (Peter, 16 Nov 2026, 10:47: "Starting with C6 our strobe gets a
 * new design. We must break the law on not using notes as reference ... Left one is the tuned reference tone, one string";
 * 10:53, the border: "up to C6 we are good ... C#6 must already be tuned this way"; 18:58, "go").
 *
 * From C#6 up the strobe gives way to two bars: the key an octave below, struck alone, against its own target, and the key
 * on screen, struck alone after it, against a target moved by how far the key below stood from its own. The octave then
 * stands as the stretch sets it, 2:1, 4:1 or whatever the treble's type, on the key below as it sounds, whether or not
 * the octave beats. Struck in turn, never together: struck together, the key below's partial 2 stands 0-21 dB (mostly
 * 5-12) under the upper key's partial 1, and a reading of the one reads both (the Bechstein, 16 Nov 2026,
 * research/octave_pairs_20261116.py in Pro).
 */
object OctaveBars {
    /** C#6, the first key tuned by the key an octave below. */
    const val FROM = 85

    /** The key an octave below [midi]. */
    fun refOf(midi: Int): Int = midi - 12

    /** Whether [midi] is tuned by the two bars under [s]. */
    fun on(s: TunerSettings, midi: Int): Boolean = s.octaveBars && midi >= FROM
}
