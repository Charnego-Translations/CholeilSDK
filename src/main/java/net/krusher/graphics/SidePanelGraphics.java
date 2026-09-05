package net.krusher.graphics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Two static 24x48 panels flanking Jesus Gil on Anemone Beach.
 *
 * Only ONE panel is drawn: the right-hand copy is the same eighteen tiles with
 * the VDP's horizontal-flip bit set and the column order reversed, so the art
 * costs nothing extra in VRAM and always stays symmetric.
 *
 * <h2>Where the tiles live</h2>
 *
 * The room's background tileset is the LZ-Toshio block at 0x135322 (496 tiles,
 * entry 11 of the tileset archive at 0x120000). The game decompresses it and
 * DMAs it to VRAM tile 0x100, so a metatile word holding value {@code t} draws
 * tileset tile {@code t}.
 *
 * The block's size never changes: the art goes into slots the game is already
 * carrying. Picking those slots has one subtlety that is easy to get wrong.
 *
 * The block holds 496 tiles but the game transfers only about 486 of them; the
 * tail is ROM padding that never ships, and that VRAM is reused at runtime.
 * A blank tile therefore proves nothing -- "blank in ROM, blank in VRAM" is
 * trivially true whether or not the transfer ever reached it. The slots in
 * {@link #SLOTS} are instead tiles that hold REAL ART and are byte-identical
 * in a savestate's VRAM, which proves the transfer covered them and that the
 * game keeps that content there. They are also referenced by no metatile
 * definition in this room -- so overwriting the art is invisible -- and by no
 * map that cannot be ruled out from sharing this tileset.
 *
 * The first attempt at this used blank slots at the top of the block, was not
 * transferred, and overwrote VRAM the game needed. The one before that
 * appended tiles PAST the end of a sprite block, with the same result.
 *
 * <h2>How they are drawn</h2>
 *
 * The room map at 0x178E7A addresses 2x2 metatiles. To repaint a single 8x8
 * cell we clone the metatile that already covers it into an id the room never
 * places, swap in our tile, and repoint the map word. Each cell keeps its
 * original palette bits, and priority is cleared so the panels stay behind
 * Jesus Gil. A fully blank 8x8 tile in the PNG is skipped, which leaves that
 * piece of the original beach untouched.
 *
 * The recompressed map has to fit back into a slot one byte larger than the
 * original, so {@link #spareMetatiles} hands out ids that already hold data
 * before ids whose definition is all zeroes -- see the note there.
 */
public final class SidePanelGraphics {
    public static final int TILESET_BLOCK = 0x135322;
    public static final int TILESET_TILES = 496;
    public static final int TILE_BYTES = 32;
    /**
     * The highest tile of the block a savestate proves reaches VRAM. The block
     * has 496 tiles; roughly the last ten are ROM padding the game never
     * transfers, and it reuses that VRAM for other things.
     */
    public static final int HIGHEST_TRANSFERRED_TILE = 0x1E5;

    public static final int PANEL_TILES_W = 3;
    public static final int PANEL_TILES_H = 6;
    public static final int PANEL_TILES = PANEL_TILES_W * PANEL_TILES_H;
    public static final int PANEL_WIDTH = PANEL_TILES_W * 8;
    public static final int PANEL_HEIGHT = PANEL_TILES_H * 8;

    public static final String DEFAULT_EDIT = "special_gfx_out/sonic_lateral_EDITAME.png";
    public static final String DEFAULT_VIEW = "special_gfx_out/sonic_lateral_x4_VISTA.png";
    public static final String DEFAULT_PALETTE_VIEW = "special_gfx_out/sonic_laterales_PALETA.png";
    public static final String DEFAULT_TILESET_GFX = "gfx_out/gfx_135322.png";
    public static final String DEFAULT_MAP_GFX = "gfx_out/gfx_178e7a.png";

    /**
     * The eighteen tileset slots the panel is stored in, in the PNG's own
     * left-to-right, top-to-bottom order.
     *
     * Scattered rather than contiguous, because each metatile names its four
     * tiles individually so there is no reason to want a run. Every one holds
     * art the room never draws, and all sit below tile 0x1E5, the highest the
     * savestate proves is transferred. 27 slots qualify; these are 18 of them.
     */
    public static final int[] SLOTS = {
        0x0FE, 0x0FF, 0x12D,
        0x12F, 0x14A, 0x14B,
        0x14D, 0x14E, 0x14F,
        0x159, 0x15A, 0x164,
        0x1D0, 0x1D2, 0x1D3,
        0x1E0, 0x1E2, 0x1E3,
    };

    private static final int MAP_BLOCK_OFFSET = 0x178E7A;
    private static final int ROOM_MAP_BASE = 0x3C5C;
    private static final int ROOM_METATILES_PER_ROW = 64;
    /** Jesus Gil's 48x48 sprite covers room tiles x=10..15, y=14..19. */
    private static final int SONIC_TILE_X = 10;
    private static final int SONIC_TILE_Y = 14;
    private static final int SONIC_TILES_W = 6;
    private static final int METATILE_COUNT = 0x400;
    private static final int SIDE_PALETTE_LINE = 1;
    private static final int HFLIP = 0x0800;

    // Palette line 1 of the beach, captured from CRAM in a confirmed state.
    private static final byte[] SIDE_CRAM = {
        0x08, (byte) 0xCC, 0x0E, (byte) 0xEE, 0x0A, (byte) 0xEE, 0x08, (byte) 0xCC,
        0x06, (byte) 0xAA, 0x04, (byte) 0x88, 0x06, (byte) 0xEA, 0x04, (byte) 0xC8,
        0x02, (byte) 0xA6, 0x04, (byte) 0xAC, 0x04, 0x64, 0x02, 0x66,
        0x04, (byte) 0x8A, 0x0A, 0x64, 0x0C, (byte) 0x86, 0x0E, (byte) 0xC8,
    };

    /** One 8x8 cell of the room that a panel tile has to repaint. */
    private record Cell(int roomX, int roomY, int panelTile, boolean flipped) {}

    /** The four attribute words of one metatile, being rewritten. */
    private static final class MetaPatch {
        final int mapOffset;
        final int[] attributes = new int[4];
        MetaPatch(int mapOffset, byte[] map) {
            this.mapOffset = mapOffset;
            int id = readU16(map, mapOffset) & 0x3FF;
            for (int i = 0; i < 4; i++) attributes[i] = readU16(map, id * 8 + i * 2);
        }
    }

    private SidePanelGraphics() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("usage:");
            System.out.println("  SidePanelGraphics extract");
            System.out.println("  SidePanelGraphics sync [rom]");
            System.out.println("  SidePanelGraphics verify [rom]");
            return;
        }
        String mode = args[0];
        if (mode.equals("extract")) extract();
        else if (mode.equals("sync")) sync(args.length > 1 ? args[1] : "Soleil (Spain).md");
        else if (mode.equals("verify")) verify(args.length > 1 ? args[1] : "Choleil.md");
        else throw new IllegalArgumentException("unknown mode: " + mode);
    }

    /**
     * Creates the editable panel if it is not there yet, and always refreshes
     * the two read-only references. An existing EDITAME is never overwritten:
     * it holds the translator's drawing.
     */
    public static void extract() throws IOException {
        Path edit = Paths.get(DEFAULT_EDIT);
        if (!Files.exists(edit)) {
            writePlaceholder(edit);
            System.out.println("Created the side-panel editor " + edit + " with placeholder art ("
                    + PANEL_WIDTH + "x" + PANEL_HEIGHT + ").");
        } else {
            System.out.println("Side-panel editor " + edit + " already exists; left untouched.");
        }
        writeView(readPanel(DEFAULT_EDIT), DEFAULT_VIEW);
        writePaletteReference(DEFAULT_PALETTE_VIEW);
    }

    /**
     * Writes the panel into its tileset slots and paints both copies into the
     * Anemone Beach tilemap. Runs before GraphicsInserter, which recompresses
     * the two gfx_out sheets this touches.
     */
    public static void sync(String romPath) throws IOException {
        Map<String, Integer> positions = SonicHammockGraphics.readScenePositions(
                SonicHammockGraphics.DEFAULT_POSITIONS);
        int lateralX = positions.get("lateral_x");
        int lateralY = positions.get("lateral_y");
        requireTileAligned("lateral_x", lateralX);
        requireTileAligned("lateral_y", lateralY);

        byte[] panel = readPanel(DEFAULT_EDIT);
        writeView(panel, DEFAULT_VIEW);
        writePaletteReference(DEFAULT_PALETTE_VIEW);
        syncTileset(panel, romPath);
        syncMap(panel, romPath, lateralX, lateralY);
    }

    /** Drops the eighteen tiles into their slots without resizing the block. */
    private static void syncTileset(byte[] panel, String romPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        byte[] original = LzToshio.decompress(rom, TILESET_BLOCK);
        for (int slot : SLOTS) {
            if (slot > HIGHEST_TRANSFERRED_TILE) {
                throw new IllegalStateException(String.format(
                        "slot 0x%X is above 0x%X, the highest tile the game is known to transfer "
                                + "to VRAM -- it would not be drawn and would clobber VRAM the game "
                                + "reuses", slot, HIGHEST_TRANSFERRED_TILE));
            }
        }
        if (original.length != TILESET_TILES * TILE_BYTES) {
            throw new IllegalStateException(String.format(
                    "tileset 0x%X has %d bytes, expected %d -- the slot list was chosen for the "
                            + "original block", TILESET_BLOCK, original.length, TILESET_TILES * TILE_BYTES));
        }
        Path gfx = Paths.get(DEFAULT_TILESET_GFX);
        byte[] tiles;
        if (Files.exists(gfx)) {
            tiles = TileRenderer.decodeTileSheet(TileRenderer.readPng(gfx.toString()),
                    TileRenderer.defaultGrayscalePalette(), 16, 1, TILESET_TILES);
        } else {
            tiles = original.clone();
        }
        // Start from the stock bytes so a shrinking edit cannot leave debris in
        // slots the panel no longer covers.
        for (int slot : SLOTS) {
            System.arraycopy(original, slot * TILE_BYTES, tiles, slot * TILE_BYTES, TILE_BYTES);
        }
        for (int i = 0; i < PANEL_TILES; i++) {
            System.arraycopy(panel, i * TILE_BYTES, tiles, SLOTS[i] * TILE_BYTES, TILE_BYTES);
        }
        if (tiles.length != TILESET_TILES * TILE_BYTES) {
            throw new IllegalStateException("the tileset sheet changed size; the block must stay "
                    + TILESET_TILES + " tiles");
        }
        if (gfx.getParent() != null) Files.createDirectories(gfx.getParent());
        TileRenderer.writePng(TileRenderer.renderTileSheet(
                tiles, TileRenderer.defaultGrayscalePalette(), 16, 1), gfx.toString());
        System.out.println("Wrote the side panel into " + SLOTS.length + " free slots of "
                + DEFAULT_TILESET_GFX + " (block still " + TILESET_TILES + " tiles).");
    }

    /**
     * Paints the left panel and its mirrored twin into the room map.
     *
     * Always rebuilt from the stock block rather than from the existing PNG:
     * this is a tilemap, not artwork anyone edits by hand, so starting from
     * the ROM makes repeated builds idempotent however the panels are moved.
     */
    private static void syncMap(byte[] panel, String romPath, int lateralX, int lateralY)
            throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        byte[] originalMap = LzToshio.decompress(rom, MAP_BLOCK_OFFSET);
        byte[] map = Arrays.copyOf(originalMap, originalMap.length);

        int leftX = SONIC_TILE_X + lateralX / 8;
        int topY = SONIC_TILE_Y + lateralY / 8;
        // Mirror the panel about Jesus Gil's 6-tile span.
        int mirror = SONIC_TILE_X + (SONIC_TILE_X + SONIC_TILES_W - 1);

        Map<Long, Cell> cells = new LinkedHashMap<Long, Cell>();
        for (int y = 0; y < PANEL_TILES_H; y++) {
            for (int x = 0; x < PANEL_TILES_W; x++) {
                int tile = y * PANEL_TILES_W + x;
                if (isBlank(panel, tile)) continue;
                addCell(cells, leftX + x, topY + y, tile, false);
                addCell(cells, mirror - (leftX + x), topY + y, tile, true);
            }
        }
        patchCells(map, cells, spareMetatiles(map));

        Path mapGfx = Paths.get(DEFAULT_MAP_GFX);
        int tileCount = map.length / TILE_BYTES;
        if (Files.exists(mapGfx)) {
            byte[] current = TileRenderer.decodeTileSheet(TileRenderer.readPng(mapGfx.toString()),
                    TileRenderer.defaultGrayscalePalette(), 16, 1, tileCount);
            if (Arrays.equals(current, Arrays.copyOf(map, current.length))) {
                System.out.println("Side panels are unchanged; keeping " + mapGfx + " byte-for-byte.");
                return;
            }
        }
        if (mapGfx.getParent() != null) Files.createDirectories(mapGfx.getParent());
        TileRenderer.writePng(TileRenderer.renderTileSheet(
                map, TileRenderer.defaultGrayscalePalette(), 16, 1), mapGfx.toString());
        System.out.println("Placed both side panels in the Anemone Beach tilemap ("
                + cells.size() + " cells, mirrored on the right).");
    }

    /**
     * Metatile ids the room never places, best candidates first.
     *
     * Ids whose definition already holds data come first, and empty ones last.
     * The block is LZ-compressed and written back into a slot only one byte
     * bigger than the original, so replacing four zero words with four real
     * ones costs compressed bytes that the slot does not have; replacing words
     * that were already varied costs almost nothing.
     */
    private static List<Integer> spareMetatiles(byte[] map) {
        Set<Integer> placed = new HashSet<Integer>();
        for (int offset = 0x2000; offset + 1 < map.length; offset += 2) {
            placed.add(readU16(map, offset) & 0x3FF);
        }
        List<Integer> withData = new ArrayList<Integer>();
        List<Integer> empty = new ArrayList<Integer>();
        for (int id = 0; id < METATILE_COUNT; id++) {
            if (placed.contains(id)) continue;
            boolean zero = true;
            for (int i = 0; i < 8; i++) if (map[id * 8 + i] != 0) { zero = false; break; }
            (zero ? empty : withData).add(id);
        }
        withData.addAll(empty);
        return withData;
    }

    private static void addCell(Map<Long, Cell> cells, int roomX, int roomY, int tile, boolean flipped) {
        cells.put((long) roomY << 32 | roomX & 0xFFFFFFFFL, new Cell(roomX, roomY, tile, flipped));
    }

    private static void patchCells(byte[] map, Map<Long, Cell> cells, List<Integer> spare) {
        Map<Integer, MetaPatch> metas = new LinkedHashMap<Integer, MetaPatch>();
        for (Cell cell : cells.values()) {
            int metaX = Math.floorDiv(cell.roomX, 2);
            int metaY = Math.floorDiv(cell.roomY, 2);
            int mapOffset = ROOM_MAP_BASE + (metaY * ROOM_METATILES_PER_ROW + metaX) * 2;
            if (metaX < 0 || metaX >= ROOM_METATILES_PER_ROW
                    || mapOffset < 0x2000 || mapOffset + 1 >= map.length) {
                throw new IllegalStateException("a side panel falls outside the editable room map; "
                        + "check lateral_x / lateral_y");
            }
            MetaPatch meta = metas.get(mapOffset);
            if (meta == null) {
                meta = new MetaPatch(mapOffset, map);
                metas.put(mapOffset, meta);
            }
            int local = Math.floorMod(cell.roomY, 2) * 2 + Math.floorMod(cell.roomX, 2);
            int originalAttribute = meta.attributes[local];
            int paletteLine = originalAttribute >>> 13 & 3;
            if (paletteLine != SIDE_PALETTE_LINE) {
                throw new IllegalStateException("a side panel reaches palette line " + paletteLine
                        + "; move it back onto the original palette-1 beach tiles");
            }
            // Keep the palette bits, clear priority so the panel stays behind
            // Jesus Gil, and set the flip bit for the mirrored copy.
            meta.attributes[local] = (originalAttribute & 0x6000)
                    | (cell.flipped ? HFLIP : 0) | SLOTS[cell.panelTile];
        }
        if (metas.size() > spare.size()) {
            throw new IllegalStateException("the panels need " + metas.size()
                    + " spare metatiles but the room only has " + spare.size());
        }
        int next = 0;
        for (MetaPatch meta : metas.values()) {
            int id = spare.get(next++);
            for (int i = 0; i < meta.attributes.length; i++) {
                writeU16(map, id * 8 + i * 2, meta.attributes[i]);
            }
            writeU16(map, meta.mapOffset, readU16(map, meta.mapOffset) & 0xFC00 | id);
        }
    }

    public static void verify(String romPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        int tilesetAt = 0x120000 + (int) readU32(rom, 0x120000 + 11 * 4);
        byte[] tileset = LzToshio.decompress(rom, tilesetAt);
        if (tileset.length != TILESET_TILES * TILE_BYTES) {
            throw new IllegalStateException("the tileset block is " + tileset.length / TILE_BYTES
                    + " tiles, expected " + TILESET_TILES + " -- it must never change size");
        }
        byte[] panel = readPanel(DEFAULT_EDIT);
        for (int i = 0; i < PANEL_TILES; i++) {
            for (int b = 0; b < TILE_BYTES; b++) {
                if (tileset[SLOTS[i] * TILE_BYTES + b] != panel[i * TILE_BYTES + b]) {
                    throw new IllegalStateException(String.format(
                            "panel tile %d is not in slot 0x%X", i, SLOTS[i]));
                }
            }
        }
        System.out.println("Side panels: " + TILESET_TILES + " tiles intact, all "
                + PANEL_TILES + " panel tiles present in their slots.");
    }

    private static byte[] readPanel(String path) throws IOException {
        Path edit = Paths.get(path);
        if (!Files.exists(edit)) return new byte[PANEL_TILES * TILE_BYTES];
        Bitmap image = TileRenderer.readPng(edit.toString());
        if (image.getWidth() != PANEL_WIDTH || image.getHeight() != PANEL_HEIGHT) {
            throw new IllegalStateException(edit + " must stay " + PANEL_WIDTH + "x" + PANEL_HEIGHT);
        }
        return TileRenderer.decodeTileSheet(image, sidePalette(), PANEL_TILES_W, 1, PANEL_TILES);
    }

    private static void writeView(byte[] panel, String path) throws IOException {
        Path output = Paths.get(path);
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        TileRenderer.writePng(
                TileRenderer.renderTileSheet(panel, sidePalette(), PANEL_TILES_W, 4), output.toString());
    }

    /**
     * A placeholder that is deliberately obvious: a framed block with a cross,
     * so the panels can be found on screen and repositioned before anyone
     * spends time drawing. Every 8x8 tile is non-blank, so all eighteen show.
     */
    private static void writePlaceholder(Path path) throws IOException {
        int[] palette = sidePalette();
        Bitmap image = Bitmap.indexed(PANEL_WIDTH, PANEL_HEIGHT, palette);
        for (int y = 0; y < PANEL_HEIGHT; y++) {
            for (int x = 0; x < PANEL_WIDTH; x++) {
                boolean border = x == 0 || y == 0 || x == PANEL_WIDTH - 1 || y == PANEL_HEIGHT - 1;
                boolean grid = x % 8 == 0 || y % 8 == 0;
                boolean cross = x * PANEL_HEIGHT / PANEL_WIDTH == y
                        || (PANEL_WIDTH - 1 - x) * PANEL_HEIGHT / PANEL_WIDTH == y;
                image.setIndex(x, y, border ? 1 : cross ? 6 : grid ? 9 : 4);
            }
        }
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        TileRenderer.writePng(image, path.toString());
    }

    private static void writePaletteReference(String path) throws IOException {
        int[] palette = sidePalette();
        Bitmap strip = Bitmap.indexed(16 * 8, 8, palette);
        for (int color = 0; color < 16; color++) {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) strip.setIndex(color * 8 + x, y, color);
            }
        }
        Path output = Paths.get(path);
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        TileRenderer.writePng(strip, output.toString());
    }

    private static boolean isBlank(byte[] tiles, int tile) {
        int start = tile * TILE_BYTES;
        for (int i = 0; i < TILE_BYTES; i++) if (tiles[start + i] != 0) return false;
        return true;
    }

    private static void requireTileAligned(String name, int value) {
        if (value % 8 != 0) {
            throw new IllegalStateException(name + " must be a multiple of 8 pixels, found " + value);
        }
    }

    private static int[] sidePalette() {
        return TileRenderer.readGenesisPalette(SIDE_CRAM, 0);
    }

    private static int readU16(byte[] data, int offset) {
        return (data[offset] & 0xFF) << 8 | data[offset + 1] & 0xFF;
    }

    private static long readU32(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF) << 24) | (data[offset + 1] & 0xFF) << 16
                | (data[offset + 2] & 0xFF) << 8 | data[offset + 3] & 0xFF;
    }

    private static void writeU16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 8);
        data[offset + 1] = (byte) value;
    }
}
