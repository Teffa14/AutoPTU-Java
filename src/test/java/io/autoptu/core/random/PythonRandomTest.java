package io.autoptu.core.random;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PythonRandomTest {
    @Test
    void getRandBits32MatchesPythonForSeed42() {
        PythonRandom rng = new PythonRandom(42L);
        long[] expected = {
                2_746_317_213L, 478_163_327L, 107_420_369L, 3_184_935_163L,
                1_181_241_943L, 1_051_802_512L, 958_682_846L, 599_310_825L
        };
        for (long value : expected) {
            assertEquals(value, rng.getRandBits(32));
        }
    }

    @Test
    void getRandBits32MatchesPythonForMultipleSeeds() {
        assertSequence(0L, new long[]{
                3_626_764_237L, 1_654_615_998L, 3_255_389_356L, 3_823_568_514L
        });
        assertSequence(1L, new long[]{
                577_090_037L, 2_444_712_010L, 3_639_700_191L, 3_445_702_192L
        });
        assertSequence(123_456_789L, new long[]{
                2_754_794_679L, 1_899_526_012L, 2_328_685_183L, 3_049_235_403L
        });
        assertSequence(987_654_321L, new long[]{
                748_065_316L, 1_801_290_655L, 1_643_552_794L, 1_656_009_625L
        });
    }

    @Test
    void randomDoubleMatchesPythonExactly() {
        PythonRandom rng = new PythonRandom(42L);
        double[] expected = {
                0.6394267984578837,
                0.025010755222666936,
                0.27502931836911926,
                0.22321073814882275,
                0.7364712141640124,
                0.6766994874229113,
                0.8921795677048454,
                0.08693883262941615
        };
        for (double value : expected) {
            assertEquals(value, rng.random());
        }
    }

    @Test
    void randRangeUsesPythonsRejectionSemantics() {
        PythonRandom rng = new PythonRandom(42L);
        int[] expected = {81, 14, 3, 94, 35, 31, 28, 17, 94, 13, 86, 94};
        for (int value : expected) {
            assertEquals(value, rng.randRange(100));
        }
    }

    @Test
    void mixedCallsConsumeTheSameFramesAsPython() {
        PythonRandom rng = new PythonRandom(123_456_789L);
        assertEquals(0.6414006161858726, rng.random());
        assertEquals(17L, rng.getRandBits(5));
        assertEquals(9, rng.randRange(10));
        assertEquals(13, rng.randIntInclusive(1, 20));
        assertEquals(0.9879999230585628, rng.random());
        assertEquals(2, rng.randRange(6));
    }

    @Test
    void negativeIntegerSeedMatchesPythonAbsoluteSeedRule() {
        PythonRandom positive = new PythonRandom(42L);
        PythonRandom negative = new PythonRandom(-42L);
        for (int i = 0; i < 20; i++) {
            assertEquals(positive.getRandBits(32), negative.getRandBits(32));
        }
    }

    @Test
    void multiWordIntegerSeedMatchesPython() {
        PythonRandom rng = new PythonRandom(new BigInteger("1099511640121"));
        long[] expected = {
                1_332_995_613L, 1_500_173_691L, 493_462_063L, 16_738_666L, 2_291_790_675L
        };
        for (long value : expected) {
            assertEquals(value, rng.getRandBits(32));
        }
    }

    @Test
    void rejectsUnsupportedArgumentsInsteadOfSilentlyChangingSemantics() {
        PythonRandom rng = new PythonRandom(0L);
        assertEquals(0L, rng.getRandBits(0));
        assertThrows(IllegalArgumentException.class, () -> rng.getRandBits(33));
        assertThrows(IllegalArgumentException.class, () -> rng.randBelow(0));
        assertThrows(IllegalArgumentException.class, () -> rng.randIntInclusive(10, 1));
    }

    private static void assertSequence(long seed, long[] expected) {
        PythonRandom rng = new PythonRandom(seed);
        for (long value : expected) {
            assertEquals(value, rng.getRandBits(32));
        }
    }
}
