package at.clavierhaus.unisonmaster.research

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrikeProtocolTest {

    @Test
    fun theProtocolCoversTheCompassThreeTimesEach() {
        val takes = StrikeProtocol.takes(Piano.STEINWAY_D, firstPlainMidi = 40)
        assertEquals(13 * 3 + 3 * 2 * 3, takes.size)
        assertEquals(takes.size, takes.map { it.id }.toSet().size, "every take is unique")
        assertEquals("A1-L-s1", takes.first().id, "wound bichord: left string")
        assertTrue(takes.any { it.id == "E2-C-s1" }, "first plain unison: centre string")
        assertTrue(takes.any { it.id == "C7-R-s3" })
    }

    @Test
    fun fileNamesCarryEverything() {
        val t = Take(69, StringPos.CENTRE, 2)
        assertEquals("D_A4-C-s2_unproc_20260916-153012.wav", StrikeProtocol.fileName(Piano.STEINWAY_D, t, true, "20260916-153012"))
        assertEquals("Boesendorfer_A4-C-s2_mic_x.wav", StrikeProtocol.fileName(Piano.BOESENDORFER, t, false, "x"))
    }

    @Test
    fun durationsAsSetAtTheInstrument() {
        val d = Piano.STEINWAY_D
        assertEquals(20, StrikeProtocol.seconds(d, 81))      // A5: already recorded at 20 s
        assertEquals(5, StrikeProtocol.seconds(d, 84))       // C6 and up: 5 s
        assertEquals(5, StrikeProtocol.seconds(d, 96))
        val b = Piano.BOESENDORFER
        assertEquals(listOf(10, 10, 12, 12, 10, 10, 8, 5, 5, 5),
            listOf(24, 35, 36, 47, 48, 59, 60, 61, 72, 96).map { StrikeProtocol.seconds(b, it) })
    }

    @Test
    fun theBoesendorferStartsAtC1() {
        val takes = StrikeProtocol.takes(Piano.BOESENDORFER, firstPlainMidi = 43)
        assertEquals("C1-L-s1", takes.first().id)
        assertTrue(takes.any { it.id == "G2-C-s1" }, "first plain unison from the settings")
        assertEquals(takes.size, takes.map { it.id }.toSet().size)
    }

    @Test
    fun wavHeaderAndSamples() {
        val b = Wav.pcm16(floatArrayOf(0f, 1f, -1f, 0.5f), 48_000)
        assertEquals(44 + 8, b.size)
        assertEquals("RIFF", b.copyOfRange(0, 4).decodeToString())
        assertEquals("WAVE", b.copyOfRange(8, 12).decodeToString())
        assertEquals("data", b.copyOfRange(36, 40).decodeToString())
        fun s16(at: Int) = (b[at].toInt() and 0xff) or (b[at + 1].toInt() shl 8)
        fun u32(at: Int) = (0..3).sumOf { (b[at + it].toInt() and 0xff) shl (8 * it) }
        assertEquals(48_000, u32(24))
        assertEquals(8, u32(40))
        assertEquals(listOf(0, 32767, -32767, 16384), listOf(s16(44), s16(46), s16(48), s16(50)).map { it.toShort().toInt() })
    }

    @Test
    fun theReferenceStudyCoversTheInstrumentTwiceOnTheLeftStringAndItsCornerNotesByStrength() {
        val takes = StrikeProtocol.takes(Piano.BOESENDORFER, 40, Study.REFERENCE)
        val keys = 108 - 17 + 1                                                    // F0 .. C8 on the 225
        val single = takes.filter { it.variant.isEmpty() && it.string != StringPos.ALL }
        val whole = takes.filter { it.variant.isEmpty() && it.string == StringPos.ALL }
        assertEquals(keys, single.size, "every key, one string")
        assertEquals(keys, whole.size, "every key as it is")
        assertTrue(single.all { it.string == StringPos.LEFT }, "always the left string: one wedge mutes the others")
        val corners = StrikeProtocol.cornerNotes(Piano.BOESENDORFER, 40)
        assertEquals(listOf(17, 39, 40, 45, 69, 84, 96), corners)
        val cases = takes.filter { it.variant.isNotEmpty() }
        // the left string at each corner note, soft, medium and hard: nothing else set by hand
        assertEquals(corners.size * 3, cases.size, "the cases: ${cases.map { it.id }}")
        assertEquals(setOf("soft", "medium", "hard"), cases.map { it.variant }.toSet())
        assertTrue(cases.all { it.string == StringPos.LEFT })
        assertEquals(takes.size, takes.map { it.id }.toSet().size, "every take has its own file name")
        assertTrue(cases.all { it.note.isNotEmpty() }, "every corner case carries its instruction")
        assertTrue(single.all { it.part == StrikeProtocol.PART_A } && whole.all { it.part == StrikeProtocol.PART_B } && cases.all { it.part == StrikeProtocol.PART_C })
        assertTrue(StrikeProtocol.partSetup(StrikeProtocol.PART_A).contains("left string"))
        assertEquals(0, cases.count { StrikeProtocol.wantsReading(it) }, "no take asks for a second tuner")
        assertEquals(8, StrikeProtocol.seconds(Piano.BOESENDORFER, 21, Study.REFERENCE))
        assertEquals(4, StrikeProtocol.seconds(Piano.BOESENDORFER, 96, Study.REFERENCE))
        assertTrue(takes.size < 280, "an afternoon: ${takes.size} takes")
    }

    @Test
    fun everyStringAloneWalksLeftCentreRightThenTheUnisonKeyByKey() {
        val takes = StrikeProtocol.takes(Piano.SAMICK, 40, Study.STRINGS, firstBichordMidi = 28, firstTrichordMidi = 40)
        // 7 monochords × 1, 12 bichords × 3, 69 trichords × 4
        assertEquals(7 + 12 * 3 + 69 * 4, takes.size)
        assertEquals(listOf(StringPos.LEFT), takes.filter { it.midi == 21 }.map { it.string })
        assertEquals(listOf(StringPos.LEFT, StringPos.RIGHT, StringPos.ALL), takes.filter { it.midi == 30 }.map { it.string })
        assertEquals(listOf(StringPos.LEFT, StringPos.CENTRE, StringPos.RIGHT, StringPos.ALL), takes.filter { it.midi == 69 }.map { it.string })
        assertEquals(21, takes.first().midi)
        val drift = StrikeProtocol.takes(Piano.SAMICK, 40, Study.DRIFT)
        assertEquals(16, drift.size)
        assertEquals(listOf(21, 24, 33, 36), drift.take(4).map { it.midi })
        assertEquals(108, takes.last().midi)
        assertEquals("Samick_A4-R-s1_unproc_x.wav", StrikeProtocol.fileName(Piano.SAMICK, takes.first { it.midi == 69 && it.string == StringPos.RIGHT }, true, "x"))
        assertEquals(8, StrikeProtocol.seconds(Piano.SAMICK, 21, Study.STRINGS))
    }

    @Test
    fun stageOneTakesOneStringPerKeyByTheFloorsAndStageTwoTheOtherTwoOfThePlainTrichords() {
        val one = StrikeProtocol.takes(Piano.SAMICK, 40, Study.ONE_STRING, firstBichordMidi = 28, firstTrichordMidi = 40)
        assertEquals(88, one.size)
        assertEquals(StringPos.LEFT, one.first { it.midi == 21 }.string)     // monochord
        assertEquals(StringPos.RIGHT, one.first { it.midi == 30 }.string)    // wound bichord
        assertEquals(StringPos.LEFT, one.first { it.midi == 69 }.string)     // plain trichord
        val other = StrikeProtocol.takes(Piano.SAMICK, 40, Study.OTHER_STRINGS, firstBichordMidi = 28, firstTrichordMidi = 40)
        assertEquals((108 - 40 + 1) * 2, other.size)
        assertEquals(listOf(StringPos.CENTRE, StringPos.RIGHT), other.filter { it.midi == 69 }.map { it.string })
        assertTrue(other.none { it.midi < 40 })
    }
}
