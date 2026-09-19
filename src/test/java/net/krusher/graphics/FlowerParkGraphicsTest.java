package net.krusher.graphics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FlowerParkGraphicsTest {
    @Test
    void editorContainsAllEightExactRomFrames() throws Exception {
        Path romPath = Path.of("Soleil (Spain).md");
        Assumptions.assumeTrue(Files.exists(romPath));
        byte[] rom = Files.readAllBytes(romPath);
        Bitmap editor = FlowerParkGraphics.readEditor();
        assertEquals(64, editor.getWidth());
        assertEquals(64, editor.getHeight());
        assertArrayEquals(Arrays.copyOfRange(rom, FlowerParkGraphics.ANIMATION_ROM,
                        FlowerParkGraphics.ANIMATION_ROM + FlowerParkGraphics.FRAME_COUNT * FlowerParkGraphics.FRAME_BYTES),
                FlowerParkGraphics.decodeFrames(editor));
    }

    @Test
    void commonStemMatchesTheCompressedParkTiles() throws Exception {
        Path romPath = Path.of("Soleil (Spain).md");
        Assumptions.assumeTrue(Files.exists(romPath));
        byte[] park = LzToshio.decompress(Files.readAllBytes(romPath), FlowerParkGraphics.PARK_BLOCK);
        assertArrayEquals(Arrays.copyOfRange(park, FlowerParkGraphics.STEM_OFFSET,
                        FlowerParkGraphics.STEM_OFFSET + FlowerParkGraphics.STEM_BYTES),
                FlowerParkGraphics.decodeStem(FlowerParkGraphics.readEditor()));
    }

    @Test
    void paletteCarriesTheTransparentAndOpaqueDuplicateSeparately() {
        int[] palette = FlowerParkGraphics.palette();
        assertEquals(16, palette.length);
        assertEquals(0xFFAACC66, palette[0]);
        assertEquals(palette[0], palette[13]);
    }

    @Test
    void changingAnIndexedPixelPatchesOnlyItsFrameByte() throws Exception {
        Path romPath = Path.of("Soleil (Spain).md");
        Assumptions.assumeTrue(Files.exists(romPath));
        Bitmap editor = FlowerParkGraphics.readEditor();
        int old = editor.getIndex(3, 3);
        int changed = old == 13 ? 0 : 13; // Equal RGB, intentionally different Mega Drive behavior.
        editor.setIndex(3, 3, changed);
        byte[] rom = Files.readAllBytes(romPath);
        byte[] expected = rom.clone();
        int byteOffset = FlowerParkGraphics.ANIMATION_ROM + 3 * 4 + 3 / 2;
        expected[byteOffset] = (byte) ((expected[byteOffset] & 0xF0) | changed);
        assertTrue(FlowerParkGraphics.patchFrames(rom, editor));
        assertArrayEquals(expected, rom);
    }
}
