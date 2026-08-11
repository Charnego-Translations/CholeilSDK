package net.krusher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reinserts whatever blocks remain in a (presumably pruned-down) stray_text.txt
 * -- see StrayTextScanner. Unlike TextInserter, these strings are referenced by
 * fixed absolute address directly in 68k code, not through a relocatable
 * pointer table, so there is nowhere to relocate an oversized replacement to:
 * every block must fit in its original byte length or shorter.
 *   - "ascii" blocks are written as literal bytes, left-padded... no,
 *     right-padded with spaces to fill the original length exactly (fixed-
 *     width display field, not terminator-based).
 *   - "encoded" blocks go through TblTable, get a 0xFF terminator, and any
 *     leftover bytes before the original length are left untouched (harmless,
 *     since nothing reads past the terminator).
 */
public final class StrayTextInserter {

    static final Pattern HEADER = Pattern.compile("^==== (ascii|encoded) addr=0x([0-9a-fA-F]+) len=(\\d+) ====$");

    static final class Block {
        String type;
        int addr, len;
        String text;
    }

    public static void main(String[] args) throws IOException {
        String romPath = args.length > 0 ? args[0] : "Choleil.md";
        String strayTextPath = args.length > 1 ? args[1] : "stray_text.txt";
        String tblPath = args.length > 2 ? args[2] : "soleil.tbl";
        String outPath = args.length > 3 ? args[3] : "Choleil.md";

        byte[] rom = Files.readAllBytes(Paths.get(romPath));
        TblTable table = TblTable.load(tblPath);
        List<Block> blocks = parse(strayTextPath);
        System.out.println("Parsed " + blocks.size() + " block(s) from " + strayTextPath);

        List<Block> failures = new ArrayList<Block>();
        List<byte[]> encodedBytes = new ArrayList<byte[]>();

        for (Block b : blocks) {
            byte[] bytes;
            if (b.type.equals("ascii")) {
                if (b.text.length() > b.len) {
                    failures.add(b);
                    encodedBytes.add(null);
                    continue;
                }
                bytes = new byte[b.len];
                for (int i = 0; i < b.len; i++) {
                    bytes[i] = (byte) (i < b.text.length() ? b.text.charAt(i) : ' ');
                }
            } else {
                try {
                    byte[] body = table.encode(b.text);
                    if (body.length + 1 > b.len) {
                        failures.add(b);
                        encodedBytes.add(null);
                        continue;
                    }
                    bytes = new byte[body.length + 1];
                    System.arraycopy(body, 0, bytes, 0, body.length);
                    bytes[body.length] = (byte) 0xFF;
                } catch (IllegalArgumentException ex) {
                    System.out.println("ENCODE FAILURE addr=0x" + Integer.toHexString(b.addr) + ": " + ex.getMessage());
                    failures.add(b);
                    encodedBytes.add(null);
                    continue;
                }
            }
            encodedBytes.add(bytes);
        }

        if (!failures.isEmpty()) {
            System.out.println();
            System.out.println(failures.size() + " block(s) don't fit their original space. " + outPath + " was NOT written.");
            for (Block b : failures) {
                System.out.println("  addr=0x" + Integer.toHexString(b.addr) + " type=" + b.type
                        + ": original budget " + b.len + " bytes, new content needs more. Shorten it.");
            }
            return;
        }

        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            byte[] bytes = encodedBytes.get(i);
            System.arraycopy(bytes, 0, rom, b.addr, bytes.length);
        }

        TextInserter.fixChecksum(rom);
        Files.write(Paths.get(outPath), rom);
        System.out.println("Patched " + blocks.size() + " block(s) into " + outPath);
    }

    static List<Block> parse(String path) throws IOException {
        List<String> rawLines = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<String>();
        for (String l : rawLines) {
            if (l.startsWith("; ")) continue;
            lines.add(l);
        }

        List<Block> blocks = new ArrayList<Block>();
        int i = 0;
        while (i < lines.size()) {
            Matcher m = HEADER.matcher(lines.get(i));
            if (!m.matches()) { i++; continue; }

            Block b = new Block();
            b.type = m.group(1);
            b.addr = (int) Long.parseLong(m.group(2), 16);
            b.len = Integer.parseInt(m.group(3));

            int bodyStart = i + 1;
            int bodyEnd = bodyStart;
            while (bodyEnd < lines.size() && !HEADER.matcher(lines.get(bodyEnd)).matches()) bodyEnd++;
            List<String> bodyLines = new ArrayList<String>(lines.subList(bodyStart, bodyEnd));
            if (!bodyLines.isEmpty() && bodyLines.get(bodyLines.size() - 1).isEmpty()) {
                bodyLines.remove(bodyLines.size() - 1);
            }
            b.text = String.join("\n", bodyLines);

            blocks.add(b);
            i = bodyEnd;
        }
        return blocks;
    }
}
