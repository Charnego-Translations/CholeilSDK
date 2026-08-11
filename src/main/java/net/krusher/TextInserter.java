package net.krusher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reinserts a (possibly translated) script.txt back into the ROM, producing
 * Choleil.md. Rebuilds every string's bytes from scratch and repacks them
 * sequentially (in original address order) into a combined writable pool:
 * the original script region (fully reusable, since all of it is being
 * rewritten) followed by known/scanned free space further in the ROM.
 *
 * Room, NPC, and string INDEX tables never move (their sizes are fixed by
 * counts that don't change) -- only the 2-byte offset VALUES inside the
 * existing string-index tables are rewritten, to point at each string's new
 * location. Because those offsets are unsigned and forward-only with a
 * 16-bit range, a string can only be placed within 65535 bytes after its
 * own NPC's string-index table; this is checked for every string before
 * anything is written.
 */
public final class TextInserter {

    static final int SCRIPT_BASE = 0x1C0000;
    static final int DEFAULT_GAP_END = 0x1D8000; // known plausible-safe boundary, see soleil.tbl/graphics notes
    static final Pattern HEADER = Pattern.compile(
            "^==== room=(\\d+) npc=(\\d+) str=(\\d+) textAddr=0x([0-9a-fA-F]+) ptrFieldAddr=0x([0-9a-fA-F]+) ====$");
    static final Pattern SAME_REF = Pattern.compile(
            "^<SAME room=(\\d+) npc=(\\d+) str=(\\d+)>$");

    static final class Entry {
        int room, npc, str;
        int textAddr;
        int ptrFieldAddr;
        String text;
        byte[] encoded;
        int strTableAddr;
        int newAddr = -1; // used only for str=0 entries, placed at a mandatory fixed address
        Group group;
    }

    /**
     * A set of entries whose text encodes to byte-identical content, stored
     * once and shared by all their pointers (mirrors the original data's own
     * space-saving deduplication, which some strings rely on to fit).
     */
    static final class Group {
        byte[] encoded;
        List<Entry> members = new ArrayList<Entry>();
        int representativeAddr;
        int newAddr = -1;
    }

    /** usage: TextInserter [romPath] [scriptPath] [tblPath] [freeSpacePath] [outPath] */
    public static void main(String[] args) throws IOException {
        String romPath = args.length > 0 ? args[0] : "Soleil (Spain).md";
        String scriptPath = args.length > 1 ? args[1] : "script.txt";
        String tblPath = args.length > 2 ? args[2] : "soleil.tbl";
        String freeSpacePath = args.length > 3 ? args[3] : "free_space.txt";
        String outPath = args.length > 4 ? args[4] : "Choleil.md";
        run(romPath, scriptPath, tblPath, freeSpacePath, outPath);
    }

    public static void run(String romPath, String scriptPath, String tblPath,
                            String freeSpacePath, String outPath) throws IOException {
        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        TblTable table = TblTable.load(tblPath);
        List<Entry> entries = parseScript(scriptPath);
        System.out.println("Parsed " + entries.size() + " entries from " + scriptPath);
        resolveSameRefs(entries, scriptPath);

        int encodeFailures = 0;
        for (Entry e : entries) {
            e.strTableAddr = e.ptrFieldAddr - e.str * 2;
            try {
                byte[] body = table.encode(e.text);
                e.encoded = new byte[body.length + 1];
                System.arraycopy(body, 0, e.encoded, 0, body.length);
                e.encoded[body.length] = (byte) 0xFF;
            } catch (IllegalArgumentException ex) {
                System.out.println("ENCODE FAILURE room=" + e.room + " npc=" + e.npc + " str=" + e.str
                        + " (textAddr=0x" + Integer.toHexString(e.textAddr) + "): " + ex.getMessage());
                encodeFailures++;
            }
        }
        if (encodeFailures > 0) {
            System.out.println(encodeFailures + " string(s) failed to encode. Fix soleil.tbl or the offending "
                    + "text in " + scriptPath + " and re-run. " + outPath + " was NOT written.");
            return;
        }

        int minTextAddr = Integer.MAX_VALUE;
        int maxTextAddr = 0;
        Entry lastEntry = null;
        for (Entry e : entries) {
            if (e.textAddr < minTextAddr) minTextAddr = e.textAddr;
            if (e.textAddr > maxTextAddr) { maxTextAddr = e.textAddr; lastEntry = e; }
        }
        int trueScriptEnd = findOriginalTerminator(rom, lastEntry.textAddr) + 1;
        System.out.println("Original script span: 0x" + Integer.toHexString(minTextAddr)
                + " - 0x" + Integer.toHexString(trueScriptEnd));

        // Room/NPC/string INDEX tables are interspersed with the text throughout
        // the script region (an NPC's string-index-table sits right between two
        // other strings' text, not segregated into one block). Those bytes must
        // never be touched -- only the text itself is free to repack.
        List<int[]> tableRanges = computeTableRanges(rom);

        // CRITICAL: string counts aren't stored anywhere explicitly -- the game
        // derives strCount for an NPC as (offset stored at that NPC's str=0 slot)/2.
        // That only works if str=0's text sits at EXACTLY strTableAddr+strCount*2
        // (immediately after its own index table); the offset value doubles as a
        // self-describing table-size encoding. Unlike str>=1, str=0 can never be
        // freely relocated -- doing so silently corrupts the derived string count
        // for that NPC, which is exactly what broke the game on the first attempt.
        // So str=0 gets a mandatory fixed placement, reserved before anything else
        // is packed; only str>=1 goes through the general flexible allocator.
        Map<Integer, Integer> strCountByTable = new LinkedHashMap<Integer, Integer>();
        for (Entry e : entries) {
            Integer cur = strCountByTable.get(e.strTableAddr);
            strCountByTable.put(e.strTableAddr, cur == null ? e.str + 1 : Math.max(cur, e.str + 1));
        }
        Map<Integer, Entry> str0ByTable = new LinkedHashMap<Integer, Entry>();
        for (Entry e : entries) {
            if (e.str == 0) str0ByTable.put(e.strTableAddr, e);
        }

        List<int[]> reservations = new ArrayList<int[]>();
        List<Entry> str0Failures = new ArrayList<Entry>();
        for (Map.Entry<Integer, Entry> me : str0ByTable.entrySet()) {
            int strTableAddr = me.getKey();
            Entry str0 = me.getValue();
            int strCount = strCountByTable.get(strTableAddr);
            int requiredAddr = strTableAddr + strCount * 2;
            int requiredEnd = requiredAddr + str0.encoded.length;

            boolean collision = false;
            for (int[] r : tableRanges) {
                if (r[0] == strTableAddr) continue; // this table's own (already-included) range
                if (requiredAddr < r[1] && requiredEnd > r[0]) { collision = true; break; }
            }
            if (collision) {
                str0Failures.add(str0);
                continue;
            }
            str0.newAddr = requiredAddr;
            reservations.add(new int[]{strTableAddr, requiredEnd});
        }

        if (!str0Failures.isEmpty()) {
            System.out.println();
            System.out.println(str0Failures.size() + " str=0 string(s) don't fit immediately after their own "
                    + "string table (mandatory position -- these can't be relocated). " + outPath + " was NOT written.");
            for (Entry e : str0Failures) {
                System.out.println("  room=" + e.room + " npc=" + e.npc + " str=0: needs " + e.encoded.length
                        + " bytes right after its table (0x" + Integer.toHexString(e.strTableAddr)
                        + ") but collides with the next fixed table. Shorten this string.");
            }
            return;
        }

        List<int[]> pool = new ArrayList<int[]>();
        pool.add(new int[]{minTextAddr, trueScriptEnd});
        pool.add(new int[]{trueScriptEnd, DEFAULT_GAP_END});
        if (freeSpacePath != null && Files.exists(Paths.get(freeSpacePath))) {
            for (FreeSpaceScanner.Region r : FreeSpaceScanner.readRegionsFile(freeSpacePath)) {
                pool.add(new int[]{r.start, r.end()});
            }
        }
        List<int[]> excluded = new ArrayList<int[]>(tableRanges);
        excluded.addAll(reservations);
        List<int[]> ranges = subtractRanges(mergeRanges(pool), mergeRanges(excluded));
        System.out.println("Writable pool ranges (after reserving " + reservations.size() + " str=0 slots):");
        long totalCapacity = 0;
        for (int[] r : ranges) {
            System.out.println("  0x" + Integer.toHexString(r[0]) + " - 0x" + Integer.toHexString(r[1])
                    + " (" + (r[1] - r[0]) + " bytes)");
            totalCapacity += (r[1] - r[0]);
        }
        System.out.println("Total capacity: " + totalCapacity + " bytes");

        // Preserve ONLY sharing that already existed in the original data (entries
        // that already pointed at the same original textAddr) -- deduping by
        // encoded-content equality alone would risk merging unrelated entries that
        // simply translate to the same short text (e.g. "no") across far-apart
        // rooms, creating a shared-storage requirement that never existed and can
        // be structurally impossible to satisfy (a single address reachable from
        // every referencing table). Within an original sharing set, if translation
        // made some members diverge, they're split into their own sub-group.
        // str=0 entries never participate: their position is mandatory and already
        // fixed above, never shared.
        // representativeAddr is a PLACEMENT FLOOR, not a sort convenience: since
        // offsets are unsigned/forward-only, a shared string can only go at or
        // after the LATEST (highest-address) of all the string-index tables
        // that reference it -- otherwise the earliest-placed member's offset
        // would need to be negative.
        Map<Integer, List<Entry>> byOriginalTextAddr = new LinkedHashMap<Integer, List<Entry>>();
        for (Entry e : entries) {
            if (e.str == 0) continue;
            byOriginalTextAddr.computeIfAbsent(e.textAddr, k -> new ArrayList<Entry>()).add(e);
        }
        List<Group> groups = new ArrayList<Group>();
        for (List<Entry> sameOrigin : byOriginalTextAddr.values()) {
            Map<String, Group> subGroups = new LinkedHashMap<String, Group>();
            for (Entry e : sameOrigin) {
                String key = bytesToHex(e.encoded);
                Group g = subGroups.get(key);
                if (g == null) {
                    g = new Group();
                    g.encoded = e.encoded;
                    g.representativeAddr = e.strTableAddr;
                    subGroups.put(key, g);
                }
                g.representativeAddr = Math.max(g.representativeAddr, e.strTableAddr);
                g.members.add(e);
                e.group = g;
            }
            groups.addAll(subGroups.values());
        }
        Collections.sort(groups, (a, b) -> Integer.compare(a.representativeAddr, b.representativeAddr));
        System.out.println(entries.size() + " strings, " + str0ByTable.size() + " placed at mandatory str=0 slots, "
                + groups.size() + " unique flexible locations for the rest ("
                + ((entries.size() - str0ByTable.size()) - groups.size()) + " shared copies avoided).");

        int rangeIndex = 0;
        int cursor = ranges.get(0)[0];
        List<Entry> failures = new ArrayList<Entry>();
        long totalBytesUsed = 0;
        for (int[] r : reservations) totalBytesUsed += str0ByTable.get(r[0]).encoded.length;

        for (Group g : groups) {
            cursor = Math.max(cursor, g.representativeAddr);
            while (rangeIndex < ranges.size() && cursor >= ranges.get(rangeIndex)[1]) rangeIndex++;
            if (rangeIndex < ranges.size() && cursor < ranges.get(rangeIndex)[0]) cursor = ranges.get(rangeIndex)[0];
            while (rangeIndex < ranges.size() && cursor + g.encoded.length > ranges.get(rangeIndex)[1]) {
                rangeIndex++;
                if (rangeIndex < ranges.size()) cursor = Math.max(ranges.get(rangeIndex)[0], cursor);
            }
            if (rangeIndex >= ranges.size()) {
                g.newAddr = -1;
                failures.addAll(g.members);
                continue;
            }
            g.newAddr = cursor;
            cursor += g.encoded.length; // always advance, so later groups' reports stay accurate

            boolean groupOk = true;
            for (Entry e : g.members) {
                int reach = g.newAddr - e.strTableAddr;
                if (reach < 0 || reach > 0xFFFF) {
                    failures.add(e);
                    groupOk = false;
                }
            }
            if (groupOk) totalBytesUsed += g.encoded.length;
        }

        if (!failures.isEmpty()) {
            System.out.println();
            System.out.println(failures.size() + " string(s) could not be placed. " + outPath + " was NOT written.");
            for (Entry e : failures) {
                Group g = e.group;
                if (g.newAddr == -1) {
                    System.out.println("  room=" + e.room + " npc=" + e.npc + " str=" + e.str
                            + ": ran out of writable space (needs " + g.encoded.length + " bytes)");
                } else {
                    int reach = g.newAddr - e.strTableAddr;
                    System.out.println("  room=" + e.room + " npc=" + e.npc + " str=" + e.str
                            + ": shared text at 0x" + Integer.toHexString(g.newAddr)
                            + " is " + reach + " bytes past its string table (0x" + Integer.toHexString(e.strTableAddr)
                            + "), limit is 65535 -- over by " + (reach - 0xFFFF) + " bytes. Shorten this string"
                            + " (or an earlier one in the same table); note it's shared with "
                            + (g.members.size() - 1) + " other pointer(s).");
                }
            }
            return;
        }

        System.out.println();
        System.out.println("All " + entries.size() + " strings placed successfully ("
                + totalBytesUsed + " / " + (totalCapacity + reservations.stream().mapToLong(r -> r[1] - r[0]).sum())
                + " bytes used).");

        int moved = 0;
        for (Entry str0 : str0ByTable.values()) {
            for (int i = 0; i < str0.encoded.length; i++) {
                rom[str0.newAddr + i] = str0.encoded[i];
            }
            int newOffset = str0.newAddr - str0.strTableAddr;
            rom[str0.ptrFieldAddr] = (byte) ((newOffset >> 8) & 0xFF);
            rom[str0.ptrFieldAddr + 1] = (byte) (newOffset & 0xFF);
            if (str0.newAddr != str0.textAddr) moved++;
        }
        for (Group g : groups) {
            for (int i = 0; i < g.encoded.length; i++) {
                rom[g.newAddr + i] = g.encoded[i];
            }
            for (Entry e : g.members) {
                int newOffset = g.newAddr - e.strTableAddr;
                rom[e.ptrFieldAddr] = (byte) ((newOffset >> 8) & 0xFF);
                rom[e.ptrFieldAddr + 1] = (byte) (newOffset & 0xFF);
                if (g.newAddr != e.textAddr) moved++;
            }
        }
        System.out.println(moved + " string(s) relocated, " + (entries.size() - moved) + " stayed at their original address.");

        fixChecksum(rom);

        Files.write(Paths.get(outPath), rom);
        System.out.println("Wrote " + outPath);
    }

    /**
     * Recomputes the Genesis header checksum (big-endian sum of all 16-bit
     * words from 0x200 to the end of the ROM, mod 0x10000) and writes it to
     * 0x18E-0x18F. Real Genesis hardware doesn't verify this at boot, but
     * some emulators/flashcarts do, and every legitimate ROM patcher fixes
     * it as a matter of course.
     */
    public static void fixChecksum(byte[] rom) {
        int sum = 0;
        for (int i = 0x200; i + 1 < rom.length; i += 2) {
            int word = ((rom[i] & 0xFF) << 8) | (rom[i + 1] & 0xFF);
            sum = (sum + word) & 0xFFFF;
        }
        rom[0x18e] = (byte) ((sum >> 8) & 0xFF);
        rom[0x18f] = (byte) (sum & 0xFF);
        System.out.println("Checksum fixed: 0x" + Integer.toHexString(sum));
    }

    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static int findOriginalTerminator(byte[] rom, int addr) {
        int pos = addr;
        while ((rom[pos] & 0xFF) != 0xFF) pos++;
        return pos;
    }

    /**
     * Walks the original ROM's room/NPC/string index tables (same traversal
     * as TextExtractor) and returns their exact byte ranges, so they can be
     * excluded from the writable text pool.
     */
    static List<int[]> computeTableRanges(byte[] rom) {
        List<int[]> ranges = new ArrayList<int[]>();

        int firstRoomOffset = TextExtractor.readU32(rom, SCRIPT_BASE);
        int roomCount = (firstRoomOffset / 4) - 1;
        ranges.add(new int[]{SCRIPT_BASE, SCRIPT_BASE + roomCount * 4});

        for (int roomIndex = 0; roomIndex < roomCount; roomIndex++) {
            int roomOffset = TextExtractor.readU32(rom, SCRIPT_BASE + roomIndex * 4);
            int npcTableAddr = SCRIPT_BASE + roomOffset;

            int firstNpcOffset = TextExtractor.readU16(rom, npcTableAddr);
            int npcCount = firstNpcOffset / 2;
            ranges.add(new int[]{npcTableAddr, npcTableAddr + npcCount * 2});

            for (int npcIndex = 0; npcIndex < npcCount; npcIndex++) {
                int npcOffset = TextExtractor.readU16(rom, npcTableAddr + npcIndex * 2);
                int strTableAddr = npcTableAddr + npcOffset;

                int firstStrOffset = TextExtractor.readU16(rom, strTableAddr);
                int strCount = firstStrOffset / 2;
                ranges.add(new int[]{strTableAddr, strTableAddr + strCount * 2});
            }
        }
        return ranges;
    }

    static List<int[]> mergeRanges(List<int[]> ranges) {
        List<int[]> sorted = new ArrayList<int[]>(ranges);
        Collections.sort(sorted, (a, b) -> Integer.compare(a[0], b[0]));
        List<int[]> merged = new ArrayList<int[]>();
        for (int[] r : sorted) {
            if (!merged.isEmpty() && r[0] <= merged.get(merged.size() - 1)[1]) {
                int[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], r[1]);
            } else {
                merged.add(new int[]{r[0], r[1]});
            }
        }
        return merged;
    }

    /** Both inputs must already be sorted, non-overlapping (via mergeRanges). */
    static List<int[]> subtractRanges(List<int[]> from, List<int[]> remove) {
        List<int[]> result = new ArrayList<int[]>();
        int ri = 0;
        for (int[] f : from) {
            int cur = f[0];
            while (ri < remove.size() && remove.get(ri)[1] <= cur) ri++;
            int scan = ri;
            while (scan < remove.size() && remove.get(scan)[0] < f[1]) {
                int[] r = remove.get(scan);
                if (r[0] > cur) result.add(new int[]{cur, Math.min(r[0], f[1])});
                cur = Math.max(cur, r[1]);
                scan++;
            }
            if (cur < f[1]) result.add(new int[]{cur, f[1]});
        }
        return result;
    }

    /**
     * Resolves TextExtractor's "<SAME room=R npc=N str=S>" dedup references
     * (see its class doc) back into real text, in place, before encoding.
     * Follows chains defensively (normal extractor output never chains --
     * references always point straight at the first-seen entry -- but a
     * hand-edit could create one) and fails loudly on a dangling or circular
     * reference rather than silently encoding the literal marker text.
     */
    static void resolveSameRefs(List<Entry> entries, String scriptPath) {
        Map<String, Entry> byKey = new LinkedHashMap<String, Entry>();
        for (Entry e : entries) byKey.put(e.room + "," + e.npc + "," + e.str, e);

        for (Entry e : entries) {
            Matcher m = SAME_REF.matcher(e.text.trim());
            if (!m.matches()) continue;

            Set<Entry> visited = new HashSet<Entry>();
            Entry cur = e;
            Matcher curMatch = m;
            while (true) {
                visited.add(cur);
                String targetKey = curMatch.group(1) + "," + curMatch.group(2) + "," + curMatch.group(3);
                Entry target = byKey.get(targetKey);
                if (target == null) {
                    throw new IllegalArgumentException("room=" + e.room + " npc=" + e.npc + " str=" + e.str
                            + " in " + scriptPath + " references room=" + curMatch.group(1) + " npc=" + curMatch.group(2)
                            + " str=" + curMatch.group(3) + ", which doesn't exist.");
                }
                Matcher targetMatch = SAME_REF.matcher(target.text.trim());
                if (!targetMatch.matches()) {
                    e.text = target.text;
                    break;
                }
                if (visited.contains(target)) {
                    throw new IllegalArgumentException("circular <SAME ...> reference chain involving room=" + e.room
                            + " npc=" + e.npc + " str=" + e.str + " in " + scriptPath);
                }
                cur = target;
                curMatch = targetMatch;
            }
        }
    }

    static List<Entry> parseScript(String path) throws IOException {
        List<String> rawLines = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<String>();
        for (String l : rawLines) {
            if (l.startsWith("; ")) continue; // skip WARNING/comment lines
            lines.add(l);
        }

        List<Entry> entries = new ArrayList<Entry>();
        int i = 0;
        while (i < lines.size()) {
            Matcher m = HEADER.matcher(lines.get(i));
            if (!m.matches()) {
                i++;
                continue;
            }
            Entry e = new Entry();
            e.room = Integer.parseInt(m.group(1));
            e.npc = Integer.parseInt(m.group(2));
            e.str = Integer.parseInt(m.group(3));
            e.textAddr = (int) Long.parseLong(m.group(4), 16);
            e.ptrFieldAddr = (int) Long.parseLong(m.group(5), 16);

            int bodyStart = i + 1;
            int bodyEnd = bodyStart;
            while (bodyEnd < lines.size() && !HEADER.matcher(lines.get(bodyEnd)).matches()) {
                bodyEnd++;
            }
            List<String> bodyLines = new ArrayList<String>(lines.subList(bodyStart, bodyEnd));
            if (!bodyLines.isEmpty() && bodyLines.get(bodyLines.size() - 1).isEmpty()) {
                bodyLines.remove(bodyLines.size() - 1);
            }
            e.text = String.join("\n", bodyLines);

            entries.add(e);
            i = bodyEnd;
        }
        return entries;
    }
}
