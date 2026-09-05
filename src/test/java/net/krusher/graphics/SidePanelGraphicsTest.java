package net.krusher.graphics;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the choice of tileset slots the side panels live in.
 *
 * Two earlier attempts put the panel art where the game never transfers it,
 * and both failed the same way: the tiles were absent from VRAM, so the panels
 * drew whatever the console had left there. Neither mistake was visible in the
 * build -- only on screen. These checks encode what makes a slot safe, so the
 * next person to move them gets told off by the suite instead.
 */
@DisplayName("SidePanelGraphics slots")
final class SidePanelGraphicsTest {

    private static byte[] tileset;
    private static Set<Integer> referenced;

    @BeforeAll
    static void loadRom() throws Exception {
        Assumptions.assumeTrue(Files.exists(Paths.get(net.krusher.DefaultPaths.ROM)),
                "base ROM not present -- it is gitignored on purpose");
        byte[] rom = Files.readAllBytes(Paths.get(net.krusher.DefaultPaths.ROM));
        tileset = LzToshio.decompress(rom, SidePanelGraphics.TILESET_BLOCK);
        byte[] map = LzToshio.decompress(rom, 0x178E7A);
        referenced = new TreeSet<>();
        for (int id = 0; id < 0x400; id++) {
            for (int i = 0; i < 4; i++) referenced.add(u16(map, id * 8 + i * 2) & 0x7FF);
        }
    }

    @Test
    @DisplayName("there are exactly eighteen distinct slots, one per panel tile")
    void eighteenDistinctSlots() {
        assertEquals(SidePanelGraphics.PANEL_TILES, SidePanelGraphics.SLOTS.length,
                "the slot list must have one entry per 8x8 tile of the panel");
        Set<Integer> distinct = new HashSet<>();
        for (int slot : SidePanelGraphics.SLOTS) distinct.add(slot);
        assertEquals(SidePanelGraphics.SLOTS.length, distinct.size(),
                "two panel tiles would be stored in the same slot");
    }

    /**
     * The block holds 496 tiles but the game transfers about 486. A slot past
     * that is never drawn AND lands on VRAM the game reuses for other things.
     */
    @Test
    @DisplayName("no slot lies past the last tile the game actually transfers")
    void slotsAreWithinTheTransfer() {
        List<String> bad = new ArrayList<>();
        for (int slot : SidePanelGraphics.SLOTS) {
            if (slot > SidePanelGraphics.HIGHEST_TRANSFERRED_TILE) {
                bad.add(String.format("0x%X", slot));
            }
        }
        if (!bad.isEmpty()) {
            fail("slots above 0x" + Integer.toHexString(SidePanelGraphics.HIGHEST_TRANSFERRED_TILE)
                    + " are never sent to VRAM: " + bad);
        }
    }

    /**
     * The evidence that a slot really is transferred is that it holds art in
     * the ROM and that art shows up in a savestate's VRAM. A blank slot proves
     * nothing -- blank in ROM equals blank in VRAM whether it was sent or not,
     * which is exactly the trap the second attempt fell into. The savestate is
     * not in the repo, so the half that can be checked here is the art.
     */
    @Test
    @DisplayName("every slot holds real art, never a blank tile")
    void slotsHoldRealArt() {
        List<String> blank = new ArrayList<>();
        for (int slot : SidePanelGraphics.SLOTS) {
            boolean isBlank = true;
            for (int i = 0; i < SidePanelGraphics.TILE_BYTES; i++) {
                if (tileset[slot * SidePanelGraphics.TILE_BYTES + i] != 0) { isBlank = false; break; }
            }
            if (isBlank) blank.add(String.format("0x%X", slot));
        }
        if (!blank.isEmpty()) {
            fail("blank slots are not proof of anything -- a blank tile looks identical in VRAM "
                    + "whether or not it was transferred: " + blank);
        }
    }

    @Test
    @DisplayName("no slot holds a tile the room actually draws")
    void slotsAreUnusedByTheRoom() {
        List<String> used = new ArrayList<>();
        for (int slot : SidePanelGraphics.SLOTS) {
            if (referenced.contains(slot)) used.add(String.format("0x%X", slot));
        }
        if (!used.isEmpty()) {
            fail("overwriting these would change the beach itself, they are referenced by a "
                    + "metatile definition: " + used);
        }
    }

    @Test
    @DisplayName("the block the slots live in is the size the slot list was chosen for")
    void theBlockIsTheExpectedSize() {
        assertTrue(tileset.length == SidePanelGraphics.TILESET_TILES * SidePanelGraphics.TILE_BYTES,
                "tileset 0x" + Integer.toHexString(SidePanelGraphics.TILESET_BLOCK) + " is "
                        + tileset.length / SidePanelGraphics.TILE_BYTES + " tiles, but the slots were "
                        + "picked for " + SidePanelGraphics.TILESET_TILES);
    }

    private static int u16(byte[] d, int o) {
        return (d[o] & 0xFF) << 8 | d[o + 1] & 0xFF;
    }
}
