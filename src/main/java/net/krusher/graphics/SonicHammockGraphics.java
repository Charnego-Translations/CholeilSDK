package net.krusher.graphics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Friendly extractor/adapter for Sonic lying in the hammock at Anemone Beach.
 *
 * The ROM stores one compressed 108-tile block at 0x05EBE8. It contains three
 * complete 48x48 poses, but groups all top quadrants first and all bottom
 * quadrants afterwards. This adapter exposes them as three normal frames,
 * side by side, then maps an edited PNG back to the SDK's ordinary compressed
 * tile sheet so GraphicsInserter can keep handling compression and relocation.
 */
public final class SonicHammockGraphics {
    public static final int BLOCK_OFFSET = 0x05EBE8;
    public static final int TILE_BYTES = 32;
    public static final int FRAME_TILES = 36;
    public static final int FRAME_COUNT = 3;
    public static final int TILE_COUNT = FRAME_TILES * FRAME_COUNT;
    public static final int VRAM_FIRST_TILE = 0x376;
    public static final int SIDE_TILES_W = 3;
    public static final int SIDE_TILES_H = 6;
    public static final int SIDE_TILE_COUNT = SIDE_TILES_W * SIDE_TILES_H;
    public static final int PADDING_TILES = 2;
    public static final int LEFT_SIDE_STORAGE_TILE = TILE_COUNT + PADDING_TILES;
    public static final int RIGHT_SIDE_STORAGE_TILE = LEFT_SIDE_STORAGE_TILE + SIDE_TILE_COUNT;
    public static final int EXPANDED_TILE_COUNT = RIGHT_SIDE_STORAGE_TILE + SIDE_TILE_COUNT;
    public static final int EDIT_WIDTH = 144;
    public static final int EDIT_HEIGHT = 48;

    public static final String DEFAULT_EDIT = "special_gfx_out/sonic_hamaca_EDITAME.png";
    public static final String DEFAULT_VIEW = "special_gfx_out/sonic_hamaca_x4_VISTA.png";
    public static final String DEFAULT_GFX = "gfx_out/gfx_05ebe8.png";
    public static final String DEFAULT_LEFT_EDIT = "special_gfx_out/sonic_lateral_izquierdo_EDITAME.png";
    public static final String DEFAULT_LEFT_VIEW = "special_gfx_out/sonic_lateral_izquierdo_x4_VISTA.png";
    public static final String DEFAULT_RIGHT_EDIT = "special_gfx_out/sonic_lateral_derecho_EDITAME.png";
    public static final String DEFAULT_RIGHT_VIEW = "special_gfx_out/sonic_lateral_derecho_x4_VISTA.png";
    public static final String DEFAULT_SIDE_PALETTE_VIEW = "special_gfx_out/sonic_laterales_PALETA.png";
    public static final String DEFAULT_POSITIONS = "sonic_scene_positions.txt";
    public static final String DEFAULT_MAP_GFX = "gfx_out/gfx_178e7a.png";
    public static final String DEFAULT_GRAPHICS_OFFSETS = "graphics_offsets.txt";

    private static final int MAP_BLOCK_OFFSET = 0x178E7A;
    private static final int ROOM_TILE_VRAM_BIAS = 0x100;
    private static final int ROOM_MAP_BASE = 0x3C5C;
    private static final int ROOM_METATILES_PER_ROW = 64;
    private static final int ORIGINAL_SONIC_TILE_X = 10;
    private static final int ORIGINAL_SONIC_TILE_Y = 14;
    private static final int FIRST_CUSTOM_METATILE = 0x3E0;
    private static final int CUSTOM_METATILE_COUNT = 16;
    private static final int LEGACY_FIRST_CUSTOM_METATILE = 0x3F1;
    private static final int LEGACY_CUSTOM_METATILE_COUNT = 12;
    private static final int SIDE_PALETTE_LINE = 1;
    private static final int[] POSITION_X_OFFSETS = {0x2F856, 0x2F85E, 0x2F866, 0x2F86E};
    private static final int[] POSITION_X_BASE = {0xFFE8, 0x0000, 0xFFE8, 0x0000};
    private static final int[] POSITION_Y_OFFSETS = {0x2F850, 0x2F858, 0x2F860, 0x2F868};
    private static final int[] POSITION_Y_BASE = {0x0000, 0xFFE8, 0xFFE8, 0x0000};

    // Captured from CRAM palette line 3 in the confirmed Anemone Beach state.
    private static final byte[] CRAM = {
        0x06, 0x62, 0x00, 0x00, 0x0A, 0x22, 0x0C, 0x42,
        0x0E, 0x44, 0x0E, 0x66, 0x0E, (byte) 0xEE, 0x0A, (byte) 0xAA,
        0x08, (byte) 0x88, 0x04, 0x44, 0x08, (byte) 0xAE, 0x04, 0x6A,
        0x00, 0x0E, 0x00, 0x08, 0x00, (byte) 0xAE, 0x00, (byte) 0x8E,
    };

    // CRAM palette line 1, used by every original background tile in both
    // 24x48 side regions in the confirmed Anemone Beach state.
    private static final byte[] SIDE_CRAM = {
        0x08, (byte) 0xCC, 0x0E, (byte) 0xEE, 0x0A, (byte) 0xEE, 0x08, (byte) 0xCC,
        0x06, (byte) 0xAA, 0x04, (byte) 0x88, 0x06, (byte) 0xEA, 0x04, (byte) 0xC8,
        0x02, (byte) 0xA6, 0x04, (byte) 0xAC, 0x04, 0x64, 0x02, 0x66,
        0x04, (byte) 0x8A, 0x0A, 0x64, 0x0C, (byte) 0x86, 0x0E, (byte) 0xC8,
    };

    private record ScenePositions(int sonicX, int sonicY,
                                  int leftX, int leftY,
                                  int rightX, int rightY) {}

    private record TilePlacement(int roomTileX, int roomTileY,
                                 int panelTile, int firstVramTile) {}

    private static final class CellPatch {
        final int mapOffset;
        final int[] attributes = new int[4];

        CellPatch(int mapOffset, byte[] map) {
            this.mapOffset = mapOffset;
            int originalId = readU16(map, mapOffset) & 0x3FF;
            for (int i = 0; i < attributes.length; i++) {
                attributes[i] = readU16(map, originalId * 8 + i * 2);
            }
        }
    }

    private SonicHammockGraphics() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("usage:");
            System.out.println("  SonicHammockGraphics extract [rom] [editPng] [viewPng]");
            System.out.println("  SonicHammockGraphics sync [editPng] [gfxPng]");
            System.out.println("  SonicHammockGraphics sync-scene [rom]");
            System.out.println("  SonicHammockGraphics init-sides");
            System.out.println("  SonicHammockGraphics place-scene [rom]");
            System.out.println("  SonicHammockGraphics verify [rom]");
            System.out.println("  SonicHammockGraphics verify-scene [rom]");
            return;
        }
        String mode = args[0];
        if (mode.equals("extract")) {
            extract(args.length > 1 ? args[1] : "Soleil (Spain).md",
                    args.length > 2 ? args[2] : DEFAULT_EDIT,
                    args.length > 3 ? args[3] : DEFAULT_VIEW);
        } else if (mode.equals("sync")) {
            sync(args.length > 1 ? args[1] : DEFAULT_EDIT,
                    args.length > 2 ? args[2] : DEFAULT_GFX);
        } else if (mode.equals("sync-scene")) {
            syncScene(args.length > 1 ? args[1] : "Soleil (Spain).md");
        } else if (mode.equals("init-sides")) {
            extractSides();
        } else if (mode.equals("place-scene") || mode.equals("move-left")) {
            patchPosition(args.length > 1 ? args[1] : "Choleil.md");
        } else if (mode.equals("verify")) {
            verify(args.length > 1 ? args[1] : "Soleil (Spain).md");
        } else if (mode.equals("verify-scene")) {
            verifyScene(args.length > 1 ? args[1] : "Choleil.md");
        }
        else throw new IllegalArgumentException("unknown mode: " + mode);
    }

    public static void extract(String romPath, String editPath, String viewPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        byte[] storage = LzToshio.decompress(rom, BLOCK_OFFSET);
        if (storage.length != TILE_COUNT * TILE_BYTES) {
            throw new IllegalStateException(String.format(
                    "Sonic/hammock block 0x%X has %d bytes, expected %d",
                    BLOCK_OFFSET, storage.length, TILE_COUNT * TILE_BYTES));
        }
        byte[] logical = storageToLogical(storage);

        Path edit = Paths.get(editPath);
        Path view = Paths.get(viewPath);
        if (edit.getParent() != null) Files.createDirectories(edit.getParent());
        if (view.getParent() != null) Files.createDirectories(view.getParent());

        TileRenderer.writePng(
                TileRenderer.renderSpriteSheet(logical, editPalette(), 6, 6, 3, 1, true), edit.toString());
        TileRenderer.writePng(
                TileRenderer.renderSpriteSheet(logical, viewPalette(), 6, 6, 3, 4, true), view.toString());
        writeBlankSideEditor(DEFAULT_LEFT_EDIT, DEFAULT_LEFT_VIEW);
        writeBlankSideEditor(DEFAULT_RIGHT_EDIT, DEFAULT_RIGHT_VIEW);
        writeSidePaletteReference(DEFAULT_SIDE_PALETTE_VIEW);
        syncScene(romPath);
        System.out.println("Extracted Sonic + hammock: " + edit + " (3 frames, 48x48 each)");
    }

    /** Converts the friendly edit PNG into the ordinary 16-column gfx_out tile sheet. */
    public static void sync(String editPath, String gfxPath) throws IOException {
        syncExpanded(editPath, DEFAULT_LEFT_EDIT, DEFAULT_RIGHT_EDIT, gfxPath);
    }

    /** Builds the expanded Sonic block and patches the Playa Anemona map asset. */
    public static void syncScene(String romPath) throws IOException {
        ScenePositions positions = readPositions(DEFAULT_POSITIONS);
        syncExpanded(DEFAULT_EDIT, DEFAULT_LEFT_EDIT, DEFAULT_RIGHT_EDIT, DEFAULT_GFX);
        syncSideView(DEFAULT_LEFT_EDIT, DEFAULT_LEFT_VIEW);
        syncSideView(DEFAULT_RIGHT_EDIT, DEFAULT_RIGHT_VIEW);
        syncMap(romPath, DEFAULT_LEFT_EDIT, DEFAULT_RIGHT_EDIT, DEFAULT_MAP_GFX, positions);
        ensureExpandedRegistry(DEFAULT_GRAPHICS_OFFSETS);
    }

    private static void syncExpanded(String editPath, String leftEditPath, String rightEditPath,
                                     String gfxPath) throws IOException {
        Path edit = Paths.get(editPath);
        if (!Files.exists(edit)) {
            System.out.println("Sonic + hammock edit PNG not found; leaving block 0x"
                    + Integer.toHexString(BLOCK_OFFSET) + " untouched.");
            return;
        }
        Bitmap image = TileRenderer.readPng(edit.toString());
        if (image.getWidth() != EDIT_WIDTH || image.getHeight() != EDIT_HEIGHT) {
            throw new IllegalStateException(edit + " must stay " + EDIT_WIDTH + "x" + EDIT_HEIGHT
                    + " (three 48x48 frames side by side)");
        }
        byte[] logical = TileRenderer.decodeSpriteSheet(
                image, editPalette(), 6, 6, 3, 1, TILE_COUNT, true);
        byte[] storage = logicalToStorage(logical);
        byte[] expanded = new byte[EXPANDED_TILE_COUNT * TILE_BYTES];
        System.arraycopy(storage, 0, expanded, 0, storage.length);
        copySideTiles(leftEditPath, expanded, LEFT_SIDE_STORAGE_TILE);
        copySideTiles(rightEditPath, expanded, RIGHT_SIDE_STORAGE_TILE);
        Path gfx = Paths.get(gfxPath);
        if (Files.exists(gfx)) {
            Bitmap current = TileRenderer.readPng(gfx.toString());
            if (current.getWidth() == 128 && current.getHeight() == 80) {
                byte[] currentStorage = TileRenderer.decodeTileSheet(
                        current, TileRenderer.defaultGrayscalePalette(), 16, 1, EXPANDED_TILE_COUNT);
                if (Arrays.equals(expanded, currentStorage)) {
                    System.out.println("Sonic scene edits are unchanged; keeping " + gfx + " byte-for-byte.");
                    return;
                }
            }
        }
        Bitmap sdkSheet = TileRenderer.renderTileSheet(
                expanded, TileRenderer.defaultGrayscalePalette(), 16, 1);
        if (gfx.getParent() != null) Files.createDirectories(gfx.getParent());
        TileRenderer.writePng(sdkSheet, gfx.toString());
        System.out.println("Synced Sonic + hammock and both static side panels into " + gfx);
    }

    private static void copySideTiles(String editPath, byte[] expanded, int destinationTile) throws IOException {
        Path path = Paths.get(editPath);
        if (!Files.exists(path)) return;
        Bitmap image = TileRenderer.readPng(path.toString());
        if (image.getWidth() != 24 || image.getHeight() != 48) {
            throw new IllegalStateException(path + " must stay 24x48");
        }
        byte[] tiles = TileRenderer.decodeTileSheet(image, sidePalette(), SIDE_TILES_W, 1, SIDE_TILE_COUNT);
        System.arraycopy(tiles, 0, expanded, destinationTile * TILE_BYTES, tiles.length);
    }

    private static void syncSideView(String editPath, String viewPath) throws IOException {
        byte[] tiles = readSideTiles(editPath);
        Path output = Paths.get(viewPath);
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        TileRenderer.writePng(TileRenderer.renderTileSheet(
                tiles, sidePalette(), SIDE_TILES_W, 4), output.toString());
    }

    private static void syncMap(String romPath, String leftEditPath, String rightEditPath,
                                String mapGfxPath, ScenePositions positions) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        byte[] originalMap = LzToshio.decompress(rom, MAP_BLOCK_OFFSET);
        byte[] map = Arrays.copyOf(originalMap, originalMap.length);
        Path mapGfx = Paths.get(mapGfxPath);
        int tileCount = map.length / TILE_BYTES;
        byte[] currentMap = null;
        if (Files.exists(mapGfx)) {
            Bitmap current = TileRenderer.readPng(mapGfx.toString());
            currentMap = TileRenderer.decodeTileSheet(current,
                    TileRenderer.defaultGrayscalePalette(), 16, 1, tileCount);
            System.arraycopy(currentMap, 0, map, 0, currentMap.length);
        }

        // Remove any placement made by an earlier build, regardless of where
        // the configuration put it. The reserved IDs had no references in
        // the original map. Also clean the first fixed implementation's IDs.
        restoreReservedPlacements(map, originalMap);

        byte[] left = readSideTiles(leftEditPath);
        byte[] right = readSideTiles(rightEditPath);
        Map<Long, TilePlacement> placements = new LinkedHashMap<Long, TilePlacement>();
        addPanelPlacements(placements, left, positions.leftX, positions.leftY,
                VRAM_FIRST_TILE + LEFT_SIDE_STORAGE_TILE);
        addPanelPlacements(placements, right, positions.rightX, positions.rightY,
                VRAM_FIRST_TILE + RIGHT_SIDE_STORAGE_TILE);
        patchPanelPlacements(map, placements);

        boolean sameEditableTiles = currentMap != null;
        for (int i = 0; sameEditableTiles && i < currentMap.length; i++) {
            sameEditableTiles = map[i] == currentMap[i];
        }
        // The block ends with four non-graphic bytes that the PNG cannot
        // represent; they remain verbatim from the original ROM.
        for (int i = currentMap == null ? 0 : currentMap.length;
             sameEditableTiles && i < map.length; i++) {
            sameEditableTiles = map[i] == originalMap[i];
        }
        if (sameEditableTiles) {
            System.out.println("Sonic side panels are unchanged; keeping " + mapGfx + " byte-for-byte.");
            return;
        }
        if (mapGfx.getParent() != null) Files.createDirectories(mapGfx.getParent());
        TileRenderer.writePng(TileRenderer.renderTileSheet(map,
                TileRenderer.defaultGrayscalePalette(), 16, 1), mapGfx.toString());
        System.out.println("Synced the two static 24x48 panels into the Anemone Beach tilemap.");
    }

    private static void restoreReservedPlacements(byte[] map, byte[] originalMap) {
        for (int offset = 0x2000; offset + 1 < map.length; offset += 2) {
            int id = readU16(map, offset) & 0x3FF;
            boolean current = id >= FIRST_CUSTOM_METATILE
                    && id < FIRST_CUSTOM_METATILE + CUSTOM_METATILE_COUNT;
            boolean legacy = id >= LEGACY_FIRST_CUSTOM_METATILE
                    && id < LEGACY_FIRST_CUSTOM_METATILE + LEGACY_CUSTOM_METATILE_COUNT;
            if (current || legacy) writeU16(map, offset, readU16(originalMap, offset));
        }
        restoreDefinitions(map, originalMap, FIRST_CUSTOM_METATILE, CUSTOM_METATILE_COUNT);
        restoreDefinitions(map, originalMap, LEGACY_FIRST_CUSTOM_METATILE, LEGACY_CUSTOM_METATILE_COUNT);
    }

    private static void restoreDefinitions(byte[] map, byte[] originalMap, int first, int count) {
        for (int id = first; id < first + count; id++) {
            System.arraycopy(originalMap, id * 8, map, id * 8, 8);
        }
    }

    private static void addPanelPlacements(Map<Long, TilePlacement> placements,
                                           byte[] panel, int offsetX, int offsetY,
                                           int firstVramTile) {
        requireTileAligned("panel X", offsetX);
        requireTileAligned("panel Y", offsetY);
        int firstX = ORIGINAL_SONIC_TILE_X + offsetX / 8;
        int firstY = ORIGINAL_SONIC_TILE_Y + offsetY / 8;
        for (int y = 0; y < SIDE_TILES_H; y++) {
            for (int x = 0; x < SIDE_TILES_W; x++) {
                int panelTile = y * SIDE_TILES_W + x;
                if (isBlankTile(panel, panelTile)) continue;
                int roomX = firstX + x;
                int roomY = firstY + y;
                long key = (long) roomY << 32 | roomX & 0xFFFFFFFFL;
                placements.put(key, new TilePlacement(roomX, roomY, panelTile, firstVramTile));
            }
        }
    }

    private static void patchPanelPlacements(byte[] map, Map<Long, TilePlacement> placements) {
        Map<Integer, CellPatch> cells = new LinkedHashMap<Integer, CellPatch>();
        for (TilePlacement placement : placements.values()) {
            int metaX = Math.floorDiv(placement.roomTileX, 2);
            int metaY = Math.floorDiv(placement.roomTileY, 2);
            int mapOffset = ROOM_MAP_BASE
                    + (metaY * ROOM_METATILES_PER_ROW + metaX) * 2;
            if (metaX < 0 || metaX >= ROOM_METATILES_PER_ROW
                    || mapOffset < 0x2000 || mapOffset + 1 >= map.length) {
                throw new IllegalStateException("panel position falls outside the editable room map");
            }
            CellPatch cell = cells.get(mapOffset);
            if (cell == null) {
                cell = new CellPatch(mapOffset, map);
                cells.put(mapOffset, cell);
            }
            int local = Math.floorMod(placement.roomTileY, 2) * 2
                    + Math.floorMod(placement.roomTileX, 2);
            int originalAttribute = cell.attributes[local];
            int paletteLine = originalAttribute >>> 13 & 3;
            if (paletteLine != SIDE_PALETTE_LINE) {
                throw new IllegalStateException("panel position reaches palette line " + paletteLine
                        + "; move it back onto the original palette-1 beach tiles");
            }
            int storedTile = placement.firstVramTile + placement.panelTile - ROOM_TILE_VRAM_BIAS;
            // Keep the original palette bits. Priority is cleared deliberately
            // so these static tiles always remain behind Jesus Gil.
            cell.attributes[local] = (originalAttribute & 0x6000) | storedTile;
        }
        if (cells.size() > CUSTOM_METATILE_COUNT) {
            throw new IllegalStateException("the configured panels need " + cells.size()
                    + " custom metatiles; maximum is " + CUSTOM_METATILE_COUNT);
        }
        int id = FIRST_CUSTOM_METATILE;
        for (CellPatch cell : cells.values()) {
            for (int i = 0; i < cell.attributes.length; i++) {
                writeU16(map, id * 8 + i * 2, cell.attributes[i]);
            }
            int oldMapWord = readU16(map, cell.mapOffset);
            writeU16(map, cell.mapOffset, oldMapWord & 0xFC00 | id);
            id++;
        }
    }

    private static void requireTileAligned(String name, int value) {
        if (value % 8 != 0) {
            throw new IllegalStateException(name + " must be a multiple of 8 pixels, found " + value);
        }
    }

    private static byte[] readSideTiles(String path) throws IOException {
        Path edit = Paths.get(path);
        if (!Files.exists(edit)) return new byte[SIDE_TILE_COUNT * TILE_BYTES];
        Bitmap image = TileRenderer.readPng(edit.toString());
        if (image.getWidth() != 24 || image.getHeight() != 48) {
            throw new IllegalStateException(edit + " must stay 24x48");
        }
        return TileRenderer.decodeTileSheet(image, sidePalette(), SIDE_TILES_W, 1, SIDE_TILE_COUNT);
    }

    private static boolean isBlankTile(byte[] tiles, int tile) {
        int start = tile * TILE_BYTES;
        for (int i = 0; i < TILE_BYTES; i++) if (tiles[start + i] != 0) return false;
        return true;
    }

    private static ScenePositions readPositions(String path) throws IOException {
        Map<String, Integer> values = new LinkedHashMap<String, Integer>();
        values.put("sonic_x", -24);
        values.put("sonic_y", 0);
        values.put("izquierdo_x", -40);
        values.put("izquierdo_y", -8);
        values.put("derecho_x", 16);
        values.put("derecho_y", -8);

        Path file = Paths.get(path);
        if (!Files.exists(file)) {
            throw new IllegalStateException(path + " is missing");
        }
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw;
            int comment = line.indexOf('#');
            if (comment >= 0) line = line.substring(0, comment);
            line = line.trim();
            if (line.isEmpty()) continue;
            int equals = line.indexOf('=');
            if (equals <= 0) throw new IllegalStateException("bad position line: " + raw);
            String key = line.substring(0, equals).trim().toLowerCase();
            if (!values.containsKey(key)) throw new IllegalStateException("unknown position: " + key);
            try {
                values.put(key, Integer.decode(line.substring(equals + 1).trim()));
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("bad number in position line: " + raw);
            }
        }
        ScenePositions positions = new ScenePositions(
                values.get("sonic_x"), values.get("sonic_y"),
                values.get("izquierdo_x"), values.get("izquierdo_y"),
                values.get("derecho_x"), values.get("derecho_y"));
        requireTileAligned("izquierdo_x", positions.leftX);
        requireTileAligned("izquierdo_y", positions.leftY);
        requireTileAligned("derecho_x", positions.rightX);
        requireTileAligned("derecho_y", positions.rightY);
        return positions;
    }

    /** Places Jesus Gil at the configured pixel offset from his original anchor. */
    public static void patchPosition(String romPath) throws IOException {
        ScenePositions positions = readPositions(DEFAULT_POSITIONS);
        Path path = Paths.get(romPath);
        byte[] rom = Files.readAllBytes(path);
        for (int i = 0; i < POSITION_X_OFFSETS.length; i++) {
            patchWord(rom, POSITION_X_OFFSETS[i], POSITION_X_BASE[i],
                    POSITION_X_BASE[i] + positions.sonicX);
            patchWord(rom, POSITION_Y_OFFSETS[i], POSITION_Y_BASE[i],
                    POSITION_Y_BASE[i] + positions.sonicY);
        }
        net.krusher.TextInserter.fixChecksum(rom);
        Files.write(path, rom);
        System.out.println("Placed Jesus Gil at configurable offset X="
                + positions.sonicX + ", Y=" + positions.sonicY + ".");
    }

    private static void patchWord(byte[] rom, int offset, int expected, int replacement) {
        int actual = readU16(rom, offset);
        if (actual == replacement) return;
        if (actual != expected) {
            throw new IllegalStateException(String.format(
                    "position patch mismatch at 0x%X: expected %04X, found %04X",
                    offset, expected, actual));
        }
        writeU16(rom, offset, replacement);
    }

    private static int readU16(byte[] data, int offset) {
        return (data[offset] & 0xFF) << 8 | data[offset + 1] & 0xFF;
    }

    private static int readU32(byte[] data, int offset) {
        return (data[offset] & 0xFF) << 24 | (data[offset + 1] & 0xFF) << 16
                | (data[offset + 2] & 0xFF) << 8 | data[offset + 3] & 0xFF;
    }

    private static void writeU16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 8);
        data[offset + 1] = (byte) value;
    }

    private static void writeBlankSideEditor(String editPath, String viewPath) throws IOException {
        Bitmap edit = Bitmap.indexed(24, 48, sidePalette());
        Bitmap view = Bitmap.indexed(96, 192, sidePalette());
        for (int y = 0; y < 48; y++) for (int x = 0; x < 24; x++) edit.setIndex(x, y, 0);
        for (int y = 0; y < 192; y++) for (int x = 0; x < 96; x++) view.setIndex(x, y, 0);
        Path editFile = Paths.get(editPath);
        Path viewFile = Paths.get(viewPath);
        if (editFile.getParent() != null) Files.createDirectories(editFile.getParent());
        if (viewFile.getParent() != null) Files.createDirectories(viewFile.getParent());
        TileRenderer.writePng(edit, editFile.toString());
        TileRenderer.writePng(view, viewFile.toString());
    }

    public static void extractSides() throws IOException {
        writeBlankSideEditor(DEFAULT_LEFT_EDIT, DEFAULT_LEFT_VIEW);
        writeBlankSideEditor(DEFAULT_RIGHT_EDIT, DEFAULT_RIGHT_VIEW);
        writeSidePaletteReference(DEFAULT_SIDE_PALETTE_VIEW);
        System.out.println("Created the two static 24x48 Sonic side-panel editors with the original background palette.");
    }

    private static void writeSidePaletteReference(String path) throws IOException {
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

    private static void ensureExpandedRegistry(String registryPath) throws IOException {
        Path path = Paths.get(registryPath);
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<String> updated = new ArrayList<String>(lines.size());
        boolean found = false;
        for (String line : lines) {
            if (line.toLowerCase().startsWith("0x5ebe8,")) {
                String[] parts = line.split(",");
                updated.add(parts[0] + "," + parts[1] + "," + (EXPANDED_TILE_COUNT * TILE_BYTES));
                found = true;
            } else {
                updated.add(line);
            }
        }
        if (!found) throw new IllegalStateException("0x5ebe8 is missing from " + registryPath);
        Files.write(path, updated, StandardCharsets.UTF_8);
    }

    public static void verify(String romPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        byte[] original = LzToshio.decompress(rom, BLOCK_OFFSET);
        byte[] logical = storageToLogical(original);
        Bitmap editImage = TileRenderer.renderSpriteSheet(logical, editPalette(), 6, 6, 3, 1, true);
        byte[] decodedEdit = TileRenderer.decodeSpriteSheet(
                editImage, editPalette(), 6, 6, 3, 1, TILE_COUNT, true);
        byte[] roundTrip = logicalToStorage(decodedEdit);
        if (!Arrays.equals(original, roundTrip)) {
            throw new IllegalStateException("Sonic/hammock tile mapping is not byte-exact");
        }
        System.out.println("Sonic + hammock mapping round-trip: byte-identical ("
                + original.length + " bytes, " + TILE_COUNT + " tiles)");
    }

    public static void verifyScene(String romPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        ScenePositions positions = readPositions(DEFAULT_POSITIONS);
        for (int i = 0; i < POSITION_X_OFFSETS.length; i++) {
            if (readU16(rom, POSITION_X_OFFSETS[i])
                    != (POSITION_X_BASE[i] + positions.sonicX & 0xFFFF)) {
                throw new IllegalStateException(String.format(
                        "Jesus Gil X position is not patched at 0x%X", POSITION_X_OFFSETS[i]));
            }
            if (readU16(rom, POSITION_Y_OFFSETS[i])
                    != (POSITION_Y_BASE[i] + positions.sonicY & 0xFFFF)) {
                throw new IllegalStateException(String.format(
                        "Jesus Gil Y position is not patched at 0x%X", POSITION_Y_OFFSETS[i]));
            }
        }

        int sonicAddress = 0x59000 + readU32(rom, 0x5903C);
        byte[] expanded = LzToshio.decompress(rom, sonicAddress);
        if (expanded.length != EXPANDED_TILE_COUNT * TILE_BYTES) {
            throw new IllegalStateException("expanded Sonic block has " + expanded.length
                    + " bytes, expected " + (EXPANDED_TILE_COUNT * TILE_BYTES));
        }
        byte[] left = readSideTiles(DEFAULT_LEFT_EDIT);
        byte[] right = readSideTiles(DEFAULT_RIGHT_EDIT);
        verifySidePngPalette(DEFAULT_LEFT_EDIT, "left panel");
        verifySidePngPalette(DEFAULT_RIGHT_EDIT, "right panel");
        verifySideBytes(expanded, left, LEFT_SIDE_STORAGE_TILE, "left panel");
        verifySideBytes(expanded, right, RIGHT_SIDE_STORAGE_TILE, "right panel");

        int mapAddress = 0x15E000 + readU32(rom, 0x15E084);
        byte[] map = LzToshio.decompress(rom, mapAddress);
        byte[] originalRom = Files.readAllBytes(Paths.get("Soleil (Spain).md"));
        byte[] originalMap = LzToshio.decompress(originalRom, MAP_BLOCK_OFFSET);
        Map<Long, TilePlacement> placements = new LinkedHashMap<Long, TilePlacement>();
        addPanelPlacements(placements, left, positions.leftX, positions.leftY,
                VRAM_FIRST_TILE + LEFT_SIDE_STORAGE_TILE);
        addPanelPlacements(placements, right, positions.rightX, positions.rightY,
                VRAM_FIRST_TILE + RIGHT_SIDE_STORAGE_TILE);
        verifyPanelPlacements(map, originalMap, placements);
        System.out.println("Sonic scene: all three configured positions, both 24x48 editors,"
                + " palettes and tilemap references are byte-exact.");
    }

    private static void verifySideBytes(byte[] expanded, byte[] panel, int destinationTile,
                                        String name) {
        int start = destinationTile * TILE_BYTES;
        for (int i = 0; i < panel.length; i++) {
            if (expanded[start + i] != panel[i]) {
                throw new IllegalStateException(name + " pixels differ at byte " + i);
            }
        }
    }

    private static void verifyPanelPlacements(byte[] map, byte[] originalMap,
                                              Map<Long, TilePlacement> placements) {
        for (TilePlacement placement : placements.values()) {
            int metaX = Math.floorDiv(placement.roomTileX, 2);
            int metaY = Math.floorDiv(placement.roomTileY, 2);
            int mapOffset = ROOM_MAP_BASE
                    + (metaY * ROOM_METATILES_PER_ROW + metaX) * 2;
            int id = readU16(map, mapOffset) & 0x3FF;
            if (id < FIRST_CUSTOM_METATILE
                    || id >= FIRST_CUSTOM_METATILE + CUSTOM_METATILE_COUNT) {
                throw new IllegalStateException("configured panel metatile is not placed");
            }
            int local = Math.floorMod(placement.roomTileY, 2) * 2
                    + Math.floorMod(placement.roomTileX, 2);
            int actual = readU16(map, id * 8 + local * 2);
            int originalId = readU16(originalMap, mapOffset) & 0x3FF;
            int originalAttribute = readU16(originalMap, originalId * 8 + local * 2);
            int expected = (originalAttribute & 0x6000)
                    | (placement.firstVramTile + placement.panelTile - ROOM_TILE_VRAM_BIAS);
            if (actual != expected) {
                throw new IllegalStateException("configured panel tilemap reference differs");
            }
        }
    }

    private static void verifySidePngPalette(String path, String name) throws IOException {
        if (!Arrays.equals(readIndexedPngPalette(Paths.get(path)), sidePalette())) {
            throw new IllegalStateException(name + " PNG does not contain the exact original background palette");
        }
    }

    private static int[] readIndexedPngPalette(Path path) throws IOException {
        byte[] png = Files.readAllBytes(path);
        for (int at = 8; at + 12 <= png.length;) {
            int length = readU32(png, at);
            int data = at + 8;
            if (length < 0 || data + length + 4 > png.length) break;
            String type = new String(png, at + 4, 4, StandardCharsets.US_ASCII);
            if (type.equals("PLTE")) {
                int[] palette = new int[length / 3];
                for (int i = 0; i < palette.length; i++) {
                    int r = png[data + i * 3] & 0xFF;
                    int g = png[data + i * 3 + 1] & 0xFF;
                    int b = png[data + i * 3 + 2] & 0xFF;
                    palette[i] = 0xFF000000 | r << 16 | g << 8 | b;
                }
                return palette;
            }
            at = data + length + 4;
        }
        return null;
    }

    static byte[] storageToLogical(byte[] storage) {
        byte[] logical = new byte[storage.length];
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            for (int row = 0; row < 6; row++) {
                for (int col = 0; col < 6; col++) {
                    int quadrant = (row / 3) * 2 + (col / 3);
                    int localRow = row % 3;
                    int localCol = col % 3;
                    int sourceSprite = sourceSprite(frame, quadrant);
                    int sourceTile = sourceSprite * 9 + localCol * 3 + localRow;
                    int logicalTile = frame * FRAME_TILES + row * 6 + col;
                    copyTile(storage, sourceTile, logical, logicalTile);
                }
            }
        }
        return logical;
    }

    static byte[] logicalToStorage(byte[] logical) {
        byte[] storage = new byte[logical.length];
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            for (int row = 0; row < 6; row++) {
                for (int col = 0; col < 6; col++) {
                    int quadrant = (row / 3) * 2 + (col / 3);
                    int localRow = row % 3;
                    int localCol = col % 3;
                    int sourceSprite = sourceSprite(frame, quadrant);
                    int sourceTile = sourceSprite * 9 + localCol * 3 + localRow;
                    int logicalTile = frame * FRAME_TILES + row * 6 + col;
                    copyTile(logical, logicalTile, storage, sourceTile);
                }
            }
        }
        return storage;
    }

    private static int sourceSprite(int frame, int quadrant) {
        // ROM order: TL/TR for all 3 frames, then BL/BR for all 3 frames.
        return quadrant < 2 ? frame * 2 + quadrant : 6 + frame * 2 + (quadrant - 2);
    }

    private static void copyTile(byte[] from, int fromTile, byte[] to, int toTile) {
        System.arraycopy(from, fromTile * TILE_BYTES, to, toTile * TILE_BYTES, TILE_BYTES);
    }

    private static int[] editPalette() {
        int[] palette = TileRenderer.readGenesisPalette(CRAM, 0);
        palette[0] = 0xFFFF00FF; // transparent sprite index, made magenta so it cannot be confused with black outlines
        return palette;
    }

    private static int[] viewPalette() {
        int[] palette = TileRenderer.readGenesisPalette(CRAM, 0);
        // Keep it distinct from the real black at index 1: duplicate PLTE
        // colours can be collapsed by image software and shift the other indices.
        palette[0] = 0xFF010101; // visual-only approximation of transparency on a black crop
        return palette;
    }

    private static int[] sidePalette() {
        return TileRenderer.readGenesisPalette(SIDE_CRAM, 0);
    }
}
