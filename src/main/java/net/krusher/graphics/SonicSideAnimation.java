package net.krusher.graphics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Two-frame, 24x24 background animation for the two SonicGil side characters. */
public final class SonicSideAnimation {
    public static final String LEFT_EDIT = "special_gfx_out/sonic_lateral_izquierdo_arriba_ANIM_EDITAME.png";
    public static final String RIGHT_EDIT = "special_gfx_out/sonic_lateral_derecho_arriba_ANIM_EDITAME.png";
    public static final String LEFT_VIEW = "special_gfx_out/sonic_lateral_izquierdo_arriba_ANIM_x4_VISTA.png";
    public static final String RIGHT_VIEW = "special_gfx_out/sonic_lateral_derecho_arriba_ANIM_x4_VISTA.png";
    static final int FRAME_BYTES = 9 * 32;
    static final int PAIR_BYTES = FRAME_BYTES * 2;
    static final int HOOK = 0x006828;
    // Previously verified filler (also listed in IntroInserter.HUECOS).
    // Insert LAST, after the intro, and refuse if any of this space was claimed.
    static final int BLOCK = 0x15DA3C;
    static final int DATA_OFFSET = 0x100;
    static final int BLOCK_BYTES = DATA_OFFSET + PAIR_BYTES * 2;
    static final int ENTRY = BLOCK + 16;
    static final byte[] ORIGINAL_HOOK = hex("610002944a78a4d6");
    private static final byte[] MAGIC = "CHOL-SIDE-ANIM-01".getBytes(StandardCharsets.US_ASCII);

    private SonicSideAnimation() {}

    public static void main(String[] args) throws IOException {
        String mode = args.length == 0 ? "verify" : args[0];
        String rom = args.length > 1 ? args[1] : "Choleil.md";
        switch (mode) {
            case "init" -> ensureEditors();
            case "insert" -> insert(rom, "Soleil (Spain).md");
            case "verify" -> verify(rom);
            default -> throw new IllegalArgumentException("use init, insert [rom], or verify [rom]");
        }
    }

    public static void ensureEditors() throws IOException {
        ensureEditor(SonicHammockGraphics.DEFAULT_LEFT_EDIT, LEFT_EDIT, LEFT_VIEW);
        ensureEditor(SonicHammockGraphics.DEFAULT_RIGHT_EDIT, RIGHT_EDIT, RIGHT_VIEW);
    }

    private static void ensureEditor(String base, String edit, String view) throws IOException {
        Path file = Path.of(edit);
        if (!Files.exists(file)) {
            byte[] panel = SonicHammockGraphics.readBaseSideTiles(base);
            byte[] frames = new byte[PAIR_BYTES];
            System.arraycopy(panel, 0, frames, 0, FRAME_BYTES);
            System.arraycopy(panel, 0, frames, FRAME_BYTES, FRAME_BYTES);
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            TileRenderer.writePng(TileRenderer.renderSpriteSheet(frames,
                    SonicHammockGraphics.sidePalette(), 3, 3, 2, 1, false), edit);
            System.out.println("Created " + edit + " (two identical 24x24 starting frames).");
        }
        byte[] frames = readFrames(edit);
        TileRenderer.writePng(TileRenderer.renderSpriteSheet(frames,
                SonicHammockGraphics.sidePalette(), 3, 3, 2, 4, false), view);
    }

    static byte[] readFrames(String path) throws IOException {
        SonicHammockGraphics.verifySidePngPalette(path, "animated side panel");
        Bitmap image = TileRenderer.readPng(path);
        if (image.getWidth() != 48 || image.getHeight() != 24) {
            throw new IllegalStateException(path + " must stay 48x24 (two 24x24 frames)");
        }
        return TileRenderer.decodeSpriteSheet(image, SonicHammockGraphics.sidePalette(),
                3, 3, 2, 1, 18, false);
    }

    private static String editorFor(String basePath) {
        if (Path.of(basePath).equals(Path.of(SonicHammockGraphics.DEFAULT_LEFT_EDIT))) return LEFT_EDIT;
        if (Path.of(basePath).equals(Path.of(SonicHammockGraphics.DEFAULT_RIGHT_EDIT))) return RIGHT_EDIT;
        return null;
    }

    static byte[] firstFramePanel(String basePath, byte[] panel) throws IOException {
        String edit = editorFor(basePath);
        if (edit == null || !Files.exists(Path.of(edit))) return panel;
        return composePanel(panel, readFrames(edit), 0);
    }

    static byte[] composePanel(byte[] panel, byte[] frames, int frame) {
        if (panel.length != PAIR_BYTES || frames.length != PAIR_BYTES || frame < 0 || frame > 1) {
            throw new IllegalArgumentException("expected 24x48 panel and two 24x24 animation frames");
        }
        byte[] out = panel.clone();
        System.arraycopy(frames, frame * FRAME_BYTES, out, 0, FRAME_BYTES);
        return out;
    }

    /** Only used to decide tilemap coverage; these ORed bytes are never drawn. */
    static byte[] placementMask(String basePath, byte[] panel) throws IOException {
        String edit = editorFor(basePath);
        if (edit == null || !Files.exists(Path.of(edit))) return panel;
        return unionMask(panel, readFrames(edit));
    }

    static byte[] unionMask(byte[] panel, byte[] frames) {
        byte[] mask = composePanel(panel, frames, 0);
        for (int i = 0; i < FRAME_BYTES; i++) mask[i] |= frames[FRAME_BYTES + i];
        return mask;
    }

    public static void insert(String romPath, String originalPath) throws IOException {
        Path path = Path.of(romPath);
        byte[] rom = Files.readAllBytes(path);
        byte[] original = Files.readAllBytes(Path.of(originalPath));
        byte[] block = buildBlock(readFrames(LEFT_EDIT), readFrames(RIGHT_EDIT));
        byte[] patched = patchRom(rom, original, block);
        net.krusher.TextInserter.fixChecksum(patched);
        Files.write(path, patched);
        System.out.println("Inserted side animation: two 24x24 frames each, 8 game frames per pose; no new VRAM tiles.");
    }

    static byte[] patchRom(byte[] input, byte[] original, byte[] block) {
        if (block.length != BLOCK_BYTES) throw new IllegalArgumentException("invalid animation block length");
        byte[] rom = input.clone();
        byte[] patchedHook = hookBytes();
        boolean installed = matches(rom, HOOK, patchedHook)
                && matches(rom, BLOCK, Arrays.copyOf(block, DATA_OFFSET));
        if (rom.length < BLOCK + BLOCK_BYTES || original.length < BLOCK + BLOCK_BYTES
                || !matches(original, HOOK, ORIGINAL_HOOK)) {
            throw new IllegalStateException("unsupported original ROM for side animation");
        }
        if (!installed && !matches(rom, HOOK, ORIGINAL_HOOK)) {
            throw new IllegalStateException("side-animation hook is occupied; ROM left untouched");
        }
        for (int i = BLOCK; i < BLOCK + BLOCK_BYTES; i++) {
            if (original[i] != 0x20 || (!installed && rom[i] != original[i])) {
                throw new IllegalStateException("side-animation filler at 0x15DA3C is occupied; ROM left untouched");
            }
        }
        System.arraycopy(block, 0, rom, BLOCK, block.length);
        System.arraycopy(patchedHook, 0, rom, HOOK, patchedHook.length);
        return rom;
    }

    public static void verify(String romPath) throws IOException {
        byte[] rom = Files.readAllBytes(Path.of(romPath));
        byte[] block = buildBlock(readFrames(LEFT_EDIT), readFrames(RIGHT_EDIT));
        if (!matches(rom, HOOK, hookBytes()) || !matches(rom, BLOCK, block)) {
            throw new IllegalStateException("side-animation code/data do not match the editable frames");
        }
        System.out.println("Side animation: hook and four 24x24 frames verified byte-for-byte.");
    }

    static byte[] buildBlock(byte[] left, byte[] right) {
        if (left.length != PAIR_BYTES || right.length != PAIR_BYTES) {
            throw new IllegalArgumentException("two 24x24 frames required per character");
        }
        byte[] block = new byte[BLOCK_BYTES];
        System.arraycopy(MAGIC, 0, block, 0, MAGIC.length);
        byte[] code = buildCode();
        if (16 + code.length > DATA_OFFSET) throw new IllegalStateException("animation code exceeds its reservation");
        System.arraycopy(code, 0, block, 16, code.length);
        for (int frame = 0; frame < 2; frame++) {
            System.arraycopy(left, frame * FRAME_BYTES, block, DATA_OFFSET + frame * PAIR_BYTES, FRAME_BYTES);
            System.arraycopy(right, frame * FRAME_BYTES, block, DATA_OFFSET + frame * PAIR_BYTES + FRAME_BYTES, FRAME_BYTES);
        }
        return block;
    }

    static byte[] hookBytes() {
        Code c = new Code();
        c.word(0x4EB9); c.longword(ENTRY); // JSR wrapper
        c.word(0x4E71);                  // NOP (original call + TST occupied 8 bytes)
        return c.finish();
    }

    static byte[] buildCode() {
        Code c = new Code();
        c.word(0x4EB9); c.longword(0x006ABE); // Original VBlank wait and upload routine, exactly once.
        c.word(0x40E7);                     // MOVE.W SR,-(SP)
        c.word(0x48E7); c.word(0xFFFE);     // MOVEM.L D0-D7/A0-A6,-(SP)
        c.word(0x46FC); c.word(0x2700);     // Exclude interrupts during the VDP transaction.
        c.word(0x0C78); c.word(0x001A); c.word(0xFE7A); // CMP.W #beach,current room
        c.branch(0x6600, "done");
        // FE7A retains the entrance ID; FE76 is the resolved story variant.
        // Late beach 0x6D shares the map but no longer loads Sonic's graphics.
        c.word(0x0C78); c.word(0x001A); c.word(0xFE76);
        c.branch(0x6600, "done");
        c.word(0x4A78); c.word(0xA4D6);    // Skip room transitions.
        c.branch(0x6600, "done");
        c.word(0x3038); c.word(0xB55E);    // Gameplay frame counter (pause never calls this hook).
        c.word(0x0240); c.word(7);
        c.branch(0x6600, "done");
        c.word(0x41F9); c.longword(BLOCK + DATA_OFFSET); // LEA frame 0 pair,A0
        c.word(0x0838); c.word(3); c.word(0xB55F);      // Test phase bit in low counter byte.
        c.branch(0x6700, "copy");
        c.word(0xD0FC); c.word(PAIR_BYTES);             // ADDA.W #pair bytes,A0
        c.label("copy");
        c.word(0x33FC); c.word(0x8F02); c.longword(0xC00004); // VDP auto-increment 2
        c.word(0x43F9); c.longword(0xC00000);           // LEA VDP data,A1
        copyTop(c, "left", 0x3E4 * 32);
        copyTop(c, "right", 0x3F6 * 32);
        c.label("done");
        c.word(0x4CDF); c.word(0x7FFF); // Restore all registers and interrupt mask.
        c.word(0x46DF);
        c.word(0x4A78); c.word(0xA4D6); // Original TST for the caller's BNE at 0x6830.
        c.word(0x4E75);
        return c.finish();
    }

    private static void copyTop(Code c, String label, int vramAddress) {
        c.word(0x23FC);
        c.longword(0x40000000 | (vramAddress & 0x3FFF) << 16 | vramAddress >>> 14);
        c.longword(0xC00004);
        c.word(0x7047);            // MOVEQ #71,D0: 72 longwords = 9 tiles.
        c.label(label);
        c.word(0x2298);            // MOVE.L (A0)+,(A1)
        c.branch(0x51C8, label);   // DBRA D0,loop
    }

    private static boolean matches(byte[] rom, int at, byte[] expected) {
        return at >= 0 && at + expected.length <= rom.length
                && Arrays.equals(rom, at, at + expected.length, expected, 0, expected.length);
    }

    private static byte[] hex(String text) { return java.util.HexFormat.of().parseHex(text); }

    /** Tiny label/fixup writer, keeping the 68000 patch auditable and build-tool independent. */
    static final class Code {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final Map<String, Integer> labels = new LinkedHashMap<>();
        final ArrayList<Map.Entry<Integer, String>> branches = new ArrayList<>();
        void word(int value) { bytes.write(value >>> 8 & 255); bytes.write(value & 255); }
        void longword(int value) { word(value >>> 16); word(value); }
        void label(String name) { labels.put(name, bytes.size()); }
        void branch(int opcode, String name) {
            word(opcode); branches.add(Map.entry(bytes.size(), name)); word(0);
        }
        byte[] finish() {
            byte[] out = bytes.toByteArray();
            for (var fix : branches) {
                int at = fix.getKey();
                int delta = labels.get(fix.getValue()) - at;
                if (delta < Short.MIN_VALUE || delta > Short.MAX_VALUE) throw new IllegalStateException("branch too far");
                out[at] = (byte)(delta >>> 8); out[at + 1] = (byte)delta;
            }
            return out;
        }
    }
}
