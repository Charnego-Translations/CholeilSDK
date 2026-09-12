package net.krusher.graphics;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;

/** Moves only SonicGil's interaction coordinates, never his sprite, shadow or entity. */
public final class SonicTalkDetection {
    static final int HOOK = 0x02EF84;
    // Last 68 bytes of the verified 0x15DA3C filler; outside the animation block.
    static final int BLOCK = 0x15DFBC;
    static final int BLOCK_BYTES = 64;
    static final int ENTRY = BLOCK + 8;
    static final byte[] ORIGINAL_HOOK = hex("6100f66c08280005002f");
    private static final byte[] MAGIC = "CHOLTALK".getBytes(StandardCharsets.US_ASCII);

    private SonicTalkDetection() {}

    record TalkPoint(int x, int y) {}

    static TalkPoint talkPoint(SonicHammockGraphics.ScenePositions positions) {
        int x = 808 + positions.sonicX();
        int bottom = Math.floorDiv(624 + positions.sonicY() + 48 + 15, 16) * 16;
        int y = bottom - 16;
        if (x < 0 || x >= 1024 || y < 0 || y >= 2048) {
            throw new IllegalStateException("SonicGil conversation detector is outside the room");
        }
        return new TalkPoint(x, y);
    }

    static byte[] buildBlock(SonicHammockGraphics.ScenePositions positions) {
        TalkPoint point = talkPoint(positions);
        ByteBuffer out = ByteBuffer.allocate(BLOCK_BYTES);
        out.put(MAGIC);
        out.put(hex("48e73000"));         // MOVEM.L D2-D3,-(SP): real position for drawing/shadow.
        out.put(hex("0c78001afe7a6616")); // Beach only, else original call.
        out.put(hex("0c5000166610"));     // NPC type 0x16 only.
        out.put(hex("0c680001000e6608")); // Sonic variant 1 only.
        out.putShort((short)0x343C).putShort((short)point.x); // MOVE.W #talk X,D2
        out.putShort((short)0x363C).putShort((short)point.y); // MOVE.W #talk Y,D3
        out.put(hex("4eb90002e5f2"));     // Original proximity/facing/interaction routine, once.
        out.put(hex("4cdf000c"));         // Restore real D2/D3 before the caller draws anything.
        out.put(hex("08280005002f"));     // Original BTST.B #5,0x2F(A0), flags for BEQ at 0x2EF8E.
        out.putShort((short)0x4E75);       // RTS
        return out.array();
    }

    static byte[] hookBytes() {
        return ByteBuffer.allocate(10).putShort((short)0x4EB9).putInt(ENTRY)
                .putShort((short)0x4E71).putShort((short)0x4E71).array();
    }

    static byte[] patchRom(byte[] input, byte[] original, SonicHammockGraphics.ScenePositions positions) {
        byte[] block = buildBlock(positions);
        if (input.length < BLOCK + BLOCK_BYTES || original.length < BLOCK + BLOCK_BYTES
                || !matches(original, HOOK, ORIGINAL_HOOK)) {
            throw new IllegalStateException("unsupported original ROM for SonicGil conversation detection");
        }
        boolean installed = matches(input, HOOK, hookBytes());
        if (installed) {
            // Only X/Y immediates may differ when changing the configured position.
            for (int i = 0; i < BLOCK_BYTES; i++) {
                if (i == 36 || i == 37 || i == 40 || i == 41) continue;
                if (input[BLOCK + i] != block[i]) {
                    throw new IllegalStateException("conversation code reservation was modified; ROM left untouched");
                }
            }
        } else if (!matches(input, HOOK, ORIGINAL_HOOK)) {
            throw new IllegalStateException("conversation hook is occupied; ROM left untouched");
        }
        for (int i = BLOCK; i < BLOCK + BLOCK_BYTES; i++) {
            if (original[i] != 0x20 || (!installed && input[i] != original[i])) {
                throw new IllegalStateException("conversation filler is occupied; ROM left untouched");
            }
        }
        byte[] out = input.clone();
        System.arraycopy(block, 0, out, BLOCK, block.length);
        System.arraycopy(hookBytes(), 0, out, HOOK, 10);
        return out;
    }

    public static void insert(String romPath, String originalPath) throws IOException {
        var positions = SonicHammockGraphics.readPositions(SonicHammockGraphics.DEFAULT_POSITIONS);
        byte[] out = patchRom(Files.readAllBytes(Path.of(romPath)),
                Files.readAllBytes(Path.of(originalPath)), positions);
        net.krusher.TextInserter.fixChecksum(out);
        Files.write(Path.of(romPath), out);
        TalkPoint point = talkPoint(positions);
        System.out.println("SonicGil talk detector: (" + point.x + "," + point.y + "); sprite and shadow unchanged.");
    }

    public static void verify(String romPath) throws IOException {
        byte[] rom = Files.readAllBytes(Path.of(romPath));
        var positions = SonicHammockGraphics.readPositions(SonicHammockGraphics.DEFAULT_POSITIONS);
        if (!matches(rom, HOOK, hookBytes()) || !matches(rom, BLOCK, buildBlock(positions))) {
            throw new IllegalStateException("SonicGil conversation detection does not match the configured position");
        }
        System.out.println("SonicGil conversation detector: code, guards and configured lower-edge coordinates verified.");
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

    private static byte[] hex(String s) { return HexFormat.of().parseHex(s); }
}
