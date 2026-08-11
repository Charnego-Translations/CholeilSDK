package net.krusher.graphics;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * CLI for finding and dumping LZ-Toshio-compressed graphics blocks.
 *
 * Usage:
 *   scan    [romPath] [offsetsPath]           - auto-detect candidate blocks, write them to an editable offsets file
 *   extract [romPath] [offsetsPath] [outDir]  - decompress each listed offset and render a PNG tile sheet
 */
public class GraphicsExtractor {

    static final int DEFAULT_MAX_ENC_SIZE = 0x8000;  // 32KB compressed
    static final int DEFAULT_MAX_DEC_SIZE = 0x20000;  // 128KB decompressed
    static final int DEFAULT_SCAN_STEP = 1;           // blocks are packed back-to-back, not word-aligned

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            printUsage();
            return;
        }

        String mode = args[0];
        if (mode.equals("scan")) {
            String romPath = args.length > 1 ? args[1] : "Soleil (Spain).md";
            String offsetsPath = args.length > 2 ? args[2] : "graphics_offsets.txt";
            scan(romPath, offsetsPath);
        } else if (mode.equals("extract")) {
            String romPath = args.length > 1 ? args[1] : "Soleil (Spain).md";
            String offsetsPath = args.length > 2 ? args[2] : "graphics_offsets.txt";
            String outDir = args.length > 3 ? args[3] : "gfx_out";
            extract(romPath, offsetsPath, outDir);
        } else {
            printUsage();
        }
    }

    static void scan(String romPath, String offsetsPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        List<RomScanner.Candidate> candidates = RomScanner.scan(rom, DEFAULT_SCAN_STEP, DEFAULT_MAX_ENC_SIZE, DEFAULT_MAX_DEC_SIZE);
        RomScanner.writeOffsetsFile(candidates, offsetsPath);
        System.out.println("Scanned " + rom.length + " bytes, found " + candidates.size() + " candidate LZ-Toshio blocks.");
        System.out.println("Offsets written to: " + offsetsPath);
    }

    static void extract(String romPath, String offsetsPath, String outDir) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        List<Integer> offsets = RomScanner.readOffsetsFile(offsetsPath);
        Path outPath = Paths.get(outDir);
        Files.createDirectories(outPath);

        int[] palette = TileRenderer.defaultGrayscalePalette();
        int ok = 0, failed = 0;

        for (int offset : offsets) {
            LzToshio.Result r = LzToshio.tryDecompress(rom, offset, Integer.MAX_VALUE, Integer.MAX_VALUE);
            if (r == null) {
                System.out.println("SKIP 0x" + Integer.toHexString(offset) + ": no longer decodes as a valid LZ-Toshio stream");
                failed++;
                continue;
            }
            BufferedImage img = TileRenderer.renderTileSheet(r.data, palette, 16, 2);
            String fileName = String.format("gfx_%06x.png", offset);
            TileRenderer.writePng(img, outPath.resolve(fileName).toString());
            ok++;
        }
        System.out.println("Extracted " + ok + " tile sheets to " + outDir + " (" + failed + " skipped)");
    }

    static void printUsage() {
        System.out.println("usage:");
        System.out.println("  scan    [romPath] [offsetsPath]           - auto-detect LZ-Toshio blocks in the ROM");
        System.out.println("  extract [romPath] [offsetsPath] [outDir]  - decompress each listed offset and render a PNG tile sheet");
    }
}
