package net.krusher.graphics;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SonicShadowPositionTest {
    private static SonicHammockGraphics.ScenePositions positions(int x, int y) {
        return new SonicHammockGraphics.ScenePositions(x, y, -40, -8, 16, -8);
    }
    private static byte[] original() {
        byte[] rom = new byte[0x200000];
        Arrays.fill(rom, SonicShadowPosition.BLOCK, SonicShadowPosition.BLOCK + SonicShadowPosition.BLOCK_BYTES, (byte)0xFF);
        System.arraycopy(SonicShadowPosition.ORIGINAL_HOOK, 0, rom, SonicShadowPosition.HOOK, 6);
        return rom;
    }
    @Test void shadowFollowsBothAxesAndKeepsItsOriginalOffsetFromGil() {
        for (int[] xy : new int[][]{{0,0},{-24,0},{-40,-16},{24,16},{-23,7}}) {
            var offset = SonicShadowPosition.offsets(positions(xy[0], xy[1]));
            assertEquals(-8 + xy[0], offset.x());
            assertEquals(4 + xy[1], offset.y());
            byte[] block = SonicShadowPosition.buildBlock(positions(xy[0], xy[1]));
            assertEquals(offset.x(), ByteBuffer.wrap(block).getShort(SonicShadowPosition.X_IMMEDIATE));
            assertEquals(offset.y(), ByteBuffer.wrap(block).getShort(SonicShadowPosition.Y_IMMEDIATE));
        }
        assertThrows(IllegalStateException.class, () -> SonicShadowPosition.offsets(positions(Integer.MAX_VALUE, 0)));
        assertThrows(IllegalStateException.class, () -> SonicShadowPosition.offsets(positions(0, Integer.MIN_VALUE)));
    }
    @Test void sideCharacterPositionsDoNotAffectTheShadow() {
        assertArrayEquals(SonicShadowPosition.buildBlock(positions(-24,0)), SonicShadowPosition.buildBlock(
                new SonicHammockGraphics.ScenePositions(-24,0,-240,128,64,-24)));
    }
    @Test void wrapperFiltersOnlySonicAndPreservesOriginalCallRegistersAndFlags() {
        byte[] block = SonicShadowPosition.buildBlock(positions(-24,0));
        assertArrayEquals(java.util.HexFormat.of().parseHex("48e7c00040e70c78001afe766600001c0c500016660000140c680001000e6600000a"),
                Arrays.copyOfRange(block, 8, 42));
        assertArrayEquals(java.util.HexFormat.of().parseHex("46df4eb90001d0e64cdf00034e75"), Arrays.copyOfRange(block, 50, 64));
        assertEquals(SonicScenePresence.BLOCK + SonicScenePresence.BLOCK_BYTES, SonicShadowPosition.BLOCK);
        assertTrue(SonicShadowPosition.BLOCK + block.length <= 0x1EC000);
    }
    @Test void onlyOwnCallAndFillerChangeNeverMapSpritesDialogueOrAnimation() {
        byte[] original = original();
        byte[] patched = SonicShadowPosition.patchRom(original, original, positions(-24,0));
        byte[] expected = original.clone();
        System.arraycopy(SonicShadowPosition.buildBlock(positions(-24,0)), 0, expected, SonicShadowPosition.BLOCK, SonicShadowPosition.BLOCK_BYTES);
        System.arraycopy(SonicShadowPosition.hookBytes(), 0, expected, SonicShadowPosition.HOOK, 6);
        assertArrayEquals(expected, patched);
        assertArrayEquals(original(), original);
        assertArrayEquals(patched, SonicShadowPosition.patchRom(patched, original, positions(-24,0)));
        assertArrayEquals(SonicShadowPosition.patchRom(original, original, positions(-40,16)),
                SonicShadowPosition.patchRom(patched, original, positions(-40,16)));
    }
    @Test void rejectsForeignHookOccupiedFillerAndCorruptedWrapperWithoutMutatingInputs() {
        byte[] original = original();
        for (int at : new int[]{SonicShadowPosition.HOOK, SonicShadowPosition.BLOCK, SonicShadowPosition.BLOCK + SonicShadowPosition.BLOCK_BYTES - 1}) {
            byte[] occupied = original.clone(); occupied[at] ^= 1; byte[] before = occupied.clone();
            assertThrows(IllegalStateException.class, () -> SonicShadowPosition.patchRom(occupied, original, positions(-24,0)));
            assertArrayEquals(before, occupied);
            assertThrows(IllegalStateException.class, () -> SonicShadowPosition.patchRom(original, occupied, positions(-24,0)));
        }
        byte[] corrupt = SonicShadowPosition.patchRom(original, original, positions(-24,0));
        corrupt[SonicShadowPosition.BLOCK + 16] ^= 1;
        assertThrows(IllegalStateException.class, () -> SonicShadowPosition.patchRom(corrupt, original, positions(-24,0)));
        assertThrows(IllegalStateException.class, () -> SonicShadowPosition.patchRom(new byte[8], original, positions(-24,0)));
    }
}
