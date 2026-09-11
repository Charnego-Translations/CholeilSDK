package net.krusher.graphics;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SonicTalkDetectionTest {
    private static SonicHammockGraphics.ScenePositions positions(int x, int y) {
        return new SonicHammockGraphics.ScenePositions(x, y, -40, -8, 16, -8);
    }

    private static byte[] original() {
        byte[] rom = new byte[0x200000];
        Arrays.fill(rom, SonicTalkDetection.BLOCK, SonicTalkDetection.BLOCK + 64, (byte)0x20);
        System.arraycopy(SonicTalkDetection.ORIGINAL_HOOK, 0, rom, SonicTalkDetection.HOOK, 10);
        return rom;
    }

    @Test void defaultAndMovedDetectorsReachTheAccessibleRowBelowTheSolidBox() {
        assertEquals(new SonicTalkDetection.TalkPoint(784, 656), SonicTalkDetection.talkPoint(positions(-24, 0)));
        for (int y = -32; y <= 32; y++) {
            var point = SonicTalkDetection.talkPoint(positions(-23, y));
            int bottom = Math.floorDiv(624 + y + 48 + 15, 16) * 16;
            assertEquals(785, point.x());
            assertEquals(bottom - 16, point.y());
            assertTrue(point.y() > bottom + 8 - 34); // Original NPC/player overlap test.
            assertTrue(point.y() <= bottom + 8);
        }
        assertThrows(IllegalStateException.class, () -> SonicTalkDetection.talkPoint(positions(-900, 0)));
    }

    @Test void guardedWrapperRestoresDrawingCoordinatesAndReplaysTheOriginalBitTest() {
        byte[] block = SonicTalkDetection.buildBlock(positions(-24, 0));
        assertEquals(784, ByteBuffer.wrap(block).getShort(36));
        assertEquals(656, ByteBuffer.wrap(block).getShort(40));
        assertArrayEquals(java.util.HexFormat.of().parseHex("48e730000c78001afe7a66160c50001666100c680001000e6608"),
                Arrays.copyOfRange(block, 8, 34));
        assertArrayEquals(java.util.HexFormat.of().parseHex("4eb90002e5f24cdf000c08280005002f4e75"),
                Arrays.copyOfRange(block, 42, 60));
        assertEquals(SonicSideAnimation.BLOCK + SonicSideAnimation.BLOCK_BYTES, SonicTalkDetection.BLOCK);
        assertTrue(SonicTalkDetection.BLOCK + block.length <= 0x15E000);
    }

    @Test void onlyHookAndOwnFillerChangeNeverSpriteCoordinatesMapOrAnimation() {
        byte[] original = original();
        byte[] patched = SonicTalkDetection.patchRom(original, original, positions(-24, 0));
        byte[] expected = original.clone();
        System.arraycopy(SonicTalkDetection.buildBlock(positions(-24, 0)), 0, expected, SonicTalkDetection.BLOCK, 64);
        System.arraycopy(SonicTalkDetection.hookBytes(), 0, expected, SonicTalkDetection.HOOK, 10);
        assertArrayEquals(expected, patched);
        assertArrayEquals(original(), original);
        assertArrayEquals(patched, SonicTalkDetection.patchRom(patched, original, positions(-24, 0)));
        assertArrayEquals(SonicTalkDetection.patchRom(original, original, positions(-40, 8)),
                SonicTalkDetection.patchRom(patched, original, positions(-40, 8)));
    }

    @Test void occupiedCodeAndFillerAreRejectedWithoutChangingInputs() {
        byte[] original = original();
        for (int at : new int[]{SonicTalkDetection.HOOK, SonicTalkDetection.BLOCK, SonicTalkDetection.BLOCK + 63}) {
            byte[] occupied = original.clone(); occupied[at] ^= 1;
            byte[] before = occupied.clone();
            assertThrows(IllegalStateException.class, () -> SonicTalkDetection.patchRom(occupied, original, positions(-24, 0)));
            assertArrayEquals(before, occupied);
            assertThrows(IllegalStateException.class, () -> SonicTalkDetection.patchRom(original, occupied, positions(-24, 0)));
        }
        byte[] corrupted = SonicTalkDetection.patchRom(original, original, positions(-24, 0));
        corrupted[SonicTalkDetection.BLOCK + 14] ^= 1;
        assertThrows(IllegalStateException.class, () -> SonicTalkDetection.patchRom(corrupted, original, positions(-24, 0)));
    }
}
