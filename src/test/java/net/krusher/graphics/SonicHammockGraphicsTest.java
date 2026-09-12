package net.krusher.graphics;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SonicHammockGraphicsTest {
    private static void word(byte[] data, int at, int value) {
        data[at] = (byte)(value >>> 8);
        data[at + 1] = (byte)value;
    }

    @Test void reservationIsOutsideDynamicFootprints() {
        assertEquals(0x3C0, SonicHammockGraphics.FIRST_CUSTOM_METATILE);
        SonicHammockGraphics.validatePanelReservation(new byte[0x6C04]);
    }

    @Test void reservationMustBeBlankAndUnreferenced() {
        byte[] map = new byte[0x6C04];
        map[0x3D0 * 8] = 1;
        assertThrows(IllegalStateException.class,
                () -> SonicHammockGraphics.validatePanelReservation(map));
        map[0x3D0 * 8] = 0;
        word(map, 0x2000 + 0x3D0 * 2, 0x9100);
        assertThrows(IllegalStateException.class,
                () -> SonicHammockGraphics.validatePanelReservation(map));
        word(map, 0x2000 + 0x3D0 * 2, 0);
        word(map, 0x4000, 0x83DF);
        assertThrows(IllegalStateException.class,
                () -> SonicHammockGraphics.validatePanelReservation(map));
    }

    @Test void detectsEveryFootprintDefinitionBeingOverwritten() {
        byte[] original = new byte[0x6C04];
        for (int id = 0x3E0; id <= 0x3FF; id++) {
            byte[] map = original.clone();
            map[id * 8] = 1;
            assertThrows(IllegalStateException.class,
                    () -> SonicHammockGraphics.verifyFootprints(map, original));
            map[id * 8] = 0;
            map[0x2000 + id * 2] = 1;
            assertThrows(IllegalStateException.class,
                    () -> SonicHammockGraphics.verifyFootprints(map, original));
        }
    }

    @Test void migratesBothBrokenLayoutsAndCleansPreviousSafePlacement() {
        byte[] original = new byte[0x6C04];
        Arrays.fill(original, 0x3E0 * 8, 0x400 * 8, (byte)0x25);
        word(original, 0x4000, 0x8020);
        word(original, 0x4002, 0x0021);
        word(original, 0x4004, 0x0022);
        byte[] map = original.clone();
        word(map, 0x4000, 0x83E0); // old movable layout
        word(map, 0x4002, 0x03F1); // old fixed layout
        word(map, 0x4004, 0x03D0); // previous safe placement
        Arrays.fill(map, 0x3D0 * 8, 0x400 * 8, (byte)0x11);
        map[0x4100] = 0x55; // unrelated user edit must survive
        SonicHammockGraphics.restoreReservedPlacements(map, original);
        SonicHammockGraphics.verifyFootprints(map, original);
        byte[] expected = original.clone();
        expected[0x4100] = 0x55;
        assertArrayEquals(expected, map);
    }

    @Test void xyOffsetsOnlyChangeTheEightSpriteCoordinates() {
        byte[] rom = new byte[0x30000];
        // Header/pointers are NOT coordinates; old Y patch included 0x2F850.
        word(rom, 0x2F850, 0x0000);
        word(rom, 0x2F852, 0x0012);
        word(rom, 0x2F854, 0x0003);
        int[] xs = {0x2F856, 0x2F85E, 0x2F866, 0x2F86E};
        int[] ys = {0x2F858, 0x2F860, 0x2F868, 0x2F870};
        for (int i = 0; i < 4; i++) {
            word(rom, xs[i], i % 2 == 0 ? -24 : 0);
            word(rom, ys[i], i < 2 ? -24 : 0);
            word(rom, ys[i] + 2, 0x0A00); // dimensions
            word(rom, ys[i] + 4, i * 9); // tile index
        }
        byte[] expected = rom.clone();
        for (int i = 0; i < 4; i++) {
            word(expected, xs[i], (i % 2 == 0 ? -24 : 0) - 24);
            word(expected, ys[i], (i < 2 ? -24 : 0) - 8);
        }
        SonicHammockGraphics.patchSpritePosition(rom, -24, -8);
        assertArrayEquals(expected, rom);
        SonicHammockGraphics.patchSpritePosition(rom, -24, -8);
        assertArrayEquals(expected, rom); // idempotent even with negative offsets
    }

    private static byte[] sceneFixture() {
        byte[] map = new byte[0x6C04];
        for (int i = 0; i < 4; i++) word(map, 0x27 * 8 + i * 2, 0x2014 + i);
        for (int at = 0x2C04; at < map.length; at += 2) word(map, at, 0x27);
        return map;
    }

    private static int wordAt(byte[] data, int at) {
        return (data[at] & 255) << 8 | data[at + 1] & 255;
    }

    private static int terrainAt(byte[] map, int x, int y) {
        int id = wordAt(map, 0x2C04 + (y / 16 * 64 + x / 16) * 2) & 1023;
        return wordAt(map, 0x2000 + id * 2);
    }

    @Test void allThreeRectanglesAreSolidButSurroundingGroundAndGraphicsStayIntact() {
        byte[] original = sceneFixture();
        byte[] map = original.clone();
        byte[] blank = new byte[18 * 32];
        var pos = new SonicHammockGraphics.ScenePositions(-24, 0, -40, -8, 16, -8);
        SonicHammockGraphics.patchSceneMap(map, blank, blank, pos);
        int[][] rectangles = {{760,624,48,48}, {744,616,24,48}, {800,616,24,48}};
        for (int[] r : rectangles) {
            for (int y = r[1]; y < r[1] + r[3]; y++) {
                for (int x = r[0]; x < r[0] + r[2]; x++) {
                    assertEquals(3, terrainAt(map, x, y));
                }
            }
        }
        for (int y = 36; y <= 44; y++) for (int x = 44; x <= 54; x++) {
            boolean solid = false;
            for (int[] r : rectangles) {
                solid |= x * 16 < r[0] + r[2] && x * 16 + 16 > r[0]
                        && y * 16 < r[1] + r[3] && y * 16 + 16 > r[1];
            }
            assertEquals(solid ? 3 : 0, terrainAt(map, x * 16, y * 16));
            int id = wordAt(map, 0x2C04 + (y * 64 + x) * 2) & 1023;
            for (int q = 0; q < 4; q++) assertEquals(0x2014 + q, wordAt(map, id * 8 + q * 2));
        }
        SonicHammockGraphics.verifyFootprints(map, original);
    }

    @Test void movingTheSceneRemovesOldCollisionAndRebuildsAtAllThreeNewPositions() {
        byte[] original = sceneFixture();
        byte[] map = original.clone();
        byte[] blank = new byte[18 * 32];
        SonicHammockGraphics.patchSceneMap(map, blank, blank,
                new SonicHammockGraphics.ScenePositions(-24, 0, -40, -8, 16, -8));
        SonicHammockGraphics.restoreReservedPlacements(map, original);
        assertArrayEquals(original, map);
        SonicHammockGraphics.patchSceneMap(map, blank, blank,
                new SonicHammockGraphics.ScenePositions(-23, 129, -240, 120, 144, 120));
        assertEquals(0, terrainAt(map, 784, 648));
        assertEquals(3, terrainAt(map, 784, 768));
        assertEquals(3, terrainAt(map, 552, 752));
        assertEquals(3, terrainAt(map, 936, 752));
        SonicHammockGraphics.restoreReservedPlacements(map, original);
        assertArrayEquals(original, map);
    }

    @Test void outOfRoomSolidsAreRejected() {
        byte[] blank = new byte[18 * 32];
        assertThrows(IllegalStateException.class,
                () -> SonicHammockGraphics.patchSceneMap(sceneFixture(), blank, blank,
                        new SonicHammockGraphics.ScenePositions(-800,0,-40,-8,16,-8)));
    }

    // Also runnable with javac/java and the cached JUnit jars, without Maven.
    public static void main(String[] args) {
        var test = new SonicHammockGraphicsTest();
        test.reservationIsOutsideDynamicFootprints();
        test.reservationMustBeBlankAndUnreferenced();
        test.detectsEveryFootprintDefinitionBeingOverwritten();
        test.migratesBothBrokenLayoutsAndCleansPreviousSafePlacement();
        test.xyOffsetsOnlyChangeTheEightSpriteCoordinates();
        test.allThreeRectanglesAreSolidButSurroundingGroundAndGraphicsStayIntact();
        test.movingTheSceneRemovesOldCollisionAndRebuildsAtAllThreeNewPositions();
        test.outOfRoomSolidsAreRejected();
        System.out.println("SonicHammockGraphics: 8 regression tests passed.");
    }
}
