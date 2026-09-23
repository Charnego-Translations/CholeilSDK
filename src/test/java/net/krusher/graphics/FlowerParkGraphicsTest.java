package net.krusher.graphics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FlowerParkGraphicsTest {
    @Test
    void editorContainsEightIndexedFrames() throws Exception {
        Bitmap editor = FlowerParkGraphics.readEditor();
        assertEquals(64, editor.getWidth());
        assertEquals(64, editor.getHeight());
        assertEquals(8 * 192, FlowerParkGraphics.decodeFrames(editor).length);
    }

    @Test
    void editorIsTheCustomParkArtNotTheSharedStockFlower() throws Exception {
        Path romPath = Path.of("Soleil (Spain).md");
        Assumptions.assumeTrue(Files.exists(romPath));
        byte[] rom = Files.readAllBytes(romPath);
        byte[] stock = Arrays.copyOfRange(rom, FlowerParkGraphics.ANIMATION_ROM,
                FlowerParkGraphics.ANIMATION_ROM + FlowerParkGraphics.FRAME_COUNT * FlowerParkGraphics.FRAME_BYTES);
        assertFalse(Arrays.equals(stock, FlowerParkGraphics.decodeFrames(FlowerParkGraphics.readEditor())));
    }

    @Test
    void paletteCarriesTheTransparentAndOpaqueDuplicateSeparately() {
        int[] palette = FlowerParkGraphics.palette();
        assertEquals(16, palette.length);
        assertEquals(0xFFAACC66, palette[0]);
        assertEquals(palette[0], palette[13]);
    }

    @Test
    void patchRestoresSharedFlowerAndStoresCustomFramesSeparately() throws Exception {
        Path romPath = Path.of("Soleil (Spain).md");
        Assumptions.assumeTrue(Files.exists(romPath));
        byte[] original = Files.readAllBytes(romPath);
        byte[] input = original.clone();
        int bytes = FlowerParkGraphics.FRAME_COUNT * FlowerParkGraphics.FRAME_BYTES;
        Arrays.fill(input, FlowerParkGraphics.ANIMATION_ROM,
                FlowerParkGraphics.ANIMATION_ROM + bytes, (byte) 0x5A); // Simulate the old global patch.
        byte[] edited = FlowerParkGraphics.decodeFrames(FlowerParkGraphics.readEditor());

        byte[] patched = FlowerParkGraphics.patchRom(input, original, edited);

        assertArrayEquals(Arrays.copyOfRange(original, FlowerParkGraphics.ANIMATION_ROM,
                        FlowerParkGraphics.ANIMATION_ROM + bytes),
                Arrays.copyOfRange(patched, FlowerParkGraphics.ANIMATION_ROM,
                        FlowerParkGraphics.ANIMATION_ROM + bytes));
        assertArrayEquals(edited, Arrays.copyOfRange(patched, FlowerParkGraphics.CUSTOM_ANIMATION_ROM,
                FlowerParkGraphics.CUSTOM_ANIMATION_ROM + bytes));
        assertArrayEquals(FlowerParkGraphics.hookBytes(), Arrays.copyOfRange(patched,
                FlowerParkGraphics.HOOK, FlowerParkGraphics.HOOK + FlowerParkGraphics.HOOK_BYTES));
        assertArrayEquals(patched, FlowerParkGraphics.patchRom(patched, original, edited));
    }

    @Test
    void sourceSelectorChecksFlowerTypeAndSoleilPark() {
        String code = java.util.HexFormat.of().formatHex(FlowerParkGraphics.buildCode());
        assertTrue(code.contains("0c400003"));       // animation slot 0x03
        assertTrue(code.contains("0c780032fe76"));   // resolved room 0x32
        assertTrue(code.contains("4bf900000e96"));   // custom syringe bank
        assertTrue(code.contains("4bf9000d0000"));   // original shared animation table
        assertEquals(FlowerParkGraphics.HOOK_BYTES, FlowerParkGraphics.hookBytes().length);
    }

    @Test
    void changedPixelOnlyChangesTheCustomParkBank() throws Exception {
        Path romPath = Path.of("Soleil (Spain).md");
        Assumptions.assumeTrue(Files.exists(romPath));
        byte[] original = Files.readAllBytes(romPath);
        Bitmap editor = FlowerParkGraphics.readEditor();
        byte[] baseline = FlowerParkGraphics.patchRom(original, original,
                FlowerParkGraphics.decodeFrames(editor));
        int old = editor.getIndex(3, 3);
        int changed = old == 13 ? 0 : 13;
        editor.setIndex(3, 3, changed);
        byte[] patched = FlowerParkGraphics.patchRom(original, original,
                FlowerParkGraphics.decodeFrames(editor));

        int at = FlowerParkGraphics.CUSTOM_ANIMATION_ROM + 3 * 4 + 3 / 2;
        assertEquals((baseline[at] & 0xF0) | changed, patched[at] & 0xFF);
        assertArrayEquals(Arrays.copyOfRange(original, FlowerParkGraphics.ANIMATION_ROM,
                        FlowerParkGraphics.ANIMATION_ROM + 8 * 192),
                Arrays.copyOfRange(patched, FlowerParkGraphics.ANIMATION_ROM,
                        FlowerParkGraphics.ANIMATION_ROM + 8 * 192));
    }

    @Test
    void reservationExactlyProtectsTheDeadBootLogoBlock() {
        int[] range = FlowerParkGraphics.reservedRanges().getFirst();
        assertArrayEquals(new int[]{FlowerParkGraphics.BLOCK,
                FlowerParkGraphics.BLOCK + FlowerParkGraphics.BLOCK_BYTES}, range);
        assertEquals(0x660, FlowerParkGraphics.BLOCK_BYTES);
    }

    @Test
    void occupiedHookIsRejected() throws Exception {
        Path romPath = Path.of("Soleil (Spain).md");
        Assumptions.assumeTrue(Files.exists(romPath));
        byte[] original = Files.readAllBytes(romPath);
        byte[] occupied = original.clone();
        occupied[FlowerParkGraphics.HOOK] ^= 1;
        assertThrows(IllegalStateException.class, () -> FlowerParkGraphics.patchRom(occupied, original,
                FlowerParkGraphics.decodeFrames(FlowerParkGraphics.readEditor())));
    }
}
