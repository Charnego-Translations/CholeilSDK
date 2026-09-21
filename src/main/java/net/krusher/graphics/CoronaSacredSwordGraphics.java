package net.krusher.graphics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Editor for the five 16x16 orientations of the Sacred Sword while Corona is
 * carrying it. These tiles are a separate, uncompressed bank from the larger
 * attack/charge poses handled by {@link CoronaSwordGraphics}.
 */
public final class CoronaSacredSwordGraphics {
    public static final int RAW_OFFSET = 0x0F1C00;
    public static final int POSE_COUNT = 5;
    public static final int POSE_TILES_W = 2;
    public static final int POSE_TILES_H = 2;
    public static final int TILES_PER_POSE = POSE_TILES_W * POSE_TILES_H;
    public static final int TILE_COUNT = POSE_COUNT * TILES_PER_POSE;
    public static final int BYTE_LENGTH = TILE_COUNT * TileRenderer.TILE_BYTES;
    public static final int POSE_SIZE = POSE_TILES_W * TileRenderer.TILE_SIZE;
    public static final int GRID_SIZE = 1;
    public static final int CELL_STRIDE = POSE_SIZE + GRID_SIZE;
    public static final int EDIT_WIDTH = GRID_SIZE + POSE_COUNT * CELL_STRIDE;
    public static final int EDIT_HEIGHT = GRID_SIZE + CELL_STRIDE;
    public static final int VIEW_SCALE = 4;

    public static final String DEFAULT_EDIT =
            "special_gfx_out/espada_sagrada_equipada_EDITAME.png";
    public static final String DEFAULT_VIEW =
            "special_gfx_out/espada_sagrada_equipada_x4_VISTA.png";

    private static final int GRID_PALETTE_INDEX = 15;
    private static final int SOURCE_PALETTE_OFFSET = 0x000548;
    private static final int SOURCE_FIRST_TILE = 0xC0;
    private static final int SOURCE_TILE_COUNT = 256;

    /* CRAM line 0 captured from navajaAlbacete.State. It colours the editor;
     * insertion only writes tile bytes and never modifies any palette. */
    private static final byte[] CAPTURED_CRAM = {
        0x00, 0x00, 0x04, 0x24, 0x0E, 0x66, 0x04, 0x4C,
        0x06, (byte) 0xEE, 0x06, (byte) 0x8E, 0x08, (byte) 0xCE, 0x00, (byte) 0xAE,
        0x0E, (byte) 0xAA, 0x00, (byte) 0xC8, 0x00, (byte) 0xA4, 0x00, 0x64,
        0x02, 0x28, 0x0C, 0x40, 0x02, 0x22, 0x0E, (byte) 0xEE,
    };

    private CoronaSacredSwordGraphics() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("usage:");
            System.out.println("  CoronaSacredSwordGraphics extract [rom] [editPng] [viewPng]");
            System.out.println("  CoronaSacredSwordGraphics seed-from-gfx [rom] [gfxPng] [editPng] [viewPng]");
            System.out.println("  CoronaSacredSwordGraphics insert [rom] [editPng] [outRom]");
            System.out.println("  CoronaSacredSwordGraphics verify [rom] [editPng]");
            return;
        }
        String mode = args[0];
        if (mode.equals("extract")) {
            extract(arg(args, 1, "Soleil (Spain).md"),
                    arg(args, 2, DEFAULT_EDIT), arg(args, 3, DEFAULT_VIEW));
        } else if (mode.equals("seed-from-gfx")) {
            seedFromGfx(arg(args, 1, "Soleil (Spain).md"),
                    arg(args, 2, "gfx_out/gfx_0f2000.png"),
                    arg(args, 3, DEFAULT_EDIT), arg(args, 4, DEFAULT_VIEW));
        } else if (mode.equals("insert")) {
            insert(arg(args, 1, "Choleil.md"), arg(args, 2, DEFAULT_EDIT),
                    arg(args, 3, "Choleil.md"));
        } else if (mode.equals("verify")) {
            verify(arg(args, 1, "Choleil.md"), arg(args, 2, DEFAULT_EDIT));
        } else {
            throw new IllegalArgumentException("unknown mode: " + mode);
        }
    }

    /** Extracts the currently installed five held-sword orientations. */
    public static void extract(String romPath, String editPath, String viewPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        requireRomRange(rom);
        byte[] tiles = Arrays.copyOfRange(rom, RAW_OFFSET, RAW_OFFSET + BYTE_LENGTH);
        writeEditors(tiles, editPath, viewPath);
        System.out.println("Extracted equipped Sacred Sword: " + editPath
                + " (five boxed 16x16 orientations)");
    }

    /**
     * Seeds the held Sacred Sword editor with tiles C0-D3 from Scorpion's
     * existing gfx_0f2000 sheet. The source bank is the normal sword; copying
     * it here makes the same Navaja de Albacete art available to the separate
     * Sacred Sword bank without coupling the two editors afterwards.
     */
    public static void seedFromGfx(String romPath, String gfxPath,
            String editPath, String viewPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        if (rom.length < SOURCE_PALETTE_OFFSET + 32) {
            throw new IllegalStateException("ROM is too short for the source palette");
        }
        Bitmap source = TileRenderer.readPng(gfxPath);
        if (source.getWidth() < 16 * TileRenderer.TILE_SIZE
                || source.getHeight() < 16 * TileRenderer.TILE_SIZE) {
            throw new IllegalStateException(gfxPath + " must contain the 16x16 tile sheet");
        }
        int[] sourcePalette = TileRenderer.readGenesisPalette(rom, SOURCE_PALETTE_OFFSET);
        byte[] allTiles = TileRenderer.decodeTileSheet(source, sourcePalette,
                16, 1, SOURCE_TILE_COUNT);
        int first = SOURCE_FIRST_TILE * TileRenderer.TILE_BYTES;
        byte[] heldTiles = Arrays.copyOfRange(allTiles, first, first + BYTE_LENGTH);
        remapNormalBladeToSacred(heldTiles);
        writeEditors(heldTiles, editPath, viewPath);
        System.out.println("Seeded equipped Sacred Sword from Scorpion's C0-D3 poses"
                + " with the Sacred yellow-blade indices: " + editPath);
    }

    public static void insert(String romPath, String editPath, String outPath) throws IOException {
        Path edit = Paths.get(editPath);
        if (!Files.exists(edit)) {
            System.out.println("Equipped Sacred Sword PNG not found; leaving its raw bank untouched.");
            return;
        }
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        requireRomRange(rom);
        byte[] tiles = decodeEditor(edit);
        byte[] current = Arrays.copyOfRange(rom, RAW_OFFSET, RAW_OFFSET + BYTE_LENGTH);
        if (Arrays.equals(current, tiles)) {
            System.out.println("Equipped Sacred Sword is unchanged; keeping ROM bytes untouched.");
            if (!samePath(romPath, outPath)) Files.write(Paths.get(outPath), rom);
            return;
        }
        System.arraycopy(tiles, 0, rom, RAW_OFFSET, BYTE_LENGTH);
        net.krusher.TextInserter.fixChecksum(rom);
        Files.write(Paths.get(outPath), rom);
        System.out.println("Updated equipped Sacred Sword at 0x0F1C00-0x0F1E7F"
                + " (palette untouched); wrote " + outPath);
    }

    public static void verify(String romPath, String editPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        requireRomRange(rom);
        byte[] decoded = decodeEditor(Paths.get(editPath));
        byte[] installed = Arrays.copyOfRange(rom, RAW_OFFSET, RAW_OFFSET + BYTE_LENGTH);
        if (!Arrays.equals(installed, decoded)) {
            throw new IllegalStateException("Equipped Sacred Sword PNG does not round-trip to ROM 0x0F1C00");
        }
        System.out.println("Equipped Sacred Sword verified: all five 16x16 orientations match byte-for-byte.");
    }

    public static void verifyAvailable(String romPath, String editPath) throws IOException {
        if (!Files.exists(Paths.get(editPath))) {
            System.out.println("Equipped Sacred Sword PNG is disabled; verification skipped.");
            return;
        }
        verify(romPath, editPath);
    }

    private static void writeEditors(byte[] tiles, String editPath, String viewPath) throws IOException {
        Bitmap content = TileRenderer.renderSpriteSheet(tiles, editPalette(),
                POSE_TILES_W, POSE_TILES_H, POSE_COUNT, 1, false);
        Bitmap editor = addGrid(content);
        Path edit = Paths.get(editPath);
        Path view = Paths.get(viewPath);
        if (edit.getParent() != null) Files.createDirectories(edit.getParent());
        if (view.getParent() != null) Files.createDirectories(view.getParent());
        TileRenderer.writePng(editor, edit.toString());
        TileRenderer.writePng(scale(editor, VIEW_SCALE), view.toString());
    }

    private static byte[] decodeEditor(Path edit) throws IOException {
        Bitmap image = TileRenderer.readPng(edit.toString());
        if (image.getWidth() != EDIT_WIDTH || image.getHeight() != EDIT_HEIGHT) {
            throw new IllegalStateException(edit + " must stay " + EDIT_WIDTH + "x" + EDIT_HEIGHT
                    + " (five boxed 16x16 orientations)");
        }
        return TileRenderer.decodeSpriteSheet(removeGrid(image), editPalette(),
                POSE_TILES_W, POSE_TILES_H, POSE_COUNT, 1, TILE_COUNT, false);
    }

    private static Bitmap addGrid(Bitmap content) {
        int[] palette = editPalette();
        Bitmap editor = Bitmap.indexed(EDIT_WIDTH, EDIT_HEIGHT, palette);
        fill(editor, GRID_PALETTE_INDEX);
        for (int pose = 0; pose < POSE_COUNT; pose++) {
            copy(content, pose * POSE_SIZE, 0, editor,
                    GRID_SIZE + pose * CELL_STRIDE, GRID_SIZE, POSE_SIZE, POSE_SIZE, palette);
        }
        return editor;
    }

    private static Bitmap removeGrid(Bitmap editor) {
        int[] palette = editPalette();
        Bitmap content = Bitmap.indexed(POSE_COUNT * POSE_SIZE, POSE_SIZE, palette);
        for (int pose = 0; pose < POSE_COUNT; pose++) {
            copy(editor, GRID_SIZE + pose * CELL_STRIDE, GRID_SIZE,
                    content, pose * POSE_SIZE, 0, POSE_SIZE, POSE_SIZE, palette);
        }
        return content;
    }

    private static Bitmap scale(Bitmap source, int factor) {
        int[] palette = editPalette();
        Bitmap scaled = Bitmap.indexed(source.getWidth() * factor, source.getHeight() * factor, palette);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int index = nearest(source.getRgb(x, y), palette);
                for (int dy = 0; dy < factor; dy++) {
                    for (int dx = 0; dx < factor; dx++) {
                        scaled.setIndex(x * factor + dx, y * factor + dy, index);
                    }
                }
            }
        }
        return scaled;
    }

    private static void fill(Bitmap image, int index) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.setIndex(x, y, index);
        }
    }

    private static void copy(Bitmap source, int sx, int sy, Bitmap destination,
            int dx, int dy, int width, int height, int[] palette) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                destination.setIndex(dx + x, dy + y,
                        nearest(source.getRgb(sx + x, sy + y), palette));
            }
        }
    }

    private static int nearest(int argb, int[] palette) {
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            int dr = red - ((palette[i] >> 16) & 0xFF);
            int dg = green - ((palette[i] >> 8) & 0xFF);
            int db = blue - (palette[i] & 0xFF);
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static int[] editPalette() {
        int[] palette = TileRenderer.readGenesisPalette(CAPTURED_CRAM, 0);
        palette[0] = 0xFFFF00FF;
        return palette;
    }

    private static void requireRomRange(byte[] rom) {
        if (rom.length < RAW_OFFSET + BYTE_LENGTH) {
            throw new IllegalStateException("ROM is too short for the equipped Sacred Sword bank");
        }
    }

    /**
     * Scorpion's large normal and Sacred Sword poses use the same Navaja de
     * Albacete outline. Comparing their palette indices gives this exact blade
     * conversion: dark/light blues 2,F,8,D become the Sacred 3,4,7,C colours.
     * The handle indices stay unchanged.
     */
    private static void remapNormalBladeToSacred(byte[] tiles) {
        int[] map = { 0, 1, 3, 3, 4, 5, 6, 7, 7, 9, 10, 11, 12, 12, 14, 4 };
        for (int i = 0; i < tiles.length; i++) {
            int value = tiles[i] & 0xFF;
            tiles[i] = (byte) ((map[value >>> 4] << 4) | map[value & 0x0F]);
        }
    }

    private static boolean samePath(String a, String b) {
        return Paths.get(a).toAbsolutePath().normalize()
                .equals(Paths.get(b).toAbsolutePath().normalize());
    }

    private static String arg(String[] args, int index, String fallback) {
        return args.length > index ? args[index] : fallback;
    }
}
