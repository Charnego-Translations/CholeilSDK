package net.krusher.graphics;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("NameEntryCursorGraphics")
final class NameEntryCursorGraphicsTest {

    @Test
    @DisplayName("the frame is 18x21 inside a transparent 24x24 sprite")
    void buildsTheRequestedVisibleBounds() {
        byte[] stock = stockBlockWithSimpleFrame();
        byte[] expanded = NameEntryCursorGraphics.buildExpandedBlock(stock);
        byte[] pixels = NameEntryCursorGraphics.decodeColumnMajorSprite(
                expanded, NameEntryCursorGraphics.STOCK_CURSOR_OFFSET, 3, 3);

        assertEquals(NameEntryCursorGraphics.STOCK_BLOCK_BYTES, expanded.length,
                "the fixed-length VRAM transfer must not grow");
        assertEquals(1, pixels[0], "the top-left corner stays anchored");
        assertEquals(1, pixels[20 * 24], "the bottom border reaches row 20");
        assertEquals(0, pixels[21 * 24], "rows 21..23 stay transparent");
        assertEquals(1, pixels[17], "the right border reaches column 17");
        assertEquals(0, pixels[18], "columns 18..23 stay transparent");
        assertEquals(0, pixels[10 * 24 + 9], "the enlarged interior remains transparent");
    }

    @Test
    @DisplayName("only the nine cursor tiles change in the compressed resource payload")
    void preservesEverythingOutsideTheCursorRun() {
        byte[] stock = stockBlockWithSimpleFrame();
        Arrays.fill(stock, NameEntryCursorGraphics.STOCK_CURSOR_OFFSET + 9 * 32,
                stock.length, (byte) 0x5A);
        byte[] expanded = NameEntryCursorGraphics.buildExpandedBlock(stock);

        assertArrayEquals(
                Arrays.copyOfRange(stock, 0, NameEntryCursorGraphics.STOCK_CURSOR_OFFSET),
                Arrays.copyOfRange(expanded, 0, NameEntryCursorGraphics.STOCK_CURSOR_OFFSET));
        assertArrayEquals(
                Arrays.copyOfRange(stock, NameEntryCursorGraphics.STOCK_CURSOR_OFFSET + 9 * 32, stock.length),
                Arrays.copyOfRange(expanded, NameEntryCursorGraphics.STOCK_CURSOR_OFFSET + 9 * 32,
                        expanded.length));
    }

    private static byte[] stockBlockWithSimpleFrame() {
        byte[] block = new byte[NameEntryCursorGraphics.STOCK_BLOCK_BYTES];
        byte[] pixels = new byte[16 * 16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (x < 3 || x >= 13 || y < 3 || y >= 13) pixels[y * 16 + x] = 1;
            }
        }
        byte[] tiles = NameEntryCursorGraphics.encodeColumnMajorSprite(pixels, 2, 2);
        System.arraycopy(tiles, 0, block, NameEntryCursorGraphics.STOCK_CURSOR_OFFSET, tiles.length);
        return block;
    }
}
