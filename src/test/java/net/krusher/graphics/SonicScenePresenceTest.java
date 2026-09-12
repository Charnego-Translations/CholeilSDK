package net.krusher.graphics;

import java.util.Arrays;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SonicScenePresenceTest {
    private static void word(byte[] b, int at, int v) { ByteBuffer.wrap(b).putShort(at, (short)v); }
    private static int word(byte[] b, int at) { return ByteBuffer.wrap(b).getShort(at) & 0xFFFF; }
    private static byte[] map() {
        byte[] map = new byte[0x6C04];
        for (int q = 0; q < 4; q++) word(map, 0x27 * 8 + q * 2, 0x2014 + q);
        for (int at = 0x2C04; at < map.length; at += 2) word(map, at, 0x4027);
        return map;
    }
    private static byte[] scene(byte[] original, int dx, int dy) {
        byte[] map = original.clone();
        byte[] panel = new byte[18 * 32]; Arrays.fill(panel, (byte)0x11);
        SonicHammockGraphics.patchSceneMap(map, panel, panel,
                new SonicHammockGraphics.ScenePositions(-24 + dx, dy, -40 + dx, -8 + dy, 16 + dx, -8 + dy));
        return map;
    }
    private static byte[] originalRom() {
        byte[] rom = new byte[0x200000];
        Arrays.fill(rom, SonicScenePresence.BLOCK, SonicScenePresence.BLOCK + SonicScenePresence.BLOCK_BYTES, (byte)0xFF);
        System.arraycopy(SonicScenePresence.ORIGINAL_HOOK, 0, rom, SonicScenePresence.HOOK, 6);
        return rom;
    }

    @Test void restoresEveryCellIncludingAllThreeSolidBoxesAndOriginalMapFlagsAfterMoving() {
        for (int[] move : new int[][]{{0,0}, {-16,-24}, {24,16}}) {
            byte[] original = map();
            byte[] scene = scene(original, move[0], move[1]);
            byte[] before = scene.clone();
            byte[] block = SonicScenePresence.buildBlock(scene, original);
            int cells = 0;
            for (int at = SonicScenePresence.DATA_OFFSET; word(block, at) != 0xFFFF; at += 4) {
                int offset = word(block, at);
                assertTrue((word(scene, offset) & 0x3FF) >= 0x3C0);
                word(scene, offset, word(block, at + 2));
                assertEquals(0, word(scene, 0x2000 + (word(scene, offset) & 0x3FF) * 2));
                cells++;
            }
            assertTrue(cells > 0 && cells <= 32);
            assertArrayEquals(Arrays.copyOfRange(original, 0x2C04, original.length),
                    Arrays.copyOfRange(scene, 0x2C04, scene.length));
            assertArrayEquals(Arrays.copyOf(before, 0x2C04), Arrays.copyOf(scene, 0x2C04));
            SonicHammockGraphics.verifyFootprints(scene, original);
        }
    }

    @Test void restorationDoesNotTouchUnrelatedUserMapEditsOrFootprints() {
        byte[] original = map(); byte[] scene = scene(original, 0, 0);
        word(scene, 0x2C04, 0x1055);
        byte[] block = SonicScenePresence.buildBlock(scene, original);
        for (int at = SonicScenePresence.DATA_OFFSET; word(block, at) != 0xFFFF; at += 4) {
            assertNotEquals(0x2C04, word(block, at));
        }
        assertEquals(0x1055, word(scene, 0x2C04));
        assertArrayEquals(map(), original);
    }

    @Test void invalidMapsFootprintCorruptionAndTooManyCellsAreRejected() {
        byte[] original = map(); byte[] scene = original.clone();
        assertThrows(IllegalArgumentException.class, () -> SonicScenePresence.buildBlock(new byte[32], original));
        for (int i = 0; i < 33; i++) word(scene, 0x2C04 + i * 2, 0x3C0);
        assertThrows(IllegalStateException.class, () -> SonicScenePresence.buildBlock(scene, original));
        byte[] corrupted = scene(original, 0, 0); corrupted[0x3E0 * 8] = 1;
        assertThrows(IllegalStateException.class, () -> SonicScenePresence.buildBlock(corrupted, original));
    }

    @Test void emptySceneProducesOnlyTerminatorAndThirtyTwoCellsFit() {
        byte[] original = map(); byte[] scene = original.clone();
        assertEquals(0xFFFF, word(SonicScenePresence.buildBlock(scene, original), SonicScenePresence.DATA_OFFSET));
        for (int i = 0; i < 32; i++) word(scene, 0x2C04 + i * 2, 0x3C0 + i);
        byte[] block = SonicScenePresence.buildBlock(scene, original);
        assertEquals(0xFFFF, word(block, SonicScenePresence.DATA_OFFSET + 128));
    }

    @Test void wrapperReplaysOriginalCallAndFlagsAndTargetsOnlyLateBeach() {
        byte[] code = SonicScenePresence.buildCode();
        assertArrayEquals(SonicScenePresence.ORIGINAL_HOOK, Arrays.copyOf(code, 6));
        assertArrayEquals(java.util.HexFormat.of().parseHex("40e748e7fffe0c78006dfe76"), Arrays.copyOfRange(code, 6, 18));
        assertArrayEquals(java.util.HexFormat.of().parseHex("4cdf7fff46df4e75"), Arrays.copyOfRange(code, code.length-8, code.length));
        assertTrue(16 + code.length <= SonicScenePresence.DATA_OFFSET);
        String animation = java.util.HexFormat.of().formatHex(SonicSideAnimation.buildCode());
        assertTrue(animation.contains("0c78001afe76")); // Do not upload to late-beach NPC/VRAM slots.
    }

    @Test void insertionOnlyChangesOwnHookAndFillerAndCanUpdateMovedPositions() {
        byte[] original = originalRom(); byte[] map = map();
        byte[] block = SonicScenePresence.buildBlock(scene(map, 0, 0), map);
        byte[] patched = SonicScenePresence.patchRom(original, original, block);
        byte[] expected = original.clone();
        System.arraycopy(block, 0, expected, SonicScenePresence.BLOCK, block.length);
        System.arraycopy(SonicScenePresence.hookBytes(), 0, expected, SonicScenePresence.HOOK, 6);
        assertArrayEquals(expected, patched);
        assertArrayEquals(originalRom(), original);
        assertArrayEquals(patched, SonicScenePresence.patchRom(patched, original, block));
        byte[] moved = SonicScenePresence.buildBlock(scene(map, -16, -24), map);
        assertArrayEquals(SonicScenePresence.patchRom(original, original, moved),
                SonicScenePresence.patchRom(patched, original, moved));
    }

    @Test void occupiedCodeOrFillerAndForeignOriginalAreRejectedWithoutMutation() {
        byte[] original = originalRom(); byte[] block = SonicScenePresence.buildBlock(map(), map());
        for (int at : new int[]{SonicScenePresence.HOOK, SonicScenePresence.BLOCK, SonicScenePresence.BLOCK + SonicScenePresence.BLOCK_BYTES - 1}) {
            byte[] occupied = original.clone(); occupied[at] ^= 1; byte[] before = occupied.clone();
            assertThrows(IllegalStateException.class, () -> SonicScenePresence.patchRom(occupied, original, block));
            assertArrayEquals(before, occupied);
            assertThrows(IllegalStateException.class, () -> SonicScenePresence.patchRom(original, occupied, block));
        }
        byte[] corrupted = SonicScenePresence.patchRom(original, original, block);
        corrupted[SonicScenePresence.BLOCK + 20] ^= 1;
        assertThrows(IllegalStateException.class, () -> SonicScenePresence.patchRom(corrupted, original, block));
        assertThrows(IllegalStateException.class, () -> SonicScenePresence.patchRom(new byte[8], original, block));
    }
}
