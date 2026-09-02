package net.krusher.graphics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Friendly extractor/adapter for Sonic lying in the hammock at Anemone Beach.
 *
 * The ROM stores one compressed 108-tile block at 0x05EBE8. It contains three
 * complete 48x48 poses, but groups all top quadrants first and all bottom
 * quadrants afterwards. This adapter exposes them as three normal frames,
 * side by side, then maps an edited PNG back to the SDK's ordinary compressed
 * tile sheet so GraphicsInserter can keep handling compression and relocation.
 */
public final class SonicHammockGraphics {
    public static final int BLOCK_OFFSET = 0x05EBE8;
    public static final int TILE_BYTES = 32;
    public static final int FRAME_TILES = 36;
    public static final int FRAME_COUNT = 3;
    public static final int TILE_COUNT = FRAME_TILES * FRAME_COUNT;
    public static final int EDIT_WIDTH = 144;
    public static final int EDIT_HEIGHT = 48;

    public static final String DEFAULT_EDIT = "special_gfx_out/sonic_hamaca_EDITAME.png";
    public static final String DEFAULT_VIEW = "special_gfx_out/sonic_hamaca_x4_VISTA.png";
    public static final String DEFAULT_GFX = "gfx_out/gfx_05ebe8.png";

    // Captured from CRAM palette line 3 in the confirmed Anemone Beach state.
    private static final byte[] CRAM = {
        0x06, 0x62, 0x00, 0x00, 0x0A, 0x22, 0x0C, 0x42,
        0x0E, 0x44, 0x0E, 0x66, 0x0E, (byte) 0xEE, 0x0A, (byte) 0xAA,
        0x08, (byte) 0x88, 0x04, 0x44, 0x08, (byte) 0xAE, 0x04, 0x6A,
        0x00, 0x0E, 0x00, 0x08, 0x00, (byte) 0xAE, 0x00, (byte) 0x8E,
    };

    private SonicHammockGraphics() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("usage:");
            System.out.println("  SonicHammockGraphics extract [rom] [editPng] [viewPng]");
            System.out.println("  SonicHammockGraphics sync [editPng] [gfxPng]");
            System.out.println("  SonicHammockGraphics verify [rom]");
            return;
        }
        String mode = args[0];
        if (mode.equals("extract")) {
            extract(args.length > 1 ? args[1] : "Soleil (Spain).md",
                    args.length > 2 ? args[2] : DEFAULT_EDIT,
                    args.length > 3 ? args[3] : DEFAULT_VIEW);
        } else if (mode.equals("sync")) {
            sync(args.length > 1 ? args[1] : DEFAULT_EDIT,
                    args.length > 2 ? args[2] : DEFAULT_GFX);
        } else if (mode.equals("verify")) {
            verify(args.length > 1 ? args[1] : "Soleil (Spain).md");
        }
        else throw new IllegalArgumentException("unknown mode: " + mode);
    }

    public static void extract(String romPath, String editPath, String viewPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        byte[] storage = LzToshio.decompress(rom, BLOCK_OFFSET);
        if (storage.length != TILE_COUNT * TILE_BYTES) {
            throw new IllegalStateException(String.format(
                    "Sonic/hammock block 0x%X has %d bytes, expected %d",
                    BLOCK_OFFSET, storage.length, TILE_COUNT * TILE_BYTES));
        }
        byte[] logical = storageToLogical(storage);

        Path edit = Paths.get(editPath);
        Path view = Paths.get(viewPath);
        if (edit.getParent() != null) Files.createDirectories(edit.getParent());
        if (view.getParent() != null) Files.createDirectories(view.getParent());

        TileRenderer.writePng(
                TileRenderer.renderSpriteSheet(logical, editPalette(), 6, 6, 3, 1, true), edit.toString());
        TileRenderer.writePng(
                TileRenderer.renderSpriteSheet(logical, viewPalette(), 6, 6, 3, 4, true), view.toString());
        System.out.println("Extracted Sonic + hammock: " + edit + " (3 frames, 48x48 each)");
    }

    /** Converts the friendly edit PNG into the ordinary 16-column gfx_out tile sheet. */
    public static void sync(String editPath, String gfxPath) throws IOException {
        Path edit = Paths.get(editPath);
        if (!Files.exists(edit)) {
            System.out.println("Sonic + hammock edit PNG not found; leaving block 0x"
                    + Integer.toHexString(BLOCK_OFFSET) + " untouched.");
            return;
        }
        Bitmap image = TileRenderer.readPng(edit.toString());
        if (image.getWidth() != EDIT_WIDTH || image.getHeight() != EDIT_HEIGHT) {
            throw new IllegalStateException(edit + " must stay " + EDIT_WIDTH + "x" + EDIT_HEIGHT
                    + " (three 48x48 frames side by side)");
        }
        byte[] logical = TileRenderer.decodeSpriteSheet(
                image, editPalette(), 6, 6, 3, 1, TILE_COUNT, true);
        byte[] storage = logicalToStorage(logical);
        Path gfx = Paths.get(gfxPath);
        if (Files.exists(gfx)) {
            Bitmap current = TileRenderer.readPng(gfx.toString());
            if (current.getWidth() == 128 && current.getHeight() == 56) {
                byte[] currentStorage = TileRenderer.decodeTileSheet(
                        current, TileRenderer.defaultGrayscalePalette(), 16, 1, TILE_COUNT);
                if (Arrays.equals(storage, currentStorage)) {
                    System.out.println("Sonic + hammock edit is unchanged; keeping " + gfx + " byte-for-byte.");
                    return;
                }
            }
        }
        Bitmap sdkSheet = TileRenderer.renderTileSheet(
                storage, TileRenderer.defaultGrayscalePalette(), 16, 1);
        if (gfx.getParent() != null) Files.createDirectories(gfx.getParent());
        TileRenderer.writePng(sdkSheet, gfx.toString());
        System.out.println("Synced Sonic + hammock edit into " + gfx);
    }

    public static void verify(String romPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        byte[] original = LzToshio.decompress(rom, BLOCK_OFFSET);
        byte[] logical = storageToLogical(original);
        Bitmap editImage = TileRenderer.renderSpriteSheet(logical, editPalette(), 6, 6, 3, 1, true);
        byte[] decodedEdit = TileRenderer.decodeSpriteSheet(
                editImage, editPalette(), 6, 6, 3, 1, TILE_COUNT, true);
        byte[] roundTrip = logicalToStorage(decodedEdit);
        if (!Arrays.equals(original, roundTrip)) {
            throw new IllegalStateException("Sonic/hammock tile mapping is not byte-exact");
        }
        System.out.println("Sonic + hammock mapping round-trip: byte-identical ("
                + original.length + " bytes, " + TILE_COUNT + " tiles)");
    }

    static byte[] storageToLogical(byte[] storage) {
        byte[] logical = new byte[storage.length];
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            for (int row = 0; row < 6; row++) {
                for (int col = 0; col < 6; col++) {
                    int quadrant = (row / 3) * 2 + (col / 3);
                    int localRow = row % 3;
                    int localCol = col % 3;
                    int sourceSprite = sourceSprite(frame, quadrant);
                    int sourceTile = sourceSprite * 9 + localCol * 3 + localRow;
                    int logicalTile = frame * FRAME_TILES + row * 6 + col;
                    copyTile(storage, sourceTile, logical, logicalTile);
                }
            }
        }
        return logical;
    }

    static byte[] logicalToStorage(byte[] logical) {
        byte[] storage = new byte[logical.length];
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            for (int row = 0; row < 6; row++) {
                for (int col = 0; col < 6; col++) {
                    int quadrant = (row / 3) * 2 + (col / 3);
                    int localRow = row % 3;
                    int localCol = col % 3;
                    int sourceSprite = sourceSprite(frame, quadrant);
                    int sourceTile = sourceSprite * 9 + localCol * 3 + localRow;
                    int logicalTile = frame * FRAME_TILES + row * 6 + col;
                    copyTile(logical, logicalTile, storage, sourceTile);
                }
            }
        }
        return storage;
    }

    private static int sourceSprite(int frame, int quadrant) {
        // ROM order: TL/TR for all 3 frames, then BL/BR for all 3 frames.
        return quadrant < 2 ? frame * 2 + quadrant : 6 + frame * 2 + (quadrant - 2);
    }

    private static void copyTile(byte[] from, int fromTile, byte[] to, int toTile) {
        System.arraycopy(from, fromTile * TILE_BYTES, to, toTile * TILE_BYTES, TILE_BYTES);
    }

    private static int[] editPalette() {
        int[] palette = TileRenderer.readGenesisPalette(CRAM, 0);
        palette[0] = 0xFFFF00FF; // transparent sprite index, made magenta so it cannot be confused with black outlines
        return palette;
    }

    private static int[] viewPalette() {
        int[] palette = TileRenderer.readGenesisPalette(CRAM, 0);
        // Keep it distinct from the real black at index 1: duplicate PLTE
        // colours can be collapsed by image software and shift the other indices.
        palette[0] = 0xFF010101; // visual-only approximation of transparency on a black crop
        return palette;
    }
}
