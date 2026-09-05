package net.krusher.graphics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Friendly editor for the cursive "Fin." shown after the ending credits.
 *
 * The stock game stores it as one LZ-Toshio block containing two 3x3 hardware
 * sprites. The patch expands that to four sprites: 96x24 pixels, centred at
 * the same screen position. Tiles inside each sprite use the Mega Drive's
 * column-major order, so the ordinary 16-column gfx_out view looks scrambled.
 * This adapter exposes the friendly image and maps it back to gfx_out before
 * GraphicsInserter handles compression and relocation.
 */
public final class FinGraphics {
    public static final int BLOCK_OFFSET = 0x11BE50;
    public static final int PALETTE_OFFSET = 0x11A518;
    public static final int SPRITE_TILES_W = 3;
    public static final int SPRITE_TILES_H = 3;
    public static final int ORIGINAL_SPRITE_COUNT = 2;
    public static final int SPRITES_PER_ROW = 4;
    public static final int TILES_PER_SPRITE = SPRITE_TILES_W * SPRITE_TILES_H;
    public static final int ORIGINAL_TILE_COUNT = ORIGINAL_SPRITE_COUNT * TILES_PER_SPRITE;
    public static final int TILE_COUNT = SPRITES_PER_ROW * TILES_PER_SPRITE;
    public static final int WIDTH = 96;
    public static final int HEIGHT = 24;

    private static final int POINTER_TABLE_BASE = 0x116E00;
    private static final int POINTER_FIELD = 0x116E50;
    private static final int LAYOUT_ROUTINE_OFFSET = 0x03A9CC;

    private static final int[] ORIGINAL_LAYOUT_ROUTINE = {
        0x22,0x79,0x00,0xFF,0xA0,0x00,0x24,0x79,0x00,0xFF,0xA0,0x04,
        0x34,0x3C,0x00,0x68,0x06,0x42,0x00,0x80,0x36,0x3C,0x00,0x58,
        0x06,0x43,0x00,0x80,0x2A,0x3C,0x00,0x00,0x3F,0xFF,0x4B,0xFA,
        0x00,0x36,0x4E,0x71,0x4E,0xB9,0x00,0x01,0x95,0x28,0x34,0x3C,
        0x00,0x80,0x06,0x42,0x00,0x80,0x36,0x3C,0x00,0x58,0x06,0x43,
        0x00,0x80,0x2A,0x3C,0x00,0x00,0x3F,0xFF,0x4B,0xFA,0x00,0x18,
        0x4E,0x71,0x4E,0xB9,0x00,0x01,0x95,0x28,0x21,0xC9,0xA0,0x00,
        0x21,0xCA,0xA0,0x04,0x4E,0x75,0x0A,0x00,0x40,0x02,0x0A,0x00,
        0x40,0x0B
    };

    /*
     * Four 3x3 sprites at screen X 80, 104, 128 and 152. Mega Drive sprite
     * coordinates carry a +128 bias, hence D0/E8/100/118 in d2. The four
     * descriptors use consecutive nine-tile runs: 2, 11, 20 and 29.
     */
    private static final int[] EXPANDED_LAYOUT_ROUTINE = {
        0x22,0x79,0x00,0xFF,0xA0,0x00,0x24,0x79,0x00,0xFF,0xA0,0x04,
        0x2F,0x07,0x34,0x3C,0x00,0xD0,0x36,0x3C,0x00,0xD8,0x2A,0x3C,
        0x00,0x00,0x3F,0xFF,0x4B,0xFA,0x00,0x20,0x7E,0x03,0x4E,0xB9,
        0x00,0x01,0x95,0x28,0x06,0x42,0x00,0x18,0x58,0x8D,0x51,0xCF,
        0xFF,0xF2,0x2E,0x1F,0x21,0xC9,0xA0,0x00,0x21,0xCA,0xA0,0x04,
        0x4E,0x75,
        0x0A,0x00,0x40,0x02,0x0A,0x00,0x40,0x0B,
        0x0A,0x00,0x40,0x14,0x0A,0x00,0x40,0x1D,
        0x4E,0x71,0x4E,0x71,0x4E,0x71,0x4E,0x71,0x4E,0x71,0x4E,0x71,
        0x4E,0x71,0x4E,0x71,0x4E,0x71,0x4E,0x71
    };

    public static final String DEFAULT_EDIT = "special_gfx_out/fin_EDITAME.png";
    public static final String DEFAULT_VIEW = "special_gfx_out/fin_x4_VISTA.png";
    public static final String DEFAULT_GFX = "gfx_out/gfx_11be50.png";

    private FinGraphics() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("usage:");
            System.out.println("  FinGraphics extract [rom] [editPng] [viewPng]");
            System.out.println("  FinGraphics sync [rom] [editPng] [gfxPng]");
            System.out.println("  FinGraphics verify [rom] [editPng]");
            System.out.println("  FinGraphics patch-layout [rom]");
            return;
        }
        String mode = args[0];
        if (mode.equals("extract")) {
            extract(args.length > 1 ? args[1] : "Soleil (Spain).md",
                    args.length > 2 ? args[2] : DEFAULT_EDIT,
                    args.length > 3 ? args[3] : DEFAULT_VIEW);
        } else if (mode.equals("sync")) {
            sync(args.length > 1 ? args[1] : "Soleil (Spain).md",
                    args.length > 2 ? args[2] : DEFAULT_EDIT,
                    args.length > 3 ? args[3] : DEFAULT_GFX);
        } else if (mode.equals("verify")) {
            verify(args.length > 1 ? args[1] : "Soleil (Spain).md",
                    args.length > 2 ? args[2] : DEFAULT_EDIT);
        } else if (mode.equals("patch-layout")) {
            patchLayout(args.length > 1 ? args[1] : "Choleil.md");
        } else {
            throw new IllegalArgumentException("unknown mode: " + mode);
        }
    }

    public static void extract(String romPath, String editPath, String viewPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        byte[] tiles = readTiles(rom);
        int[] palette = TileRenderer.readGenesisPalette(rom, PALETTE_OFFSET);

        Path edit = Paths.get(editPath);
        Path view = Paths.get(viewPath);
        if (edit.getParent() != null) Files.createDirectories(edit.getParent());
        if (view.getParent() != null) Files.createDirectories(view.getParent());

        TileRenderer.writePng(TileRenderer.renderSpriteSheet(tiles, palette,
                SPRITE_TILES_W, SPRITE_TILES_H, SPRITES_PER_ROW, 1, false), edit.toString());
        TileRenderer.writePng(TileRenderer.renderSpriteSheet(tiles, palette,
                SPRITE_TILES_W, SPRITE_TILES_H, SPRITES_PER_ROW, 4, false), view.toString());
        System.out.println("Extracted ending Fin.: " + edit + " (96x24, original artwork centred)");
    }

    /** Converts the friendly sprite mosaic into GraphicsInserter's ordinary tile sheet. */
    public static void sync(String romPath, String editPath, String gfxPath) throws IOException {
        Path edit = Paths.get(editPath);
        if (!Files.exists(edit)) {
            System.out.println("Ending Fin. edit PNG not found; leaving block 0x"
                    + Integer.toHexString(BLOCK_OFFSET) + " untouched.");
            return;
        }

        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        int[] palette = TileRenderer.readGenesisPalette(rom, PALETTE_OFFSET);
        Bitmap image = TileRenderer.readPng(edit.toString());
        if (image.getWidth() != WIDTH || image.getHeight() != HEIGHT) {
            throw new IllegalStateException(edit + " must stay " + WIDTH + "x" + HEIGHT);
        }

        byte[] tiles = TileRenderer.decodeSpriteSheet(image, palette,
                SPRITE_TILES_W, SPRITE_TILES_H, SPRITES_PER_ROW, 1, TILE_COUNT, false);
        ensureExpandedRegistry("graphics_offsets.txt");
        Path gfx = Paths.get(gfxPath);
        if (Files.exists(gfx)) {
            Bitmap current = TileRenderer.readPng(gfx.toString());
            if (current.getWidth() == 128 && current.getHeight() == 24) {
                byte[] currentTiles = TileRenderer.decodeTileSheet(current,
                        TileRenderer.defaultGrayscalePalette(), 16, 1, TILE_COUNT);
                if (Arrays.equals(tiles, currentTiles)) {
                    System.out.println("Ending Fin. is unchanged; keeping " + gfx + " byte-for-byte.");
                    return;
                }
            }
        }

        if (gfx.getParent() != null) Files.createDirectories(gfx.getParent());
        TileRenderer.writePng(TileRenderer.renderTileSheet(
                tiles, TileRenderer.defaultGrayscalePalette(), 16, 1), gfx.toString());
        System.out.println("Synced ending Fin. into " + gfx);
    }

    public static void verify(String romPath, String editPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        byte[] original = readTiles(rom);
        int[] palette = TileRenderer.readGenesisPalette(rom, PALETTE_OFFSET);
        Bitmap image = TileRenderer.readPng(editPath);
        if (image.getWidth() != WIDTH || image.getHeight() != HEIGHT) {
            throw new IllegalStateException(editPath + " must stay " + WIDTH + "x" + HEIGHT);
        }
        byte[] decoded = TileRenderer.decodeSpriteSheet(image, palette,
                SPRITE_TILES_W, SPRITE_TILES_H, SPRITES_PER_ROW, 1, TILE_COUNT, false);
        if (!Arrays.equals(original, decoded)) {
            throw new IllegalStateException("ending Fin. PNG does not round-trip to the ROM tile bytes");
        }
        System.out.println("Ending Fin. round-trip verified: 36 tiles match the centred 96x24 layout byte-for-byte.");
    }

    private static byte[] readTiles(byte[] rom) {
        int address = resolveBlockAddress(rom);
        byte[] stored = LzToshio.decompress(rom, address);
        int originalLength = ORIGINAL_TILE_COUNT * TileRenderer.TILE_BYTES;
        int expandedLength = TILE_COUNT * TileRenderer.TILE_BYTES;
        if (stored.length == expandedLength) return stored;
        if (stored.length != originalLength) {
            throw new IllegalStateException(String.format(
                    "ending Fin. block 0x%X has %d bytes, expected %d or %d",
                    address, stored.length, originalLength, expandedLength));
        }

        // Keep the original 48px drawing in its old screen position: one
        // blank 24px sprite on each side expands the canvas symmetrically.
        byte[] expanded = new byte[expandedLength];
        System.arraycopy(stored, 0, expanded, TILES_PER_SPRITE * TileRenderer.TILE_BYTES, stored.length);
        return expanded;
    }

    /** Patches the ending renderer from two 24x24 sprites to four, centred. */
    public static void patchLayout(String romPath) throws IOException {
        if (EXPANDED_LAYOUT_ROUTINE.length != ORIGINAL_LAYOUT_ROUTINE.length) {
            throw new IllegalStateException("internal ending layout patch has the wrong size");
        }
        Path path = Paths.get(romPath);
        byte[] rom = Files.readAllBytes(path);
        int blockAddress = resolveBlockAddress(rom);
        int length = LzToshio.decompress(rom, blockAddress).length;
        if (length != TILE_COUNT * TileRenderer.TILE_BYTES) {
            throw new IllegalStateException("ending Fin. graphics were not expanded; refusing to patch its layout");
        }

        if (matches(rom, LAYOUT_ROUTINE_OFFSET, EXPANDED_LAYOUT_ROUTINE)) {
            System.out.println("Ending Fin. layout is already 96x24 and centred.");
            return;
        }
        if (!matches(rom, LAYOUT_ROUTINE_OFFSET, ORIGINAL_LAYOUT_ROUTINE)) {
            throw new IllegalStateException(String.format(
                    "unexpected ending layout code at 0x%X; ROM left untouched", LAYOUT_ROUTINE_OFFSET));
        }
        write(rom, LAYOUT_ROUTINE_OFFSET, EXPANDED_LAYOUT_ROUTINE);
        net.krusher.TextInserter.fixChecksum(rom);
        Files.write(path, rom);
        System.out.println("Ending Fin. layout patched: 96x24 at screen X 80-175 (24px left of the old start).");
    }

    private static void ensureExpandedRegistry(String registryPath) throws IOException {
        Path path = Paths.get(registryPath);
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<String> updated = new ArrayList<String>(lines.size());
        boolean found = false;
        for (String line : lines) {
            String trimmed = line.trim().toLowerCase();
            if (trimmed.startsWith("0x11be50,")) {
                updated.add("0x11be50,282," + (TILE_COUNT * TileRenderer.TILE_BYTES));
                found = true;
            } else {
                updated.add(line);
            }
        }
        if (!found) throw new IllegalStateException("0x11be50 is missing from " + registryPath);
        Files.write(path, updated, StandardCharsets.UTF_8);
    }

    private static int resolveBlockAddress(byte[] rom) {
        return POINTER_TABLE_BASE + readU32(rom, POINTER_FIELD);
    }

    private static boolean matches(byte[] data, int offset, int[] expected) {
        if (offset < 0 || offset + expected.length > data.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }

    private static void write(byte[] data, int offset, int[] values) {
        for (int i = 0; i < values.length; i++) data[offset + i] = (byte) values[i];
    }

    private static int readU32(byte[] data, int offset) {
        return (data[offset] & 0xFF) << 24 | (data[offset + 1] & 0xFF) << 16
                | (data[offset + 2] & 0xFF) << 8 | data[offset + 3] & 0xFF;
    }
}
