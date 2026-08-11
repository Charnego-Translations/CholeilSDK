package net.krusher;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a soleil.tbl-style character table and provides both directions:
 * decoding (byte -> glyph) for extraction, and encoding (text -> bytes) for
 * reinsertion. Encoding uses greedy longest-match tokenization so multi-char
 * glyphs (e.g. "..." for 0x44) are preferred over matching individual
 * characters. When multiple byte codes map to the same glyph, the one
 * defined first in the file wins for encoding.
 */
public final class TblTable {

    private final Map<Integer, String> byteToGlyph = new LinkedHashMap<Integer, String>();
    private final Map<String, Integer> glyphToByte = new LinkedHashMap<String, Integer>();
    private final int maxGlyphLength;

    private TblTable(Map<Integer, String> byteToGlyph, Map<String, Integer> glyphToByte, int maxGlyphLength) {
        this.byteToGlyph.putAll(byteToGlyph);
        this.glyphToByte.putAll(glyphToByte);
        this.maxGlyphLength = maxGlyphLength;
    }

    public static TblTable load(String path) throws IOException {
        Map<Integer, String> byteToGlyph = new LinkedHashMap<Integer, String>();
        Map<String, Integer> glyphToByte = new LinkedHashMap<String, Integer>();
        int maxLen = 1;

        List<String> lines = Files.readAllLines(Paths.get(path), Charset.forName("UTF-8"));
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("/*") || trimmed.startsWith("*") || trimmed.startsWith("//")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String hexPart = line.substring(0, eq).trim();
            String valPart = line.substring(eq + 1);
            if (valPart.endsWith("\r")) valPart = valPart.substring(0, valPart.length() - 1);
            if (hexPart.length() != 2) continue;
            int code;
            try {
                code = Integer.parseInt(hexPart, 16);
            } catch (NumberFormatException e) {
                continue;
            }
            if (valPart.equals("<SPACE>")) valPart = " ";
            if (valPart.isEmpty()) continue;

            byteToGlyph.put(code, valPart);
            if (!glyphToByte.containsKey(valPart)) { // first definition wins for encoding
                glyphToByte.put(valPart, code);
            }
            maxLen = Math.max(maxLen, valPart.length());
        }
        return new TblTable(byteToGlyph, glyphToByte, maxLen);
    }

    public boolean hasByte(int code) {
        return byteToGlyph.containsKey(code);
    }

    public String glyphFor(int code) {
        return byteToGlyph.get(code);
    }

    /**
     * Encodes text (as produced by TextExtractor's decodeString, including
     * <NAME>, <YESNO>, real newlines, and {XX} unmapped-byte placeholders)
     * back into raw bytes, greedily matching the longest known glyph at each
     * position. Does not append the 0xFF terminator.
     */
    public byte[] encode(String text) {
        List<Integer> out = new ArrayList<Integer>();
        int i = 0;
        int len = text.length();
        while (i < len) {
            char c = text.charAt(i);

            if (c == '\n') {
                out.add(0xFE);
                i++;
                continue;
            }
            if (text.startsWith("<NAME>", i)) {
                out.add(0xF1);
                i += 6;
                continue;
            }
            if (text.startsWith("<YESNO>", i)) {
                out.add(0xF2);
                i += 7;
                continue;
            }
            if (c == '{' && i + 3 < len && text.charAt(i + 3) == '}') {
                String hex = text.substring(i + 1, i + 3);
                try {
                    int b = Integer.parseInt(hex, 16);
                    out.add(b);
                    i += 4;
                    continue;
                } catch (NumberFormatException e) {
                    // fall through to normal glyph matching
                }
            }

            int matchLen = 0;
            Integer matchByte = null;
            int cap = Math.min(maxGlyphLength, len - i);
            for (int l = cap; l >= 1; l--) {
                String candidate = text.substring(i, i + l);
                Integer b = glyphToByte.get(candidate);
                if (b != null) {
                    matchLen = l;
                    matchByte = b;
                    break;
                }
            }
            if (matchByte == null) {
                throw new IllegalArgumentException("No TBL entry for character '" + c
                        + "' (U+" + Integer.toHexString(c) + ") at position " + i + " in: " + text);
            }
            out.add(matchByte);
            i += matchLen;
        }

        byte[] bytes = new byte[out.size()];
        for (int j = 0; j < bytes.length; j++) bytes[j] = (byte) (int) out.get(j);
        return bytes;
    }
}
