package net.krusher.graphics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.CRC32;
import net.krusher.FreeSpaceScanner;
import net.krusher.TextInserter;

/** Optional, race-only PSG playback; no changes to the shared room-music table. */
public final class KartRaceMusic {
    public static final String DEFAULT_VGM = "music/kart_race.vgm";
    static final int HOOK = 0x020692;
    // Three large, instrumented filler regions from IntroInserter.HUECOS.
    // Stay below 2 MiB: old GPGX savestates restore a 2 MiB cartridge mirror.
    // Allocation is BEFORE the intro, with an explicit reservation passed to it.
    static final int[][] POOL = {{0x11BF6A, 0x120000}, {0x0E4FE0, 0x0F0000}, {0x0ACF08, 0x0D0000}};
    static final int ENTRY_OFFSET = 0x40;
    static final int DATA_OFFSET = 0x400;
    static final int MAX_VGM = 2 * 1024 * 1024;
    static final int STATE_MAGIC = 0x4B505347; // Track identity at actor +$34; +$38 pointer, +$3C sample debt.
    static final byte[] ORIGINAL_HOOK = HexFormat.of().parseHex("3428000436280008");
    private static final byte[] MAGIC = "CHOLKPS1".getBytes(StandardCharsets.US_ASCII);

    private KartRaceMusic() {}

    record Track(byte[] packets, int loopOffset, int samples, int clock, int writes) {}

    static Track readTrack(byte[] file) throws IOException {
        byte[] vgm = file;
        if (file.length > MAX_VGM) throw new IOException("VGM exceeds the input size limit");
        if (file.length >= 2 && (file[0] & 255) == 0x1F && (file[1] & 255) == 0x8B) {
            try (var zip = new GZIPInputStream(new ByteArrayInputStream(file))) {
                vgm = zip.readNBytes(MAX_VGM + 1);
            }
        }
        if (vgm.length < 0x40 || vgm.length > MAX_VGM || !matches(vgm, 0, new byte[]{'V','g','m',' '})) {
            throw new IOException("Invalid or oversized VGM header");
        }
        int version = le32(vgm, 8);
        if (version < 0x110 || version > 0x150) throw new IOException("Expected PSG VGM version 1.10 through 1.50");
        int clock = le32(vgm, 0x0C);
        if (clock != 3579545 && clock != 3546895) throw new IOException("Unsupported PSG clock or dual-chip VGM");
        if (le32(vgm, 0x10) != 0 || le32(vgm, 0x2C) != 0 || le32(vgm, 0x30) != 0) {
            throw new IOException("Race music must be PSG-only (no FM chips)");
        }
        if ((vgm[0x28] & 255) != 9 || vgm[0x29] != 0 || (vgm[0x2A] & 255) != 16) {
            throw new IOException("Expected the Sega PSG noise configuration (feedback 9, width 16)");
        }
        int end = relative(vgm, 4, le32(vgm, 4));
        if (end != vgm.length) throw new IOException("VGM EOF offset does not match the file");
        int start = version >= 0x150 && le32(vgm, 0x34) != 0 ? relative(vgm, 0x34, le32(vgm, 0x34)) : 0x40;
        int loop = le32(vgm, 0x1C) == 0 ? -1 : relative(vgm, 0x1C, le32(vgm, 0x1C));
        int gd3 = le32(vgm, 0x14) == 0 ? end : relative(vgm, 0x14, le32(vgm, 0x14));
        if (start < 0x40 || loop < start || loop >= gd3) throw new IOException("A valid VGM loop is required");
        ByteArrayOutputStream packets = new ByteArrayOutputStream(), writes = new ByteArrayOutputStream();
        int samples = 0, loopSamples = -1, loopOffset = -1, writeCount = 0;
        boolean ended = false;
        for (int p = start; p < gd3;) {
            if (p == loop) {
                if (writes.size() > 0) packet(packets, writes, 0);
                loopOffset = packets.size(); loopSamples = samples;
            }
            int op = vgm[p++] & 255;
            if (op == 0x50 || op == 0x4F) {
                requireBytes(p, 1, gd3);
                int value = vgm[p++] & 255;
                if (op == 0x4F) {
                    if (value != 255) throw new IOException("Game Gear stereo masks cannot be played on Mega Drive PSG");
                } else {
                    writes.write(value); writeCount++;
                    if (writes.size() > 256) throw new IOException("Too many PSG writes at one timestamp");
                }
            } else if (op == 0x61 || op == 0x62 || op == 0x63 || op >= 0x70 && op <= 0x7F) {
                int wait;
                if (op == 0x61) { requireBytes(p, 2, gd3); wait = (vgm[p] & 255) | (vgm[p+1] & 255) << 8; p += 2; }
                else wait = op == 0x62 ? 735 : op == 0x63 ? 882 : op - 0x6F;
                packet(packets, writes, wait);
                samples = Math.addExact(samples, wait);
            } else if (op == 0x66) {
                if (writes.size() > 0) packet(packets, writes, 0);
                ended = true; break;
            } else throw new IOException(String.format("Unsupported VGM command %02X at %06X", op, p - 1));
        }
        if (!ended || loopOffset < 0 || loopOffset >= packets.size() || samples <= loopSamples
                || samples != le32(vgm, 0x18) || samples - loopSamples != le32(vgm, 0x20)) {
            throw new IOException("Invalid VGM end, loop boundary or declared duration");
        }
        byte[] encoded = packets.toByteArray();
        validateDensity(encoded, loopOffset);
        return new Track(encoded, loopOffset, samples, clock, writeCount);
    }

    // A PAL frame can owe at most 882 samples. Reject streams that could need
    // more than the player's bounded 64-packet catch-up, including across loops.
    private static void validateDensity(byte[] packets, int loopOffset) throws IOException {
        List<Integer> waits = new ArrayList<>();
        int loopIndex = -1;
        for (int p = 0; p < packets.length;) {
            if (p == loopOffset) loopIndex = waits.size();
            waits.add((packets[p] & 255) << 8 | (packets[p + 1] & 255));
            int count = (packets[p + 2] & 255) << 8 | (packets[p + 3] & 255);
            p += 4 + count + (count & 1);
        }
        for (int i = 0; i < waits.size(); i++) {
            int sum = 0, next = i;
            for (int n = 0; n < 64 && sum <= 882; n++) {
                sum += waits.get(next++);
                if (next == waits.size()) next = loopIndex;
            }
            if (sum <= 882) throw new IOException("VGM is too dense for frame-based PSG playback");
        }
    }

    private static void packet(ByteArrayOutputStream out, ByteArrayOutputStream writes, int wait) {
        int count = writes.size();
        out.write(wait >>> 8); out.write(wait); out.write(count >>> 8); out.write(count);
        out.writeBytes(writes.toByteArray());
        if ((count & 1) != 0) out.write(0);
        writes.reset();
    }

    static byte[] code(int base, Track track) {
        var c = new SonicSideAnimation.Code();
        c.word(0x40E7);                         // Save SR and every register used by the wrapper.
        c.word(0x48E7); c.word(0xFFFE);
        c.word(0x4A68); c.word(0x000C);          // Parked kart: reset our private tail, leave audio alone.
        c.branch(0x6700, "parked");
        c.word(0x0CA8); c.longword(stateMagic(track)); c.word(0x0034);
        c.branch(0x6600, "start");
        c.word(0x2268); c.word(0x0038);          // MOVEA.L $38(A0),A1
        c.word(0xB3FC); c.longword(base + DATA_OFFSET);
        c.branch(0x6500, "start");
        c.word(0xB3FC); c.longword(base + DATA_OFFSET + track.packets.length);
        c.branch(0x6200, "start");
        c.word(0x2009); c.word(0x0800); c.word(0); // Check saved pointer alignment.
        c.branch(0x6600, "start");
        samplesPerFrame(c, "running-rate");
        c.word(0x91A8); c.word(0x003C);          // SUB.L D0,$3C(A0)
        c.branch(0x6E00, "done");
        c.word(0x7E3F);                         // Bounded catch-up: at most 64 packets per game frame.
        c.label("packet");
        c.word(0xB3FC); c.longword(base + DATA_OFFSET + track.packets.length);
        c.branch(0x6600, "read");
        c.word(0x43F9); c.longword(base + DATA_OFFSET + track.loopOffset);
        c.label("read");
        c.word(0x7200); c.word(0x3219);          // MOVEQ #0,D1 / MOVE.W (A1)+,D1: delay.
        c.word(0xD3A8); c.word(0x003C);          // ADD.L D1,$3C(A0)
        c.word(0x3419);                         // MOVE.W (A1)+,D2: write count.
        c.branch(0x6700, "written");
        c.word(0x3602); c.word(0x0243); c.word(1); // D3 = padding byte count.
        c.word(0x5342);                         // SUBQ.W #1,D2
        c.label("write");
        c.word(0x13D9); c.longword(0x00C00011);  // MOVE.B (A1)+,PSG
        c.branch(0x51CA, "write");             // DBRA D2,write
        c.word(0xD2C3);                         // ADDA.W D3,A1
        c.label("written");
        c.word(0x2149); c.word(0x0038);          // Save next packet pointer.
        c.word(0x4AA8); c.word(0x003C);
        c.branch(0x6E00, "done");
        c.branch(0x51CF, "packet");
        c.branch(0x6000, "done");
        c.label("start");
        c.word(0x217C); c.longword(stateMagic(track)); c.word(0x0034);
        c.word(0x217C); c.longword(base + DATA_OFFSET); c.word(0x0038);
        samplesPerFrame(c, "initial-rate");
        c.word(0xD080);                         // ADD.L D0,D0: allow two Z80 ticks to stop old BGM.
        c.word(0x2140); c.word(0x003C);
        c.word(0x70FD); c.word(0x7200);
        c.word(0x4EB9); c.longword(0x0009E6);    // Original sound command: stop playback.
        c.word(0x4278); c.word(0xAC02);          // Invalidate BGM cache; next room load restores its song.
        c.branch(0x6000, "done");
        c.label("parked");
        c.word(0x42A8); c.word(0x0034);
        c.word(0x42A8); c.word(0x0038);
        c.word(0x42A8); c.word(0x003C);
        c.label("done");
        c.word(0x4CDF); c.word(0x7FFF);
        c.word(0x46DF);
        c.word(0x3428); c.word(4);               // Replay displaced instructions exactly once.
        c.word(0x3628); c.word(8);
        c.word(0x4E75);
        return c.finish();
    }

    // A savestate from another edit must restart, not read the middle of a
    // different packet stream that happens to occupy the same ROM addresses.
    static int stateMagic(Track track) {
        CRC32 crc = new CRC32();
        crc.update(track.packets);
        byte[] metadata = new byte[12];
        put32(metadata,0,track.loopOffset); put32(metadata,4,track.samples); put32(metadata,8,track.clock);
        crc.update(metadata);
        return STATE_MAGIC ^ (int)crc.getValue();
    }

    private static void samplesPerFrame(SonicSideAnimation.Code c, String label) {
        c.word(0x203C); c.longword(735);
        c.word(0x0838); c.word(7); c.word(0xB564); // Original hardware-region mirror, bit 7 = PAL.
        c.branch(0x6700, label);
        c.word(0x203C); c.longword(882);
        c.label(label);
    }

    static byte[] block(int base, Track track) {
        byte[] code = code(base, track);
        if (ENTRY_OFFSET + code.length > DATA_OFFSET) throw new IllegalStateException("Race player exceeds code reservation");
        byte[] out = new byte[DATA_OFFSET + track.packets.length];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        put32(out, 8, out.length); put32(out, 12, code.length);
        put32(out, 16, track.loopOffset); put32(out, 20, track.samples); put32(out, 24, track.clock);
        System.arraycopy(code, 0, out, ENTRY_OFFSET, code.length);
        System.arraycopy(track.packets, 0, out, DATA_OFFSET, track.packets.length);
        return out;
    }

    static byte[] hook(int base) {
        var c = new SonicSideAnimation.Code();
        c.word(0x4EB9); c.longword(base + ENTRY_OFFSET); c.word(0x4E71);
        return c.finish();
    }

    static byte[] patchRom(byte[] input, byte[] original, Track track, List<int[]> occupied) {
        if (!matches(original, HOOK, ORIGINAL_HOOK)
                || !matches(original, 0x1A4D2, HexFormat.of().parseHex("4268002e42a80030"))
                || !matches(input, 0x1A4D2, HexFormat.of().parseHex("4268002e42a80030"))
                || !matches(input, 0x9E6, Arrays.copyOfRange(original, 0x9E6, 0xA16))) {
            throw new IllegalStateException("Unsupported kart initializer or sound-command routine");
        }
        if (!matches(input, HOOK, ORIGINAL_HOOK)) {
            int base = read32(input, HOOK + 2) - ENTRY_OFFSET;
            if (!allowed(base, DATA_OFFSET + track.packets.length) || !matches(input, HOOK, hook(base))
                    || !matches(input, base, block(base, track))) {
                throw new IllegalStateException("Race-music hook is occupied or music changed; rebuild from the original ROM");
            }
            return input.clone();
        }
        int base = allocate(input, original, DATA_OFFSET + track.packets.length, occupied);
        byte[] block = block(base, track);
        byte[] out = input.clone();
        System.arraycopy(block, 0, out, base, block.length);
        System.arraycopy(hook(base), 0, out, HOOK, ORIGINAL_HOOK.length);
        return out;
    }

    static boolean allowed(int base, int length) {
        for (int[] r : POOL) if (base >= r[0] && (long)base + length <= r[1] && (base & 1) == 0) return true;
        return false;
    }

    static int allocate(byte[] input, byte[] original, int length, List<int[]> occupied) {
        List<int[]> blocked = new ArrayList<>(occupied);
        for (int[] r : blocked) if (r.length != 2 || r[0] < 0 || r[1] <= r[0])
            throw new IllegalArgumentException("Invalid prior ROM reservation");
        blocked.sort(Comparator.comparingInt(r -> r[0]));
        for (int[] pool : POOL) {
            int start = pool[0];
            for (int[] r : blocked) {
                if (r[1] <= start || r[0] >= pool[1]) continue;
                if (fits(input, original, start, Math.min(r[0], pool[1]), length)) return start;
                start = Math.max(start, (r[1] + 1) & ~1);
            }
            if (fits(input, original, start, pool[1], length)) return start;
        }
        throw new IllegalStateException("No verified low-ROM space for race music; no data was overwritten");
    }

    private static boolean fits(byte[] input, byte[] original, int start, int end, int length) {
        return length > 0 && (long)start + length <= end && end <= Math.min(input.length, original.length)
                && Arrays.equals(input, start, start + length, original, start, start + length);
    }

    /** Called after all text/graphics insertion and before IntroInserter. Returns whole occupied ranges. */
    public static List<int[]> insert(String romPath, String originalPath, String vgmPath,
                                     String freeSpacePath, List<int[]> occupiedGraphics) throws IOException {
        if (!Files.exists(Path.of(vgmPath))) { System.out.println("No race VGM; original music retained."); return List.of(); }
        Track track = readTrack(Files.readAllBytes(Path.of(vgmPath)));
        Path path = Path.of(romPath);
        List<int[]> occupied = new ArrayList<>(occupiedGraphics);
        for (FreeSpaceScanner.Region r : FreeSpaceScanner.readRegionsFile(freeSpacePath)) {
            occupied.add(new int[]{r.start, Math.addExact(r.start, r.length)});
        }
        byte[] out = patchRom(Files.readAllBytes(path), Files.readAllBytes(Path.of(originalPath)), track, occupied);
        TextInserter.fixChecksum(out);
        Files.write(path, out);
        System.out.printf("Race PSG music: %.2f seconds, looping, %d writes; original room-music selectors unchanged.%n",
                track.samples / 44100.0, track.writes);
        int base = read32(out, HOOK + 2) - ENTRY_OFFSET;
        System.out.printf("Race-music reservation: %06X..%06X (below 2 MiB).%n", base, base + DATA_OFFSET + track.packets.length);
        return List.of(new int[]{base, base + DATA_OFFSET + track.packets.length});
    }

    public static void verify(String romPath, String vgmPath) throws IOException {
        byte[] rom = Files.readAllBytes(Path.of(romPath));
        if (!Files.exists(Path.of(vgmPath))) {
            if (!matches(rom, HOOK, ORIGINAL_HOOK)) throw new IllegalStateException("Race music installed without its VGM source");
            return;
        }
        Track track = readTrack(Files.readAllBytes(Path.of(vgmPath)));
        int base = read32(rom, HOOK + 2) - ENTRY_OFFSET;
        if (!allowed(base, DATA_OFFSET + track.packets.length) || !matches(rom, HOOK, hook(base)) || !matches(rom, base, block(base, track))) {
            throw new IllegalStateException("Final race music does not match its VGM source");
        }
        System.out.println("Race-music code, loop and PSG packets verified.");
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) { System.out.println("KartRaceMusic verify [rom] [vgm]; insert via CholeilSDK i to reserve space safely"); return; }
        String rom = args.length > 1 ? args[1] : "Choleil.md", vgm = args.length > 2 ? args[2] : DEFAULT_VGM;
        switch (args[0]) {
            case "verify" -> verify(rom, vgm);
            default -> throw new IllegalArgumentException("use verify; music insertion must run in the full CholeilSDK i build");
        }
    }

    private static void requireBytes(int at, int n, int end) throws IOException {
        if (at < 0 || at + n > end) throw new IOException("Truncated VGM command");
    }
    private static int relative(byte[] bytes, int at, int relative) throws IOException {
        long absolute = at + Integer.toUnsignedLong(relative);
        if (absolute > bytes.length) throw new IOException("VGM offset outside the file");
        return (int)absolute;
    }
    private static int le32(byte[] bytes, int at) {
        return (bytes[at] & 255) | (bytes[at+1] & 255) << 8 | (bytes[at+2] & 255) << 16 | bytes[at+3] << 24;
    }
    private static int read32(byte[] bytes, int at) {
        if (at < 0 || at + 4 > bytes.length) return -1;
        return bytes[at] << 24 | (bytes[at+1] & 255) << 16 | (bytes[at+2] & 255) << 8 | (bytes[at+3] & 255);
    }
    private static void put32(byte[] bytes, int at, int value) {
        bytes[at]=(byte)(value>>>24); bytes[at+1]=(byte)(value>>>16); bytes[at+2]=(byte)(value>>>8); bytes[at+3]=(byte)value;
    }
    private static boolean matches(byte[] data, int at, byte[] expected) {
        return at >= 0 && (long)at + expected.length <= data.length
                && Arrays.equals(data, at, at + expected.length, expected, 0, expected.length);
    }
}
