package net.krusher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
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
 *
 * Deduplication: many string-table slots across different rooms/NPCs point
 * at the exact same textAddr -- the original data already shares that one
 * copy. The first slot seen for a given textAddr gets the full text; every
 * later slot pointing at the same address gets a single-line
 * "<SAME room=R npc=N str=S>" reference to that first slot instead of a
 * repeated copy, so a translator only has to edit it once. TextInserter
 * resolves these back to real text before encoding -- and because a
 * reference always stays byte-identical to its target, the original
 * pointer-sharing (see TextInserter's Group/representativeAddr writeup)
 * keeps working reliably instead of being accidentally split apart by two
 * copy-pasted translations drifting out of sync.
 */
public class TextExtractor {

    static final int SCRIPT_BASE = 0x1C0000;
    // Soleil's script region is larger than Crusader of Centy's US script;
    // used only as a sanity bound to flag anything reading outside it.
    static final int SCRIPT_START = 0x1C0310;
    static final int SCRIPT_END = 0x1D44C0;

    public static void run(String romPath, String tblPath, String outPath, String ptrPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        TblTable table = TblTable.load(tblPath);

        StringBuilder script = new StringBuilder();
        StringBuilder pointers = new StringBuilder();
        pointers.append("room,npc,str,ptrFieldAddr,textAddr\n");

        int firstRoomOffset = readU32(rom, SCRIPT_BASE);
        int roomCount = (firstRoomOffset / 4) - 1;

        int totalStrings = 0;
        int unknownBytes = 0;
        int dedupedStrings = 0;
        Map<Integer, int[]> firstSeenByTextAddr = new HashMap<Integer, int[]>();

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

                    totalStrings++;

                    script.append("==== room=").append(roomIndex)
                          .append(" npc=").append(npcIndex)
                          .append(" str=").append(strIndex)
                          .append(" textAddr=0x").append(Integer.toHexString(textAddr))
                          .append(" ptrFieldAddr=0x").append(Integer.toHexString(ptrFieldAddr))
                          .append(" ====\n");

                    int[] firstSeen = firstSeenByTextAddr.get(textAddr);
                    if (firstSeen == null) {
                        int[] unk = new int[1];
                        String text = decodeString(rom, textAddr, table, unk);
                        unknownBytes += unk[0];
                        firstSeenByTextAddr.put(textAddr, new int[]{roomIndex, npcIndex, strIndex});
                        script.append(text).append("\n\n");
                    } else {
                        dedupedStrings++;
                        script.append("<SAME room=").append(firstSeen[0])
                              .append(" npc=").append(firstSeen[1])
                              .append(" str=").append(firstSeen[2])
                              .append(">\n\n");
                    }

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
        System.out.println("Strings extracted: " + totalStrings + " (" + firstSeenByTextAddr.size() + " unique, "
                + dedupedStrings + " deduped to a \"<SAME ...>\" reference)");
        System.out.println("Unknown byte occurrences: " + unknownBytes);
        System.out.println("Script written to: " + outPath);
        System.out.println("Pointer map written to: " + ptrPath);
    }

    static String decodeString(byte[] rom, int addr, TblTable table, int[] unknownCounter) {
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
            } else if (table.hasByte(b)) {
                sb.append(table.glyphFor(b));
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

    static int readU16(byte[] rom, int addr) {
        return ((rom[addr] & 0xFF) << 8) | (rom[addr + 1] & 0xFF);
    }

    static int readU32(byte[] rom, int addr) {
        return ((rom[addr] & 0xFF) << 24) | ((rom[addr + 1] & 0xFF) << 16)
             | ((rom[addr + 2] & 0xFF) << 8) | (rom[addr + 3] & 0xFF);
    }
}
