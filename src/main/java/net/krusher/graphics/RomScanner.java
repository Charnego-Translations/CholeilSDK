package net.krusher.graphics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans a ROM for byte sequences that decode as well-formed LZ-Toshio
 * streams, and persists the results to an editable offsets file so
 * candidates can be pruned/added by hand before extraction.
 */
public final class RomScanner {

    private RomScanner() {}

    public static final class Candidate {
        public final int offset;
        public final int encSize;
        public final int decSize;

        Candidate(int offset, int encSize, int decSize) {
            this.offset = offset;
            this.encSize = encSize;
            this.decSize = decSize;
        }
    }

    /**
     * @param step        byte stride between scan attempts (2 = word-aligned only)
     * @param maxEncSize  reject candidates whose declared compressed size exceeds this
     * @param maxDecSize  reject candidates whose declared decompressed size exceeds this
     */
    public static List<Candidate> scan(byte[] rom, int step, int maxEncSize, int maxDecSize) {
        List<Candidate> found = new ArrayList<Candidate>();
        int limit = rom.length - 8;
        int offset = 0;
        while (offset <= limit) {
            LzToshio.Result r = LzToshio.tryDecompress(rom, offset, maxEncSize, maxDecSize);
            if (r != null) {
                found.add(new Candidate(offset, r.encSize, r.decSize));
                offset += r.encSize; // skip past the block: avoids nested/duplicate matches inside it
            } else {
                offset += step;
            }
        }
        return found;
    }

    public static void writeOffsetsFile(List<Candidate> candidates, String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("; LZ-Toshio candidate offsets auto-detected in the ROM.\n");
        sb.append("; Format: 0xOFFSET,encSize,decSize -- edit/delete lines as needed before extraction.\n");
        sb.append("; Lines starting with ';' are ignored.\n");
        for (Candidate c : candidates) {
            sb.append("0x").append(Integer.toHexString(c.offset))
              .append(',').append(c.encSize)
              .append(',').append(c.decSize)
              .append('\n');
        }
        Files.write(Paths.get(path), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static List<Integer> readOffsetsFile(String path) throws IOException {
        List<Integer> offsets = new ArrayList<Integer>();
        for (String line : Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith(";")) continue;
            String hexPart = t.split(",")[0].trim();
            if (hexPart.startsWith("0x") || hexPart.startsWith("0X")) hexPart = hexPart.substring(2);
            offsets.add((int) Long.parseLong(hexPart, 16));
        }
        return offsets;
    }
}
