package net.krusher.graphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the code/table reference that points at a given compressed graphics
 * block's ROM address, so a relocated block's new address can be patched
 * back in. Two mechanisms are used, both discovered by cross-referencing a
 * 68k disassembly (m68k-disasm) against the cataloged block offsets:
 *
 *   - TABLE: most blocks are reached via `lea TABLE_BASE,A1; ... ; adda.l
 *     (An,offset),A1` -- a table of 4-byte relative offsets. Confirmed table
 *     bases (each verified to resolve >=3 known blocks): 0x50000, 0x59000,
 *     0xF0000, 0x100000, 0x111280, 0x111400, 0x115B00, 0x116E00, 0x120000,
 *     0x15E000. Unlike the text system's 2-byte offsets, these are 4-byte
 *     (a full 32-bit relative offset), so there's no 64KB reach limit.
 *   - DIRECT: a handful of blocks are referenced by a literal absolute
 *     address embedded in an `lea ADDRESS.L,An` instruction (opcode forms
 *     0x41F9/0x43F9/0x45F9/0x47F9/0x49F9/0x4BF9/0x4DF9/0x4FF9, one per
 *     address register An).
 *
 * Cross-referencing found a reference for 493 of 508 cataloged blocks (97%);
 * the rest (mostly very small blocks already suspected to be scanner false
 * positives) simply aren't found by either mechanism.
 */
public final class PointerLocator {

    public static final int[] TABLE_BASES = {
            0x50000, 0x59000, 0xF0000, 0x100000, 0x111280,
            0x111400, 0x115B00, 0x116E00, 0x120000, 0x15E000
    };
    private static final int MAX_TABLE_ENTRIES = 600;

    private static final int[] LEA_ABSOLUTE_LONG_OPCODES = {
            0x41F9, 0x43F9, 0x45F9, 0x47F9, 0x49F9, 0x4BF9, 0x4DF9, 0x4FF9
    };

    private PointerLocator() {}

    public static final class Reference {
        public enum Kind { TABLE, DIRECT }
        public final Kind kind;
        public final int pointerFieldAddr; // where the 4-byte value to rewrite lives
        public final int tableBase;        // TABLE only; DIRECT leaves this 0

        Reference(Kind kind, int pointerFieldAddr, int tableBase) {
            this.kind = kind;
            this.pointerFieldAddr = pointerFieldAddr;
            this.tableBase = tableBase;
        }
    }

    /** Returns every reference found for blockAddr (usually 0 or 1; can be several for a shared/duplicated block). */
    public static List<Reference> findAll(byte[] rom, int blockAddr) {
        List<Reference> found = new ArrayList<Reference>();

        for (int base : TABLE_BASES) {
            for (int i = 0; i < MAX_TABLE_ENTRIES; i++) {
                int fieldAddr = base + i * 4;
                if (fieldAddr + 4 > rom.length) break;
                int val = readU32(rom, fieldAddr);
                if (base + val == blockAddr) {
                    found.add(new Reference(Reference.Kind.TABLE, fieldAddr, base));
                }
            }
        }

        for (int i = 0; i + 6 <= rom.length; i++) {
            int opcode = ((rom[i] & 0xFF) << 8) | (rom[i + 1] & 0xFF);
            if (!isLeaAbsoluteLong(opcode)) continue;
            int addr = readU32(rom, i + 2);
            if (addr == blockAddr) {
                found.add(new Reference(Reference.Kind.DIRECT, i + 2, 0));
            }
        }

        return found;
    }

    private static boolean isLeaAbsoluteLong(int opcode) {
        for (int op : LEA_ABSOLUTE_LONG_OPCODES) if (op == opcode) return true;
        return false;
    }

    private static int readU32(byte[] rom, int addr) {
        return ((rom[addr] & 0xFF) << 24) | ((rom[addr + 1] & 0xFF) << 16)
             | ((rom[addr + 2] & 0xFF) << 8) | (rom[addr + 3] & 0xFF);
    }
}
