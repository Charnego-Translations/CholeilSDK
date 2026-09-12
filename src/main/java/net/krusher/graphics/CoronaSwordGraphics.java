package net.krusher.graphics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** Friendly extractor/inserter for every Corona sword and its four swing poses. */
public final class CoronaSwordGraphics {
    public static final int BLOCK_OFFSET = 0x54200;
    public static final int VARIANT_COUNT = 8;
    public static final int VARIANT_STRIDE = 0xA00;
    public static final int POSE_COUNT = 4;
    public static final int POSE_TILES_W = 4;
    public static final int POSE_TILES_H = 4;
    public static final int TILES_PER_POSE = POSE_TILES_W * POSE_TILES_H;
    public static final int TILES_PER_VARIANT = POSE_COUNT * TILES_PER_POSE;
    public static final int VARIANT_LENGTH = TILES_PER_VARIANT * TileRenderer.TILE_BYTES;
    public static final int TILE_COUNT = VARIANT_COUNT * TILES_PER_VARIANT;
    public static final int BLOCK_LENGTH = VARIANT_COUNT * VARIANT_LENGTH;
    public static final int POSE_SIZE = POSE_TILES_W * TileRenderer.TILE_SIZE;
    public static final int CONTENT_WIDTH = POSE_COUNT * POSE_SIZE;
    public static final int CONTENT_HEIGHT = VARIANT_COUNT * POSE_SIZE;
    public static final int GRID_SIZE = 1;
    public static final int CELL_STRIDE = POSE_SIZE + GRID_SIZE;
    public static final int EDIT_WIDTH = GRID_SIZE + POSE_COUNT * CELL_STRIDE;
    public static final int EDIT_HEIGHT = GRID_SIZE + VARIANT_COUNT * CELL_STRIDE;
    public static final int VIEW_SCALE = 4;
    private static final int GRID_PALETTE_INDEX = 15;

    public static final String DEFAULT_EDIT = "special_gfx_out/espada_corona_EDITAME.png";
    public static final String DEFAULT_VIEW = "special_gfx_out/espada_corona_x4_VISTA.png";

    /*
     * CRAM line 0 captured in the player-supplied slot 4 while the sword was
     * visible. It is used only to colour/decode the PNG. Insertion writes the
     * tile indices at 0x54200 and never changes CRAM or any palette data.
     */
    private static final byte[] CAPTURED_CRAM = {
        0x00, 0x00, 0x04, 0x24, 0x0E, 0x66, 0x04, 0x4C,
        0x06, (byte) 0xEE, 0x06, (byte) 0x8E, 0x08, (byte) 0xCE, 0x00, (byte) 0xAE,
        0x0E, (byte) 0xAA, 0x00, (byte) 0xC8, 0x00, (byte) 0xA4, 0x00, 0x64,
        0x02, 0x28, 0x0C, 0x40, 0x02, 0x22, 0x0E, (byte) 0xEE,
    };

    private CoronaSwordGraphics() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("usage:");
            System.out.println("  CoronaSwordGraphics extract [rom] [editPng] [viewPng]");
            System.out.println("  CoronaSwordGraphics insert [rom] [editPng] [outRom]");
            System.out.println("  CoronaSwordGraphics verify [rom] [editPng]");
            return;
        }
        String mode = args[0];
        if (mode.equals("extract")) {
            extract(args.length > 1 ? args[1] : "Soleil (Spain).md",
                    args.length > 2 ? args[2] : DEFAULT_EDIT,
                    args.length > 3 ? args[3] : DEFAULT_VIEW);
        } else if (mode.equals("insert")) {
            insert(args.length > 1 ? args[1] : "Choleil.md",
                    args.length > 2 ? args[2] : DEFAULT_EDIT,
                    args.length > 3 ? args[3] : "Choleil.md");
        } else if (mode.equals("verify")) {
            verify(args.length > 1 ? args[1] : "Soleil (Spain).md",
                    args.length > 2 ? args[2] : DEFAULT_EDIT);
        } else {
            throw new IllegalArgumentException("unknown mode: " + mode);
        }
    }

    public static void extract(String romPath, String editPath, String viewPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        byte[] tiles = readTiles(rom);
        Path edit = Paths.get(editPath);
        Path view = Paths.get(viewPath);
        if (edit.getParent() != null) Files.createDirectories(edit.getParent());
        if (view.getParent() != null) Files.createDirectories(view.getParent());

        Bitmap content = TileRenderer.renderSpriteSheet(tiles, editPalette(),
                POSE_TILES_W, POSE_TILES_H, POSE_COUNT, 1, false);
        Bitmap editor = addEditorGrid(content);
        TileRenderer.writePng(editor, edit.toString());
        TileRenderer.writePng(scale(editor, VIEW_SCALE), view.toString());
        System.out.println("Extracted Corona swords: " + edit
                + " (eight variants, four boxed 32x32 poses each)");
    }

    public static void insert(String romPath, String editPath, String outPath) throws IOException {
        Path edit = Paths.get(editPath);
        if (!Files.exists(edit)) {
            System.out.println("Corona sword edit PNG not found; leaving all eight variants untouched.");
            return;
        }

        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        Bitmap image = TileRenderer.readPng(edit.toString());
        validateSize(image, edit);
        byte[] tiles = TileRenderer.decodeSpriteSheet(removeEditorGrid(image), editPalette(),
                POSE_TILES_W, POSE_TILES_H, POSE_COUNT, 1, TILE_COUNT, false);
        byte[] original = readTiles(rom);

        if (Arrays.equals(original, tiles)) {
            System.out.println("Corona sword is unchanged; keeping ROM bytes untouched.");
            if (!samePath(romPath, outPath)) Files.write(Paths.get(outPath), rom);
            return;
        }

        writeTiles(rom, tiles);
        net.krusher.TextInserter.fixChecksum(rom);
        Files.write(Paths.get(outPath), rom);
        System.out.println("Updated all eight Corona sword variants (palette and 0x200-byte gaps untouched); wrote "
                + outPath);
    }

    public static void verify(String romPath, String editPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        Bitmap image = TileRenderer.readPng(editPath);
        validateSize(image, Paths.get(editPath));
        byte[] decoded = TileRenderer.decodeSpriteSheet(removeEditorGrid(image), editPalette(),
                POSE_TILES_W, POSE_TILES_H, POSE_COUNT, 1, TILE_COUNT, false);
        if (!Arrays.equals(readTiles(rom), decoded)) {
            throw new IllegalStateException("Corona sword PNG does not round-trip to the ROM tile bytes");
        }
        System.out.println("Corona sword round-trip verified: 512 tiles across eight variants match byte-for-byte.");
    }

    private static byte[] readTiles(byte[] rom) {
        int lastEnd = BLOCK_OFFSET + (VARIANT_COUNT - 1) * VARIANT_STRIDE + VARIANT_LENGTH;
        if (rom.length < lastEnd) {
            throw new IllegalStateException("ROM is too short for the Corona sword block");
        }
        byte[] tiles = new byte[BLOCK_LENGTH];
        for (int variant = 0; variant < VARIANT_COUNT; variant++) {
            int source = BLOCK_OFFSET + variant * VARIANT_STRIDE;
            System.arraycopy(rom, source, tiles, variant * VARIANT_LENGTH, VARIANT_LENGTH);
        }
        return tiles;
    }

    private static void writeTiles(byte[] rom, byte[] tiles) {
        for (int variant = 0; variant < VARIANT_COUNT; variant++) {
            int destination = BLOCK_OFFSET + variant * VARIANT_STRIDE;
            System.arraycopy(tiles, variant * VARIANT_LENGTH, rom, destination, VARIANT_LENGTH);
        }
    }

    private static int[] palette() {
        return TileRenderer.readGenesisPalette(CAPTURED_CRAM, 0);
    }

    private static int[] editPalette() {
        int[] palette = palette();
        palette[0] = 0xFFFF00FF;
        return palette;
    }

    /** Adds guide-only one-pixel boxes around every 32x32 pose. */
    private static Bitmap addEditorGrid(Bitmap content) {
        int[] palette = editPalette();
        Bitmap editor = Bitmap.indexed(EDIT_WIDTH, EDIT_HEIGHT, palette);
        for (int y = 0; y < EDIT_HEIGHT; y++) {
            for (int x = 0; x < EDIT_WIDTH; x++) {
                editor.setIndex(x, y, GRID_PALETTE_INDEX);
            }
        }
        for (int variant = 0; variant < VARIANT_COUNT; variant++) {
            for (int pose = 0; pose < POSE_COUNT; pose++) {
                int sourceX = pose * POSE_SIZE;
                int sourceY = variant * POSE_SIZE;
                int destinationX = GRID_SIZE + pose * CELL_STRIDE;
                int destinationY = GRID_SIZE + variant * CELL_STRIDE;
                copyToIndexed(content, sourceX, sourceY, editor,
                        destinationX, destinationY, POSE_SIZE, POSE_SIZE, palette);
            }
        }
        return editor;
    }

    /** Removes guide pixels before decoding, so boxes can never enter the ROM. */
    private static Bitmap removeEditorGrid(Bitmap editor) {
        int[] palette = editPalette();
        Bitmap content = Bitmap.indexed(CONTENT_WIDTH, CONTENT_HEIGHT, palette);
        for (int variant = 0; variant < VARIANT_COUNT; variant++) {
            for (int pose = 0; pose < POSE_COUNT; pose++) {
                int sourceX = GRID_SIZE + pose * CELL_STRIDE;
                int sourceY = GRID_SIZE + variant * CELL_STRIDE;
                int destinationX = pose * POSE_SIZE;
                int destinationY = variant * POSE_SIZE;
                copyToIndexed(editor, sourceX, sourceY, content,
                        destinationX, destinationY, POSE_SIZE, POSE_SIZE, palette);
            }
        }
        return content;
    }

    private static Bitmap scale(Bitmap source, int factor) {
        int[] palette = editPalette();
        Bitmap scaled = Bitmap.indexed(source.getWidth() * factor, source.getHeight() * factor, palette);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int index = nearestPaletteIndex(source.getRgb(x, y), palette);
                for (int dy = 0; dy < factor; dy++) {
                    for (int dx = 0; dx < factor; dx++) {
                        scaled.setIndex(x * factor + dx, y * factor + dy, index);
                    }
                }
            }
        }
        return scaled;
    }

    private static void copyToIndexed(Bitmap source, int sourceX, int sourceY,
            Bitmap destination, int destinationX, int destinationY,
            int width, int height, int[] palette) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = nearestPaletteIndex(source.getRgb(sourceX + x, sourceY + y), palette);
                destination.setIndex(destinationX + x, destinationY + y, index);
            }
        }
    }

    private static int nearestPaletteIndex(int argb, int[] palette) {
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

    private static void validateSize(Bitmap image, Path path) {
        if (image.getWidth() != EDIT_WIDTH || image.getHeight() != EDIT_HEIGHT) {
            throw new IllegalStateException(path + " must stay " + EDIT_WIDTH + "x" + EDIT_HEIGHT
                    + " (eight rows of four boxed 32x32 poses)");
        }
    }

    private static boolean samePath(String a, String b) {
        return Paths.get(a).toAbsolutePath().normalize().equals(Paths.get(b).toAbsolutePath().normalize());
    }
}
