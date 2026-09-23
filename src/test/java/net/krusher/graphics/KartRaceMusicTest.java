package net.krusher.graphics;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class KartRaceMusicTest {
    private static byte[] original, source, vgm;
    private static KartRaceMusic.Track track;

    @BeforeAll static void load() throws Exception {
        source = Files.readAllBytes(Path.of(KartRaceMusic.DEFAULT_VGM));
        vgm = source;
        track = KartRaceMusic.readTrack(source);
        Path path = Path.of("Soleil (Spain).md");
        Assumptions.assumeTrue(Files.exists(path), "original ROM required");
        original = Files.readAllBytes(path);
    }

    @Test void suppliedTrackHasExpectedChipDurationLoopAndWrites() {
        assertEquals(3579545, track.clock());
        assertEquals(7670454, track.ymClock());
        assertEquals(3005856, track.samples());
        assertEquals(8318, track.writes());
        assertEquals(5105, track.psgWrites());
        assertEquals(2112, track.ym0Writes());
        assertEquals(1101, track.ym1Writes());
        assertEquals(208, track.dictionaryCount());
        assertEquals(1653, track.sequenceOffset());
        assertEquals(track.sequenceOffset(), track.loopOffset());
        assertEquals(14501, track.packets().length);
        assertTrue(KartRaceMusic.DATA_OFFSET + track.packets().length
                <= KartRaceMusic.POOL[0][1] - KartRaceMusic.POOL[0][0]);
    }

    @Test void gzipAndUncompressedVgmProduceIdenticalPackets() throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(compressed)) { gzip.write(vgm); }
        var zipped = KartRaceMusic.readTrack(compressed.toByteArray());
        assertArrayEquals(track.packets(), zipped.packets());
        assertEquals(track.loopOffset(), zipped.loopOffset());
        assertEquals(track.samples(), zipped.samples());
    }

    @Test void packetConversionPreservesEveryRegisterWriteAndExactSampleTime() {
        record Event(long sample, int type, int register, int value) {}
        List<Event> expected = new ArrayList<>(), actual = new ArrayList<>();
        long sample = 0;
        for (int p = 0x40; p < vgm.length;) {
            int op = vgm[p++] & 255;
            if (op == 0x66) break;
            if (op == 0x4F) p++;
            else if (op == 0x50) expected.add(new Event(sample, 0, 0, vgm[p++] & 255));
            else if (op == 0x52 || op == 0x53) {
                expected.add(new Event(sample, op == 0x52 ? 1 : 2, vgm[p] & 255, vgm[p + 1] & 255));
                p += 2;
            } else if (op == 0x62) sample += 735;
            else if (op == 0x63) sample += 882;
            else if (op == 0x61) { sample += (vgm[p] & 255) | (vgm[p+1] & 255) << 8; p += 2; }
            else if (op >= 0x70 && op <= 0x7F) sample += op - 0x6F;
            else fail("unexpected command in supplied source");
        }
        sample = 0;
        for (byte[] packet : assertDoesNotThrow(() -> KartRaceMusic.decodePackets(track))) {
            int count = packet[1] & 255, typeBytes = (count + 3) / 4, data = 2 + typeBytes;
            for (int n = 0; n < count; n++) {
                int type = packet[2 + n / 4] >>> ((n & 3) * 2) & 3;
                if (type == 0) actual.add(new Event(sample, type, 0, packet[data++] & 255));
                else actual.add(new Event(sample, type, packet[data++] & 255, packet[data++] & 255));
            }
            assertEquals(packet.length, data);
            sample += (packet[0] & 255) * 441L;
        }
        assertEquals(expected, actual);
        assertEquals(track.samples(), sample);
    }

    @Test void frameQuantizationDoesNotAccumulateDriftOnPalOrNtsc() {
        List<byte[]> packets = assertDoesNotThrow(() -> KartRaceMusic.decodePackets(track));
        for (int delta : new int[]{882,735}) {
            int pointer = 0, debt = 0, loops = 0;
            long emitted = 0;
            for (int frame = 0; frame < 12000; frame++) {
                int iterations = 0;
                while (debt <= 0) {
                    assertTrue(++iterations <= 64);
                    if (pointer == packets.size()) { pointer = 0; loops++; }
                    int delay = (packets.get(pointer++)[0] & 255) * 441;
                    debt += delay; emitted += delay;
                }
                assertEquals((long)frame * delta + debt, emitted);
                debt -= delta;
            }
            assertTrue(loops >= 2);
        }
    }

    @Test void malformedHeadersAndUnsupportedChipsAreRejected() {
        for (int offset : new int[]{0,4,9,0x0C,0x10,0x18,0x1C,0x20,0x28,0x2A,0x2C,0x30}) {
            byte[] bad = vgm.clone(); bad[offset] ^= 0x20;
            assertThrows(IOException.class, () -> KartRaceMusic.readTrack(bad), "offset " + offset);
        }
        assertThrows(IOException.class, () -> KartRaceMusic.readTrack(new byte[10]));
        assertThrows(IOException.class, () -> KartRaceMusic.readTrack(Arrays.copyOf(vgm,vgm.length-1)));
        assertThrows(IOException.class, () -> KartRaceMusic.readTrack(Arrays.copyOf(source,source.length-8)));
    }

    @Test void stereoMasksUnknownCommandsAndMidCommandLoopsAreRejected() {
        byte[] stereo = vgm.clone(); stereo[0x40] = 0x4F; stereo[0x41] = 0;
        assertThrows(IOException.class, () -> KartRaceMusic.readTrack(stereo));
        byte[] unsupported = vgm.clone(); unsupported[0x40] = 0x55;
        assertThrows(IOException.class, () -> KartRaceMusic.readTrack(unsupported));
        byte[] loop = vgm.clone(); le32(loop,0x1C,0x41-0x1C);
        assertThrows(IOException.class, () -> KartRaceMusic.readTrack(loop));
    }

    @Test void pathologicalFastLoopsAreRejectedInsteadOfHangingTheGame() {
        byte[] dense = Arrays.copyOf(vgm, 0x40 + 65 * 3 + 3 + 1);
        le32(dense,4,dense.length-4); le32(dense,0x14,0);
        le32(dense,0x18,441); le32(dense,0x20,441); le32(dense,0x1C,0x40-0x1C);
        int p = 0x40;
        for (int i = 0; i < 65; i++) { dense[p++]=0x61; dense[p++]=0; dense[p++]=0; }
        dense[p++]=0x61; dense[p++]=(byte)0xB9; dense[p++]=1; dense[p]=0x66;
        assertThrows(IOException.class, () -> KartRaceMusic.readTrack(dense));
    }

    @Test void patchOnlyChangesItsHookAndReservedBlockWithoutGrowingTheRom() {
        byte[] before = original.clone();
        byte[] patched = KartRaceMusic.patchRom(original,original,track,List.of());
        int base = base(patched), end = base + KartRaceMusic.DATA_OFFSET + track.packets().length;
        assertTrue(KartRaceMusic.allowed(base,end-base));
        assertTrue(end < 0x200000);
        assertArrayEquals(before,original);
        assertEquals(original.length,patched.length);
        byte[] recovered = patched.clone();
        System.arraycopy(original,KartRaceMusic.HOOK,recovered,KartRaceMusic.HOOK,8);
        System.arraycopy(original,base,recovered,base,end-base);
        assertArrayEquals(original,recovered);
        assertArrayEquals(patched,KartRaceMusic.patchRom(patched,original,track,List.of()));
        // In particular: shared music selector, original Z80 code, kart timing,
        // graphics, header ROM end, and rendering/visibility patches are untouched.
    }

    @Test void allocatorProtectsWholeReservationsEvenIfTheyStillLookLikeFiller() {
        int length = KartRaceMusic.DATA_OFFSET + track.packets().length;
        int start = KartRaceMusic.POOL[0][0];
        List<int[]> reserved = List.of(new int[]{start,start+20});
        int chosen = KartRaceMusic.allocate(original,original,length,reserved);
        assertEquals(start+20,chosen);
        assertTrue(KartRaceMusic.allowed(chosen,length));
        assertThrows(IllegalStateException.class, () -> KartRaceMusic.allocate(original,original,length,
                Arrays.asList(KartRaceMusic.POOL)));
        assertThrows(IllegalArgumentException.class, () -> KartRaceMusic.allocate(original,original,length,
                List.of(new int[]{start,start})));
    }

    @Test void changedFillerAndOccupiedHooksAreNotOverwritten() {
        byte[] blocked = original.clone();
        for (int[] r : KartRaceMusic.POOL) blocked[r[0]] ^= 1;
        assertThrows(IllegalStateException.class, () -> KartRaceMusic.patchRom(blocked,original,track,List.of()));
        for (int at : new int[]{KartRaceMusic.HOOK,0x1A4D2,0x9E6}) {
            byte[] occupied = original.clone(); occupied[at]^=1;
            assertThrows(IllegalStateException.class, () -> KartRaceMusic.patchRom(occupied,original,track,List.of()));
        }
        assertThrows(IllegalStateException.class, () -> KartRaceMusic.patchRom(new byte[512],original,track,List.of()));
    }

    @Test void playerGuardsOriginalRaceCounterAndReplaysDisplacedInstructions() {
        byte[] code = KartRaceMusic.code(0x11BF6A,track);
        assertArrayEquals(HexFormat.of().parseHex("40e748e7fffe46fc2700"),Arrays.copyOf(code,10));
        String hex = HexFormat.of().formatHex(code);
        assertTrue(hex.contains("33fc010000a11100"));
        assertTrue(hex.contains("33fc000000a11100"));
        for (String port : new String[]{"00a04000", "00a04001", "00a04002", "00a04003"}) {
            assertTrue(hex.contains(port));
        }
        assertTrue(hex.contains("4cdf7fff46df34280004362800084e75"));
        assertTrue(KartRaceMusic.ENTRY_OFFSET + code.length <= KartRaceMusic.DATA_OFFSET);
    }

    @Test void editingTheMusicInvalidatesSavedPlaybackPointers() {
        byte[] changed = track.packets().clone(); changed[4] ^= 1;
        var edited = new KartRaceMusic.Track(changed, track.sequenceOffset(), track.loopOffset(),
                track.dictionaryCount(), track.samples(), track.clock(), track.ymClock(), track.writes(),
                track.psgWrites(), track.ym0Writes(), track.ym1Writes());
        assertNotEquals(KartRaceMusic.stateMagic(track),KartRaceMusic.stateMagic(edited));
        byte[] patched = KartRaceMusic.patchRom(original,original,track,List.of());
        assertThrows(IllegalStateException.class, () -> KartRaceMusic.patchRom(patched,original,edited,List.of()));
    }

    @Test void insertionReturnsTheWholeReservationAndVerificationRejectsCorruption(@TempDir Path dir) throws Exception {
        Path rom=Files.write(dir.resolve("rom.md"),original), base=Files.write(dir.resolve("base.md"),original);
        Path song=Files.write(dir.resolve("music.vgm"),source), free=Files.writeString(dir.resolve("free.txt"),"");
        List<int[]> ranges=KartRaceMusic.insert(rom.toString(),base.toString(),song.toString(),free.toString(),List.of());
        KartRaceMusic.verify(rom.toString(),song.toString());
        byte[] patched=Files.readAllBytes(rom);
        assertEquals(1,ranges.size()); assertEquals(base(patched),ranges.getFirst()[0]);
        assertEquals(KartRaceMusic.DATA_OFFSET+track.packets().length,ranges.getFirst()[1]-ranges.getFirst()[0]);
        for (int at : new int[]{KartRaceMusic.HOOK,base(patched)+KartRaceMusic.ENTRY_OFFSET,base(patched)+KartRaceMusic.DATA_OFFSET}) {
            byte[] broken=patched.clone(); broken[at]^=1; Files.write(rom,broken);
            assertThrows(IllegalStateException.class, () -> KartRaceMusic.verify(rom.toString(),song.toString()));
        }
    }

    @Test void absentMusicKeepsOriginalAndInvalidMusicLeavesFileUntouched(@TempDir Path dir) throws Exception {
        Path rom=Files.write(dir.resolve("rom.md"),original), base=Files.write(dir.resolve("base.md"),original);
        Path song=dir.resolve("music.vgm"), free=Files.writeString(dir.resolve("free.txt"),"");
        assertTrue(KartRaceMusic.insert(rom.toString(),base.toString(),song.toString(),free.toString(),List.of()).isEmpty());
        KartRaceMusic.verify(rom.toString(),song.toString());
        assertArrayEquals(original,Files.readAllBytes(rom));
        Files.write(song,new byte[32]);
        assertThrows(IOException.class, () -> KartRaceMusic.insert(rom.toString(),base.toString(),song.toString(),free.toString(),List.of()));
        assertArrayEquals(original,Files.readAllBytes(rom));
        Files.delete(song);
        Files.write(rom,KartRaceMusic.patchRom(original,original,track,List.of()));
        assertThrows(IllegalStateException.class, () -> KartRaceMusic.verify(rom.toString(),song.toString()));
    }

    private static int base(byte[] b) { int p=KartRaceMusic.HOOK+2; return ((b[p]&255)<<24|(b[p+1]&255)<<16|(b[p+2]&255)<<8|(b[p+3]&255))-KartRaceMusic.ENTRY_OFFSET; }
    private static void le32(byte[] b,int p,int v) { for(int i=0;i<4;i++) b[p+i]=(byte)(v>>>(8*i)); }
}
