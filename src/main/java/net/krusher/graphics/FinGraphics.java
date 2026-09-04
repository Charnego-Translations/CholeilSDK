package net.krusher.graphics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Friendly editor for the cursive "Fin." shown after the ending credits.
 *
 * The game stores it as one LZ-Toshio block containing two 3x3 hardware
 * sprites. Tiles inside each sprite use the Mega Drive's column-major order,
 * so the ordinary 16-column gfx_out view looks scrambled. This adapter exposes
 * the real 48x24 image and maps it back to gfx_out before GraphicsInserter
 * handles compression and relocation.
 */
public final class FinGraphics {
    public static final int BLOCK_OFFSET = 0x11BE50;
    public static final int PALETTE_OFFSET = 0x11A518;
    public static final int SPRITE_TILES_W = 3;
    public static final int SPRITE_TILES_H = 3;
    public static final int SPRITES_PER_ROW = 2;
    public static final int TILE_COUNT = 18;
    public static final int WIDTH = 48;
    public static final int HEIGHT = 24;

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
        System.out.println("Extracted ending Fin.: " + edit + " (48x24, original palette)");
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
        Path gfx = Paths.get(gfxPath);
        if (Files.exists(gfx)) {
            Bitmap current = TileRenderer.readPng(gfx.toString());
            if (current.getWidth() == 128 && current.getHeight() == 16) {
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
        byte[] decoded = TileRenderer.decodeSpriteSheet(image, palette,
                SPRITE_TILES_W, SPRITE_TILES_H, SPRITES_PER_ROW, 1, TILE_COUNT, false);
        if (!Arrays.equals(original, decoded)) {
            throw new IllegalStateException("ending Fin. PNG does not round-trip to the ROM tile bytes");
        }
        System.out.println("Ending Fin. round-trip verified: 18 tiles match the ROM byte-for-byte.");
    }

    private static byte[] readTiles(byte[] rom) {
        byte[] tiles = LzToshio.decompress(rom, BLOCK_OFFSET);
        int expected = TILE_COUNT * TileRenderer.TILE_BYTES;
        if (tiles.length != expected) {
            throw new IllegalStateException(String.format(
                    "ending Fin. block 0x%X has %d bytes, expected %d",
                    BLOCK_OFFSET, tiles.length, expected));
        }
        return tiles;
    }
}
