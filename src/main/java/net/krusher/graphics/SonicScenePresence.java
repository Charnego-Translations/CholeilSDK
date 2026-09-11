package net.krusher.graphics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Restore the original ground when the game's story selects the beach without Sonic. */
public final class SonicScenePresence {
    static final int HOOK = 0x006750;
    // Verified original FF filler; installed after the intro, with occupancy checks.
    static final int BLOCK = 0x1EBACC;
    static final int BLOCK_BYTES = 0x120;
    static final int ENTRY = BLOCK + 16;
    static final int DATA_OFFSET = 0x80;
    static final byte[] ORIGINAL_HOOK = java.util.HexFormat.of().parseHex("4eb9000186ec");
    private static final byte[] MAGIC = "CHOL-SCENE-OFF01".getBytes(StandardCharsets.US_ASCII);
    private static final int MAP_CELLS = 0x2C04;
    private static final int MAP_BYTES = 0x6C04;

    private SonicScenePresence() {}

    public static void main(String[] args) throws IOException {
        verify(args.length == 0 ? "Choleil.md" : args[0], "Soleil (Spain).md");
    }

    public static void insert(String romPath, String originalPath) throws IOException {
        Path path = Path.of(romPath);
        byte[] rom = Files.readAllBytes(path);
        byte[] original = Files.readAllBytes(Path.of(originalPath));
        byte[] patched = patchRom(rom, original, blockFor(rom, original));
        net.krusher.TextInserter.fixChecksum(patched);
        Files.write(path, patched);
        System.out.println("Late Benalmadena: restore original ground and collisions when Sonic is absent.");
    }

    public static void verify(String romPath, String originalPath) throws IOException {
        byte[] rom = Files.readAllBytes(Path.of(romPath));
        byte[] original = Files.readAllBytes(Path.of(originalPath));
        if (!matches(rom, HOOK, hookBytes()) || !matches(rom, BLOCK, blockFor(rom, original))) {
            throw new IllegalStateException("Sonic scene disappearance does not match the current map");
        }
        System.out.println("Sonic scene disappearance: code and original-ground restoration table verified.");
    }

    private static byte[] blockFor(byte[] rom, byte[] original) {
        int pointer = java.nio.ByteBuffer.wrap(rom).getInt(0x15E084);
        return buildBlock(LzToshio.decompress(rom, 0x15E000 + pointer),
                LzToshio.decompress(original, 0x178E7A));
    }

    static byte[] buildBlock(byte[] scene, byte[] original) {
        if (scene.length != MAP_BYTES || original.length != MAP_BYTES) {
            throw new IllegalArgumentException("expected the 64x128 Benalmadena metatile map");
        }
        SonicHammockGraphics.validatePanelReservation(original);
        SonicHammockGraphics.verifyFootprints(scene, original);
        byte[] block = new byte[BLOCK_BYTES];
        System.arraycopy(MAGIC, 0, block, 0, MAGIC.length);
        byte[] code = buildCode();
        if (16 + code.length > DATA_OFFSET) throw new IllegalStateException("scene code exceeds reservation");
        System.arraycopy(code, 0, block, 16, code.length);
        int out = DATA_OFFSET;
        for (int at = MAP_CELLS; at < scene.length; at += 2) {
            int id = word(scene, at) & 0x3FF;
            if (id < 0x3C0 || id >= 0x3E0) continue;
            if (out >= DATA_OFFSET + 32 * 4) throw new IllegalStateException("more than 32 scene cells");
            putWord(block, out, at);
            // Restore the entire original map word (ground, palette/flip flags and
            // collision lookup), not a guessed grass tile. Follows the configured positions.
            putWord(block, out + 2, word(original, at));
            out += 4;
        }
        putWord(block, out, 0xFFFF);
        return block;
    }

    static byte[] buildCode() {
        SonicSideAnimation.Code c = new SonicSideAnimation.Code();
        c.word(0x4EB9); c.longword(0x0186EC); // Replay original camera/map initialization exactly once.
        c.word(0x40E7);                     // Preserve its flags and every register.
        c.word(0x48E7); c.word(0xFFFE);
        // Original redirect at 0x5F06: flag D7=1 and flag 104=0 selects
        // room 6D instead of 1A (table 0x18660). Both use map 22;
        // 6D substitutes actor list 29, which contains no Sonic.
        c.word(0x0C78); c.word(0x006D); c.word(0xFE76);
        c.branch(0x6600, "done");
        c.word(0x41F9); c.longword(BLOCK + DATA_OFFSET);
        c.word(0x43F9); c.longword(0xFF0000);
        c.label("restore");
        c.word(0x3018);                 // MOVE.W (A0)+,D0: map-cell byte offset.
        c.branch(0x6B00, "done");       // BMI: FFFF terminator.
        c.word(0x3398); c.word(0x0000); // MOVE.W (A0)+,(A1,D0.W).
        c.branch(0x6000, "restore");
        c.label("done");
        c.word(0x4CDF); c.word(0x7FFF);
        c.word(0x46DF); c.word(0x4E75);
        return c.finish();
    }

    static byte[] hookBytes() {
        SonicSideAnimation.Code c = new SonicSideAnimation.Code();
        c.word(0x4EB9); c.longword(ENTRY);
        return c.finish();
    }

    static byte[] patchRom(byte[] input, byte[] original, byte[] block) {
        if (block.length != BLOCK_BYTES) throw new IllegalArgumentException("invalid scene block length");
        if (input.length < BLOCK + BLOCK_BYTES || original.length < BLOCK + BLOCK_BYTES
                || !matches(original, HOOK, ORIGINAL_HOOK)) {
            throw new IllegalStateException("unsupported original ROM for scene disappearance");
        }
        boolean installed = matches(input, HOOK, hookBytes())
                && matches(input, BLOCK, Arrays.copyOf(block, DATA_OFFSET));
        if (!installed && !matches(input, HOOK, ORIGINAL_HOOK)) {
            throw new IllegalStateException("scene-disappearance hook is occupied; ROM left untouched");
        }
        for (int i = BLOCK; i < BLOCK + BLOCK_BYTES; i++) {
            if (original[i] != (byte)0xFF || (!installed && input[i] != original[i])) {
                throw new IllegalStateException("scene filler at 0x1EBACC is occupied; ROM left untouched");
            }
        }
        byte[] rom = input.clone();
        System.arraycopy(block, 0, rom, BLOCK, block.length);
        System.arraycopy(hookBytes(), 0, rom, HOOK, ORIGINAL_HOOK.length);
        return rom;
    }

    private static int word(byte[] b, int at) { return (b[at] & 255) << 8 | b[at + 1] & 255; }
    private static void putWord(byte[] b, int at, int v) { b[at] = (byte)(v >>> 8); b[at + 1] = (byte)v; }
    private static boolean matches(byte[] rom, int at, byte[] value) {
        return at >= 0 && at + value.length <= rom.length
                && Arrays.equals(rom, at, at + value.length, value, 0, value.length);
    }
}
