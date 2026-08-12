package net.krusher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans a ROM region for runs of a single repeated byte value, which is a
 * heuristic for unused/padding space (as opposed to genuine game data, which
 * essentially never contains long uniform byte runs -- compressed data in
 * particular would never decompress to a long uniform run without itself
 * being tiny). Candidates are written to an editable file so they can be
 * reviewed/pruned before being trusted as safe to overwrite.
 *
 * Only scans forward of a given start address: the text-insertion pointer
 * format can only reference addresses via small forward offsets, so space
 * before the script region is never usable regardless of whether it's free.
 */
public final class FreeSpaceScanner {

    private FreeSpaceScanner() {}

    static final int DEFAULT_MIN_RUN = 500;

    /**
     * usage: FreeSpaceScanner [romPath] [fromHexAddr] [outPath] [graphicsOffsetsPath] [minRunLength]
     * fromHexAddr defaults to the byte right after TextInserter's known script/gap region (0x1D8000),
     * since anything before that is already covered by TextInserter's default writable pool.
     */
    public static void main(String[] args) throws IOException {
        String romPath = args.length > 0 ? args[0] : "Soleil (Spain).md";
        String fromHex = args.length > 1 ? args[1] : "1d8000";
        String outPath = args.length > 2 ? args[2] : "free_space.txt";
        String graphicsOffsetsPath = args.length > 3 ? args[3] : "graphics_offsets.txt";
        int minRun = args.length > 4 ? Integer.parseInt(args[4]) : DEFAULT_MIN_RUN;

        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        int fromAddr = (int) Long.parseLong(fromHex.startsWith("0x") ? fromHex.substring(2) : fromHex, 16);
        List<int[]> excluded = loadExcludedRanges(graphicsOffsetsPath);

        List<Region> regions = scan(rom, fromAddr, rom.length, minRun, excluded);
        writeRegionsFile(regions, outPath);

        long total = 0;
        for (Region r : regions) total += r.length;
        System.out.println("Scanned 0x" + Integer.toHexString(fromAddr) + " - 0x" + Integer.toHexString(rom.length)
                + ", found " + regions.size() + " candidate region(s), " + total + " bytes total.");
        System.out.println("Written to: " + outPath);
    }

    public static final class Region {
        public final int start;
        public final int length;
        public final int fillByte;

        Region(int start, int length, int fillByte) {
            this.start = start;
            this.length = length;
            this.fillByte = fillByte;
        }

        public int end() { return start + length; }
    }

    /**
     * @param minRunLength  minimum run length (bytes) to be considered a candidate
     * @param excluded      known-used byte ranges (e.g. validated compressed graphics
     *                      blocks) to skip; a run overlapping any of these is dropped
     */
    public static List<Region> scan(byte[] rom, int fromAddr, int toAddr, int minRunLength, List<int[]> excluded) {
        List<Region> regions = new ArrayList<Region>();
        int i = fromAddr;
        while (i < toAddr) {
            int runStart = i;
            int b = rom[i] & 0xFF;
            int j = i + 1;
            while (j < toAddr && (rom[j] & 0xFF) == b) j++;
            int runLen = j - runStart;
            if (runLen >= minRunLength && !overlapsAny(runStart, j, excluded)) {
                regions.add(new Region(runStart, runLen, b));
            }
            i = j;
        }
        return regions;
    }

    private static boolean overlapsAny(int start, int end, List<int[]> excluded) {
        if (excluded == null) return false;
        for (int[] range : excluded) {
            if (start < range[1] && end > range[0]) return true;
        }
        return false;
    }

    /** Loads {offset,encSize} ranges from a graphics_offsets.txt-style file, for exclusion. */
    public static List<int[]> loadExcludedRanges(String graphicsOffsetsPath) throws IOException {
        List<int[]> ranges = new ArrayList<int[]>();
        if (graphicsOffsetsPath == null || !Files.exists(Paths.get(graphicsOffsetsPath))) {
            return ranges;
        }
        for (String line : Files.readAllLines(Paths.get(graphicsOffsetsPath), StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith(";")) continue;
            String[] parts = t.split(",");
            if (parts.length < 2) continue;
            String hexPart = parts[0].trim();
            if (hexPart.startsWith("0x") || hexPart.startsWith("0X")) hexPart = hexPart.substring(2);
            int offset = (int) Long.parseLong(hexPart, 16);
            int encSize = Integer.parseInt(parts[1].trim());
            ranges.add(new int[]{offset, offset + encSize});
        }
        return ranges;
    }

    public static void writeRegionsFile(List<Region> regions, String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("; Candidate free-space regions auto-detected by scanning for long runs of a\n");
        sb.append("; single repeated byte value. This is a heuristic, not a guarantee -- review\n");
        sb.append("; before trusting, and delete any line you're not sure is safe to overwrite.\n");
        sb.append("; Format: 0xSTART,length,0xFILLBYTE\n");
        for (Region r : regions) {
            sb.append("0x").append(Integer.toHexString(r.start))
              .append(',').append(r.length)
              .append(",0x").append(String.format("%02x", r.fillByte))
              .append('\n');
        }
        Files.write(Paths.get(path), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static List<Region> readRegionsFile(String path) throws IOException {
        List<Region> regions = new ArrayList<Region>();
        for (String line : Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith(";")) continue;
            String[] parts = t.split(",");
            if (parts.length < 3) continue;
            String hexPart = parts[0].trim();
            if (hexPart.startsWith("0x") || hexPart.startsWith("0X")) hexPart = hexPart.substring(2);
            int start = (int) Long.parseLong(hexPart, 16);
            int length = Integer.parseInt(parts[1].trim());
            String fillHex = parts[2].trim();
            if (fillHex.startsWith("0x") || fillHex.startsWith("0X")) fillHex = fillHex.substring(2);
            int fillByte = Integer.parseInt(fillHex, 16);
            regions.add(new Region(start, length, fillByte));
        }
        return regions;
    }
}
