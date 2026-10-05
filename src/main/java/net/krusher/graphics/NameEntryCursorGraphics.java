package net.krusher.graphics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Expands the name-entry selection frame without changing its palette.
 *
 * <p>The stock frame is a 2x2-tile (16x16) hardware sprite at VRAM tile
 * {@code 0x315}. Mega Drive sprite dimensions advance in complete 8-pixel
 * tiles, so the visible 18x21 frame is stored inside a transparent 3x3-tile
 * (24x24) cell. Its top-left corner stays fixed.</p>
 *
 * <p>The screen's loader copies a fixed byte count, so appending tiles to the
 * compressed resource would leave them outside VRAM. Instead, the enlarged
 * frame replaces the existing cursor run at {@code 0x315..0x31d}; the five
 * extra cells after the stock 2x2 sprite are not addressed by this screen.
 * The generic graphics inserter still relocates the recompressed ROM block if
 * necessary and patches its pointer normally.</p>
 */
public final class NameEntryCursorGraphics {

    public static final int BLOCK_OFFSET = 0x115D92;
    public static final int STOCK_BLOCK_BYTES = 7680;
    public static final int STOCK_CURSOR_OFFSET = TileRenderer.TILE_BYTES;
    public static final int EXPANDED_CURSOR_VRAM_TILE = 0x315;
    public static final String DEFAULT_GFX = "gfx_out/gfx_115d92.png";

    static final int STOCK_SIZE = 16;
    static final int VISIBLE_WIDTH = 18;
    static final int VISIBLE_HEIGHT = 21;
    static final int HARDWARE_SIZE = 24;
    static final int BORDER = 3;

    private NameEntryCursorGraphics() {}

    /** Recreates the expanded generic tile sheet from the untouched stock ROM. */
    public static void sync(String romPath, String gfxPath) throws IOException {
        byte[] rom = Files.readAllBytes(Path.of(romPath));
        byte[] stock = LzToshio.decompress(rom, BLOCK_OFFSET);
        if (stock.length != STOCK_BLOCK_BYTES) {
            throw new IllegalStateException(String.format(
                    "name-entry graphics at 0x%x should decode to %d bytes, got %d",
                    BLOCK_OFFSET, STOCK_BLOCK_BYTES, stock.length));
        }

        byte[] expanded = buildExpandedBlock(stock);
        TileRenderer.writePng(TileRenderer.renderTileSheet(expanded,
                TileRenderer.defaultGrayscalePalette(), 16, TileRenderer.SCALE), gfxPath);
        System.out.printf("Name-entry frame changed from 16x16 to visible 18x21 "
                + "(24x24 hardware cell, VRAM tiles 0x%03x..0x%03x).%n",
                EXPANDED_CURSOR_VRAM_TILE, EXPANDED_CURSOR_VRAM_TILE + 8);
    }

    static byte[] buildExpandedBlock(byte[] stock) {
        if (stock.length != STOCK_BLOCK_BYTES) {
            throw new IllegalArgumentException("stock name-entry block has unexpected size " + stock.length);
        }
        byte[] source = decodeColumnMajorSprite(stock, STOCK_CURSOR_OFFSET, 2, 2);
        byte[] enlarged = enlargeVisibleFrame(source);
        byte[] tiles = encodeColumnMajorSprite(enlarged, 3, 3);

        byte[] expanded = Arrays.copyOf(stock, stock.length);
        System.arraycopy(tiles, 0, expanded, STOCK_CURSOR_OFFSET, tiles.length);
        return expanded;
    }

    /** Nine-slice expansion: preserve the three-pixel border and stretch only its spans. */
    static byte[] enlargeVisibleFrame(byte[] source) {
        if (source.length != STOCK_SIZE * STOCK_SIZE) {
            throw new IllegalArgumentException("stock cursor must be 16x16 pixels");
        }
        byte[] result = new byte[HARDWARE_SIZE * HARDWARE_SIZE];
        for (int y = 0; y < VISIBLE_HEIGHT; y++) {
            int sourceY = sourceCoordinate(y, VISIBLE_HEIGHT);
            for (int x = 0; x < VISIBLE_WIDTH; x++) {
                int sourceX = sourceCoordinate(x, VISIBLE_WIDTH);
                result[y * HARDWARE_SIZE + x] = source[sourceY * STOCK_SIZE + sourceX];
            }
        }
        return result;
    }

    private static int sourceCoordinate(int coordinate, int visibleSize) {
        if (coordinate < BORDER) return coordinate;
        int farBorder = visibleSize - BORDER;
        if (coordinate >= farBorder) return coordinate - (visibleSize - STOCK_SIZE);

        int expandedInterior = visibleSize - 2 * BORDER;
        int stockInterior = STOCK_SIZE - 2 * BORDER;
        int inside = coordinate - BORDER;
        return BORDER + inside * stockInterior / expandedInterior;
    }

    static byte[] decodeColumnMajorSprite(byte[] tiles, int offset, int tilesWide, int tilesHigh) {
        byte[] pixels = new byte[tilesWide * 8 * tilesHigh * 8];
        int pixelWidth = tilesWide * 8;
        for (int tileX = 0; tileX < tilesWide; tileX++) {
            for (int tileY = 0; tileY < tilesHigh; tileY++) {
                int tile = tileX * tilesHigh + tileY;
                int tileBase = offset + tile * TileRenderer.TILE_BYTES;
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        int packed = tiles[tileBase + y * 4 + x / 2] & 0xFF;
                        int colour = (x & 1) == 0 ? packed >>> 4 : packed & 0xF;
                        pixels[(tileY * 8 + y) * pixelWidth + tileX * 8 + x] = (byte) colour;
                    }
                }
            }
        }
        return pixels;
    }

    static byte[] encodeColumnMajorSprite(byte[] pixels, int tilesWide, int tilesHigh) {
        int pixelWidth = tilesWide * 8;
        int pixelHeight = tilesHigh * 8;
        if (pixels.length != pixelWidth * pixelHeight) {
            throw new IllegalArgumentException("sprite pixel buffer has unexpected size " + pixels.length);
        }
        byte[] tiles = new byte[tilesWide * tilesHigh * TileRenderer.TILE_BYTES];
        for (int tileX = 0; tileX < tilesWide; tileX++) {
            for (int tileY = 0; tileY < tilesHigh; tileY++) {
                int tile = tileX * tilesHigh + tileY;
                int tileBase = tile * TileRenderer.TILE_BYTES;
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x += 2) {
                        int left = pixels[(tileY * 8 + y) * pixelWidth + tileX * 8 + x] & 0xF;
                        int right = pixels[(tileY * 8 + y) * pixelWidth + tileX * 8 + x + 1] & 0xF;
                        tiles[tileBase + y * 4 + x / 2] = (byte) ((left << 4) | right);
                    }
                }
            }
        }
        return tiles;
    }
}
