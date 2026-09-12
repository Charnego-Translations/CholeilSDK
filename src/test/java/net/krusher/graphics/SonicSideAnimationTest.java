package net.krusher.graphics;

import java.util.Arrays;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SonicSideAnimationTest {
    private static final int FRAME = SonicSideAnimation.FRAME_BYTES;

    private static byte[] pair(int a, int b) {
        byte[] frames = new byte[FRAME * 2];
        Arrays.fill(frames, 0, FRAME, (byte)a);
        Arrays.fill(frames, FRAME, frames.length, (byte)b);
        return frames;
    }

    @Test void composingEitherFramePreservesTheEntireBottomAndInputs() {
        byte[] panel = pair(0x11, 0x22);
        byte[] frames = pair(0x33, 0x44);
        assertArrayEquals(pair(0x33, 0x22), SonicSideAnimation.composePanel(panel, frames, 0));
        assertArrayEquals(pair(0x44, 0x22), SonicSideAnimation.composePanel(panel, frames, 1));
        assertArrayEquals(pair(0x11, 0x22), panel);
        assertArrayEquals(pair(0x33, 0x44), frames);
    }

    @Test void frameBOnlyTilesAreIncludedInMapCoverage() {
        byte[] frames = pair(0, 0);
        frames[FRAME + 8 * 32 + 31] = 0x20; // bottom-right tile has a pixel only in B
        frames[0] = 1; // top-left tile only in A
        byte[] mask = SonicSideAnimation.unionMask(pair(0, 0x55), frames);
        assertEquals(1, mask[0]);
        assertEquals(0x20, mask[FRAME - 1]);
        assertArrayEquals(pair(0, 0x55), SonicSideAnimation.composePanel(mask, pair(0, 0), 0));
        assertEquals(0, frames[FRAME - 1]); // inputs are not modified
    }

    @Test void wrongSizesAndFrameNumbersAreRejected() {
        byte[] valid = pair(0, 0);
        assertThrows(IllegalArgumentException.class, () -> SonicSideAnimation.composePanel(valid, valid, 2));
        assertThrows(IllegalArgumentException.class, () -> SonicSideAnimation.composePanel(valid, valid, -1));
        assertThrows(IllegalArgumentException.class, () -> SonicSideAnimation.composePanel(new byte[32], valid, 0));
        assertThrows(IllegalArgumentException.class, () -> SonicSideAnimation.buildBlock(valid, new byte[32]));
    }

    private static void word(byte[] bytes, int at, int value) {
        bytes[at] = (byte)(value >>> 8);
        bytes[at + 1] = (byte)value;
    }

    private static int wordAt(byte[] bytes, int at) {
        return (bytes[at] & 255) << 8 | bytes[at + 1] & 255;
    }

    @Test void actualTilemapReferencesATileDrawnOnlyInFrameB() {
        byte[] map = new byte[0x6C04];
        for (int q = 0; q < 4; q++) word(map, 0x27 * 8 + q * 2, 0x2014 + q);
        for (int at = 0x2C04; at < map.length; at += 2) word(map, at, 0x27);
        byte[] original = map.clone();
        byte[] frames = pair(0, 0);
        frames[FRAME + 8 * 32] = 1;
        byte[] coverage = SonicSideAnimation.unionMask(pair(0, 0), frames);
        SonicHammockGraphics.patchSceneMap(map, coverage, pair(0, 0),
                new SonicHammockGraphics.ScenePositions(-24, 0, -40, -8, 16, -8));
        // Left upper half ends at world tile (95,79), local quadrant 3.
        int id = wordAt(map, 0x2C04 + (39 * 64 + 47) * 2) & 1023;
        assertEquals(0x2000 | (0x3E4 + 8 - 0x100), wordAt(map, id * 8 + 6));
        assertEquals(3, wordAt(map, 0x2000 + id * 2));
        SonicHammockGraphics.verifyFootprints(map, original);
    }

    @Test void editablePngRoundTripsBothFramesWithTheOriginalPalette(@TempDir Path dir) throws Exception {
        byte[] frames = pair(0x12, 0x45);
        String file = dir.resolve("frames.png").toString();
        TileRenderer.writePng(TileRenderer.renderSpriteSheet(frames,
                SonicHammockGraphics.sidePalette(), 3, 3, 2, 1, true), file);
        assertArrayEquals(frames, SonicSideAnimation.readFrames(file));
    }

    private static Bitmap asymmetricFrames() {
        Bitmap image = Bitmap.indexed(48, 24, SonicHammockGraphics.sidePalette());
        // Different colours in all nine tiles; A and B differ. Avoid the duplicate
        // palette entries 0/3. Uniform or diagonal-symmetric tiles hide transposes.
        int[] colors = {1, 2, 4, 5, 6, 7, 8, 9, 10};
        for (int y = 0; y < 24; y++) for (int x = 0; x < 48; x++) {
            int tile = y / 8 * 3 + (x % 24) / 8;
            image.setIndex(x, y, colors[(tile + (x / 24) * 4) % colors.length]);
        }
        return image;
    }

    @Test void pngFramesComposeIntoTheBackgroundWithoutTransposingTiles(@TempDir Path dir) throws Exception {
        Bitmap source = asymmetricFrames();
        String file = dir.resolve("frames.png").toString();
        TileRenderer.writePng(source, file);
        byte[] frames = SonicSideAnimation.readFrames(file);
        for (int frame = 0; frame < 2; frame++) {
            byte[] panel = SonicSideAnimation.composePanel(pair(0, 0x55), frames, frame);
            // Background/map tiles are row-major, NOT the hardware sprite order.
            Bitmap actual = TileRenderer.renderTileSheet(panel, SonicHammockGraphics.sidePalette(), 3, 1);
            for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) {
                assertEquals(source.getRgb(frame * 24 + x, y), actual.getRgb(x, y),
                        "frame=" + frame + " x=" + x + " y=" + y);
            }
            assertArrayEquals(pair(0, 0x55), SonicSideAnimation.composePanel(panel, pair(0, 0), 0));
        }
    }

    @Test void mapReferencesKeepEveryPngTileAtItsOwnWorldCoordinateInBothFrames(@TempDir Path dir) throws Exception {
        Bitmap source = asymmetricFrames();
        String file = dir.resolve("frames.png").toString();
        TileRenderer.writePng(source, file);
        byte[] frames = SonicSideAnimation.readFrames(file);
        byte[] map = new byte[0x6C04];
        for (int q = 0; q < 4; q++) word(map, 0x27 * 8 + q * 2, 0x2014 + q);
        for (int at = 0x2C04; at < map.length; at += 2) word(map, at, 0x27);
        SonicHammockGraphics.patchSceneMap(map, SonicSideAnimation.unionMask(pair(0, 0), frames),
                pair(0, 0), new SonicHammockGraphics.ScenePositions(-24, 0, -40, -8, 16, -8));
        int[] palette = SonicHammockGraphics.sidePalette();
        for (int frame = 0; frame < 2; frame++) for (int ty = 0; ty < 3; ty++) for (int tx = 0; tx < 3; tx++) {
            int wx = 93 + tx, wy = 77 + ty; // Configured top-left of left panel.
            int id = wordAt(map, 0x2C04 + (wy / 2 * 64 + wx / 2) * 2) & 1023;
            int attribute = wordAt(map, id * 8 + (wy % 2 * 2 + wx % 2) * 2);
            int tile = (attribute & 0x7FF) + 0x100 - 0x3E4;
            int color = (frames[frame * FRAME + tile * 32] & 255) >>> 4;
            assertEquals(source.getRgb(frame * 24 + tx * 8, ty * 8), palette[color],
                    "world tile=" + wx + "," + wy + " frame=" + frame);
        }
    }

    @Test void initializingEditorCopiesTheActualTopHalfAndNeverRewritesExistingArt(@TempDir Path dir) throws Exception {
        Bitmap source = asymmetricFrames();
        Bitmap panel = Bitmap.indexed(24, 48, SonicHammockGraphics.sidePalette());
        for (int y = 0; y < 48; y++) for (int x = 0; x < 24; x++) {
            int rgb = source.getRgb(x + (y >= 24 ? 24 : 0), y % 24);
            int[] palette = SonicHammockGraphics.sidePalette();
            for (int i = 0; i < palette.length; i++) if (palette[i] == rgb) panel.setIndex(x, y, i);
        }
        String base = dir.resolve("base.png").toString();
        String edit = dir.resolve("edit.png").toString();
        String view = dir.resolve("view.png").toString();
        TileRenderer.writePng(panel, base);
        SonicSideAnimation.ensureEditor(base, edit, view);
        Bitmap created = TileRenderer.readPng(edit);
        Bitmap preview = TileRenderer.readPng(view);
        for (int y = 0; y < 24; y++) for (int x = 0; x < 48; x++) {
            assertEquals(panel.getRgb(x % 24, y), created.getRgb(x, y));
            assertEquals(created.getRgb(x, y), preview.getRgb(x * 4, y * 4));
        }
        TileRenderer.writePng(source, edit);
        byte[] artistEdit = java.nio.file.Files.readAllBytes(Path.of(edit));
        SonicSideAnimation.ensureEditor(base, edit, view);
        assertArrayEquals(artistEdit, java.nio.file.Files.readAllBytes(Path.of(edit)));
    }

    @Test void editablePngRejectsChangedPaletteAndSize(@TempDir Path dir) throws Exception {
        String file = dir.resolve("invalid.png").toString();
        int[] palette = SonicHammockGraphics.sidePalette();
        palette[5] ^= 1;
        TileRenderer.writePng(TileRenderer.renderSpriteSheet(pair(0x12, 0x45),
                palette, 3, 3, 2, 1, true), file);
        assertThrows(IllegalStateException.class, () -> SonicSideAnimation.readFrames(file));
        TileRenderer.writePng(TileRenderer.renderSpriteSheet(new byte[FRAME],
                SonicHammockGraphics.sidePalette(), 3, 3, 1, 1, true), file);
        assertThrows(IllegalStateException.class, () -> SonicSideAnimation.readFrames(file));
    }

    @Test void payloadHasTheTwoCharactersInEachOfTwoConsecutivePhases() {
        byte[] block = SonicSideAnimation.buildBlock(pair(0x11, 0x22), pair(0x33, 0x44));
        int at = SonicSideAnimation.DATA_OFFSET;
        for (int color : new int[]{0x11, 0x33, 0x22, 0x44}) {
            byte[] expected = new byte[FRAME];
            Arrays.fill(expected, (byte)color);
            assertArrayEquals(expected, Arrays.copyOfRange(block, at, at + FRAME));
            at += FRAME;
        }
        assertEquals(SonicSideAnimation.BLOCK_BYTES, at);
    }

    private static byte[] original() {
        byte[] rom = new byte[0x200000];
        Arrays.fill(rom, SonicSideAnimation.BLOCK,
                SonicSideAnimation.BLOCK + SonicSideAnimation.BLOCK_BYTES, (byte)0x20);
        System.arraycopy(SonicSideAnimation.ORIGINAL_HOOK, 0, rom,
                SonicSideAnimation.HOOK, SonicSideAnimation.ORIGINAL_HOOK.length);
        return rom;
    }

    @Test void patchOnlyTouchesItsReservedBlockAndHookAndIsIdempotent() {
        byte[] original = original();
        byte[] block = SonicSideAnimation.buildBlock(pair(1, 2), pair(3, 4));
        byte[] patched = SonicSideAnimation.patchRom(original, original, block);
        assertArrayEquals(original(), original);
        byte[] expected = original.clone();
        System.arraycopy(block, 0, expected, SonicSideAnimation.BLOCK, block.length);
        System.arraycopy(SonicSideAnimation.hookBytes(), 0, expected, SonicSideAnimation.HOOK, 8);
        assertArrayEquals(expected, patched);
        assertArrayEquals(patched, SonicSideAnimation.patchRom(patched, original, block));
        byte[] updated = SonicSideAnimation.buildBlock(pair(1, 5), pair(3, 6));
        assertArrayEquals(updated, Arrays.copyOfRange(SonicSideAnimation.patchRom(patched, original, updated),
                SonicSideAnimation.BLOCK, SonicSideAnimation.BLOCK + SonicSideAnimation.BLOCK_BYTES));
    }

    @Test void occupiedFillerOrHookAndForeignRomAreRefusedWithoutTouchingInputs() {
        byte[] original = original();
        byte[] block = SonicSideAnimation.buildBlock(pair(0, 0), pair(0, 0));
        for (int at : new int[]{SonicSideAnimation.HOOK, SonicSideAnimation.BLOCK,
                SonicSideAnimation.BLOCK + SonicSideAnimation.BLOCK_BYTES - 1}) {
            byte[] occupied = original.clone();
            occupied[at] ^= 1;
            byte[] before = occupied.clone();
            assertThrows(IllegalStateException.class, () -> SonicSideAnimation.patchRom(occupied, original, block));
            assertArrayEquals(before, occupied);
            assertThrows(IllegalStateException.class, () -> SonicSideAnimation.patchRom(original, occupied, block));
        }
        assertThrows(IllegalStateException.class, () -> SonicSideAnimation.patchRom(new byte[8], original, block));
    }

    @Test void wrapperKeepsTheOriginalCallAndFinalFlagsInstruction() {
        byte[] code = SonicSideAnimation.buildCode();
        assertArrayEquals(java.util.HexFormat.of().parseHex("4eb900006abe"), Arrays.copyOf(code, 6));
        assertArrayEquals(java.util.HexFormat.of().parseHex("4cdf7fff46df4a78a4d64e75"),
                Arrays.copyOfRange(code, code.length - 12, code.length));
        assertTrue(code.length + 16 <= SonicSideAnimation.DATA_OFFSET);
        assertArrayEquals(java.util.HexFormat.of().parseHex("4eb90015da4c4e71"), SonicSideAnimation.hookBytes());
    }
}
