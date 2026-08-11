package net.krusher.graphics;

/**
 * Decoder for the "LZ-Toshio" compression scheme used by several Mega Drive
 * titles (Crusader of Centy / Soleil among them). Ported from the reference
 * C++ implementation at https://github.com/lab313ru/lztoshio
 *
 * Stream layout:
 *   bytes 0-3: enc_size - 8 (little-endian) -- compressed size, header excluded
 *   bytes 4-7: dec_size (little-endian)     -- decompressed size
 *   byte 8+  : control-bit stream. Bits are consumed LSB-first from each
 *              command byte. Bit=1 -> next byte is a literal. Bit=0 -> next
 *              16-bit little-endian word is a back-reference token into a
 *              4096-byte circular window (12-bit distance, 4-bit length-3,
 *              so match length is 3..18).
 *
 * The window is pre-filled with a fixed pattern before decoding starts (see
 * {@link #initWindow()}) and the write cursor starts at 0xFEE, exactly as in
 * the reference tool.
 */
public final class LzToshio {

    private static final int WINDOW_SIZE = 1 << 12;
    private static final int WINDOW_MASK = WINDOW_SIZE - 1;

    private LzToshio() {}

    public static final class Result {
        /** Decompressed bytes. */
        public final byte[] data;
        /** Total compressed size, header included (i.e. bytes consumed from the ROM). */
        public final int encSize;
        /** Declared decompressed size. */
        public final int decSize;

        Result(byte[] data, int encSize, int decSize) {
            this.data = data;
            this.encSize = encSize;
            this.decSize = decSize;
        }
    }

    /**
     * Attempts to decompress a stream located at {@code offset} in {@code rom}.
     * Returns {@code null} if the bytes at {@code offset} are not a well-formed
     * LZ-Toshio stream: declared sizes outside the given bounds, a read past
     * the end of the ROM/declared stream, or a byte-count mismatch once the
     * control-bit stream is exhausted. Used both for ROM scanning (where the
     * input is untrusted) and for normal extraction.
     */
    public static Result tryDecompress(byte[] rom, int offset, int maxEncSize, int maxDecSize) {
        if (offset < 0 || offset + 8 > rom.length) return null;

        int declaredEnc = readU32LE(rom, offset);
        int declaredDec = readU32LE(rom, offset + 4);
        int encSize = declaredEnc + 8;

        if (declaredEnc <= 0 || encSize > maxEncSize) return null;
        if (declaredDec <= 0 || declaredDec > maxDecSize) return null;
        if (offset + encSize > rom.length) return null;

        byte[] window = initWindow();
        int wndOff = 0xFEE;
        byte[] out = new byte[declaredDec];
        int outOff = 0;
        int readOff = offset + 8;
        int streamEnd = offset + encSize;

        int cmd = 0;
        int bitsLeft = 1; // first read_cmd_bit call always forces a command-byte load

        while (readOff < streamEnd) {
            bitsLeft--;
            if (bitsLeft == 0) {
                if (readOff >= streamEnd) return null;
                cmd = rom[readOff++] & 0xFF;
                bitsLeft = 8;
            }
            int bit = cmd & 1;
            cmd >>= 1;

            if (bit == 1) {
                if (readOff >= streamEnd || outOff >= out.length) return null;
                byte b = rom[readOff++];
                out[outOff++] = b;
                window[wndOff] = b;
                wndOff = (wndOff + 1) & WINDOW_MASK;
            } else {
                if (readOff + 2 > streamEnd) return null;
                int t = (rom[readOff] & 0xFF) | ((rom[readOff + 1] & 0xFF) << 8);
                readOff += 2;
                int reps = ((t & 0x0F00) >> 8) + 3;
                int from = ((t & 0xF000) >> 4) | (t & 0xFF);
                for (int i = 0; i < reps; i++) {
                    if (outOff >= out.length) return null;
                    byte b = window[from & WINDOW_MASK];
                    from = (from + 1) & WINDOW_MASK;
                    out[outOff++] = b;
                    window[wndOff] = b;
                    wndOff = (wndOff + 1) & WINDOW_MASK;
                }
            }
        }

        if (outOff != declaredDec) return null;
        if (readOff != streamEnd) return null;

        return new Result(out, encSize, declaredDec);
    }

    /** Decompress, trusting the offset is already known-good (throws if not). */
    public static byte[] decompress(byte[] rom, int offset) {
        Result r = tryDecompress(rom, offset, Integer.MAX_VALUE, Integer.MAX_VALUE);
        if (r == null) {
            throw new IllegalArgumentException("Not a valid LZ-Toshio stream at offset 0x" + Integer.toHexString(offset));
        }
        return r.data;
    }

    private static byte[] initWindow() {
        byte[] w = new byte[WINDOW_SIZE];
        for (int i = 0; i < 0x100; i++) {
            for (int j = 0; j < 0x0D; j++) {
                w[i * 0x0D + j] = (byte) i;
            }
        }
        for (int i = 0; i < 0x100; i++) w[0xD00 + i] = (byte) i;
        for (int i = 0; i < 0x100; i++) w[0xE00 + i] = (byte) (0xFF - i);
        for (int i = 0; i < 0x80; i++) w[0xF00 + i] = 0x00;
        for (int i = 0; i < 0x6E; i++) w[0xF80 + i] = 0x20;
        for (int i = 0; i < 0x12; i++) w[0xFEE + i] = 0x00;
        return w;
    }

    private static int readU32LE(byte[] data, int off) {
        return (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8)
             | ((data[off + 2] & 0xFF) << 16) | ((data[off + 3] & 0xFF) << 24);
    }
}
