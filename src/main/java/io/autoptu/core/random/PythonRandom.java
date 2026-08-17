package io.autoptu.core.random;

import java.math.BigInteger;

/**
 * Behavioral compatibility RNG for CPython's random.Random when seeded with an integer.
 *
 * AutoPTU's Python oracle relies on random.Random(seed). Java's built-in Random uses a
 * different generator, so battle parity requires matching Python's MT19937 state,
 * integer seeding, 53-bit random() construction, getrandbits(), and rejection-based
 * randrange behavior.
 *
 * This is a clean Java implementation of those observable semantics, not a source
 * translation of CPython.
 */
public final class PythonRandom {
    private static final int N = 624;
    private static final int M = 397;
    private static final int MATRIX_A = 0x9908b0df;
    private static final int UPPER_MASK = 0x80000000;
    private static final int LOWER_MASK = 0x7fffffff;
    private static final long UINT32_MASK = 0xffffffffL;
    private static final BigInteger UINT32_BIG_MASK = BigInteger.valueOf(UINT32_MASK);

    private final int[] state = new int[N];
    private int index = N;

    public PythonRandom(long seed) {
        this(BigInteger.valueOf(seed));
    }

    public PythonRandom(BigInteger seed) {
        seed(seed);
    }

    public void seed(long seed) {
        seed(BigInteger.valueOf(seed));
    }

    public void seed(BigInteger seed) {
        BigInteger magnitude = (seed == null ? BigInteger.ZERO : seed).abs();
        int wordCount = Math.max(1, (magnitude.bitLength() + 31) / 32);
        int[] words = new int[wordCount];
        BigInteger remaining = magnitude;
        for (int i = 0; i < wordCount; i++) {
            words[i] = remaining.and(UINT32_BIG_MASK).intValue();
            remaining = remaining.shiftRight(32);
        }
        initByArray(words);
    }

    /** Return the next tempered MT19937 word as an unsigned 32-bit value. */
    public long nextUInt32() {
        if (index >= N) {
            twist();
        }

        int y = state[index++];
        y ^= y >>> 11;
        y ^= (y << 7) & 0x9d2c5680;
        y ^= (y << 15) & 0xefc60000;
        y ^= y >>> 18;
        return Integer.toUnsignedLong(y);
    }

    /** Match CPython random.Random.random(): a 53-bit double in [0.0, 1.0). */
    public double random() {
        long a = nextUInt32() >>> 5;
        long b = nextUInt32() >>> 6;
        return (a * 67_108_864.0 + b) / 9_007_199_254_740_992.0;
    }

    /** Match CPython getrandbits(k) for 0 <= k <= 32. */
    public long getRandBits(int bits) {
        if (bits < 0 || bits > 32) {
            throw new IllegalArgumentException("getRandBits currently supports 0..32 bits.");
        }
        if (bits == 0) {
            return 0L;
        }
        return nextUInt32() >>> (32 - bits);
    }

    /** Match Random._randbelow_with_getrandbits for positive int bounds. */
    public int randBelow(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        int bits = Integer.SIZE - Integer.numberOfLeadingZeros(bound);
        long candidate;
        do {
            candidate = getRandBits(bits);
        } while (candidate >= bound);
        return (int) candidate;
    }

    /** Equivalent to Python randrange(stop) for stop > 0. */
    public int randRange(int stop) {
        return randBelow(stop);
    }

    /** Equivalent to Python randint(a, b), inclusive on both ends. */
    public int randIntInclusive(int start, int end) {
        if (end < start) {
            throw new IllegalArgumentException("end must be >= start");
        }
        long width = (long) end - start + 1L;
        if (width > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("range too wide for int-backed parity helper");
        }
        return start + randBelow((int) width);
    }

    /** Equivalent to selecting an index with Python random.choice(sequence). */
    public int choiceIndex(int size) {
        return randBelow(size);
    }

    private void initGenRand(int seed) {
        state[0] = seed;
        for (int i = 1; i < N; i++) {
            long previous = Integer.toUnsignedLong(state[i - 1]);
            long mixed = previous ^ (previous >>> 30);
            state[i] = (int) ((1_812_433_253L * mixed + i) & UINT32_MASK);
        }
        index = N;
    }

    private void initByArray(int[] key) {
        initGenRand(19_650_218);
        int i = 1;
        int j = 0;
        int k = Math.max(N, key.length);

        for (; k > 0; k--) {
            long previous = Integer.toUnsignedLong(state[i - 1]);
            long mixed = previous ^ (previous >>> 30);
            long value = (Integer.toUnsignedLong(state[i]) ^ ((mixed * 1_664_525L) & UINT32_MASK));
            value = (value + Integer.toUnsignedLong(key[j]) + j) & UINT32_MASK;
            state[i] = (int) value;
            i++;
            j++;
            if (i >= N) {
                state[0] = state[N - 1];
                i = 1;
            }
            if (j >= key.length) {
                j = 0;
            }
        }

        for (k = N - 1; k > 0; k--) {
            long previous = Integer.toUnsignedLong(state[i - 1]);
            long mixed = previous ^ (previous >>> 30);
            long value = (Integer.toUnsignedLong(state[i]) ^ ((mixed * 1_566_083_941L) & UINT32_MASK));
            value = (value - i) & UINT32_MASK;
            state[i] = (int) value;
            i++;
            if (i >= N) {
                state[0] = state[N - 1];
                i = 1;
            }
        }

        state[0] = 0x80000000;
        index = N;
    }

    private void twist() {
        int y;
        int kk = 0;

        for (; kk < N - M; kk++) {
            y = (state[kk] & UPPER_MASK) | (state[kk + 1] & LOWER_MASK);
            state[kk] = state[kk + M] ^ (y >>> 1) ^ ((y & 1) == 0 ? 0 : MATRIX_A);
        }
        for (; kk < N - 1; kk++) {
            y = (state[kk] & UPPER_MASK) | (state[kk + 1] & LOWER_MASK);
            state[kk] = state[kk + (M - N)] ^ (y >>> 1) ^ ((y & 1) == 0 ? 0 : MATRIX_A);
        }

        y = (state[N - 1] & UPPER_MASK) | (state[0] & LOWER_MASK);
        state[N - 1] = state[M - 1] ^ (y >>> 1) ^ ((y & 1) == 0 ? 0 : MATRIX_A);
        index = 0;
    }
}
