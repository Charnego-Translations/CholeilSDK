package net.krusher.graphics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** Friendly editors for the final enemy portrait and its closed/open mouth states. */
public final class CholoFinalGraphics {
    public static final int BLOCK_OFFSET = 0x0A2F56;
    public static final int TILE_COUNT = 64;
    public static final int SPRITE_TILES_W = 4;
    public static final int SPRITE_TILES_H = 4;
    public static final int SPRITES_PER_ROW = 2;
    public static final int WIDTH = 64;
    public static final int HEIGHT = 64;

    private static final int POINTER_TABLE_BASE = 0x059000;
    private static final int POINTER_FIELD = 0x05935C;

    /** Four 16x16 eye states occupy the first 16 tiles of this 36-tile block. */
    public static final int MOUTH_BLOCK_OFFSET = 0x0A33E0;
    public static final int MOUTH_BLOCK_TILE_COUNT = 36;
    private static final int MOUTH_POINTER_FIELD = 0x059360;
    private static final int MOUTH_CLOSED_FIRST_TILE = 0;  // VRAM 0x364
    private static final int MOUTH_OPENING_FIRST_TILE = 4; // VRAM 0x368
    private static final int MOUTH_OPEN_FIRST_TILE = 8;    // VRAM 0x36C
    private static final int MOUTH_BACK_FIRST_TILE = 12;   // VRAM 0x370
    private static final int MOUTH_STATE_TILE_COUNT = 4;
    private static final int MOUTH_STATE_COUNT = 4;
    private static final int MOUTH_WIDTH = 64;
    private static final int MOUTH_HEIGHT = 16;

    /** Y offsets for closed, opening, open and rear eye sprite-piece records. */
    private static final int[] MOUTH_Y_FIELDS = {
            0x02E0B8, 0x02E0F6, 0x02E134, 0x02E142
    };
    private static final int ORIGINAL_MOUTH_Y = 0xFFF8; // face centre / original eye
    private static final int CENTERED_MOUTH_Y = 0x0002; // ten pixels lower / natural mouth

    /** CRAM line 3 captured from choloFinal.State after leaving pause. */
    private static final byte[] CRAM = {
            0x00,0x00, 0x02,0x22, 0x06,(byte)0x8E, 0x04,0x44,
            0x0E,(byte)0xCC, 0x0E,(byte)0xEE, 0x0E,0x4E, 0x0E,(byte)0xAE,
            0x0C,(byte)0xCC, 0x0A,(byte)0xAA, 0x0C,(byte)0xCC, 0x04,0x44,
            0x06,0x66, 0x08,(byte)0x88, 0x04,0x00, 0x0E,(byte)0xEE
    };

    public static final String DEFAULT_EDIT = "special_gfx_out/cholo_final_EDITAME.png";
    public static final String DEFAULT_VIEW = "special_gfx_out/cholo_final_x8_VISTA.png";
    public static final String DEFAULT_GFX = "gfx_out/gfx_0a2f56.png";
    public static final String DEFAULT_MOUTH_EDIT = "special_gfx_out/cholo_final_boca_EDITAME.png";
    public static final String DEFAULT_MOUTH_VIEW = "special_gfx_out/cholo_final_boca_x8_VISTA.png";
    public static final String DEFAULT_MOUTH_GFX = "gfx_out/gfx_0a33e0.png";

    private CholoFinalGraphics() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("usage:");
            System.out.println("  CholoFinalGraphics extract [rom] [editPng] [viewPng]");
            System.out.println("  CholoFinalGraphics extract-mouth [rom] [editPng] [viewPng]");
            System.out.println("  CholoFinalGraphics seed <sourcePng> [rom] [editPng] [viewPng]");
            System.out.println("  CholoFinalGraphics sync [editPng] [gfxPng]");
            System.out.println("  CholoFinalGraphics sync-mouth [editPng] [gfxPng]");
            System.out.println("  CholoFinalGraphics verify [rom] [editPng] [mouthPng]");
            return;
        }
        switch (args[0]) {
            case "extract" -> extract(
                    args.length > 1 ? args[1] : "Soleil (Spain).md",
                    args.length > 2 ? args[2] : DEFAULT_EDIT,
                    args.length > 3 ? args[3] : DEFAULT_VIEW);
            case "extract-mouth" -> extractMouth(
                    args.length > 1 ? args[1] : "Soleil (Spain).md",
                    args.length > 2 ? args[2] : DEFAULT_MOUTH_EDIT,
                    args.length > 3 ? args[3] : DEFAULT_MOUTH_VIEW);
            case "seed" -> seedFromImage(
                    args[1],
                    args.length > 2 ? args[2] : "Soleil (Spain).md",
                    args.length > 3 ? args[3] : DEFAULT_EDIT,
                    args.length > 4 ? args[4] : DEFAULT_VIEW);
            case "sync" -> sync(
                    args.length > 1 ? args[1] : DEFAULT_EDIT,
                    args.length > 2 ? args[2] : DEFAULT_GFX);
            case "sync-mouth" -> syncMouth(
                    args.length > 1 ? args[1] : DEFAULT_MOUTH_EDIT,
                    args.length > 2 ? args[2] : DEFAULT_MOUTH_GFX);
            case "verify" -> verify(
                    args.length > 1 ? args[1] : "Choleil.md",
                    args.length > 2 ? args[2] : DEFAULT_EDIT,
                    args.length > 3 ? args[3] : DEFAULT_MOUTH_EDIT);
            default -> throw new IllegalArgumentException("unknown mode: " + args[0]);
        }
    }

    public static void extract(String romPath, String editPath, String viewPath) throws IOException {
        writeEditors(readTiles(Files.readAllBytes(Paths.get(romPath))), editPath, viewPath);
        System.out.println("Extracted final enemy as editable 64x64 four-sprite mosaic.");
    }

    public static void extractMouth(String romPath, String editPath, String viewPath) throws IOException {
        byte[] block = readMouthBlock(Files.readAllBytes(Paths.get(romPath)));
        writeMouthEditors(selectMouthTiles(block), editPath, viewPath);
        System.out.println("Extracted final enemy's closed, opening, open and rear 16x16 states.");
    }

    /**
     * Reduces a concept image to the captured Mega Drive palette. The stock
     * orb supplies the transparency mask, so dark hair and eyes remain opaque
     * while everything outside the original glow stays colour index zero.
     */
    public static void seedFromImage(String sourcePath, String romPath,
                                     String editPath, String viewPath) throws IOException {
        Bitmap source = TileRenderer.readPng(sourcePath);
        byte[] originalTiles = readTiles(Files.readAllBytes(Paths.get(romPath)));
        int[] palette = palette();
        Bitmap mask = TileRenderer.renderSpriteSheet(originalTiles, palette,
                SPRITE_TILES_W, SPRITE_TILES_H, SPRITES_PER_ROW, 1, false);
        Bitmap reduced = Bitmap.indexed(WIDTH, HEIGHT, palette);

        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (mask.getIndex(x, y) == 0) {
                    reduced.setIndex(x, y, 0);
                    continue;
                }
                int sx = Math.min(source.getWidth() - 1,
                        (int) ((x + 0.5) * source.getWidth() / WIDTH));
                int sy = Math.min(source.getHeight() - 1,
                        (int) ((y + 0.5) * source.getHeight() / HEIGHT));
                reduced.setIndex(x, y, nearestOpaque(source.getRgb(sx, sy), palette));
            }
        }

        Path edit = Paths.get(editPath);
        if (edit.getParent() != null) Files.createDirectories(edit.getParent());
        TileRenderer.writePng(reduced, edit.toString());
        byte[] tiles = TileRenderer.decodeSpriteSheet(reduced, palette,
                SPRITE_TILES_W, SPRITE_TILES_H, SPRITES_PER_ROW, 1, TILE_COUNT, false);
        writeView(tiles, viewPath);
        System.out.println("Seeded Cholo final-enemy editor with the captured palette and original mask.");
    }

    /** Converts the friendly four-sprite mosaic to GraphicsInserter's tile sheet. */
    public static void sync(String editPath, String gfxPath) throws IOException {
        Path edit = Paths.get(editPath);
        if (!Files.exists(edit)) {
            System.out.println("Final-enemy edit PNG not found; leaving block 0xA2F56 untouched.");
            return;
        }
        Bitmap image = TileRenderer.readPng(edit.toString());
        validateSize(image, edit.toString());
        byte[] tiles = TileRenderer.decodeSpriteSheet(image, palette(),
                SPRITE_TILES_W, SPRITE_TILES_H, SPRITES_PER_ROW, 1, TILE_COUNT, false);

        Path gfx = Paths.get(gfxPath);
        if (Files.exists(gfx)) {
            Bitmap current = TileRenderer.readPng(gfx.toString());
            if (current.getWidth() == 128 && current.getHeight() == 32) {
                byte[] currentTiles = TileRenderer.decodeTileSheet(current,
                        TileRenderer.defaultGrayscalePalette(), 16, 1, TILE_COUNT);
                if (Arrays.equals(tiles, currentTiles)) {
                    System.out.println("Cholo final-enemy edit is unchanged; keeping " + gfx + " byte-for-byte.");
                    return;
                }
            }
        }
        if (gfx.getParent() != null) Files.createDirectories(gfx.getParent());
        TileRenderer.writePng(TileRenderer.renderTileSheet(
                tiles, TileRenderer.defaultGrayscalePalette(), 16, 1), gfx.toString());
        System.out.println("Synced Cholo final-enemy edit into " + gfx + ".");
    }

    /** Patches only the four eye states, preserving the other 20 tiles in block 0xA33E0. */
    public static void syncMouth(String editPath, String gfxPath) throws IOException {
        Path edit = Paths.get(editPath);
        if (!Files.exists(edit)) {
            System.out.println("Final-enemy mouth PNG not found; leaving block 0xA33E0 untouched.");
            return;
        }
        Bitmap image = TileRenderer.readPng(edit.toString());
        validateMouthSize(image, edit.toString());
        byte[] mouth = decodeMouthEditor(image);

        Path gfx = Paths.get(gfxPath);
        if (!Files.exists(gfx)) {
            throw new IllegalStateException("Missing generic graphics block " + gfx);
        }
        Bitmap generic = TileRenderer.readPng(gfx.toString());
        byte[] block = TileRenderer.decodeTileSheet(generic,
                TileRenderer.defaultGrayscalePalette(), 16, 1, MOUTH_BLOCK_TILE_COUNT);
        copyMouthTiles(mouth, block);
        TileRenderer.writePng(TileRenderer.renderTileSheet(
                block, TileRenderer.defaultGrayscalePalette(), 16, 1), gfx.toString());
        System.out.println("Synced Cholo closed/open mouth states into " + gfx + ".");
    }

    /**
     * Keeps the full portrait centred and lowers every former-eye state ten
     * pixels onto the portrait's natural mouth. The hitbox is untouched.
     */
    public static void positionMouth(String romPath) throws IOException {
        Path path = Paths.get(romPath);
        byte[] rom = Files.readAllBytes(path);
        for (int field : MOUTH_Y_FIELDS) {
            int current = readU16(rom, field);
            if (current != ORIGINAL_MOUTH_Y && current != CENTERED_MOUTH_Y) {
                throw new IllegalStateException(String.format(
                        "unexpected final-enemy mouth Y at 0x%X: 0x%04X", field, current));
            }
            writeU16(rom, field, CENTERED_MOUTH_Y);
        }
        net.krusher.TextInserter.fixChecksum(rom);
        Files.write(path, rom);
        System.out.println("Cholo portrait centred; all four mouth states moved 10px down.");
    }

    public static void verify(String romPath, String editPath, String mouthEditPath) throws IOException {
        Bitmap image = TileRenderer.readPng(editPath);
        validateSize(image, editPath);
        byte[] expected = TileRenderer.decodeSpriteSheet(image, palette(),
                SPRITE_TILES_W, SPRITE_TILES_H, SPRITES_PER_ROW, 1, TILE_COUNT, false);
        byte[] actual = readTiles(Files.readAllBytes(Paths.get(romPath)));
        if (!Arrays.equals(expected, actual)) {
            throw new IllegalStateException("final-enemy ROM tiles differ from cholo_final_EDITAME.png");
        }

        Bitmap mouthImage = TileRenderer.readPng(mouthEditPath);
        validateMouthSize(mouthImage, mouthEditPath);
        byte[] expectedMouth = decodeMouthEditor(mouthImage);
        byte[] actualMouth = selectMouthTiles(readMouthBlock(
                Files.readAllBytes(Paths.get(romPath))));
        if (!Arrays.equals(expectedMouth, actualMouth)) {
            throw new IllegalStateException("final-enemy ROM eye/mouth tiles differ from cholo_final_boca_EDITAME.png");
        }
        verifyMouthPosition(romPath);
        System.out.println("Cholo final enemy verified: portrait plus closed/open mouth states match their editors.");
    }

    /** Kept for small callers that only supplied the portrait before the mouth editor existed. */
    public static void verify(String romPath, String editPath) throws IOException {
        verify(romPath, editPath, DEFAULT_MOUTH_EDIT);
    }

    private static void writeEditors(byte[] tiles, String editPath, String viewPath) throws IOException {
        int[] palette = palette();
        Path edit = Paths.get(editPath);
        if (edit.getParent() != null) Files.createDirectories(edit.getParent());
        TileRenderer.writePng(TileRenderer.renderSpriteSheet(tiles, palette,
                SPRITE_TILES_W, SPRITE_TILES_H, SPRITES_PER_ROW, 1, false), edit.toString());
        writeView(tiles, viewPath);
    }

    private static void writeView(byte[] tiles, String viewPath) throws IOException {
        Path view = Paths.get(viewPath);
        if (view.getParent() != null) Files.createDirectories(view.getParent());
        TileRenderer.writePng(TileRenderer.renderSpriteSheet(tiles, palette(),
                SPRITE_TILES_W, SPRITE_TILES_H, SPRITES_PER_ROW, 8, false), view.toString());
    }

    private static void writeMouthEditors(byte[] tiles, String editPath, String viewPath) throws IOException {
        int[] palette = palette();
        Path edit = Paths.get(editPath);
        if (edit.getParent() != null) Files.createDirectories(edit.getParent());
        Bitmap states = TileRenderer.renderSpriteSheet(
                tiles, palette, 2, 2, MOUTH_STATE_COUNT, 1, false);
        TileRenderer.writePng(states, edit.toString());

        Bitmap view = TileRenderer.renderSpriteSheet(
                tiles, palette, 2, 2, MOUTH_STATE_COUNT, 8, false);
        Path viewPath2 = Paths.get(viewPath);
        if (viewPath2.getParent() != null) Files.createDirectories(viewPath2.getParent());
        TileRenderer.writePng(view, viewPath2.toString());
    }

    private static byte[] readTiles(byte[] rom) {
        int address = POINTER_TABLE_BASE + readU32(rom, POINTER_FIELD);
        byte[] tiles = LzToshio.decompress(rom, address);
        if (tiles.length != TILE_COUNT * TileRenderer.TILE_BYTES) {
            throw new IllegalStateException(String.format(
                    "final-enemy block 0x%X has %d bytes, expected %d",
                    address, tiles.length, TILE_COUNT * TileRenderer.TILE_BYTES));
        }
        return tiles;
    }

    private static byte[] readMouthBlock(byte[] rom) {
        int address = POINTER_TABLE_BASE + readU32(rom, MOUTH_POINTER_FIELD);
        byte[] tiles = LzToshio.decompress(rom, address);
        if (tiles.length != MOUTH_BLOCK_TILE_COUNT * TileRenderer.TILE_BYTES) {
            throw new IllegalStateException(String.format(
                    "final-enemy mouth block 0x%X has %d bytes, expected %d",
                    address, tiles.length, MOUTH_BLOCK_TILE_COUNT * TileRenderer.TILE_BYTES));
        }
        return tiles;
    }

    private static byte[] selectMouthTiles(byte[] block) {
        byte[] selected = new byte[MOUTH_STATE_TILE_COUNT * MOUTH_STATE_COUNT
                * TileRenderer.TILE_BYTES];
        System.arraycopy(block, MOUTH_CLOSED_FIRST_TILE * TileRenderer.TILE_BYTES,
                selected, 0, selected.length);
        return selected;
    }

    private static void copyMouthTiles(byte[] selected, byte[] block) {
        System.arraycopy(selected, 0, block,
                MOUTH_CLOSED_FIRST_TILE * TileRenderer.TILE_BYTES, selected.length);
    }

    private static byte[] decodeMouthEditor(Bitmap image) {
        int[] expectedPalette = palette();
        int[] embeddedPalette = image.palette();
        if (embeddedPalette == null || !Arrays.equals(embeddedPalette, expectedPalette)) {
            return TileRenderer.decodeSpriteSheet(image, expectedPalette,
                    2, 2, MOUTH_STATE_COUNT, 1,
                    MOUTH_STATE_TILE_COUNT * MOUTH_STATE_COUNT, false);
        }

        byte[] tiles = new byte[MOUTH_STATE_TILE_COUNT * MOUTH_STATE_COUNT
                * TileRenderer.TILE_BYTES];
        int spriteBytes = MOUTH_STATE_TILE_COUNT * TileRenderer.TILE_BYTES;
        for (int sprite = 0; sprite < MOUTH_STATE_COUNT; sprite++) {
            for (int tile = 0; tile < MOUTH_STATE_TILE_COUNT; tile++) {
                int col = tile / 2;
                int row = tile % 2;
                int base = sprite * spriteBytes + tile * TileRenderer.TILE_BYTES;
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x += 2) {
                        int imageX = sprite * 16 + col * 8 + x;
                        int imageY = row * 8 + y;
                        int left = image.getIndex(imageX, imageY);
                        int right = image.getIndex(imageX + 1, imageY);
                        tiles[base + y * 4 + x / 2] = (byte) (left << 4 | right);
                    }
                }
            }
        }
        return tiles;
    }

    private static int[] palette() {
        return TileRenderer.readGenesisPalette(CRAM, 0);
    }

    private static void validateSize(Bitmap image, String path) {
        if (image.getWidth() != WIDTH || image.getHeight() != HEIGHT) {
            throw new IllegalStateException(path + " must stay exactly 64x64 pixels");
        }
    }

    private static void validateMouthSize(Bitmap image, String path) {
        if (image.getWidth() != MOUTH_WIDTH || image.getHeight() != MOUTH_HEIGHT) {
            throw new IllegalStateException(path + " must stay exactly 64x16 pixels");
        }
    }

    private static int nearestOpaque(int argb, int[] palette) {
        int r = argb >>> 16 & 0xFF;
        int g = argb >>> 8 & 0xFF;
        int b = argb & 0xFF;
        int best = 1;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 1; i < palette.length; i++) {
            int pr = palette[i] >>> 16 & 0xFF;
            int pg = palette[i] >>> 8 & 0xFF;
            int pb = palette[i] & 0xFF;
            long dr = r - pr;
            long dg = g - pg;
            long db = b - pb;
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static int readU32(byte[] data, int offset) {
        return (data[offset] & 0xFF) << 24 | (data[offset + 1] & 0xFF) << 16
                | (data[offset + 2] & 0xFF) << 8 | data[offset + 3] & 0xFF;
    }

    private static void verifyMouthPosition(String romPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        for (int field : MOUTH_Y_FIELDS) {
            if (readU16(rom, field) != CENTERED_MOUTH_Y) {
                throw new IllegalStateException(String.format(
                        "final-enemy mouth layer at 0x%X is not centred on the mouth", field));
            }
        }
    }

    private static int readU16(byte[] data, int offset) {
        return (data[offset] & 0xFF) << 8 | data[offset + 1] & 0xFF;
    }

    private static void writeU16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 8);
        data[offset + 1] = (byte) value;
    }
}
