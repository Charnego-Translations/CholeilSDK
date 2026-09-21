package net.krusher.graphics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import net.krusher.TextInserter;

/** Eight 16x24 dancing-flower poses plus the shared 16x8 stem in the park. */
public final class FlowerParkGraphics {
    public static final String EDIT = "special_gfx_out/flor_parque_EDITAME.png";
    public static final String VIEW = "special_gfx_out/flor_parque_x4_VISTA.png";
    public static final String PALETTE = "special_gfx_out/flor_parque_PALETA.png";
    public static final String PARK_GFX = "gfx_out/gfx_14d3ae.png";

    static final int ANIMATION_ROM = 0x0D3C60;
    static final int FRAME_COUNT = 8;
    static final int FRAME_WIDTH = 16;
    static final int FRAME_HEIGHT = 32;
    static final int ANIMATED_HEIGHT = 24;
    static final int TILES_PER_FRAME = 6;
    static final int FRAME_BYTES = TILES_PER_FRAME * 32;
    static final int PARK_BLOCK = 0x14D3AE;
    static final int PARK_TILES = 480;
    static final int STEM_OFFSET = 0x1A40; // VRAM tiles 0x1D2, 0x1D3, after load at tile 0x100
    static final int STEM_BYTES = 64;
    static final int SHEET_WIDTH = FRAME_WIDTH * 4;
    static final int SHEET_HEIGHT = FRAME_HEIGHT * 2;

    // CRAM line 1 captured from FlorParque.State, with the flower visible.
    // Index 0 is transparent in the Mega Drive plane; index D deliberately
    // has the same RGB value. Keep the *indices*, not just their RGB colors.
    static final byte[] CRAM = {
        0x06, (byte) 0xCA, 0x0E, (byte) 0xEE, 0x08, (byte) 0x88, 0x04, (byte) 0xAE,
        0x02, 0x6A, 0x06, 0x64, 0x06, 0x66, 0x0A, (byte) 0xAA,
        0x04, 0x66, 0x08, (byte) 0xCC, 0x06, (byte) 0xAA, 0x00, (byte) 0x82,
        0x02, (byte) 0xA6, 0x06, (byte) 0xCA, 0x04, (byte) 0x88, 0x0A, (byte) 0xEC
    };

    private FlowerParkGraphics() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("FlowerParkGraphics extract [originalRom] | sync-stem [originalRom] | insert [patchedRom]");
            return;
        }
        switch (args[0]) {
            case "extract" -> extract(args.length > 1 ? args[1] : "Soleil (Spain).md");
            case "sync-stem" -> syncStem(args.length > 1 ? args[1] : "Soleil (Spain).md");
            case "insert" -> insert(args.length > 1 ? args[1] : "Choleil.md");
            default -> throw new IllegalArgumentException("unknown mode: " + args[0]);
        }
    }

    public static void extract(String originalRom) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(originalRom));
        byte[] park = parkBlock(rom);
        Bitmap sheet = Bitmap.indexed(SHEET_WIDTH, SHEET_HEIGHT, palette());
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            int source = ANIMATION_ROM + frame * FRAME_BYTES;
            byte[] top = Arrays.copyOfRange(rom, source, source + FRAME_BYTES);
            paintFrameTop(sheet, frame, top);
            paintStem(sheet, frame, Arrays.copyOfRange(park, STEM_OFFSET, STEM_OFFSET + STEM_BYTES));
        }
        write(EDIT, sheet);
        write(VIEW, scaled(sheet, 4));
        Bitmap swatches = Bitmap.indexed(16 * 16, 16, palette());
        for (int y = 0; y < 16; y++) for (int x = 0; x < 256; x++) swatches.setIndex(x, y, x / 16);
        write(PALETTE, swatches);
        System.out.println("Park flower: 8 frames, 16x32 each; bottom 8 pixels are shared.");
    }

    /** Copy only the edited common stem into the ordinary compressed park PNG. */
    public static void syncStem(String originalRom) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(originalRom));
        parkBlock(rom); // Fail before changing anything if this is not the expected game.
        Bitmap edit = readEditor();
        byte[] stem = decodeStem(edit);
        Path path = Paths.get(PARK_GFX);
        if (!Files.exists(path)) throw new IOException(PARK_GFX + " is missing; extract the graphics first");
        Bitmap gfx = Png.read(path.toString());
        if (gfx.getWidth() != 128 || gfx.getHeight() != 240) {
            throw new IOException(PARK_GFX + " must stay 128x240");
        }
        byte[] decoded = TileRenderer.decodeTileSheet(gfx, TileRenderer.defaultGrayscalePalette(),
                16, 1, PARK_TILES);
        if (Arrays.equals(decoded, STEM_OFFSET, STEM_OFFSET + STEM_BYTES, stem, 0, STEM_BYTES)) return;
        System.arraycopy(stem, 0, decoded, STEM_OFFSET, STEM_BYTES);
        TileRenderer.writePng(TileRenderer.renderTileSheet(decoded,
                TileRenderer.defaultGrayscalePalette(), 16, 1), path.toString());
        System.out.println("Updated the two shared stem tiles in " + PARK_GFX);
    }

    /** Patch the eight fixed-size raw poses after generic graphic insertion. */
    public static void insert(String patchedRom) throws IOException {
        Bitmap edit = readEditor();
        Path path = Paths.get(patchedRom);
        byte[] rom = Files.readAllBytes(path);
        if (!patchFrames(rom, edit)) return;
        TextInserter.fixChecksum(rom);
        Files.write(path, rom);
        System.out.println("Inserted all 8 park flower poses into " + patchedRom);
    }

    static boolean patchFrames(byte[] rom, Bitmap editor) throws IOException {
        byte[] frames = decodeFrames(editor);
        if (rom.length < ANIMATION_ROM + frames.length) throw new IOException("ROM is too short for the park flower");
        if (Arrays.equals(rom, ANIMATION_ROM, ANIMATION_ROM + frames.length, frames, 0, frames.length)) return false;
        System.arraycopy(frames, 0, rom, ANIMATION_ROM, frames.length);
        return true;
    }

    static Bitmap readEditor() throws IOException {
        Bitmap image = Png.read(EDIT);
        if (image.getWidth() != SHEET_WIDTH || image.getHeight() != SHEET_HEIGHT) {
            throw new IOException(EDIT + " must stay 64x64 (4 columns by 2 rows of 16x32 frames)");
        }
        if (!Arrays.equals(image.palette(), palette())) {
            throw new IOException(EDIT + " must remain indexed with the exact 16-color park palette and index order");
        }
        for (int frame = 1; frame < FRAME_COUNT; frame++) {
            for (int y = ANIMATED_HEIGHT; y < FRAME_HEIGHT; y++) {
                for (int x = 0; x < FRAME_WIDTH; x++) {
                    if (pixel(image, frame, x, y) != pixel(image, 0, x, y)) {
                        throw new IOException("The bottom 8 pixels are one shared stem: edit them identically in all 8 frames");
                    }
                }
            }
        }
        return image;
    }

    static byte[] decodeFrames(Bitmap sheet) {
        byte[] result = new byte[FRAME_COUNT * FRAME_BYTES];
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            for (int tileY = 0; tileY < 3; tileY++) for (int tileX = 0; tileX < 2; tileX++) {
                int tile = tileY * 2 + tileX;
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x += 2) {
                    int left = pixel(sheet, frame, tileX * 8 + x, tileY * 8 + y);
                    int right = pixel(sheet, frame, tileX * 8 + x + 1, tileY * 8 + y);
                    result[frame * FRAME_BYTES + tile * 32 + y * 4 + x / 2] = (byte) (left << 4 | right);
                }
            }
        }
        return result;
    }

    static byte[] decodeStem(Bitmap sheet) {
        byte[] result = new byte[STEM_BYTES];
        for (int tileX = 0; tileX < 2; tileX++) for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x += 2) {
                int left = pixel(sheet, 0, tileX * 8 + x, ANIMATED_HEIGHT + y);
                int right = pixel(sheet, 0, tileX * 8 + x + 1, ANIMATED_HEIGHT + y);
                result[tileX * 32 + y * 4 + x / 2] = (byte) (left << 4 | right);
            }
        }
        return result;
    }

    private static void paintFrameTop(Bitmap sheet, int frame, byte[] data) {
        for (int tileY = 0; tileY < 3; tileY++) for (int tileX = 0; tileX < 2; tileX++) {
            int tile = tileY * 2 + tileX;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x += 2) {
                int value = data[tile * 32 + y * 4 + x / 2] & 0xFF;
                setPixel(sheet, frame, tileX * 8 + x, tileY * 8 + y, value >> 4);
                setPixel(sheet, frame, tileX * 8 + x + 1, tileY * 8 + y, value & 15);
            }
        }
    }

    private static void paintStem(Bitmap sheet, int frame, byte[] stem) {
        for (int tileX = 0; tileX < 2; tileX++) for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x += 2) {
                int value = stem[tileX * 32 + y * 4 + x / 2] & 0xFF;
                setPixel(sheet, frame, tileX * 8 + x, ANIMATED_HEIGHT + y, value >> 4);
                setPixel(sheet, frame, tileX * 8 + x + 1, ANIMATED_HEIGHT + y, value & 15);
            }
        }
    }

    private static int pixel(Bitmap sheet, int frame, int x, int y) {
        return sheet.getIndex((frame % 4) * FRAME_WIDTH + x, (frame / 4) * FRAME_HEIGHT + y);
    }

    private static void setPixel(Bitmap sheet, int frame, int x, int y, int index) {
        sheet.setIndex((frame % 4) * FRAME_WIDTH + x, (frame / 4) * FRAME_HEIGHT + y, index);
    }

    private static byte[] parkBlock(byte[] rom) throws IOException {
        byte[] data;
        try { data = LzToshio.decompress(rom, PARK_BLOCK); }
        catch (RuntimeException e) { throw new IOException("Cannot read park graphics at 0x14D3AE", e); }
        if (data.length != PARK_TILES * 32) throw new IOException("Unexpected park graphic size: " + data.length);
        return data;
    }

    static int[] palette() {
        int[] colors = new int[16];
        for (int i = 0; i < 16; i++) {
            int hi = CRAM[i * 2] & 255;
            int lo = CRAM[i * 2 + 1] & 255;
            colors[i] = 0xFF000000 | ((((lo >> 1) & 7) * 34) << 16)
                    | ((((lo >> 5) & 7) * 34) << 8) | (((hi >> 1) & 7) * 34);
        }
        return colors;
    }

    private static Bitmap scaled(Bitmap source, int scale) {
        Bitmap result = Bitmap.indexed(source.getWidth() * scale, source.getHeight() * scale, palette());
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            int index = source.getIndex(x, y);
            for (int sy = 0; sy < scale; sy++) for (int sx = 0; sx < scale; sx++) {
                result.setIndex(x * scale + sx, y * scale + sy, index);
            }
        }
        return result;
    }

    private static void write(String name, Bitmap image) throws IOException {
        Path path = Paths.get(name);
        Files.createDirectories(path.getParent());
        Png.write(image, name);
    }
}
