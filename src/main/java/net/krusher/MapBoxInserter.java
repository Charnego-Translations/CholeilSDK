package net.krusher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Patches fixed-width UI "container" graphics (see map_box_widths.txt) whose
 * background loop count is a literal 68k immediate operand in code, not a
 * relocatable data table -- so instead of moving anything, this just widens
 * the loop in place to cover whatever the current translation's longest
 * string for that room/npc actually is.
 */
public final class MapBoxInserter {

    static final Pattern LINE = Pattern.compile("^0x([0-9a-fA-F]+),(\\d+),(\\d+),(\\d+)$");

    // Sanity cap so a runaway translation can't blindly patch in a loop count
    // that draws a box off the edge of the screen -- Genesis planes used for
    // on-screen UI here are well under 40 tiles wide.
    static final int MAX_COLUMNS = 40;

    // The town-name popup's print routine (bsr'd from 0x48f4, right after the
    // box fill loop above) reads its VRAM write cursor from $d8d6.w instead of
    // resetting it to a fixed base first -- unlike the two other text-print
    // routines in the ROM (0x330c and 0x32176), which both do `move.w
    // #$a800,d6` before drawing. That means this popup's cursor carries over
    // whatever was left in $d8d6.w by the last unrelated thing drawn through
    // the shared cursor, so how much of the name lands in visible range is
    // leftover state, not string length -- confirmed by "Choleil" (7 chars,
    // nowhere near any box-width limit) rendering as "Cholei" in-game.
    // Fixed by swapping the 4-byte `move.w $d8d6.w,d6` (3c 38 d8 d6) for the
    // same-size `move.w #$a800,d6` (3c 3c a8 00), matching the other two
    // routines exactly.
    static final int CURSOR_ADDR = 0x4952;
    static final byte[] CURSOR_BEFORE = {(byte) 0x3c, (byte) 0x38, (byte) 0xd8, (byte) 0xd6};
    static final byte[] CURSOR_AFTER = {(byte) 0x3c, (byte) 0x3c, (byte) 0xa8, (byte) 0x00};

    /** usage: MapBoxInserter [romPath] [scriptPath] [registryPath] [outPath] */
    public static void main(String[] args) throws IOException {
        String romPath = args.length > 0 ? args[0] : "Choleil.md";
        String scriptPath = args.length > 1 ? args[1] : "script.txt";
        String registryPath = args.length > 2 ? args[2] : "map_box_widths.txt";
        String outPath = args.length > 3 ? args[3] : romPath;
        run(romPath, scriptPath, registryPath, outPath);
    }

    public static void run(String romPath, String scriptPath, String registryPath, String outPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));

        List<TextInserter.Entry> entries = TextInserter.parseScript(scriptPath);
        TextInserter.resolveSameRefs(entries, scriptPath);

        int patched = 0;
        for (String rawLine : Files.readAllLines(Paths.get(registryPath))) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("/*") || line.startsWith("*")) continue;

            Matcher m = LINE.matcher(line);
            if (!m.matches()) {
                throw new IllegalStateException("Malformed line in " + registryPath + ": " + rawLine);
            }
            int immAddr = Integer.parseInt(m.group(1), 16);
            int room = Integer.parseInt(m.group(2));
            int npc = Integer.parseInt(m.group(3));
            int marginTiles = Integer.parseInt(m.group(4));

            int maxLen = -1;
            for (TextInserter.Entry e : entries) {
                if (e.room != room || e.npc != npc) continue;
                for (String displayLine : e.text.split("\n", -1)) {
                    maxLen = Math.max(maxLen, displayLine.length());
                }
            }
            if (maxLen < 0) {
                throw new IllegalStateException("No room=" + room + " npc=" + npc + " entries found in " + scriptPath
                        + " for the map box registered at 0x" + Integer.toHexString(immAddr));
            }

            int columns = maxLen + marginTiles;
            if (columns % 2 != 0) columns++; // the loop advances 2 tile columns per iteration
            if (columns > MAX_COLUMNS) {
                System.out.println("WARN: box at 0x" + Integer.toHexString(immAddr) + " would need " + columns
                        + " tile columns (longest room=" + room + " npc=" + npc + " string is " + maxLen
                        + " chars + " + marginTiles + " margin) -- capped at " + MAX_COLUMNS + " to stay on-screen");
                columns = MAX_COLUMNS;
            }

            int iterations = columns / 2;
            int imm = iterations - 1;
            if (imm < 0 || imm > 0xFFFF) {
                throw new IllegalStateException("Computed loop count out of range for box at 0x"
                        + Integer.toHexString(immAddr) + ": " + imm);
            }

            rom[immAddr] = (byte) ((imm >> 8) & 0xFF);
            rom[immAddr + 1] = (byte) (imm & 0xFF);
            System.out.println("Patched box at 0x" + Integer.toHexString(immAddr) + ": " + columns
                    + " tile columns wide (longest room=" + room + " npc=" + npc + " string: " + maxLen + " chars)");
            patched++;
        }

        patchCursorReset(rom);

        TextInserter.fixChecksum(rom);
        Files.write(Paths.get(outPath), rom);
        System.out.println("Patched " + patched + " box width(s). Wrote " + outPath);
    }

    private static void patchCursorReset(byte[] rom) {
        boolean alreadyPatched = regionEquals(rom, CURSOR_ADDR, CURSOR_AFTER);
        if (alreadyPatched) {
            System.out.println("Town-name popup cursor reset already patched at 0x" + Integer.toHexString(CURSOR_ADDR));
            return;
        }
        if (!regionEquals(rom, CURSOR_ADDR, CURSOR_BEFORE)) {
            throw new IllegalStateException("Expected `move.w $d8d6.w,d6` (3c 38 d8 d6) at 0x"
                    + Integer.toHexString(CURSOR_ADDR) + " but found something else -- ROM doesn't match assumptions, refusing to patch blind.");
        }
        System.arraycopy(CURSOR_AFTER, 0, rom, CURSOR_ADDR, CURSOR_AFTER.length);
        System.out.println("Patched town-name popup cursor reset at 0x" + Integer.toHexString(CURSOR_ADDR)
                + ": now resets to #$a800 before drawing, like the other two text routines.");
    }

    private static boolean regionEquals(byte[] rom, int addr, byte[] expected) {
        for (int i = 0; i < expected.length; i++) {
            if (rom[addr + i] != expected[i]) return false;
        }
        return true;
    }
}
