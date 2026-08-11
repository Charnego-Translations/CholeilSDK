package net.krusher;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dumps the main script text (and its pointer structure) out of the
 * Megadrive game "Soleil" (EU release of Crusader of Centy) into a
 * script.txt file, using the encoding table described in soleil.tbl.
 *
 * Format/structure reverse-engineered by EvilJagaGenius, posted at:
 * https://www.romhacking.net/forum/index.php?topic=34617.0
 * ("Crusader of Centy text hacking")
 *
 * Pointer layout (uncompressed, big-endian, no header on the ROM file):
 *   scriptBase = 0x1C0000
 *   - Room table starts at scriptBase. Each entry is a 4-byte offset
 *     (relative to scriptBase) pointing at that room's NPC table.
 *     Room count = (offset of room #0) / 4 - 1.
 *   - Each room's NPC table is a list of 2-byte offsets (relative to
 *     scriptBase + roomOffset) pointing at that NPC's string table.
 *     NPC count = (offset of NPC #0 in that room) / 2.
 *   - Each NPC's string table is a list of 2-byte offsets (relative to
 *     scriptBase + roomOffset + npcOffset) pointing at the actual text.
 *     String count = (offset of string #0) / 2.
 *   - Text bytes are read until 0xFF (end of string). 0xFE is a line
 *     break within the text box. 0xF1 inserts the player's name. 0xF2
 *     marks a Yes/No choice prompt.
 */
public class TextExtractor {

    static final int SCRIPT_BASE = 0x1C0000;
    // Soleil's script region is larger than Crusader of Centy's US script;
    // used only as a sanity bound to flag anything reading outside it.
    static final int SCRIPT_START = 0x1C0310;
    static final int SCRIPT_END = 0x1D44C0;

    public static void run(String romPath, String tblPath, String outPath, String ptrPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        Map<Integer, String> table = loadTable(tblPath);

        StringBuilder script = new StringBuilder();
        StringBuilder pointers = new StringBuilder();
        pointers.append("room,npc,str,ptrFieldAddr,textAddr\n");

        int firstRoomOffset = readU32(rom, SCRIPT_BASE);
        int roomCount = (firstRoomOffset / 4) - 1;

        int totalStrings = 0;
        int unknownBytes = 0;

        for (int roomIndex = 0; roomIndex < roomCount; roomIndex++) {
            int roomOffset = readU32(rom, SCRIPT_BASE + roomIndex * 4);
            int npcTableAddr = SCRIPT_BASE + roomOffset;

            int firstNpcOffset = readU16(rom, npcTableAddr);
            int npcCount = firstNpcOffset / 2;

            for (int npcIndex = 0; npcIndex < npcCount; npcIndex++) {
                int npcOffset = readU16(rom, npcTableAddr + npcIndex * 2);
                int strTableAddr = npcTableAddr + npcOffset;

                int firstStrOffset = readU16(rom, strTableAddr);
                int strCount = firstStrOffset / 2;

                for (int strIndex = 0; strIndex < strCount; strIndex++) {
                    int ptrFieldAddr = strTableAddr + strIndex * 2;
                    int strOffset = readU16(rom, ptrFieldAddr);
                    int textAddr = strTableAddr + strOffset;

                    if (textAddr < SCRIPT_START || textAddr >= SCRIPT_END) {
                        // Outside the known script region; still dump it,
                        // but flag it so it can be reviewed by hand.
                        script.append("; WARNING: text address 0x")
                              .append(Integer.toHexString(textAddr))
                              .append(" is outside expected script bounds\n");
                    }

                    int[] unk = new int[1];
                    String text = decodeString(rom, textAddr, table, unk);
                    unknownBytes += unk[0];
                    totalStrings++;

                    script.append("==== room=").append(roomIndex)
                          .append(" npc=").append(npcIndex)
                          .append(" str=").append(strIndex)
                          .append(" textAddr=0x").append(Integer.toHexString(textAddr))
                          .append(" ptrFieldAddr=0x").append(Integer.toHexString(ptrFieldAddr))
                          .append(" ====\n");
                    script.append(text).append("\n\n");

                    pointers.append(roomIndex).append(',')
                            .append(npcIndex).append(',')
                            .append(strIndex).append(',')
                            .append("0x").append(Integer.toHexString(ptrFieldAddr)).append(',')
                            .append("0x").append(Integer.toHexString(textAddr)).append('\n');
                }
            }
        }

        Files.write(Paths.get(outPath), script.toString().getBytes("UTF-8"));
        Files.write(Paths.get(ptrPath), pointers.toString().getBytes("UTF-8"));

        System.out.println("Rooms: " + roomCount);
        System.out.println("Strings extracted: " + totalStrings);
        System.out.println("Unknown byte occurrences: " + unknownBytes);
        System.out.println("Script written to: " + outPath);
        System.out.println("Pointer map written to: " + ptrPath);
    }

    static String decodeString(byte[] rom, int addr, Map<Integer, String> table, int[] unknownCounter) {
        StringBuilder sb = new StringBuilder();
        int pos = addr;
        while (true) {
            int b = rom[pos++] & 0xFF;
            if (b == 0xFF) {
                break; // end of string
            } else if (b == 0xFE) {
                sb.append('\n');
            } else if (b == 0xF1) {
                sb.append("<NAME>");
            } else if (b == 0xF2) {
                sb.append("<YESNO>");
            } else if (table.containsKey(b)) {
                sb.append(table.get(b));
            } else {
                sb.append('{').append(String.format("%02X", b)).append('}');
                unknownCounter[0]++;
            }
            if (pos - addr > 4096) { // safety net against a malformed/unterminated string
                sb.append("\n; ERROR: string exceeded 4096 bytes without 0xFF terminator, aborting");
                break;
            }
        }
        return sb.toString();
    }

    static Map<Integer, String> loadTable(String path) throws IOException {
        Map<Integer, String> table = new LinkedHashMap<Integer, String>();
        List<String> lines = Files.readAllLines(Paths.get(path), Charset.forName("UTF-8"));
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("/*") || trimmed.startsWith("*") || trimmed.startsWith("//")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String hexPart = line.substring(0, eq).trim();
            String valPart = line.substring(eq + 1); // keep as-is (preserve intentional spaces)
            if (valPart.endsWith("\r")) valPart = valPart.substring(0, valPart.length() - 1);
            if (hexPart.length() != 2) continue;
            int code;
            try {
                code = Integer.parseInt(hexPart, 16);
            } catch (NumberFormatException e) {
                continue;
            }
            if (valPart.equals("<SPACE>")) valPart = " ";
            table.put(code, valPart);
        }
        return table;
    }

    static int readU16(byte[] rom, int addr) {
        return ((rom[addr] & 0xFF) << 8) | (rom[addr + 1] & 0xFF);
    }

    static int readU32(byte[] rom, int addr) {
        return ((rom[addr] & 0xFF) << 24) | ((rom[addr + 1] & 0xFF) << 16)
             | ((rom[addr + 2] & 0xFF) << 8) | (rom[addr + 3] & 0xFF);
    }
}
