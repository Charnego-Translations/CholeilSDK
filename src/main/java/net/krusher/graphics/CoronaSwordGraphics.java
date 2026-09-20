package net.krusher.graphics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** Friendly extractor/inserter for the static pose and four swing poses of every Corona sword. */
public final class CoronaSwordGraphics {
    public static final int BLOCK_OFFSET = 0x54200;
    public static final int STATIC_BLOCK_OFFSET = BLOCK_OFFSET - 0x200;
    public static final int VARIANT_COUNT = 8;
    public static final int VARIANT_STRIDE = 0xA00;
    public static final int POSE_COUNT = 4;
    public static final int POSE_TILES_W = 4;
    public static final int POSE_TILES_H = 4;
    public static final int TILES_PER_POSE = POSE_TILES_W * POSE_TILES_H;
    public static final int TILES_PER_VARIANT = POSE_COUNT * TILES_PER_POSE;
    public static final int VARIANT_LENGTH = TILES_PER_VARIANT * TileRenderer.TILE_BYTES;
    public static final int STATIC_VARIANT_LENGTH = TILES_PER_POSE * TileRenderer.TILE_BYTES;
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
    public static final String DEFAULT_STATIC_EDIT = "special_gfx_out/espada_corona_estatica_EDITAME.png";
    public static final String DEFAULT_STATIC_VIEW = "special_gfx_out/espada_corona_estatica_x4_VISTA.png";

    /*
     * CRAM line 0 captured in the player-supplied slot 4 while the sword was
     * visible. It is used only to colour/decode the PNG. Insertion writes the
     * tile indices at 0x54000-0x58FFF and never changes CRAM or any palette data.
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
            System.out.println("  CoronaSwordGraphics extract [rom] [swingPng] [swingView] [staticPng] [staticView]");
            System.out.println("  CoronaSwordGraphics derive-static [swingPng] [staticPng] [staticView]");
            System.out.println("  CoronaSwordGraphics insert [rom] [swingPng] [outRom] [staticPng]");
            System.out.println("  CoronaSwordGraphics verify [rom] [swingPng] [staticPng]");
            return;
        }
        String mode = args[0];
        if (mode.equals("extract")) {
            extract(args.length > 1 ? args[1] : "Soleil (Spain).md",
                    args.length > 2 ? args[2] : DEFAULT_EDIT,
                    args.length > 3 ? args[3] : DEFAULT_VIEW,
                    args.length > 4 ? args[4] : DEFAULT_STATIC_EDIT,
                    args.length > 5 ? args[5] : DEFAULT_STATIC_VIEW);
        } else if (mode.equals("derive-static")) {
            deriveStaticFromSwing(args.length > 1 ? args[1] : DEFAULT_EDIT,
                    args.length > 2 ? args[2] : DEFAULT_STATIC_EDIT,
                    args.length > 3 ? args[3] : DEFAULT_STATIC_VIEW);
        } else if (mode.equals("insert")) {
            insert(args.length > 1 ? args[1] : "Choleil.md",
                    args.length > 2 ? args[2] : DEFAULT_EDIT,
                    args.length > 4 ? args[4] : DEFAULT_STATIC_EDIT,
                    args.length > 3 ? args[3] : "Choleil.md");
        } else if (mode.equals("verify")) {
            verify(args.length > 1 ? args[1] : "Soleil (Spain).md",
                    args.length > 2 ? args[2] : DEFAULT_EDIT,
                    args.length > 3 ? args[3] : DEFAULT_STATIC_EDIT);
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
        Bitmap editor = addEditorGrid(content, POSE_COUNT);
        TileRenderer.writePng(editor, edit.toString());
        TileRenderer.writePng(scale(editor, VIEW_SCALE), view.toString());
        System.out.println("Extracted Corona swords: " + edit
                + " (eight variants, four boxed 32x32 poses each)");
    }

    public static void extract(String romPath, String editPath, String viewPath,
            String staticEditPath, String staticViewPath) throws IOException {
        extract(romPath, editPath, viewPath);
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        Bitmap content = TileRenderer.renderSpriteSheet(readStaticTiles(rom), editPalette(),
                POSE_TILES_W, POSE_TILES_H, 1, 1, false);
        writeStaticEditor(addEditorGrid(content, 1), staticEditPath, staticViewPath);
        System.out.println("Extracted eight static Corona sword poses: " + staticEditPath);
    }

    /** Start the new static-pose editor from the first pose of Scorpion's swing sheet. */
    public static void deriveStaticFromSwing(String swingPath, String staticEditPath,
            String staticViewPath) throws IOException {
        Bitmap swing = TileRenderer.readPng(swingPath);
        validateSize(swing, Paths.get(swingPath), POSE_COUNT);
        int[] palette = editPalette();
        Bitmap content = Bitmap.indexed(POSE_SIZE, VARIANT_COUNT * POSE_SIZE, palette);
        for (int variant = 0; variant < VARIANT_COUNT; variant++) {
            copyToIndexed(swing, GRID_SIZE, GRID_SIZE + variant * CELL_STRIDE,
                    content, 0, variant * POSE_SIZE, POSE_SIZE, POSE_SIZE, palette);
        }
        writeStaticEditor(addEditorGrid(content, 1), staticEditPath, staticViewPath);
        System.out.println("Derived eight static sword poses from the edited swing sheet: " + staticEditPath);
    }

    private static void writeStaticEditor(Bitmap editor, String editPath, String viewPath) throws IOException {
        Path edit = Paths.get(editPath);
        Path view = Paths.get(viewPath);
        if (edit.getParent() != null) Files.createDirectories(edit.getParent());
        if (view.getParent() != null) Files.createDirectories(view.getParent());
        TileRenderer.writePng(editor, edit.toString());
        TileRenderer.writePng(scale(editor, VIEW_SCALE), view.toString());
    }

    public static void insert(String romPath, String editPath, String outPath) throws IOException {
        insert(romPath, editPath, null, outPath);
    }

    public static void insert(String romPath, String editPath, String staticEditPath,
            String outPath) throws IOException {
        Path edit = Paths.get(editPath);
        Path staticEdit = staticEditPath == null ? null : Paths.get(staticEditPath);
        if (!Files.exists(edit) && (staticEdit == null || !Files.exists(staticEdit))) {
            System.out.println("Corona sword PNGs not found; leaving all eight variants untouched.");
            return;
        }

        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        boolean changed = false;
        if (Files.exists(edit)) {
            Bitmap image = TileRenderer.readPng(edit.toString());
            validateSize(image, edit, POSE_COUNT);
            byte[] tiles = TileRenderer.decodeSpriteSheet(removeEditorGrid(image, POSE_COUNT), editPalette(),
                    POSE_TILES_W, POSE_TILES_H, POSE_COUNT, 1, TILE_COUNT, false);
            if (!Arrays.equals(readTiles(rom), tiles)) {
                writeTiles(rom, tiles);
                changed = true;
            }
        }
        if (staticEdit != null && Files.exists(staticEdit)) {
            Bitmap image = TileRenderer.readPng(staticEdit.toString());
            validateSize(image, staticEdit, 1);
            byte[] tiles = TileRenderer.decodeSpriteSheet(removeEditorGrid(image, 1), editPalette(),
                    POSE_TILES_W, POSE_TILES_H, 1, 1, VARIANT_COUNT * TILES_PER_POSE, false);
            if (!Arrays.equals(readStaticTiles(rom), tiles)) {
                writeStaticTiles(rom, tiles);
                changed = true;
            }
        }
        if (!changed) {
            System.out.println("Corona sword is unchanged; keeping ROM bytes untouched.");
            if (!samePath(romPath, outPath)) Files.write(Paths.get(outPath), rom);
            return;
        }

        net.krusher.TextInserter.fixChecksum(rom);
        Files.write(Paths.get(outPath), rom);
        System.out.println("Updated Corona sword swing/static tiles (palette untouched); wrote "
                + outPath);
    }

    public static void verify(String romPath, String editPath) throws IOException {
        verify(romPath, editPath, null);
    }

    public static void verify(String romPath, String editPath, String staticEditPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        Bitmap image = TileRenderer.readPng(editPath);
        validateSize(image, Paths.get(editPath), POSE_COUNT);
        byte[] decoded = TileRenderer.decodeSpriteSheet(removeEditorGrid(image, POSE_COUNT), editPalette(),
                POSE_TILES_W, POSE_TILES_H, POSE_COUNT, 1, TILE_COUNT, false);
        if (!Arrays.equals(readTiles(rom), decoded)) {
            throw new IllegalStateException("Corona sword PNG does not round-trip to the ROM tile bytes");
        }
        if (staticEditPath != null && Files.exists(Paths.get(staticEditPath))) {
            Bitmap staticImage = TileRenderer.readPng(staticEditPath);
            validateSize(staticImage, Paths.get(staticEditPath), 1);
            byte[] staticDecoded = TileRenderer.decodeSpriteSheet(removeEditorGrid(staticImage, 1), editPalette(),
                    POSE_TILES_W, POSE_TILES_H, 1, 1, VARIANT_COUNT * TILES_PER_POSE, false);
            if (!Arrays.equals(readStaticTiles(rom), staticDecoded)) {
                throw new IllegalStateException("Corona static sword PNG does not round-trip to the ROM tile bytes");
            }
            System.out.println("Corona sword round-trip verified: 640 swing/static tiles across eight variants match byte-for-byte.");
        } else {
            System.out.println("Corona sword round-trip verified: 512 swing tiles across eight variants match byte-for-byte.");
        }
    }

    private static byte[] readStaticTiles(byte[] rom) {
        int lastEnd = STATIC_BLOCK_OFFSET + (VARIANT_COUNT - 1) * VARIANT_STRIDE + STATIC_VARIANT_LENGTH;
        if (rom.length < lastEnd) throw new IllegalStateException("ROM is too short for the static Corona swords");
        byte[] tiles = new byte[VARIANT_COUNT * STATIC_VARIANT_LENGTH];
        for (int variant = 0; variant < VARIANT_COUNT; variant++) {
            System.arraycopy(rom, STATIC_BLOCK_OFFSET + variant * VARIANT_STRIDE,
                    tiles, variant * STATIC_VARIANT_LENGTH, STATIC_VARIANT_LENGTH);
        }
        return tiles;
    }

    private static void writeStaticTiles(byte[] rom, byte[] tiles) {
        for (int variant = 0; variant < VARIANT_COUNT; variant++) {
            System.arraycopy(tiles, variant * STATIC_VARIANT_LENGTH,
                    rom, STATIC_BLOCK_OFFSET + variant * VARIANT_STRIDE, STATIC_VARIANT_LENGTH);
        }
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
    private static Bitmap addEditorGrid(Bitmap content, int columns) {
        int[] palette = editPalette();
        Bitmap editor = Bitmap.indexed(GRID_SIZE + columns * CELL_STRIDE, EDIT_HEIGHT, palette);
        for (int y = 0; y < editor.getHeight(); y++) {
            for (int x = 0; x < editor.getWidth(); x++) {
                editor.setIndex(x, y, GRID_PALETTE_INDEX);
            }
        }
        for (int variant = 0; variant < VARIANT_COUNT; variant++) {
            for (int pose = 0; pose < columns; pose++) {
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
    private static Bitmap removeEditorGrid(Bitmap editor, int columns) {
        int[] palette = editPalette();
        Bitmap content = Bitmap.indexed(columns * POSE_SIZE, CONTENT_HEIGHT, palette);
        for (int variant = 0; variant < VARIANT_COUNT; variant++) {
            for (int pose = 0; pose < columns; pose++) {
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

    private static void validateSize(Bitmap image, Path path, int columns) {
        int expectedWidth = GRID_SIZE + columns * CELL_STRIDE;
        if (image.getWidth() != expectedWidth || image.getHeight() != EDIT_HEIGHT) {
            throw new IllegalStateException(path + " must stay " + expectedWidth + "x" + EDIT_HEIGHT
                    + " (eight rows of " + columns + " boxed 32x32 poses)");
        }
    }

    private static boolean samePath(String a, String b) {
        return Paths.get(a).toAbsolutePath().normalize().equals(Paths.get(b).toAbsolutePath().normalize());
    }
}
