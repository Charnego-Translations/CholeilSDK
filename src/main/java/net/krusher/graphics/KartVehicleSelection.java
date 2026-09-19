package net.krusher.graphics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import net.krusher.DefaultPaths;
import net.krusher.FreeSpaceScanner;
import net.krusher.TblTable;
import net.krusher.TextInserter;

/** Three post-victory kart choices; the first race and its normal graphics are untouched. */
public final class KartVehicleSelection {
    public static final String KITTY_EDIT = "special_gfx_out/kart_kitty_EDITAME.png";
    public static final String MAZDA_EDIT = "special_gfx_out/kart_mazdaMX5_EDITAME.png";
    static final int NPC_TABLE = 0x1D2A7A;
    static final int OLD_STRING_TABLE = 0x1D2D2E;
    static final int NEW_STRING_TABLE = 0x1D2B00;
    static final int SCRIPT_RESERVE_END = 0x1D2D20;
    static final int CHOICE_HOOK = 0x02074C;
    static final int ENTRY_OFFSET = 0x40;
    static final int KITTY_OFFSET = 0x100;
    static final byte[] ORIGINAL_HOOK = HexFormat.of().parseHex("31fc0001b640");
    static final byte[] MAGIC = "CHOLKVR1".getBytes(StandardCharsets.US_ASCII);

    private KartVehicleSelection() {}

    public static void insertDialogue(String romPath, String originalPath, String tblPath) throws IOException {
        byte[] input = Files.readAllBytes(Path.of(romPath));
        byte[] original = Files.readAllBytes(Path.of(originalPath));
        byte[] out = patchDialogue(input, original, TblTable.load(tblPath));
        TextInserter.fixChecksum(out);
        Files.write(Path.of(romPath), out);
        System.out.println("Heidi: three vehicle responses, with a reserved 16-entry text table.");
    }

    static byte[] patchDialogue(byte[] input, byte[] original, TblTable table) {
        if (read16(original, NPC_TABLE + 2) != OLD_STRING_TABLE - NPC_TABLE
                || read16(input, NPC_TABLE + 2) != OLD_STRING_TABLE - NPC_TABLE
                || input.length < SCRIPT_RESERVE_END || original.length < SCRIPT_RESERVE_END) {
            throw new IllegalStateException("Heidi NPC table differs; refusing to patch dialogue");
        }
        if (!Arrays.equals(input, NEW_STRING_TABLE, SCRIPT_RESERVE_END,
                original, NEW_STRING_TABLE, SCRIPT_RESERVE_END)) {
            throw new IllegalStateException("Reserved Heidi dialogue area is occupied");
        }
        byte[] out = input.clone();
        int[] targets = new int[16];
        for (int i = 0; i < 14; i++) {
            targets[i] = OLD_STRING_TABLE + read16(input, OLD_STRING_TABLE + i * 2);
            if (targets[i] < OLD_STRING_TABLE + 28 || targets[i] >= input.length) {
                throw new IllegalStateException("Invalid Heidi string pointer " + i);
            }
        }
        int oldEnd = targets[0];
        while (oldEnd < input.length && input[oldEnd] != (byte) 0xFF) oldEnd++;
        if (oldEnd == input.length || oldEnd - targets[0] > 200) {
            throw new IllegalStateException("Heidi greeting has no bounded terminator");
        }
        byte[] greeting = Arrays.copyOfRange(input, targets[0], oldEnd + 1);
        int cursor = NEW_STRING_TABLE + 32;
        targets[0] = cursor;
        System.arraycopy(greeting, 0, out, cursor, greeting.length);
        cursor = align(cursor + greeting.length);
        byte[] kitty = encode(table, "{E1}0{5C}\n{E0}0{5B}\nHEIDI: ¡Kitty elegida!\n¡Dale caña!");
        byte[] mazda = encode(table, "{E1}0{5B}\n{E0}0{5C}\nHEIDI: Mazda MX5 de Krusher.\n¡Corre, chavalín!");
        targets[14] = cursor;
        System.arraycopy(kitty, 0, out, cursor, kitty.length);
        cursor = align(cursor + kitty.length);
        targets[15] = cursor;
        if (cursor + mazda.length > SCRIPT_RESERVE_END) {
            throw new IllegalStateException("Heidi's reserved text space is too small");
        }
        System.arraycopy(mazda, 0, out, cursor, mazda.length);
        for (int i = 0; i < targets.length; i++) {
            int relative = targets[i] - NEW_STRING_TABLE;
            if (relative < 0 || relative >= 0x8000) {
                throw new IllegalStateException("Heidi string " + i + " is out of relative reach");
            }
            put16(out, NEW_STRING_TABLE + i * 2, relative);
        }
        if (read16(out, NEW_STRING_TABLE) != 32) {
            throw new IllegalStateException("Heidi's new table is not self-describing");
        }
        put16(out, NPC_TABLE + 2, NEW_STRING_TABLE - NPC_TABLE);
        return out;
    }

    /** Reserve both alternative compressed sheets before the intro allocates filler. */
    public static List<int[]> insertVehicles(String romPath, String originalPath, String freeSpacePath,
                                             List<int[]> occupiedEarlier) throws IOException {
        byte[] input = Files.readAllBytes(Path.of(romPath));
        byte[] original = Files.readAllBytes(Path.of(originalPath));
        if (!matches(original, CHOICE_HOOK, ORIGINAL_HOOK)
                || !matches(input, CHOICE_HOOK, ORIGINAL_HOOK)) {
            throw new IllegalStateException("Kart mounting hook is occupied");
        }
        byte[] kitty = compressedEditor(KITTY_EDIT, original);
        byte[] mazda = compressedEditor(MAZDA_EDIT, original);
        int mazdaOffset = align(KITTY_OFFSET + kitty.length);
        int length = mazdaOffset + mazda.length;
        List<int[]> occupied = new ArrayList<>(occupiedEarlier);
        for (FreeSpaceScanner.Region r : FreeSpaceScanner.readRegionsFile(freeSpacePath)) {
            occupied.add(new int[]{r.start, Math.addExact(r.start, r.length)});
        }
        int base = KartRaceMusic.allocate(input, original, length, occupied);
        byte[] code = code(base, base + KITTY_OFFSET, base + mazdaOffset);
        if (ENTRY_OFFSET + code.length > KITTY_OFFSET) {
            throw new IllegalStateException("Kart vehicle selector does not fit before the graphics");
        }
        byte[] block = new byte[length];
        System.arraycopy(MAGIC, 0, block, 0, MAGIC.length);
        put32(block, 8, length);
        put32(block, 12, base + KITTY_OFFSET);
        put32(block, 16, base + mazdaOffset);
        System.arraycopy(code, 0, block, ENTRY_OFFSET, code.length);
        System.arraycopy(kitty, 0, block, KITTY_OFFSET, kitty.length);
        System.arraycopy(mazda, 0, block, mazdaOffset, mazda.length);
        byte[] out = input.clone();
        System.arraycopy(block, 0, out, base, block.length);
        out[CHOICE_HOOK] = (byte) 0x4E;
        out[CHOICE_HOOK + 1] = (byte) 0xB9;
        put32(out, CHOICE_HOOK + 2, base + ENTRY_OFFSET);
        TextInserter.fixChecksum(out);
        Files.write(Path.of(romPath), out);
        System.out.printf("Kart choices: Kitty and Mazda compressed at %06X..%06X; fragoneta stays at its live pointer.%n",
                base, base + length);
        return List.of(new int[]{base, base + length});
    }

    public static void verify(String romPath, String originalPath) throws IOException {
        byte[] rom = Files.readAllBytes(Path.of(romPath));
        byte[] original = Files.readAllBytes(Path.of(originalPath));
        int base = read32(rom, CHOICE_HOOK + 2) - ENTRY_OFFSET;
        if (!KartRaceMusic.allowed(base, KITTY_OFFSET)
                || !matches(rom, base, MAGIC)
                || read32(rom, base + 8) <= KITTY_OFFSET
                || !matches(rom, CHOICE_HOOK, hook(base))) {
            throw new IllegalStateException("Kart vehicle selector missing or corrupted");
        }
        int kittyAt = read32(rom, base + 12), mazdaAt = read32(rom, base + 16);
        int length = read32(rom, base + 8);
        if (!KartRaceMusic.allowed(base, length) || kittyAt != base + KITTY_OFFSET
                || mazdaAt <= kittyAt || mazdaAt >= base + length
                || !matches(rom, base + ENTRY_OFFSET, code(base, kittyAt, mazdaAt))) {
            throw new IllegalStateException("Kart selector addresses or code do not match");
        }
        byte[] kitty = KartGraphics.decode(Png.read(KITTY_EDIT), KartGraphics.editPalette(original));
        byte[] mazda = KartGraphics.decode(Png.read(MAZDA_EDIT), KartGraphics.editPalette(original));
        for (int i = 0; i < 2; i++) {
            int at = i == 0 ? kittyAt : mazdaAt;
            byte[] expected = i == 0 ? kitty : mazda;
            LzToshio.Result unpacked = LzToshio.tryDecompress(rom, at, 20000, KartGraphics.BYTE_COUNT);
            if (unpacked == null || !Arrays.equals(unpacked.data, expected)) {
                throw new IllegalStateException("Kart alternative " + i + " differs from its editor");
            }
        }
        if (read16(rom, NPC_TABLE + 2) != NEW_STRING_TABLE - NPC_TABLE
                || read16(rom, NEW_STRING_TABLE) != 32) {
            throw new IllegalStateException("Heidi's three-choice dialogue table is missing");
        }
        System.out.println("Kart choice table, two editors and mount-time VRAM upload verified structurally.");
    }

    private static byte[] code(int base, int kittyAt, int mazdaAt) {
        var c = new SonicSideAnimation.Code();
        c.word(0x31FC); c.word(1); c.word(0xB640); // Displaced mount instruction.
        c.word(0x48E7); c.word(0xFFFE);             // Save D0-D7/A0-A6.
        c.word(0x2039); c.longword(KartGraphics.POINTER_FIELD);
        c.word(0x0680); c.longword(KartGraphics.TABLE_BASE);
        c.word(0x2240);                            // MOVEA.L D0,A1: live fragoneta block.
        c.word(0x0838); c.word(4); c.word(0xC52B); // Mazda flag $015C.
        c.branch(0x6700, "kitty");
        c.word(0x227C); c.longword(mazdaAt);
        c.branch(0x6000, "upload");
        c.label("kitty");
        c.word(0x0838); c.word(3); c.word(0xC52B); // Kitty flag $015B.
        c.branch(0x6700, "upload");
        c.word(0x227C); c.longword(kittyAt);
        c.label("upload");
        c.word(0x33FC); c.word(0x8F02); c.longword(0x00C00004); // VDP +2 per word.
        c.word(0x23FC); c.longword(0x60800001); c.longword(0x00C00004); // VRAM $6080.
        c.word(0x4EB9); c.longword(0x000363FC);   // Original LZ-Toshio -> VRAM.
        c.word(0x4CDF); c.word(0x7FFF);
        c.word(0x4E75);
        return c.finish();
    }

    private static byte[] compressedEditor(String path, byte[] original) throws IOException {
        if (!Files.exists(Path.of(path))) throw new IOException("Missing editable kart: " + path);
        byte[] tiles = KartGraphics.decode(Png.read(path), KartGraphics.editPalette(original));
        byte[] compressed = LzToshio.compress(tiles);
        LzToshio.Result roundtrip = LzToshio.tryDecompress(compressed, 0, compressed.length, tiles.length);
        if (roundtrip == null || !Arrays.equals(roundtrip.data, tiles)) {
            throw new IllegalStateException("Kart compression failed: " + path);
        }
        return compressed;
    }

    private static byte[] encode(TblTable table, String text) {
        byte[] body = table.encode(text);
        byte[] result = Arrays.copyOf(body, body.length + 1);
        result[body.length] = (byte) 0xFF;
        return result;
    }

    private static byte[] hook(int base) {
        byte[] result = new byte[]{(byte) 0x4E, (byte) 0xB9, 0, 0, 0, 0};
        put32(result, 2, base + ENTRY_OFFSET);
        return result;
    }

    private static boolean matches(byte[] data, int at, byte[] expected) {
        return at >= 0 && at + expected.length <= data.length
                && Arrays.equals(data, at, at + expected.length, expected, 0, expected.length);
    }
    private static int align(int n) { return (n + 1) & ~1; }
    private static int read16(byte[] b, int at) { return (b[at] & 255) << 8 | (b[at + 1] & 255); }
    private static int read32(byte[] b, int at) {
        return b[at] << 24 | (b[at + 1] & 255) << 16 | (b[at + 2] & 255) << 8 | (b[at + 3] & 255);
    }
    private static void put16(byte[] b, int at, int n) { b[at] = (byte) (n >>> 8); b[at + 1] = (byte) n; }
    private static void put32(byte[] b, int at, int n) {
        b[at] = (byte) (n >>> 24); b[at + 1] = (byte) (n >>> 16); b[at + 2] = (byte) (n >>> 8); b[at + 3] = (byte) n;
    }
}
