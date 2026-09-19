package net.krusher.graphics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** Friendly editors for the confirmed 16x16 normal and 24x24 golden apples. */
public final class AppleGraphics {
    private static final class Copy {
        final int blockOffset;
        final int[] tileIndices;
        final int blockTileCount;
        final int pointerField;
        final int pointerBase;
        final int sheetPaletteOffset;
        final int[] gamePaletteWords;

        Copy(int blockOffset, int[] tileIndices, int blockTileCount, int pointerField,
             int pointerBase, int sheetPaletteOffset, int[] gamePaletteWords) {
            this.blockOffset = blockOffset;
            this.tileIndices = tileIndices;
            this.blockTileCount = blockTileCount;
            this.pointerField = pointerField;
            this.pointerBase = pointerBase;
            this.sheetPaletteOffset = sheetPaletteOffset;
            this.gamePaletteWords = gamePaletteWords;
        }

        String gfxPath() {
            return String.format("gfx_out/gfx_%06x.png", blockOffset);
        }
    }

    /** Row-major tile positions. The later zones keep rows 16 tiles apart. */
    private static final Copy[] MAP_COPIES = {
            new Copy(0x135322, new int[]{459, 460, 461, 462}, 496,
                    0x12002C, 0x120000, 0, null),
            new Copy(0x14D3AE, new int[]{390, 391, 392, 393}, 480,
                    0x120058, 0x120000, 0, null),
            new Copy(0x12FE58, new int[]{392, 393, 408, 409}, 512,
                    0x120024, 0x120000, 0, new int[]{
                    0x04CA, 0x0EEE, 0x0888, 0x026E, 0x002E, 0x0000, 0x0C84, 0x002C,
                    0x0466, 0x04AA, 0x068A, 0x0666, 0x04A6, 0x04CA, 0x0468, 0x08EE}),
            new Copy(0x1260CA, new int[]{138, 139, 154, 155}, 640,
                    0x120010, 0x120000, 0, new int[]{
                    0x04CA, 0x0EEE, 0x0888, 0x048E, 0x026A, 0x0444, 0x0A60, 0x0C84,
                    0x0466, 0x06AA, 0x068A, 0x0664, 0x04A6, 0x04CA, 0x0468, 0x08EE})
    };
    /** This map mirrors one 8-pixel column, so it needs a symmetric editor. */
    private static final Copy MIRRORED_MAP_COPY = new Copy(0x1528E4,
            new int[]{328, 391}, 512, 0x120068, 0x120000, 0,
            new int[]{0x024C, 0x0244, 0x0466, 0x0468, 0x048A, 0x0642, 0x0666,
                    0x08CE, 0x088C, 0x04AE, 0x0484, 0x06CA, 0x006C, 0x0466,
                    0x046A, 0x068C});
    /** Same old apple artwork, but drawn over solid map colour 8 with shadow 13. */
    private static final Copy EMBEDDED_MAP_COPY = new Copy(0x132946,
            new int[]{350, 351, 366, 367}, 480, 0x120028, 0x120000, 0, null);
    /** The 0xF4800 stream supplies the on-screen 16x16 pickup sprite. */
    private static final Copy SPRITE_PICKUP_COPY = new Copy(0x0F4800,
            new int[]{0, 2, 1, 3}, 69, 0x03123E, -1, 0x000548, null);

    private static final int TILES_W = 2;
    private static final int TILES_H = 2;
    private static final int TILE_COUNT = TILES_W * TILES_H;
    private static final int WIDTH = TILES_W * 8;
    private static final int HEIGHT = TILES_H * 8;
    private static final int TOWN_PICKUP_BLOCK = 0x0F4600;
    private static final int TOWN_PICKUP_LENGTH = 512;
    private static final int TOWN_PICKUP_SPRITES = 4;
    private static final int TOWN_PALETTE_OFFSET = 0x000548;
    private static final int[] RAW_APPLE_OFFSETS = {0x100, 0x180};
    private static final int GOLDEN_BLOCK = 0x0A5644;
    private static final int GOLDEN_BLOCK_TILE_COUNT = 36;
    private static final int[] GOLDEN_EDIT_FRAME_TILES = {9, 27};
    private static final int[] GOLDEN_GAME_FRAME_TILES = {0, 9, 18, 27};
    private static final int GOLDEN_TILES_W = 3;
    private static final int GOLDEN_TILES_H = 3;
    private static final int GOLDEN_FRAME_TILE_COUNT = GOLDEN_TILES_W * GOLDEN_TILES_H;
    private static final int GOLDEN_WIDTH = GOLDEN_TILES_W * 8 * GOLDEN_EDIT_FRAME_TILES.length;
    private static final int GOLDEN_HEIGHT = GOLDEN_TILES_H * 8;

    public static final String DEFAULT_RED_EDIT = "apple_gfx_out/manzana_roja_EDITAME.png";
    public static final String DEFAULT_RED_VIEW = "apple_gfx_out/manzana_roja_x8_VISTA.png";
    public static final String DEFAULT_GREEN_EDIT = "apple_gfx_out/manzana_verde_EDITAME.png";
    public static final String DEFAULT_GREEN_VIEW = "apple_gfx_out/manzana_verde_x8_VISTA.png";
    public static final String DEFAULT_GOLDEN_EDIT = "apple_gfx_out/manzana_dorada_EDITAME.png";
    public static final String DEFAULT_GOLDEN_VIEW = "apple_gfx_out/manzana_dorada_x8_VISTA.png";
    public static final String DEFAULT_MIRRORED_EDIT = "apple_gfx_out/manzana_simetrica_EDITAME.png";
    public static final String TOWN_PICKUP_SHEET = "sprite_gfx_out/sprite_0f4600.png";

    /* Captured CRAM line used by the map apple in QuickSave3. */
    private static final int[] CAPTURED_PALETTE = {
            0xFF000000, 0xFF442244, 0xFF6666EE, 0xFFCC4444,
            0xFFEEEE66, 0xFFEE8866, 0xFFEECC88, 0xFFEEAA00,
            0xFFAAAAEE, 0xFF88CC00, 0xFF44AA00, 0xFF446600,
            0xFF882222, 0xFF0044CC, 0xFF222222, 0xFFEEEEEE
    };

    private AppleGraphics() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("usage:");
            System.out.println("  AppleGraphics extract-red [rom] [editPng] [viewPng]");
            System.out.println("  AppleGraphics extract-green [rom] [editPng] [viewPng]");
            System.out.println("  AppleGraphics extract-golden [rom] [editPng] [viewPng]");
            System.out.println("  AppleGraphics sync-red [rom] [editPng]");
            System.out.println("  AppleGraphics sync-green [rom] [editPng]");
            System.out.println("  AppleGraphics sync-golden [rom] [editPng]");
            System.out.println("  AppleGraphics verify [rom] [redPng] [greenPng]");
            System.out.println("  AppleGraphics verify-all [rom]");
            System.out.println("  AppleGraphics verify-golden [rom] [editPng]");
            System.out.println("  AppleGraphics seed-symmetric [editPng]");
            return;
        }

        String mode = args[0];
        String rom = args.length > 1 ? args[1] : "Soleil (Spain).md";
        boolean goldenMode = mode.contains("golden");
        boolean greenMode = mode.contains("green");
        String edit = args.length > 2 ? args[2]
                : goldenMode ? DEFAULT_GOLDEN_EDIT
                : greenMode ? DEFAULT_GREEN_EDIT : DEFAULT_RED_EDIT;
        String view = args.length > 3 ? args[3]
                : goldenMode ? DEFAULT_GOLDEN_VIEW
                : greenMode ? DEFAULT_GREEN_VIEW : DEFAULT_RED_VIEW;
        if (mode.equals("extract-red")) {
            writeFriendly(rom, MAP_COPIES[0], edit, view, "red map apple", false);
        } else if (mode.equals("extract-green")) {
            writeFriendly(rom, MAP_COPIES[0], edit, view, "green pickup apple", true);
        } else if (mode.equals("extract-golden")) {
            extractGolden(rom, edit, view);
        } else if (mode.equals("sync-red")) {
            syncRed(rom, edit);
        } else if (mode.equals("sync-green")) {
            syncGreen(rom, edit);
        } else if (mode.equals("sync-golden")) {
            syncGolden(rom, edit);
        } else if (mode.equals("verify")) {
            String green = args.length > 3 ? args[3] : DEFAULT_GREEN_EDIT;
            verify(rom, edit, green);
        } else if (mode.equals("verify-golden")) {
            verifyGolden(rom, edit);
        } else if (mode.equals("verify-all")) {
            verifyAvailable(rom);
        } else if (mode.equals("seed-symmetric")) {
            seedSymmetric(args.length > 1 ? args[1] : DEFAULT_MIRRORED_EDIT);
        } else {
            throw new IllegalArgumentException("unknown mode: " + mode);
        }
    }

    public static void extractRed(String romPath, String editPath, String viewPath) throws IOException {
        writeFriendly(romPath, MAP_COPIES[0], editPath, viewPath, "red map apple", false);
    }

    public static void extractGreen(String romPath, String editPath, String viewPath) throws IOException {
        writeFriendly(romPath, MAP_COPIES[0], editPath, viewPath, "green pickup apple", true);
    }

    /** Extracts the two real 3x3 golden-apple animation frames. */
    public static void extractGolden(String romPath, String editPath, String viewPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        byte[] block = LzToshio.decompress(rom, GOLDEN_BLOCK);
        byte[] golden = readGoldenFrames(block);
        int[] palette = goldenEditPalette(rom);

        Path edit = Paths.get(editPath);
        Path view = Paths.get(viewPath);
        if (edit.getParent() != null) Files.createDirectories(edit.getParent());
        if (view.getParent() != null) Files.createDirectories(view.getParent());
        TileRenderer.writePng(TileRenderer.renderSpriteSheet(golden, palette,
                GOLDEN_TILES_W, GOLDEN_TILES_H, GOLDEN_EDIT_FRAME_TILES.length, 1, false), edit.toString());
        TileRenderer.writePng(TileRenderer.renderSpriteSheet(golden, palette,
                GOLDEN_TILES_W, GOLDEN_TILES_H, GOLDEN_EDIT_FRAME_TILES.length, 8, false), view.toString());
        System.out.println("Extracted golden apple: " + edit
                + " (48x24, two column-major 24x24 frames)");
    }

    private static void writeFriendly(String romPath, Copy source, String editPath,
                                      String viewPath, String label, boolean green) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        byte[] apple = readApple(rom, source);
        if (green) apple = recolorGreen(apple);

        Path edit = Paths.get(editPath);
        Path view = Paths.get(viewPath);
        if (edit.getParent() != null) Files.createDirectories(edit.getParent());
        if (view.getParent() != null) Files.createDirectories(view.getParent());

        TileRenderer.writePng(TileRenderer.renderSpriteSheet(apple, editPalette(),
                TILES_W, TILES_H, 1, 1, true), edit.toString());
        TileRenderer.writePng(TileRenderer.renderSpriteSheet(apple, viewPalette(),
                TILES_W, TILES_H, 1, 8, true), view.toString());
        System.out.println("Extracted " + label + ": " + edit + " (16x16, row-major)");
    }

    /** Copies the red edit into every confirmed map and pickup-sprite route. */
    public static void syncRed(String romPath, String editPath) throws IOException {
        sync(romPath, editPath, "map apple", MAP_COPIES);
        sync(romPath, editPath, "pickup sprite apple", new Copy[]{SPRITE_PICKUP_COPY});
        syncEmbedded(romPath, editPath);
        syncSymmetric(romPath, DEFAULT_MIRRORED_EDIT);
    }

    /** Copies the green edit into the two raw pickup-object slots. */
    public static void syncGreen(String romPath, String editPath) throws IOException {
        Path edit = Paths.get(editPath);
        if (!Files.exists(edit)) {
            System.out.println("Green-apple edit PNG not found; leaving raw pickup copies untouched.");
            return;
        }
        Bitmap image = TileRenderer.readPng(edit.toString());
        if (image.getWidth() != WIDTH || image.getHeight() != HEIGHT) {
            throw new IllegalStateException(edit + " must stay " + WIDTH + "x" + HEIGHT);
        }
        byte[] green = TileRenderer.decodeSpriteSheet(image, editPalette(),
                TILES_W, TILES_H, 1, 1, TILE_COUNT, true);
        syncRawPickupCopies(Files.readAllBytes(Paths.get(romPath)), green);
    }

    /** Places the independent two-frame golden edit in its compressed tileset. */
    public static void syncGolden(String romPath, String editPath) throws IOException {
        Path edit = Paths.get(editPath);
        if (!Files.exists(edit)) {
            System.out.println("Golden-apple edit PNG not found; leaving its figure untouched.");
            return;
        }

        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        Bitmap image = TileRenderer.readPng(edit.toString());
        if (image.getWidth() != GOLDEN_WIDTH || image.getHeight() != GOLDEN_HEIGHT) {
            throw new IllegalStateException(edit + " must stay "
                    + GOLDEN_WIDTH + "x" + GOLDEN_HEIGHT);
        }
        byte[] golden = TileRenderer.decodeSpriteSheet(image, goldenEditPalette(rom),
                GOLDEN_TILES_W, GOLDEN_TILES_H, GOLDEN_EDIT_FRAME_TILES.length, 1,
                GOLDEN_FRAME_TILE_COUNT * GOLDEN_EDIT_FRAME_TILES.length, false);

        Path sheetPath = Paths.get(String.format("gfx_out/gfx_%06x.png", GOLDEN_BLOCK));
        byte[] current = LzToshio.decompress(rom, GOLDEN_BLOCK);
        int[] romPalette = TileRenderer.readGenesisPalette(rom, TOWN_PALETTE_OFFSET);
        if (Files.exists(sheetPath)) {
            Bitmap sheet = TileRenderer.readPng(sheetPath.toString());
            if (sheet.getWidth() != 128 || sheet.getHeight() != 24) {
                throw new IllegalStateException(sheetPath + " must stay 128x24");
            }
            current = TileRenderer.decodeTileSheet(sheet, romPalette, 16, 1,
                    GOLDEN_BLOCK_TILE_COUNT);
        }

        byte[] updated = Arrays.copyOf(current, current.length);
        for (int frame = 0; frame < GOLDEN_GAME_FRAME_TILES.length; frame++) {
            System.arraycopy(golden, (frame / 2) * GOLDEN_FRAME_TILE_COUNT * TileRenderer.TILE_BYTES,
                    updated, GOLDEN_GAME_FRAME_TILES[frame] * TileRenderer.TILE_BYTES,
                    GOLDEN_FRAME_TILE_COUNT * TileRenderer.TILE_BYTES);
        }
        if (Arrays.equals(updated, current)) {
            System.out.println("Golden-apple edit is unchanged in " + sheetPath);
            return;
        }
        if (sheetPath.getParent() != null) Files.createDirectories(sheetPath.getParent());
        TileRenderer.writePng(TileRenderer.renderTileSheet(updated, romPalette, 16, 1),
                sheetPath.toString());
        System.out.println("Synced two golden-apple editor poses into all four game frames"
                + " (tiles 0-35 of block 0xA5644)");
    }

    private static void sync(String romPath, String editPath, String label,
                             Copy[] copies) throws IOException {
        Path edit = Paths.get(editPath);
        if (!Files.exists(edit)) {
            System.out.println(label + " edit PNG not found; leaving its tilesets untouched.");
            return;
        }

        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        Bitmap image = TileRenderer.readPng(edit.toString());
        if (image.getWidth() != WIDTH || image.getHeight() != HEIGHT) {
            throw new IllegalStateException(edit + " must stay " + WIDTH + "x" + HEIGHT);
        }
        byte[] apple = TileRenderer.decodeSpriteSheet(image, editPalette(),
                TILES_W, TILES_H, 1, 1, TILE_COUNT, true);

        for (Copy copy : copies) {
            syncCopy(rom, copy, remapToGamePalette(apple, copy), label);
        }
    }

    private static void syncCopy(byte[] rom, Copy copy, byte[] tiles, String label) throws IOException {
        Path gfx = Paths.get(copy.gfxPath());
        byte[] current = LzToshio.decompress(rom, copy.blockOffset);
        if (current.length != copy.blockTileCount * TileRenderer.TILE_BYTES) {
            throw new IllegalStateException(String.format(
                    "%s block 0x%X has %d bytes, expected %d", label,
                    copy.blockOffset, current.length,
                    copy.blockTileCount * TileRenderer.TILE_BYTES));
        }
        int[] sheetPalette = copy.sheetPaletteOffset == 0
                ? TileRenderer.defaultGrayscalePalette()
                : TileRenderer.readGenesisPalette(rom, copy.sheetPaletteOffset);
        if (Files.exists(gfx)) {
            Bitmap sheet = TileRenderer.readPng(gfx.toString());
            int expectedHeight = ((copy.blockTileCount + 15) / 16) * 8;
            if (sheet.getWidth() != 128 || sheet.getHeight() != expectedHeight) {
                throw new IllegalStateException(gfx + " must stay 128x" + expectedHeight);
            }
            current = TileRenderer.decodeTileSheet(sheet, sheetPalette, 16, 1, copy.blockTileCount);
        }
        if (tiles.length != copy.tileIndices.length * TileRenderer.TILE_BYTES) {
            throw new IllegalArgumentException("wrong tile count for " + label);
        }
        byte[] updated = Arrays.copyOf(current, current.length);
        for (int i = 0; i < copy.tileIndices.length; i++) {
            System.arraycopy(tiles, i * TileRenderer.TILE_BYTES, updated,
                    copy.tileIndices[i] * TileRenderer.TILE_BYTES, TileRenderer.TILE_BYTES);
        }
        if (Arrays.equals(updated, current)) {
            System.out.println(label + " edit is unchanged in " + gfx);
            return;
        }
        if (gfx.getParent() != null) Files.createDirectories(gfx.getParent());
        TileRenderer.writePng(TileRenderer.renderTileSheet(updated, sheetPalette, 16, 1),
                gfx.toString());
        System.out.println(String.format("Synced %s into %s (block 0x%X)",
                label, gfx, copy.blockOffset));
    }

    private static byte[] remapToGamePalette(byte[] source, Copy copy) {
        if (copy.gamePaletteWords == null) return source;
        int[] from = editPalette();
        int[] to = genesisPalette(copy.gamePaletteWords);
        int[] mapping = new int[16];
        mapping[0] = 0; // colour zero is transparent in these map tiles
        for (int i = 1; i < 16; i++) {
            int best = 1, bestDistance = Integer.MAX_VALUE;
            for (int j = 1; j < 16; j++) {
                int a = from[i], b = to[j];
                int dr = (a >> 16 & 255) - (b >> 16 & 255);
                int dg = (a >> 8 & 255) - (b >> 8 & 255);
                int db = (a & 255) - (b & 255);
                int distance = dr * dr + dg * dg + db * db;
                if (distance < bestDistance) { bestDistance = distance; best = j; }
            }
            mapping[i] = best;
        }
        byte[] remapped = new byte[source.length];
        for (int i = 0; i < source.length; i++) {
            int value = source[i] & 255;
            remapped[i] = (byte) (mapping[value >> 4] << 4 | mapping[value & 15]);
        }
        return remapped;
    }

    private static byte[] composeEmbedded(byte[] vanilla, byte[] edit) {
        byte[] result = new byte[edit.length];
        for (int i = 0; i < edit.length; i++) {
            int original = vanilla[i] & 255, replacement = edit[i] & 255;
            int hi = replacement >> 4, lo = replacement & 15;
            int originalHi = original >> 4, originalLo = original & 15;
            // Preserve only the map background and existing shadow; erase the old apple.
            int outHi = hi != 0 ? hi : (originalHi == 13 ? 13 : 8);
            int outLo = lo != 0 ? lo : (originalLo == 13 ? 13 : 8);
            result[i] = (byte) (outHi << 4 | outLo);
        }
        return result;
    }

    private static void syncEmbedded(String romPath, String editPath) throws IOException {
        if (!Files.exists(Paths.get(editPath))) return;
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        syncCopy(rom, EMBEDDED_MAP_COPY,
                composeEmbedded(readApple(rom, EMBEDDED_MAP_COPY), decodeNormalEdit(editPath)),
                "background-embedded map apple");
    }

    private static int[] genesisPalette(int[] words) {
        int[] palette = new int[words.length];
        for (int i = 0; i < words.length; i++) {
            int word = words[i];
            int r = (word >> 1 & 7) * 34;
            int g = (word >> 5 & 7) * 34;
            int b = (word >> 9 & 7) * 34;
            palette[i] = 0xFF000000 | r << 16 | g << 8 | b;
        }
        return palette;
    }

    /** A compact upright drumstick, whose left half is mirrored by the Sevilla map. */
    private static void seedSymmetric(String path) throws IOException {
        Path output = Paths.get(path);
        if (Files.exists(output)) {
            throw new IllegalStateException(output + " already exists; refusing to overwrite an edit");
        }
        String[] rows = {
                "00000000", "00000EEE", "0000E555", "00EE5333",
                "0E533333", "E5333333", "E5333333", "E5333333",
                "0E5CCCCC", "00ECCCCE", "000EE66E", "00000E6F",
                "00000E6F", "0000EEF6", "0000EFFF", "00000EEE"
        };
        Bitmap image = Bitmap.indexed(8, 16, editPalette());
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < 8; x++) {
                image.setIndex(x, y, Character.digit(rows[y].charAt(x), 16));
            }
        }
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        TileRenderer.writePng(image, output.toString());
        System.out.println("Created mirrored-map drumstick editor " + output + " (8x16)");
    }

    private static byte[] decodeSymmetricEdit(String path) throws IOException {
        Bitmap image = TileRenderer.readPng(path);
        if (image.getWidth() != 8 || image.getHeight() != 16) {
            throw new IllegalStateException(path + " must stay 8x16");
        }
        return TileRenderer.decodeTileSheet(image, editPalette(), 1, 1, 2);
    }

    private static void syncSymmetric(String romPath, String editPath) throws IOException {
        if (!Files.exists(Paths.get(editPath))) {
            throw new IllegalStateException("missing mirrored-map editor " + editPath);
        }
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        syncCopy(rom, MIRRORED_MAP_COPY,
                remapToGamePalette(decodeSymmetricEdit(editPath), MIRRORED_MAP_COPY),
                "mirrored map apple");
    }

    /**
     * Choleil's pickup strip is copied as one 512-byte run to VRAM
     * 0x7F0-0x7FF. Its last two 16x16 sprites are column-major copies of the
     * same red apple used by the compressed map tilesets.
     */
    private static void syncRawPickupCopies(byte[] rom, byte[] rowMajorApple) throws IOException {
        Path sheetPath = Paths.get(TOWN_PICKUP_SHEET);
        byte[] current = Arrays.copyOfRange(rom, TOWN_PICKUP_BLOCK,
                TOWN_PICKUP_BLOCK + TOWN_PICKUP_LENGTH);
        int[] palette = TileRenderer.readGenesisPalette(rom, TOWN_PALETTE_OFFSET);

        if (Files.exists(sheetPath)) {
            Bitmap sheet = TileRenderer.readPng(sheetPath.toString());
            if (sheet.getWidth() != 64 || sheet.getHeight() != 16) {
                throw new IllegalStateException(TOWN_PICKUP_SHEET
                        + " must stay 64x16 (four 16x16 sprites)");
            }
            current = TileRenderer.decodeSpriteSheet(sheet, palette,
                    TILES_W, TILES_H, TOWN_PICKUP_SPRITES, 1,
                    TOWN_PICKUP_LENGTH / TileRenderer.TILE_BYTES, false);
        }

        byte[] columnMajorApple = rowToColumnMajor(rowMajorApple);
        byte[] updated = Arrays.copyOf(current, current.length);
        for (int offset : RAW_APPLE_OFFSETS) {
            System.arraycopy(columnMajorApple, 0, updated, offset, columnMajorApple.length);
        }
        if (Arrays.equals(updated, current)) {
            System.out.println("Raw pickup-apple copies are unchanged in " + TOWN_PICKUP_SHEET);
            return;
        }

        if (sheetPath.getParent() != null) Files.createDirectories(sheetPath.getParent());
        TileRenderer.writePng(TileRenderer.renderSpriteSheet(updated, palette,
                TILES_W, TILES_H, TOWN_PICKUP_SPRITES, 1, false), sheetPath.toString());
        System.out.println("Synced map apple into raw pickup copies at 0xF4700 and 0xF4780");
    }

    public static void verify(String romPath, String redEditPath, String greenEditPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        verifyRed(rom, redEditPath, false);
        verifyGreen(rom, greenEditPath);
        System.out.println("Legacy apple check: first two map copies and two raw copies match their editors");
    }

    /** The full build validates every enabled editor against what the game actually loads. */
    public static void verifyAvailable(String romPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        if (Files.exists(Paths.get(DEFAULT_RED_EDIT))) verifyRed(rom, DEFAULT_RED_EDIT, true);
        if (Files.exists(Paths.get(DEFAULT_MIRRORED_EDIT))) verifySymmetric(rom, DEFAULT_MIRRORED_EDIT);
        if (Files.exists(Paths.get(DEFAULT_GREEN_EDIT))) verifyGreen(rom, DEFAULT_GREEN_EDIT);
        if (Files.exists(Paths.get(DEFAULT_GOLDEN_EDIT))) verifyGolden(romPath, DEFAULT_GOLDEN_EDIT);
        System.out.println("Apple editors verified against live ROM pointers and raw sprite slots");
    }

    private static void verifyRed(byte[] rom, String redEditPath, boolean complete) throws IOException {
        byte[] expectedRed = decodeNormalEdit(redEditPath);
        for (int i = 0; i < (complete ? MAP_COPIES.length : 2); i++) {
            Copy copy = MAP_COPIES[i];
            byte[] actual = readApple(rom, copy);
            if (!Arrays.equals(remapToGamePalette(expectedRed, copy), actual)) {
                throw new IllegalStateException(String.format(
                    "red map apple differs at live block 0x%X (original 0x%X); ROM is incomplete",
                        resolveCopyBlock(rom, copy), copy.blockOffset));
            }
        }
        if (complete && !Arrays.equals(expectedRed, readApple(rom, SPRITE_PICKUP_COPY))) {
            throw new IllegalStateException("compressed pickup sprite still shows the original apple");
        }
        if (complete) {
            int secondPointer = (rom[0x0316B8] & 255) << 24
                    | (rom[0x0316B9] & 255) << 16
                    | (rom[0x0316BA] & 255) << 8 | rom[0x0316BB] & 255;
            if (secondPointer != resolveCopyBlock(rom, SPRITE_PICKUP_COPY)) {
                throw new IllegalStateException("pickup sprite has inconsistent live pointers");
            }
        }
        if (complete) {
            byte[] originalRom = Files.readAllBytes(Paths.get(net.krusher.DefaultPaths.ROM));
            byte[] expectedEmbedded = composeEmbedded(
                    readApple(originalRom, EMBEDDED_MAP_COPY), expectedRed);
            if (!Arrays.equals(expectedEmbedded, readApple(rom, EMBEDDED_MAP_COPY))) {
                throw new IllegalStateException("background-embedded map apple is incomplete");
            }
        }
    }

    private static void verifySymmetric(byte[] rom, String editPath) throws IOException {
        byte[] expected = remapToGamePalette(decodeSymmetricEdit(editPath), MIRRORED_MAP_COPY);
        if (!Arrays.equals(expected, readApple(rom, MIRRORED_MAP_COPY))) {
            throw new IllegalStateException("mirrored map apple differs at its live pointer");
        }
    }

    private static void verifyGreen(byte[] rom, String greenEditPath) throws IOException {
        byte[] expectedGreen = decodeNormalEdit(greenEditPath);
        for (int offset : RAW_APPLE_OFFSETS) {
            byte[] columnMajor = Arrays.copyOfRange(rom,
                    TOWN_PICKUP_BLOCK + offset,
                    TOWN_PICKUP_BLOCK + offset + TILE_COUNT * TileRenderer.TILE_BYTES);
            if (!Arrays.equals(expectedGreen, columnToRowMajor(columnMajor))) {
                throw new IllegalStateException(String.format(
                        "green raw pickup apple differs at 0x%X",
                        TOWN_PICKUP_BLOCK + offset));
            }
        }
    }

    private static byte[] decodeNormalEdit(String editPath) throws IOException {
        Bitmap image = TileRenderer.readPng(editPath);
        if (image.getWidth() != WIDTH || image.getHeight() != HEIGHT) {
            throw new IllegalStateException(editPath + " must stay " + WIDTH + "x" + HEIGHT);
        }
        return TileRenderer.decodeSpriteSheet(image, editPalette(),
                TILES_W, TILES_H, 1, 1, TILE_COUNT, true);
    }

    /** Verifies that both friendly golden frames are exactly present in a built ROM. */
    public static void verifyGolden(String romPath, String editPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        Bitmap image = TileRenderer.readPng(editPath);
        if (image.getWidth() != GOLDEN_WIDTH || image.getHeight() != GOLDEN_HEIGHT) {
            throw new IllegalStateException(editPath + " must stay "
                    + GOLDEN_WIDTH + "x" + GOLDEN_HEIGHT);
        }
        byte[] expected = TileRenderer.decodeSpriteSheet(image, goldenEditPalette(rom),
                GOLDEN_TILES_W, GOLDEN_TILES_H, GOLDEN_EDIT_FRAME_TILES.length, 1,
                GOLDEN_FRAME_TILE_COUNT * GOLDEN_EDIT_FRAME_TILES.length, false);
        byte[] block = LzToshio.decompress(rom, resolveBlock(rom, 0x59000, 0x5938C));
        for (int frame = 0; frame < GOLDEN_GAME_FRAME_TILES.length; frame++) {
            byte[] actual = Arrays.copyOfRange(block,
                    GOLDEN_GAME_FRAME_TILES[frame] * TileRenderer.TILE_BYTES,
                    (GOLDEN_GAME_FRAME_TILES[frame] + GOLDEN_FRAME_TILE_COUNT)
                            * TileRenderer.TILE_BYTES);
            byte[] pose = Arrays.copyOfRange(expected,
                    (frame / 2) * GOLDEN_FRAME_TILE_COUNT * TileRenderer.TILE_BYTES,
                    (frame / 2 + 1) * GOLDEN_FRAME_TILE_COUNT * TileRenderer.TILE_BYTES);
            if (!Arrays.equals(pose, actual)) {
                throw new IllegalStateException("built golden-apple frame " + frame
                        + " differs from " + editPath);
            }
        }
        System.out.println("Golden-apple mapping: all four game frames match the two editor poses");
    }

    /** TL,TR,BL,BR -> TL,BL,TR,BR. The permutation is its own inverse. */
    private static byte[] rowToColumnMajor(byte[] source) {
        byte[] reordered = new byte[source.length];
        int[] order = {0, 2, 1, 3};
        for (int tile = 0; tile < order.length; tile++) {
            System.arraycopy(source, order[tile] * TileRenderer.TILE_BYTES,
                    reordered, tile * TileRenderer.TILE_BYTES, TileRenderer.TILE_BYTES);
        }
        return reordered;
    }

    private static byte[] columnToRowMajor(byte[] source) {
        return rowToColumnMajor(source);
    }

    private static byte[] readApple(byte[] rom, Copy copy) {
        byte[] block = LzToshio.decompress(rom, resolveCopyBlock(rom, copy));
        if (block.length != copy.blockTileCount * TileRenderer.TILE_BYTES) {
            throw new IllegalStateException("unexpected apple tileset size at live pointer");
        }
        byte[] tiles = new byte[copy.tileIndices.length * TileRenderer.TILE_BYTES];
        for (int i = 0; i < copy.tileIndices.length; i++) {
            System.arraycopy(block, copy.tileIndices[i] * TileRenderer.TILE_BYTES,
                    tiles, i * TileRenderer.TILE_BYTES, TileRenderer.TILE_BYTES);
        }
        return tiles;
    }

    private static int resolveCopyBlock(byte[] rom, Copy copy) {
        if (copy.pointerBase >= 0) return resolveBlock(rom, copy.pointerBase, copy.pointerField);
        int field = copy.pointerField;
        int target = (rom[field] & 255) << 24 | (rom[field + 1] & 255) << 16
                | (rom[field + 2] & 255) << 8 | rom[field + 3] & 255;
        if (target < 0 || target > rom.length - 8) {
            throw new IllegalStateException("direct pickup pointer outside ROM");
        }
        return target;
    }

    /** Signed 32-bit offsets: relocated blocks can precede their pointer table. */
    static int resolveBlock(byte[] rom, int tableBase, int pointerField) {
        if (pointerField < 0 || pointerField > rom.length - 4) {
            throw new IllegalStateException("apple pointer field outside ROM");
        }
        int relative = (rom[pointerField] & 255) << 24 | (rom[pointerField + 1] & 255) << 16
                | (rom[pointerField + 2] & 255) << 8 | rom[pointerField + 3] & 255;
        long target = (long) tableBase + relative;
        if (target < 0 || target > rom.length - 8) {
            throw new IllegalStateException("apple graphics pointer outside ROM");
        }
        return (int) target;
    }

    private static byte[] recolorGreen(byte[] source) {
        byte[] green = Arrays.copyOf(source, source.length);
        for (int i = 0; i < green.length; i++) {
            int value = green[i] & 0xFF;
            green[i] = (byte) (remapGreen(value >>> 4) << 4 | remapGreen(value & 15));
        }
        return green;
    }

    private static int remapGreen(int index) {
        switch (index) {
            case 3: return 10; // mid red -> mid green
            case 5: return 9;  // light red -> light green
            case 7: return 4;  // orange highlight -> yellow highlight
            case 12: return 11; // dark red -> dark green
            default: return index;
        }
    }

    private static int[] editPalette() {
        int[] palette = Arrays.copyOf(CAPTURED_PALETTE, CAPTURED_PALETTE.length);
        palette[0] = 0xFFFF00FF;
        return palette;
    }

    private static int[] viewPalette() {
        int[] palette = Arrays.copyOf(CAPTURED_PALETTE, CAPTURED_PALETTE.length);
        palette[0] = 0xFF010101;
        return palette;
    }

    private static int[] goldenEditPalette(byte[] rom) {
        int[] palette = TileRenderer.readGenesisPalette(rom, TOWN_PALETTE_OFFSET);
        palette[0] = 0xFFFF00FF;
        return palette;
    }

    private static byte[] readGoldenFrames(byte[] block) {
        byte[] frames = new byte[GOLDEN_FRAME_TILE_COUNT
                * GOLDEN_EDIT_FRAME_TILES.length * TileRenderer.TILE_BYTES];
        for (int frame = 0; frame < GOLDEN_EDIT_FRAME_TILES.length; frame++) {
            System.arraycopy(block, GOLDEN_EDIT_FRAME_TILES[frame] * TileRenderer.TILE_BYTES,
                    frames, frame * GOLDEN_FRAME_TILE_COUNT * TileRenderer.TILE_BYTES,
                    GOLDEN_FRAME_TILE_COUNT * TileRenderer.TILE_BYTES);
        }
        return frames;
    }
}
