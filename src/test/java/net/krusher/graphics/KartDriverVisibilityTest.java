package net.krusher.graphics;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class KartDriverVisibilityTest {
    private static byte[] original;

    @BeforeAll static void load() throws Exception {
        Path path = Path.of("Soleil (Spain).md");
        Assumptions.assumeTrue(Files.exists(path), "original ROM required");
        original = Files.readAllBytes(path);
    }

    @Test void patchOnlyAddsHideFlagAtMountAndWhileDriving() {
        byte[] before = original.clone();
        byte[] hidden = KartDriverVisibility.patchRom(original, original, true);
        assertArrayEquals(before, original, "caller-owned input must not be mutated");
        for (int i=0; i<original.length; i++) {
            if (i==0x20749 || i==0x209E1) continue;
            assertEquals(original[i],hidden[i],"unexpected modification at " + Integer.toHexString(i));
        }
        assertEquals(0x47, hidden[0x20749]);
        assertEquals(0x47, hidden[0x209E1]);
        assertArrayEquals(Arrays.copyOfRange(original,0x49400,0x4B400),Arrays.copyOfRange(hidden,0x49400,0x4B400));
    }

    @Test void playerRendererAndRoomResetRemainOriginal() {
        byte[] hidden = KartDriverVisibility.patchRom(original,original,true);
        assertArrayEquals(Arrays.copyOfRange(original,0x79BC,0x82E8),Arrays.copyOfRange(hidden,0x79BC,0x82E8));
        assertArrayEquals(HexFormat.of().parseHex("42b8b0a4"),Arrays.copyOfRange(hidden,0x1927A,0x1927E));
        // Countdown and exit control flow remain unchanged; the hide flag persists until room reset.
        assertArrayEquals(Arrays.copyOfRange(original,0x209E4,0x20AAE),Arrays.copyOfRange(hidden,0x209E4,0x20AAE));
    }

    @Test void hideAndRestoreAreIdempotentAndExactlyReversible() {
        byte[] hidden = KartDriverVisibility.patchRom(original,original,true);
        assertArrayEquals(hidden,KartDriverVisibility.patchRom(hidden,original,true));
        assertArrayEquals(original,KartDriverVisibility.patchRom(hidden,original,false));
        assertArrayEquals(original,KartDriverVisibility.patchRom(original,original,false));
    }

    @Test void missingSettingsRetainsOriginalBehaviorAndBothValuesWork(@TempDir Path dir) throws Exception {
        Path settings=dir.resolve("settings.txt");
        assertFalse(KartDriverVisibility.readHidden(settings));
        Files.writeString(settings,"# comment\n hide_driver = 1 \n");
        assertTrue(KartDriverVisibility.readHidden(settings));
        Files.writeString(settings,"; comment\nhide_driver=0\n");
        assertFalse(KartDriverVisibility.readHidden(settings));
    }

    @Test void malformedSettingsAreRejected(@TempDir Path dir) throws Exception {
        Path settings=dir.resolve("settings.txt");
        for(String value:List.of("", "other=1", "hide_driver=2", "hide_driver=true", "hide_driver=1\nhide_driver=0")) {
            Files.writeString(settings,value);
            assertThrows(IllegalArgumentException.class,()->KartDriverVisibility.readHidden(settings));
        }
    }

    @Test void unsupportedOrOccupiedHooksAreNotOverwritten() {
        byte[] occupied=original.clone();occupied[0x20749]=0;
        assertThrows(IllegalStateException.class,()->KartDriverVisibility.patchRom(occupied,original,true));
        byte[] driving=original.clone();driving[0x209E1]=0;
        assertThrows(IllegalStateException.class,()->KartDriverVisibility.patchRom(driving,original,true));
        for (int at : new int[]{0x79C7,0x1927A}) {
            byte[] guard=original.clone();guard[at]^=1;
            assertThrows(IllegalStateException.class,()->KartDriverVisibility.patchRom(guard,original,true));
            assertThrows(IllegalStateException.class,()->KartDriverVisibility.patchRom(original,guard,true));
        }
        assertThrows(IllegalStateException.class,()->KartDriverVisibility.patchRom(new byte[512],original,true));
    }

    @Test void fileInsertionChecksSettingsAndCanRestoreWithoutChangingTiles(@TempDir Path dir) throws Exception {
        Path rom=Files.write(dir.resolve("rom.md"),original);
        Path base=Files.write(dir.resolve("base.md"),original);
        Path settings=Files.writeString(dir.resolve("settings.txt"),"hide_driver=1\n");
        KartDriverVisibility.insert(rom.toString(),base.toString(),settings.toString());
        KartDriverVisibility.verify(rom.toString(),settings.toString());
        byte[] hidden=Files.readAllBytes(rom);
        assertArrayEquals(KartGraphics.readTiles(original),KartGraphics.readTiles(hidden));
        Files.writeString(settings,"hide_driver=0\n");
        assertThrows(IllegalStateException.class,()->KartDriverVisibility.verify(rom.toString(),settings.toString()));
        KartDriverVisibility.insert(rom.toString(),base.toString(),settings.toString());
        KartDriverVisibility.verify(rom.toString(),settings.toString());
        byte[] restored=Files.readAllBytes(rom),expected=original.clone();
        net.krusher.TextInserter.fixChecksum(expected);
        assertArrayEquals(expected,restored);
    }

    @Test void failureLeavesTheFileUnchanged(@TempDir Path dir) throws Exception {
        Path rom=Files.write(dir.resolve("rom.md"),original);
        Path base=Files.write(dir.resolve("base.md"),original);
        Path settings=Files.writeString(dir.resolve("settings.txt"),"hide_driver=invalid");
        assertThrows(IllegalArgumentException.class,()->KartDriverVisibility.insert(rom.toString(),base.toString(),settings.toString()));
        assertArrayEquals(original,Files.readAllBytes(rom));
    }
}
