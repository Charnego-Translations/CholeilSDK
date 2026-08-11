package net.krusher.graphics;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Renders decompressed graphics bytes as Mega Drive 8x8, 4bpp packed tiles
 * (two pixels per byte, 4 bytes per row, 32 bytes per tile) into a PNG tile
 * sheet. No CRAM palette is known yet, so a default 16-shade grayscale ramp
 * is used unless a real palette is supplied.
 */
public final class TileRenderer {

    public static final int TILE_SIZE = 8;
    public static final int TILE_BYTES = 32;

    private TileRenderer() {}

    public static int[] defaultGrayscalePalette() {
        int[] pal = new int[16];
        for (int i = 0; i < 16; i++) {
            int v = i * 17;
            pal[i] = (0xFF << 24) | (v << 16) | (v << 8) | v;
        }
        return pal;
    }

    /**
     * Reads 16 colors in raw Genesis CRAM format (0000 bbb0 ggg0 rrr0, one
     * word per color) starting at {@code offset} and converts them to ARGB.
     */
    public static int[] readGenesisPalette(byte[] rom, int offset) {
        int[] pal = new int[16];
        for (int i = 0; i < 16; i++) {
            int hi = rom[offset + i * 2] & 0xFF;
            int lo = rom[offset + i * 2 + 1] & 0xFF;
            int b3 = (hi >> 1) & 7;
            int g3 = (lo >> 5) & 7;
            int r3 = (lo >> 1) & 7;
            int r = Math.round(r3 * 255f / 7f);
            int g = Math.round(g3 * 255f / 7f);
            int b = Math.round(b3 * 255f / 7f);
            pal[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
        }
        return pal;
    }

    public static BufferedImage renderTileSheet(byte[] data, int[] palette, int columns, int scale) {
        int tileCount = Math.max(1, data.length / TILE_BYTES);
        int cols = Math.max(1, columns);
        int rows = (tileCount + cols - 1) / cols;

        BufferedImage img = new BufferedImage(cols * TILE_SIZE * scale, rows * TILE_SIZE * scale, BufferedImage.TYPE_INT_ARGB);

        for (int tile = 0; tile < tileCount; tile++) {
            int tileX = (tile % cols) * TILE_SIZE;
            int tileY = (tile / cols) * TILE_SIZE;
            int base = tile * TILE_BYTES;

            for (int row = 0; row < TILE_SIZE; row++) {
                for (int col = 0; col < TILE_SIZE; col += 2) {
                    int byteIndex = base + row * 4 + col / 2;
                    int b = byteIndex < data.length ? (data[byteIndex] & 0xFF) : 0;
                    int leftIdx = (b >> 4) & 0xF;
                    int rightIdx = b & 0xF;

                    plotScaled(img, tileX + col, tileY + row, palette[leftIdx], scale);
                    plotScaled(img, tileX + col + 1, tileY + row, palette[rightIdx], scale);
                }
            }
        }
        return img;
    }

    private static void plotScaled(BufferedImage img, int x, int y, int argb, int scale) {
        for (int dy = 0; dy < scale; dy++) {
            for (int dx = 0; dx < scale; dx++) {
                img.setRGB(x * scale + dx, y * scale + dy, argb);
            }
        }
    }

    public static void writePng(BufferedImage img, String path) throws IOException {
        ImageIO.write(img, "png", new File(path));
    }
}
