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

    private static final class MapDefinitionPatch {
        final int blockOffset;
        final int pointerIndex;
        final int byteOffset;
        final int[] originalWords;
        final int[] replacementWords;

        MapDefinitionPatch(int blockOffset, int pointerIndex, int byteOffset,
                           int[] originalWords, int[] replacementWords) {
            this.blockOffset = blockOffset;
            this.pointerIndex = pointerIndex;
            this.byteOffset = byteOffset;
            this.originalWords = originalWords;
            this.replacementWords = replacementWords;
        }

        String gfxPath() {
            return String.format("gfx_out/gfx_%06x.png", blockOffset);
        }
    }

    /** Fixed reservations used only after the 2 MiB intro has been packed. */
    private static final class CompactBlock {
        final int originalBlock;
        final int pointerField;
        final int pointerBase;
        final int allocated;
        final int destination;

        CompactBlock(int originalBlock, int pointerField, int pointerBase,
                     int allocated, int destination) {
            this.originalBlock = originalBlock;
            this.pointerField = pointerField;
            this.pointerBase = pointerBase;
            this.allocated = allocated;
            this.destination = destination;
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
    /** Clean left-hand floor tiles under each mirrored lava-room pickup. */
    private static final Copy MIRRORED_MAP_FLOOR_COPY = new Copy(0x1528E4,
            new int[]{12, 369}, 512, 0x120068, 0x120000, 0, null);
    /** Same old apple artwork, but drawn over solid map colour 8 with shadow 13. */
    private static final Copy EMBEDDED_MAP_COPY = new Copy(0x132946,
            new int[]{350, 351, 366, 367}, 480, 0x120028, 0x120000, 0, null);
    /** Otrolao bakes its apple into a speckled floor tile, using palette line 2. */
    private static final Copy OTROLAO_MAP_COPY = new Copy(0x13791C,
            new int[]{210, 211, 226, 227}, 464, 0x120030, 0x120000, 0,
            new int[]{0x0262, 0x0EEE, 0x0AEE, 0x08CC, 0x06AA, 0x0222, 0x0EEC,
                    0x0CCA, 0x0AA8, 0x0ACA, 0x0464, 0x0266, 0x08A8, 0x0028,
                    0x004C, 0x048E});
    private static final Copy OTROLAO_FLOOR_COPY = new Copy(0x13791C,
            new int[]{20, 21, 36, 37}, 464, 0x120030, 0x120000, 0, null);
    /** Water-zone apple baked into four floor tiles (Genplus-gx QuickSave1). */
    private static final Copy WATER_MAP_COPY = new Copy(0x12D708,
            new int[]{0x132, 0x133, 0x142, 0x143}, 400,
            0x120020, 0x120000, 0, new int[]{
                    0x0000, 0x0EA8, 0x0C8C, 0x0664, 0x0644, 0x0622, 0x0468, 0x026C,
                    0x048E, 0x0624, 0x0844, 0x0842, 0x0A62, 0x0C62, 0x0C84, 0x08A6});
    private static final Copy WATER_FLOOR_COPY = new Copy(0x12D708,
            new int[]{0x38, 0x39, 0x48, 0x49}, 400,
            0x120020, 0x120000, 0, null);
    /** Desert map mirrors this 8x16 half-apple (Genplus-gx QuickSave2). */
    private static final Copy DESERT_MIRRORED_MAP_COPY = new Copy(0x13EBE8,
            new int[]{0x174, 0x184}, 512,
            0x120040, 0x120000, 0, new int[]{
                    0x08CE, 0x0AEE, 0x0AA8, 0x0468, 0x046C, 0x0486, 0x0C86, 0x0EA6,
                    0x0466, 0x06AC, 0x048A, 0x0688, 0x0ACC, 0x08CE, 0x0468, 0x08CC});
    /**
     * Full 16x16 replacements for the two old mirrored routes. The extra two
     * tiles in each block were proven unused across every room/map variant
     * which loads that graphics bank, including the runtime-built definitions
     * and live VDP tables captured from those rooms.
     */
    private static final Copy ASYMMETRIC_LAVA_MAP_COPY = new Copy(0x1528E4,
            new int[]{0x148, 0x06D, 0x187, 0x0C9}, 512,
            0x120068, 0x120000, 0, MIRRORED_MAP_COPY.gamePaletteWords);
    private static final Copy ASYMMETRIC_DESERT_MAP_COPY = new Copy(0x13EBE8,
            new int[]{0x174, 0x057, 0x184, 0x0B7}, 512,
            0x120040, 0x120000, 0, DESERT_MIRRORED_MAP_COPY.gamePaletteWords);
    private static final int[] LAVA_ASSOCIATED_MAPS = {22, 78, 79, 86};
    private static final int[] DESERT_ASSOCIATED_MAPS = {43, 49, 50, 51, 84, 85};
    private static final MapDefinitionPatch[] ASYMMETRIC_MAP_PATCHES = {
            new MapDefinitionPatch(0x1A44F0, 78, 0x09E8,
                    new int[]{0x2148, 0x2948, 0x2187, 0x2987},
                    new int[]{0x2148, 0x206D, 0x2187, 0x20C9}),
            new MapDefinitionPatch(0x1A62A4, 79, 0x09E8,
                    new int[]{0x2148, 0x2948, 0x2187, 0x2987},
                    new int[]{0x2148, 0x206D, 0x2187, 0x20C9}),
            new MapDefinitionPatch(0x18BAA2, 49, 0x0EB0,
                    new int[]{0x2174, 0x2974, 0x2184, 0x2984},
                    new int[]{0x2174, 0x2057, 0x2184, 0x20B7}),
            new MapDefinitionPatch(0x18D9AE, 50, 0x0EB0,
                    new int[]{0x2174, 0x2974, 0x2184, 0x2984},
                    new int[]{0x2174, 0x2057, 0x2184, 0x20B7}),
            new MapDefinitionPatch(0x18FBE4, 51, 0x0EB0,
                    new int[]{0x2174, 0x2974, 0x2184, 0x2984},
                    new int[]{0x2174, 0x2057, 0x2184, 0x20B7}),
            new MapDefinitionPatch(0x1AAE7E, 84, 0x0300,
                    new int[]{0x2174, 0x2974, 0x2184, 0x2984},
                    new int[]{0x2174, 0x2057, 0x2184, 0x20B7}),
            new MapDefinitionPatch(0x1AC52A, 85, 0x0300,
                    new int[]{0x2174, 0x2974, 0x2184, 0x2984},
                    new int[]{0x2174, 0x2057, 0x2184, 0x20B7})
    };
    private static final int GRAPHICS_POINTER_BASE = 0x120000;
    private static final int MAP_POINTER_BASE = 0x15E000;
    private static final CompactBlock[] REGIONAL_COMPACT_BLOCKS = {
            new CompactBlock(0x12D708, 0x120020, GRAPHICS_POINTER_BASE,
                    10063, 0x12D708),
            new CompactBlock(0x13EBE8, 0x120040, GRAPHICS_POINTER_BASE,
                    11880, 0x13EBE8),
            // 0x1528E4 grows into the preceding block's old slot. That block
            // is first preserved in a separately guarded filler reservation.
            new CompactBlock(0x1528E4, 0x120068, GRAPHICS_POINTER_BASE,
                    0x1553BC - 0x1521CA, 0x1521CA),
            new CompactBlock(0x18BAA2, 0x15E0C4, MAP_POINTER_BASE,
                    7948, 0x18BAA2),
            new CompactBlock(0x18D9AE, 0x15E0C8, MAP_POINTER_BASE,
                    8757, 0x18D9AE),
            new CompactBlock(0x18FBE4, 0x15E0CC, MAP_POINTER_BASE,
                    8269, 0x18FBE4),
            new CompactBlock(0x1A44F0, 0x15E138, MAP_POINTER_BASE,
                    7603, 0x1A44F0),
            new CompactBlock(0x1A62A4, 0x15E13C, MAP_POINTER_BASE,
                    0x1EE000 - 0x1ED0FB, 0x1ED0FB),
            new CompactBlock(0x1AAE7E, 0x15E150, MAP_POINTER_BASE,
                    5804, 0x1AAE7E),
            new CompactBlock(0x1AC52A, 0x15E154, MAP_POINTER_BASE,
                    0x1E8000 - 0x1E6785, 0x1E6785)
    };
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
    /**
     * Late-game map packages: four row-major variants of the same apple. The
     * desert/water variants at E3AC0/E42C0 differ slightly from the two copies
     * found by the old exact-byte scan, but use the same tile order and palette
     * indices. They therefore share the normal 16x16 editor as well.
     */
    private static final int[] LATE_MAP_APPLE_OFFSETS = {
            0x0E36C0, 0x0E3AC0, 0x0E3EC0, 0x0E42C0
    };
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
    public static final String DEFAULT_MIRRORED_VIEW = "apple_gfx_out/manzana_simetrica_x8_VISTA.png";
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
            System.out.println("  AppleGraphics extract-symmetric [rom] [editPng] [viewPng]");
            System.out.println("  AppleGraphics sync-red [rom] [editPng]");
            System.out.println("  AppleGraphics sync-green [rom] [editPng]");
            System.out.println("  AppleGraphics sync-golden [rom] [editPng]");
            System.out.println("  AppleGraphics verify [rom] [redPng] [greenPng]");
            System.out.println("  AppleGraphics verify-all [rom]");
            System.out.println("  AppleGraphics verify-golden [rom] [editPng]");
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
        } else if (mode.equals("extract-symmetric")) {
            extractSymmetric(rom,
                    args.length > 2 ? args[2] : DEFAULT_MIRRORED_EDIT,
                    args.length > 3 ? args[3] : DEFAULT_MIRRORED_VIEW);
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

    /** Arranges the ordinary red-map and pickup routes for GraphicsInserter. */
    public static void syncRed(String romPath, String editPath) throws IOException {
        sync(romPath, editPath, "map apple", MAP_COPIES);
        sync(romPath, editPath, "pickup sprite apple", new Copy[]{SPRITE_PICKUP_COPY});
        syncEmbedded(romPath, editPath);
        syncOtrolao(romPath, editPath);
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

    /**
     * Inserts the normal red-editor drawing into all four uncompressed
     * late-game map variants. These are global resources, not
     * coordinate-specific room patches.
     */
    public static void insertLateMapCopies(String romPath, String editPath) throws IOException {
        Path edit = Paths.get(editPath);
        if (!Files.exists(edit)) {
            throw new IllegalStateException("missing normal-apple editor " + editPath);
        }
        byte[] replacement = decodeNormalEdit(editPath);
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        byte[] original = Files.readAllBytes(Paths.get(net.krusher.DefaultPaths.ROM));
        for (int offset : LATE_MAP_APPLE_OFFSETS) {
            if (offset < 0 || offset + replacement.length > rom.length
                    || offset + replacement.length > original.length) {
                throw new IllegalStateException(String.format(
                        "late-game map apple outside ROM at 0x%X", offset));
            }
            byte[] current = Arrays.copyOfRange(rom, offset, offset + replacement.length);
            byte[] vanilla = Arrays.copyOfRange(original, offset, offset + replacement.length);
            if (!Arrays.equals(current, vanilla) && !Arrays.equals(current, replacement)) {
                throw new IllegalStateException(String.format(
                        "late-game map apple at 0x%X was changed by another step", offset));
            }
            System.arraycopy(replacement, 0, rom, offset, replacement.length);
        }
        net.krusher.TextInserter.fixChecksum(rom);
        Files.write(Paths.get(romPath), rom);
        System.out.println("Inserted normal-apple editor into all four global raw map copies"
                + " at 0xE36C0, 0xE3AC0, 0xE3EC0 and 0xE42C0");
    }

    /**
     * Inserts the two runtime routes that the old raw-offset scan missed.
     *
     * This deliberately runs after IntroInserter. Letting the generic graphics
     * pass relocate these ten growing blocks first consumes enough of the
     * intro's allocator to push the ROM past 16 Mbit. The reservations below
     * were audited against every known pointer and are rechecked in the final
     * ROM before a byte is written.
     */
    public static void insertRegionalMapCopiesCompact(String romPath, String editPath)
            throws IOException {
        Path edit = Paths.get(editPath);
        if (!Files.exists(edit)) {
            throw new IllegalStateException("missing normal-apple editor " + editPath);
        }
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        if (rom.length != 0x200000) {
            throw new IllegalStateException("regional apple insertion requires a 2 MiB ROM");
        }
        byte[] original = Files.readAllBytes(Paths.get(net.krusher.DefaultPaths.ROM));

        verifyFreeTileReservation(rom, LAVA_ASSOCIATED_MAPS,
                new int[]{0x06D, 0x0C9}, "lava-room asymmetric apple");
        verifyFreeTileReservation(rom, DESERT_ASSOCIATED_MAPS,
                new int[]{0x057, 0x0B7}, "desert asymmetric apple");

        verifyCompactFiller(rom, 0x1EF69B, 0x1F0000);
        verifyCompactFiller(rom, 0x1ED0FB, 0x1EE000);
        verifyCompactFiller(rom, 0x1E6785, 0x1E8000);
        verifyNoCompactTargets(rom, 0x1EF69B, 0x1F0000);
        verifyNoCompactTargets(rom, 0x1ED0FB, 0x1EE000);
        verifyNoCompactTargets(rom, 0x1E6785, 0x1E8000);

        int displaced = resolveBlock(rom, GRAPHICS_POINTER_BASE, 0x120064);
        if (displaced != 0x1521CA) {
            throw new IllegalStateException(String.format(
                    "unexpected neighbouring graphics block at 0x%X", displaced));
        }
        java.util.List<PointerLocator.Reference> displacedRefs =
                PointerLocator.findAll(rom, displaced);
        if (displacedRefs.size() != 1
                || displacedRefs.get(0).pointerFieldAddr != 0x120064) {
            throw new IllegalStateException(
                    "neighbouring graphics block has unaccounted references");
        }
        byte[] displacedPacked = LzToshio.compress(LzToshio.decompress(rom, displaced));
        if (displacedPacked.length > 0x1F0000 - 0x1EF69B) {
            throw new IllegalStateException("neighbouring graphics block exceeds its reservation");
        }
        System.arraycopy(displacedPacked, 0, rom, 0x1EF69B, displacedPacked.length);
        writeU32(rom, 0x120064, 0x1EF69B - GRAPHICS_POINTER_BASE);

        byte[] drawing = decodeNormalEdit(editPath);
        java.util.Map<Integer, byte[]> replacements = new java.util.HashMap<>();

        byte[] waterDrawing = remapToGamePalette(drawing, WATER_MAP_COPY);
        replacements.put(WATER_MAP_COPY.blockOffset, patchLiveCopy(rom, original,
                WATER_MAP_COPY,
                composeOverFloor(readApple(original, WATER_FLOOR_COPY), waterDrawing),
                new int[0]));

        byte[] lavaDrawing = remapToGamePalette(drawing, ASYMMETRIC_LAVA_MAP_COPY);
        byte[] lavaFloor = mirrorSymmetricHalf(readApple(original, MIRRORED_MAP_FLOOR_COPY));
        replacements.put(ASYMMETRIC_LAVA_MAP_COPY.blockOffset, patchLiveCopy(rom, original,
                ASYMMETRIC_LAVA_MAP_COPY, composeOverFloor(lavaFloor, lavaDrawing),
                new int[]{1, 3}));

        replacements.put(ASYMMETRIC_DESERT_MAP_COPY.blockOffset,
                patchLiveCopy(rom, original, ASYMMETRIC_DESERT_MAP_COPY,
                        remapToGamePalette(drawing, ASYMMETRIC_DESERT_MAP_COPY),
                        new int[]{1, 3}));

        for (MapDefinitionPatch patch : ASYMMETRIC_MAP_PATCHES) {
            int pointerField = MAP_POINTER_BASE + patch.pointerIndex * 4;
            int live = resolveBlock(rom, MAP_POINTER_BASE, pointerField);
            if (live != patch.blockOffset) {
                throw new IllegalStateException(String.format(
                        "unexpected live map block 0x%X for original 0x%X",
                        live, patch.blockOffset));
            }
            byte[] map = LzToshio.decompress(rom, live);
            if (!wordsMatch(map, patch.byteOffset, patch.originalWords)) {
                throw new IllegalStateException(String.format(
                        "map block 0x%X definition changed before regional apple insertion",
                        patch.blockOffset));
            }
            for (int i = 0; i < patch.replacementWords.length; i++) {
                writeU16(map, patch.byteOffset + i * 2, patch.replacementWords[i]);
            }
            replacements.put(patch.blockOffset, map);
        }

        for (CompactBlock spec : REGIONAL_COMPACT_BLOCKS) {
            int live = resolveBlock(rom, spec.pointerBase, spec.pointerField);
            if (live != spec.originalBlock) {
                throw new IllegalStateException(String.format(
                        "unexpected live address 0x%X for compact block 0x%X",
                        live, spec.originalBlock));
            }
            byte[] updated = replacements.get(spec.originalBlock);
            if (updated == null) {
                throw new IllegalStateException(String.format(
                        "missing compact replacement for block 0x%X", spec.originalBlock));
            }
            byte[] packed = LzToshio.compress(updated);
            if (packed.length > spec.allocated) {
                throw new IllegalStateException(String.format(
                        "regional block 0x%X needs %d bytes; reservation has %d",
                        spec.originalBlock, packed.length, spec.allocated));
            }
            System.arraycopy(packed, 0, rom, spec.destination, packed.length);
            if (spec.destination != spec.originalBlock) {
                writeU32(rom, spec.pointerField, spec.destination - spec.pointerBase);
            }
            System.out.printf("Inserted regional apple block 0x%X at 0x%X (%d/%d bytes)%n",
                    spec.originalBlock, spec.destination, packed.length, spec.allocated);
        }

        net.krusher.TextInserter.fixChecksum(rom);
        Files.write(Paths.get(romPath), rom);
        System.out.println("Inserted water/desert apple routes without growing the 2 MiB ROM");
    }

    private static byte[] patchLiveCopy(byte[] rom, byte[] original, Copy copy,
                                        byte[] replacement, int[] reservedPositions) {
        int live = resolveCopyBlock(rom, copy);
        if (live != copy.blockOffset) {
            throw new IllegalStateException(String.format(
                    "unexpected live graphics block 0x%X for original 0x%X",
                    live, copy.blockOffset));
        }
        byte[] block = LzToshio.decompress(rom, live);
        byte[] vanilla = LzToshio.decompress(original, copy.blockOffset);
        for (int position : reservedPositions) {
            int tile = copy.tileIndices[position];
            int start = tile * TileRenderer.TILE_BYTES;
            if (!Arrays.equals(block, start, start + TileRenderer.TILE_BYTES,
                    vanilla, start, start + TileRenderer.TILE_BYTES)) {
                throw new IllegalStateException(String.format(
                        "reserved tile 0x%03X in block 0x%X is no longer free",
                        tile, copy.blockOffset));
            }
        }
        byte[] updated = Arrays.copyOf(block, block.length);
        for (int i = 0; i < copy.tileIndices.length; i++) {
            System.arraycopy(replacement, i * TileRenderer.TILE_BYTES, updated,
                    copy.tileIndices[i] * TileRenderer.TILE_BYTES, TileRenderer.TILE_BYTES);
        }
        return updated;
    }

    private static void verifyCompactFiller(byte[] rom, int start, int end) {
        int value = rom[start] & 255;
        for (int at = start + 1; at < end; at++) {
            if ((rom[at] & 255) != value) {
                throw new IllegalStateException(String.format(
                        "regional apple reservation 0x%X..0x%X is not free filler", start, end));
            }
        }
    }

    private static void verifyNoCompactTargets(byte[] rom, int start, int end) {
        for (int base : PointerLocator.TABLE_BASES) {
            for (int index = 0; index < 600; index++) {
                int field = base + index * 4;
                if (field + 4 > rom.length) break;
                int target = base + readU32(rom, field);
                if (target >= start && target < end) {
                    throw new IllegalStateException(String.format(
                            "table 0x%X entry %d targets regional reservation at 0x%X",
                            base, index, target));
                }
            }
        }
        for (int at = 0; at + 6 <= rom.length; at++) {
            int opcode = readU16(rom, at);
            if (opcode != 0x41F9 && opcode != 0x43F9 && opcode != 0x45F9
                    && opcode != 0x47F9 && opcode != 0x49F9 && opcode != 0x4BF9
                    && opcode != 0x4DF9 && opcode != 0x4FF9) continue;
            int target = readU32(rom, at + 2);
            if (target >= start && target < end) {
                throw new IllegalStateException(String.format(
                        "LEA at 0x%X targets regional reservation at 0x%X", at, target));
            }
        }
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
        return remapToGamePalette(source, editPalette(), copy);
    }

    private static byte[] remapToGamePalette(byte[] source, int[] from, Copy copy) {
        if (copy.gamePaletteWords == null) return source;
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

    private static byte[] composeOverFloor(byte[] floor, byte[] edit) {
        byte[] result = new byte[edit.length];
        for (int i = 0; i < edit.length; i++) {
            int ground = floor[i] & 255, drawing = edit[i] & 255;
            result[i] = (byte) (((drawing >> 4 == 0 ? ground >> 4 : drawing >> 4) << 4)
                    | ((drawing & 15) == 0 ? ground & 15 : drawing & 15));
        }
        return result;
    }

    private static void syncOtrolao(String romPath, String editPath) throws IOException {
        if (!Files.exists(Paths.get(editPath))) return;
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        byte[] drawing = remapToGamePalette(decodeNormalEdit(editPath), OTROLAO_MAP_COPY);
        syncCopy(rom, OTROLAO_MAP_COPY,
                composeOverFloor(readApple(rom, OTROLAO_FLOOR_COPY), drawing),
                "Otrolao apple over floor");
    }

    private static void syncWater(String romPath, String editPath) throws IOException {
        if (!Files.exists(Paths.get(editPath))) return;
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        byte[] drawing = remapToGamePalette(decodeNormalEdit(editPath), WATER_MAP_COPY);
        syncCopy(rom, WATER_MAP_COPY,
                composeOverFloor(readApple(rom, WATER_FLOOR_COPY), drawing),
                "water-zone apple over floor");
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

    /** Extracts the original half-apple; the map mirrors it to make the full object. */
    public static void extractSymmetric(String romPath, String editPath, String viewPath)
            throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        byte[] original = readApple(rom, MIRRORED_MAP_COPY);
        byte[] floor = readApple(rom, MIRRORED_MAP_FLOOR_COPY);
        byte[] drawing = new byte[original.length];
        for (int i = 0; i < drawing.length; i++) {
            int source = original[i] & 255;
            int ground = floor[i] & 255;
            int hi = source >> 4 == ground >> 4 ? 0 : source >> 4;
            int lo = (source & 15) == (ground & 15) ? 0 : source & 15;
            drawing[i] = (byte) (hi << 4 | lo);
        }

        Path edit = Paths.get(editPath);
        Path view = Paths.get(viewPath);
        if (edit.getParent() != null) Files.createDirectories(edit.getParent());
        if (view.getParent() != null) Files.createDirectories(view.getParent());
        int[] palette = symmetricEditPalette();
        TileRenderer.writePng(TileRenderer.renderTileSheet(drawing, palette, 1, 1),
                edit.toString());
        TileRenderer.writePng(TileRenderer.renderSpriteSheet(
                mirrorSymmetricHalf(drawing), palette, 2, 2, 1, 8, true),
                view.toString());
        System.out.println("Extracted original mirrored-map apple: " + edit
                + " (8x16 editable half; the game mirrors it)");
    }

    private static int tilePixel(byte[] data, int tilesWide, int x, int y) {
        int tile = y / 8 * tilesWide + x / 8;
        int packed = data[tile * TileRenderer.TILE_BYTES + (y & 7) * 4 + (x & 7) / 2] & 255;
        return (x & 1) == 0 ? packed >> 4 : packed & 15;
    }

    private static void setTilePixel(byte[] data, int tilesWide, int x, int y, int value) {
        int tile = y / 8 * tilesWide + x / 8;
        int at = tile * TileRenderer.TILE_BYTES + (y & 7) * 4 + (x & 7) / 2;
        int packed = data[at] & 255;
        data[at] = (byte) ((x & 1) == 0
                ? (value << 4 | packed & 15)
                : (packed & 0xF0 | value));
    }

    private static byte[] mirrorSymmetricHalf(byte[] half) {
        byte[] full = new byte[4 * TileRenderer.TILE_BYTES];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                setTilePixel(full, 2, x, y, tilePixel(half, 1, x < 8 ? x : 15 - x, y));
            }
        }
        return full;
    }

    private static void syncAsymmetricMapRoutes(String romPath, String editPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        byte[] drawing = decodeNormalEdit(editPath);
        boolean reservedSlotsOwned = asymmetricDefinitionsInstalled(rom);

        // Recheck the complete set of map variants every time. This is an
        // intentional hard stop: these slots may not be reused merely because
        // they look blank in one room or one savestate.
        verifyFreeTileReservation(rom, LAVA_ASSOCIATED_MAPS, new int[]{0x06D, 0x0C9},
                "lava-room asymmetric apple");
        verifyFreeTileReservation(rom, DESERT_ASSOCIATED_MAPS, new int[]{0x057, 0x0B7},
                "desert asymmetric apple");

        byte[] lavaDrawing = remapToGamePalette(drawing, ASYMMETRIC_LAVA_MAP_COPY);
        byte[] lavaFloor = mirrorSymmetricHalf(readApple(rom, MIRRORED_MAP_FLOOR_COPY));
        syncReservedCopy(rom, ASYMMETRIC_LAVA_MAP_COPY,
                composeOverFloor(lavaFloor, lavaDrawing),
                new int[]{1, 3}, reservedSlotsOwned,
                "full asymmetric lava-room objects");
        syncReservedCopy(rom, ASYMMETRIC_DESERT_MAP_COPY,
                remapToGamePalette(drawing, ASYMMETRIC_DESERT_MAP_COPY),
                new int[]{1, 3}, reservedSlotsOwned,
                "full asymmetric desert objects");

        for (MapDefinitionPatch patch : ASYMMETRIC_MAP_PATCHES) {
            syncMapDefinition(rom, patch);
        }
    }

    private static void verifyFreeTileReservation(byte[] rom, int[] mapIndices,
                                                   int[] tiles, String label) {
        for (int mapIndex : mapIndices) {
            byte[] map = readMapByIndex(rom, mapIndex);
            int end = Math.min(0x2000, map.length);
            for (int at = 0; at + 1 < end; at += 2) {
                int referenced = readU16(map, at) & 0x7FF;
                for (int tile : tiles) {
                    if (referenced == tile) {
                        throw new IllegalStateException(String.format(
                                "%s tile 0x%03X is used by map-table entry %d; ROM left untouched",
                                label, tile, mapIndex));
                    }
                }
            }
        }
    }

    private static void syncReservedCopy(byte[] rom, Copy copy, byte[] replacement,
                                         int[] reservedPositions, boolean alreadyOwned,
                                         String label) throws IOException {
        byte[] originalBlock = LzToshio.decompress(rom, copy.blockOffset);
        byte[] currentBlock = readCatalogSheet(rom, copy.blockOffset,
                copy.blockTileCount, copy.gfxPath());
        for (int position : reservedPositions) {
            int tile = copy.tileIndices[position];
            int tileStart = tile * TileRenderer.TILE_BYTES;
            boolean untouched = Arrays.equals(currentBlock, tileStart,
                    tileStart + TileRenderer.TILE_BYTES,
                    originalBlock, tileStart, tileStart + TileRenderer.TILE_BYTES);
            if (!untouched && !alreadyOwned) {
                throw new IllegalStateException(String.format(
                        "%s reserved tile 0x%03X contains another edit; refusing to overwrite it",
                        label, tile));
            }
        }
        syncCopy(rom, copy, replacement, label);
    }

    private static boolean asymmetricDefinitionsInstalled(byte[] rom) throws IOException {
        int current = 0;
        for (MapDefinitionPatch patch : ASYMMETRIC_MAP_PATCHES) {
            byte[] map = readCatalogSheet(rom, patch.blockOffset,
                    LzToshio.decompress(rom, patch.blockOffset).length / TileRenderer.TILE_BYTES,
                    patch.gfxPath());
            if (wordsMatch(map, patch.byteOffset, patch.originalWords)) {
                // Stock definition: this block has not claimed the new slots yet.
            }
            else if (wordsMatch(map, patch.byteOffset, patch.replacementWords)) current++;
            else {
                throw new IllegalStateException(String.format(
                        "map block 0x%X definition at 0x%X was changed by another edit",
                        patch.blockOffset, patch.byteOffset));
            }
        }
        // An interrupted sync may leave a mix with stock definitions. Any
        // generated definition proves this feature has already claimed its
        // two checked slots; the remaining definitions are completed below.
        return current != 0;
    }

    private static byte[] readCatalogSheet(byte[] rom, int blockOffset, int tileCount,
                                           String gfxPath) throws IOException {
        byte[] original = LzToshio.decompress(rom, blockOffset);
        byte[] current = Arrays.copyOf(original, original.length);
        Path gfx = Paths.get(gfxPath);
        if (!Files.exists(gfx)) return current;
        Bitmap sheet = TileRenderer.readPng(gfx.toString());
        int expectedHeight = ((tileCount + 15) / 16) * 8;
        if (sheet.getWidth() != 128 || sheet.getHeight() != expectedHeight) {
            throw new IllegalStateException(gfx + " must stay 128x" + expectedHeight);
        }
        byte[] decoded = TileRenderer.decodeTileSheet(sheet,
                TileRenderer.defaultGrayscalePalette(), 16, 1, tileCount);
        System.arraycopy(decoded, 0, current, 0, decoded.length);
        return current;
    }

    private static void syncMapDefinition(byte[] rom, MapDefinitionPatch patch) throws IOException {
        Path gfx = Paths.get(patch.gfxPath());
        byte[] current = readCatalogSheet(rom, patch.blockOffset,
                LzToshio.decompress(rom, patch.blockOffset).length / TileRenderer.TILE_BYTES,
                patch.gfxPath());
        boolean original = wordsMatch(current, patch.byteOffset, patch.originalWords);
        boolean alreadyPatched = wordsMatch(current, patch.byteOffset, patch.replacementWords);
        if (!original && !alreadyPatched) {
            throw new IllegalStateException(String.format(
                    "map block 0x%X definition at 0x%X was changed by another edit",
                    patch.blockOffset, patch.byteOffset));
        }
        if (alreadyPatched) return;
        for (int i = 0; i < patch.replacementWords.length; i++) {
            writeU16(current, patch.byteOffset + i * 2, patch.replacementWords[i]);
        }
        if (gfx.getParent() != null) Files.createDirectories(gfx.getParent());
        TileRenderer.writePng(TileRenderer.renderTileSheet(current,
                TileRenderer.defaultGrayscalePalette(), 16, 1), gfx.toString());
        System.out.printf("Expanded mirrored map definition in %s (entry %d)%n",
                gfx, patch.pointerIndex);
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
            byte[] expectedOtrolao = composeOverFloor(
                    readApple(originalRom, OTROLAO_FLOOR_COPY),
                    remapToGamePalette(expectedRed, OTROLAO_MAP_COPY));
            if (!Arrays.equals(expectedOtrolao, readApple(rom, OTROLAO_MAP_COPY))) {
                throw new IllegalStateException("Otrolao map apple still shows the original art");
            }
            byte[] expectedWater = composeOverFloor(
                    readApple(originalRom, WATER_FLOOR_COPY),
                    remapToGamePalette(expectedRed, WATER_MAP_COPY));
            if (!Arrays.equals(expectedWater, readApple(rom, WATER_MAP_COPY))) {
                throw new IllegalStateException("water-zone map apple still shows the original art");
            }
            for (int offset : LATE_MAP_APPLE_OFFSETS) {
                byte[] actual = Arrays.copyOfRange(rom, offset,
                        offset + expectedRed.length);
                if (!Arrays.equals(expectedRed, actual)) {
                    throw new IllegalStateException(String.format(
                            "late-game raw map apple still shows old art at 0x%X", offset));
                }
            }
            verifyAsymmetricMapRoutes(rom, redEditPath);
        }
    }

    private static void verifyAsymmetricMapRoutes(byte[] rom, String editPath) throws IOException {
        byte[] original = Files.readAllBytes(Paths.get(net.krusher.DefaultPaths.ROM));
        byte[] drawing = decodeNormalEdit(editPath);
        verifyFreeTileReservation(original, LAVA_ASSOCIATED_MAPS,
                new int[]{0x06D, 0x0C9}, "lava-room asymmetric apple");
        verifyFreeTileReservation(original, DESERT_ASSOCIATED_MAPS,
                new int[]{0x057, 0x0B7}, "desert asymmetric apple");

        byte[] expectedLava = composeOverFloor(
                mirrorSymmetricHalf(readApple(original, MIRRORED_MAP_FLOOR_COPY)),
                remapToGamePalette(drawing, ASYMMETRIC_LAVA_MAP_COPY));
        if (!Arrays.equals(expectedLava, readApple(rom, ASYMMETRIC_LAVA_MAP_COPY))) {
            throw new IllegalStateException("the full lava-room objects differ from the normal editor");
        }
        byte[] expectedDesert = remapToGamePalette(drawing, ASYMMETRIC_DESERT_MAP_COPY);
        if (!Arrays.equals(expectedDesert, readApple(rom, ASYMMETRIC_DESERT_MAP_COPY))) {
            throw new IllegalStateException("the full desert objects differ from the normal editor");
        }
        for (MapDefinitionPatch patch : ASYMMETRIC_MAP_PATCHES) {
            byte[] map = readMapByIndex(rom, patch.pointerIndex);
            if (!wordsMatch(map, patch.byteOffset, patch.replacementWords)) {
                throw new IllegalStateException(String.format(
                        "asymmetric apple map definition is incomplete at table entry %d",
                        patch.pointerIndex));
            }
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
    private static byte[] readMapByIndex(byte[] rom, int index) {
        int pointerField = 0x15E000 + index * 4;
        int address = 0x15E000 + readU32(rom, pointerField);
        if (address < 0 || address >= rom.length) {
            throw new IllegalStateException("map pointer outside ROM at table entry " + index);
        }
        return LzToshio.decompress(rom, address);
    }

    private static boolean wordsMatch(byte[] data, int offset, int[] words) {
        if (offset < 0 || offset + words.length * 2 > data.length) return false;
        for (int i = 0; i < words.length; i++) {
            if (readU16(data, offset + i * 2) != words[i]) return false;
        }
        return true;
    }

    private static int readU16(byte[] data, int offset) {
        return (data[offset] & 255) << 8 | data[offset + 1] & 255;
    }

    private static void writeU16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 8);
        data[offset + 1] = (byte) value;
    }

    private static void writeU32(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }

    private static int readU32(byte[] data, int offset) {
        return (data[offset] & 255) << 24 | (data[offset + 1] & 255) << 16
                | (data[offset + 2] & 255) << 8 | data[offset + 3] & 255;
    }

    static int resolveBlock(byte[] rom, int tableBase, int pointerField) {
        if (pointerField < 0 || pointerField > rom.length - 4) {
            throw new IllegalStateException("apple pointer field outside ROM");
        }
        int relative = readU32(rom, pointerField);
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

    private static int[] symmetricEditPalette() {
        int[] palette = genesisPalette(MIRRORED_MAP_COPY.gamePaletteWords);
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
