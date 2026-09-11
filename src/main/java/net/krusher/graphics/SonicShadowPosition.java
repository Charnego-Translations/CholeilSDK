package net.krusher.graphics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Apply Gil's configured XY displacement to his separate shadow draw call. */
public final class SonicShadowPosition {
    static final int HOOK = 0x02F01E;
    static final int BLOCK = SonicScenePresence.BLOCK + SonicScenePresence.BLOCK_BYTES;
    static final int BLOCK_BYTES = 80;
    static final int ENTRY = BLOCK + 8;
    static final int X_IMMEDIATE = 44;
    static final int Y_IMMEDIATE = 48;
    static final byte[] ORIGINAL_HOOK = java.util.HexFormat.of().parseHex("4eb90001d0e6");
    private static final byte[] MAGIC = "CHOLSHDW".getBytes(StandardCharsets.US_ASCII);

    private SonicShadowPosition() {}

    record ShadowOffsets(int x, int y) {}

    static ShadowOffsets offsets(SonicHammockGraphics.ScenePositions positions) {
        long x = -8L + positions.sonicX(), y = 4L + positions.sonicY();
        if (x < Short.MIN_VALUE || x > Short.MAX_VALUE || y < Short.MIN_VALUE || y > Short.MAX_VALUE) {
            throw new IllegalStateException("SonicGil shadow offset exceeds signed 16-bit coordinates");
        }
        return new ShadowOffsets((int)x, (int)y);
    }

    static byte[] buildBlock(SonicHammockGraphics.ScenePositions positions) {
        ShadowOffsets offsets = offsets(positions);
        SonicSideAnimation.Code c = new SonicSideAnimation.Code();
        c.word(0x48E7); c.word(0xC000); // Save D0/D1; the original shadow routine preserves them too.
        c.word(0x40E7);                // Save incoming SR before our filtering/coordinate changes.
        c.word(0x0C78); c.word(0x001A); c.word(0xFE76); // Resolved early beach only.
        c.branch(0x6600, "draw");
        c.word(0x0C50); c.word(0x0016); // NPC type.
        c.branch(0x6600, "draw");
        c.word(0x0C68); c.word(0x0001); c.word(0x000E); // Sonic variant.
        c.branch(0x6600, "draw");
        c.word(0x303C); c.word(offsets.x); // Original shadow offset -8 plus sonic_x.
        c.word(0x323C); c.word(offsets.y); // Original shadow offset +4 plus sonic_y.
        c.label("draw");
        c.word(0x46DF);                // Original routine receives the original flags.
        c.word(0x4EB9); c.longword(0x01D0E6); // Original draw/terrain/visibility logic, once.
        c.word(0x4CDF); c.word(0x0003); // Restore D0/D1 without changing the returned flags.
        c.word(0x4E75);
        byte[] code = c.finish();
        if (8 + code.length > BLOCK_BYTES) throw new IllegalStateException("shadow code exceeds reservation");
        byte[] block = new byte[BLOCK_BYTES];
        System.arraycopy(MAGIC, 0, block, 0, MAGIC.length);
        System.arraycopy(code, 0, block, 8, code.length);
        return block;
    }

    static byte[] hookBytes() {
        SonicSideAnimation.Code c = new SonicSideAnimation.Code();
        c.word(0x4EB9); c.longword(ENTRY);
        return c.finish();
    }

    static byte[] patchRom(byte[] input, byte[] original, SonicHammockGraphics.ScenePositions positions) {
        byte[] block = buildBlock(positions);
        if (input.length < BLOCK + BLOCK_BYTES || original.length < BLOCK + BLOCK_BYTES
                || !matches(original, HOOK, ORIGINAL_HOOK)) {
            throw new IllegalStateException("unsupported original ROM for SonicGil shadow placement");
        }
        boolean installed = matches(input, HOOK, hookBytes());
        if (installed) {
            for (int i = 0; i < block.length; i++) {
                if (i == X_IMMEDIATE || i == X_IMMEDIATE + 1 || i == Y_IMMEDIATE || i == Y_IMMEDIATE + 1) continue;
                if (input[BLOCK + i] != block[i]) {
                    throw new IllegalStateException("shadow code reservation was modified; ROM left untouched");
                }
            }
        } else if (!matches(input, HOOK, ORIGINAL_HOOK)) {
            throw new IllegalStateException("shadow hook is occupied; ROM left untouched");
        }
        for (int i = BLOCK; i < BLOCK + BLOCK_BYTES; i++) {
            if (original[i] != (byte)0xFF || (!installed && input[i] != original[i])) {
                throw new IllegalStateException("shadow filler at 0x1EBBEC is occupied; ROM left untouched");
            }
        }
        byte[] out = input.clone();
        System.arraycopy(block, 0, out, BLOCK, block.length);
        System.arraycopy(hookBytes(), 0, out, HOOK, ORIGINAL_HOOK.length);
        return out;
    }

    public static void insert(String romPath, String originalPath) throws IOException {
        var positions = SonicHammockGraphics.readPositions(SonicHammockGraphics.DEFAULT_POSITIONS);
        Path path = Path.of(romPath);
        byte[] out = patchRom(Files.readAllBytes(path), Files.readAllBytes(Path.of(originalPath)), positions);
        net.krusher.TextInserter.fixChecksum(out);
        Files.write(path, out);
        System.out.println("SonicGil shadow now follows sonic_x=" + positions.sonicX() + ", sonic_y=" + positions.sonicY());
    }

    public static void verify(String romPath) throws IOException {
        byte[] rom = Files.readAllBytes(Path.of(romPath));
        var positions = SonicHammockGraphics.readPositions(SonicHammockGraphics.DEFAULT_POSITIONS);
        if (!matches(rom, HOOK, hookBytes()) || !matches(rom, BLOCK, buildBlock(positions))) {
            throw new IllegalStateException("SonicGil shadow does not match the configured XY displacement");
        }
        System.out.println("SonicGil shadow: configured XY offsets, hook and NPC/room filters verified.");
    }

    public static void main(String[] args) throws IOException {
        String mode = args.length == 0 ? "verify" : args[0];
        String rom = args.length > 1 ? args[1] : "Choleil.md";
        switch (mode) {
            case "verify" -> verify(rom);
            case "insert" -> insert(rom, "Soleil (Spain).md");
            default -> throw new IllegalArgumentException("use verify [rom] or insert [rom]");
        }
    }

    private static boolean matches(byte[] rom, int at, byte[] expected) {
        return at >= 0 && at + expected.length <= rom.length
                && Arrays.equals(rom, at, at + expected.length, expected, 0, expected.length);
    }
}
