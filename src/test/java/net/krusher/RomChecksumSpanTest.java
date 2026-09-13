package net.krusher;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RomChecksumSpanTest {
    @Test void appendedIntroDoesNotEnterSoleilsOwnBootChecksum() {
        byte[] rom = new byte[0x600];
        IntroInserter.writeU32(rom, 0x1A4, 0x3FF);
        IntroInserter.writeU16(rom, 0x200, 0x0102);
        IntroInserter.writeU16(rom, 0x3FE, 0x0304);
        Arrays.fill(rom, 0x400, rom.length, (byte)0x55);
        TextInserter.fixChecksum(rom);
        assertEquals(0x0406, IntroInserter.readU16(rom, 0x18E));
        assertEquals(0x400, TextInserter.checksumEnd(rom));
        assertEquals(0x3FF, IntroInserter.readU32(rom, 0x1A4));
        rom[0x500] = 0x12;
        TextInserter.fixChecksum(rom);
        assertEquals(0x0406, IntroInserter.readU16(rom, 0x18E));
    }

    @Test void ordinaryRomStillChecksumsTheEntireDeclaredImage() {
        byte[] rom = new byte[0x400];
        IntroInserter.writeU32(rom, 0x1A4, 0x3FF);
        IntroInserter.writeU16(rom, 0x3FE, 0xCAFE);
        TextInserter.fixChecksum(rom);
        assertEquals(0xCAFE, IntroInserter.readU16(rom, 0x18E));
    }

    @Test void invalidHeaderUsesTheExistingFullFileFallback() {
        byte[] rom = new byte[0x400];
        for (int end : new int[]{0, -1, 0x800}) {
            IntroInserter.writeU32(rom, 0x1A4, end);
            IntroInserter.writeU16(rom, 0x3FE, 0x1234);
            TextInserter.fixChecksum(rom);
            assertEquals(rom.length, TextInserter.checksumEnd(rom));
            assertEquals(0x1234, IntroInserter.readU16(rom, 0x18E));
        }
    }
}
