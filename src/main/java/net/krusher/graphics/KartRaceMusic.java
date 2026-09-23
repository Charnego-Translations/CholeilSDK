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
import java.util.Map;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.CRC32;
import net.krusher.FreeSpaceScanner;
import net.krusher.TextInserter;

/** Optional, race-only PSG/YM2612 playback; no changes to the shared room-music table. */
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
    static final int STATE_MAGIC = 0x4B4D4431; // Track identity at actor +$34; +$38 pointer, +$3C sample debt.
    static final byte[] ORIGINAL_HOOK = HexFormat.of().parseHex("3428000436280008");
    private static final byte[] MAGIC = "CHOLKPS1".getBytes(StandardCharsets.US_ASCII);

    private KartRaceMusic() {}

    record Track(byte[] packets, int sequenceOffset, int loopOffset, int dictionaryCount,
                 int samples, int clock, int ymClock,
                 int writes, int psgWrites, int ym0Writes, int ym1Writes) {}

    private record Encoded(byte[] bytes, int sequenceOffset, int loopOffset, int dictionaryCount) {}
    private record Candidate(String key, byte[] packet, int savings) {}

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
        if (version < 0x110 || version > 0x150) throw new IOException("Expected Mega Drive VGM version 1.10 through 1.50");
        int clock = le32(vgm, 0x0C), ymClock = le32(vgm, 0x2C);
        if (clock != 3579545 && clock != 3546895) throw new IOException("Unsupported PSG clock or dual-chip VGM");
        if (ymClock != 0 && ymClock != 7670454 && ymClock != 7600489) {
            throw new IOException("Unsupported YM2612 clock or dual-chip VGM");
        }
        if (le32(vgm, 0x10) != 0 || le32(vgm, 0x30) != 0) {
            throw new IOException("Race music supports only the Mega Drive PSG and YM2612");
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
        List<byte[]> packets = new ArrayList<>();
        List<Integer> types = new ArrayList<>();
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int samples = 0, loopSamples = -1, loopPacket = -1;
        int psgWrites = 0, ym0Writes = 0, ym1Writes = 0;
        boolean ended = false;
        for (int p = start; p < gd3;) {
            if (p == loop) {
                if (!types.isEmpty()) packet(packets, types, data, 0);
                loopPacket = packets.size(); loopSamples = samples;
            }
            int op = vgm[p++] & 255;
            if (op == 0x50 || op == 0x52 || op == 0x53 || op == 0x4F) {
                requireBytes(p, 1, gd3);
                if (op == 0x4F) {
                    int value = vgm[p++] & 255;
                    if (value != 255) throw new IOException("Game Gear stereo masks cannot be played on Mega Drive PSG");
                } else {
                    int bytes = op == 0x50 ? 1 : 2;
                    requireBytes(p, bytes, gd3);
                    types.add(op == 0x50 ? 0 : op == 0x52 ? 1 : 2);
                    data.write(vgm[p++] & 255);
                    if (bytes == 2) data.write(vgm[p++] & 255);
                    if (op == 0x50) psgWrites++;
                    else if (op == 0x52) ym0Writes++;
                    else ym1Writes++;
                    if (types.size() > 255) throw new IOException("More than 255 chip writes at one timestamp");
                }
            } else if (op == 0x61 || op == 0x62 || op == 0x63 || op >= 0x70 && op <= 0x7F) {
                int wait;
                if (op == 0x61) { requireBytes(p, 2, gd3); wait = (vgm[p] & 255) | (vgm[p+1] & 255) << 8; p += 2; }
                else wait = op == 0x62 ? 735 : op == 0x63 ? 882 : op - 0x6F;
                packet(packets, types, data, wait);
                samples = Math.addExact(samples, wait);
            } else if (op == 0x66) {
                if (!types.isEmpty()) packet(packets, types, data, 0);
                ended = true; break;
            } else throw new IOException(String.format("Unsupported VGM command %02X at %06X", op, p - 1));
        }
        if (!ended || loopPacket < 0 || loopPacket >= packets.size() || samples <= loopSamples
                || samples != le32(vgm, 0x18) || samples - loopSamples != le32(vgm, 0x20)) {
            throw new IOException("Invalid VGM end, loop boundary or declared duration");
        }
        validateDensity(packets, loopPacket);
        Encoded encoded = encodePackets(packets, loopPacket);
        Track track = new Track(encoded.bytes, encoded.sequenceOffset, encoded.loopOffset, encoded.dictionaryCount,
                samples, clock, ymClock,
                psgWrites + ym0Writes + ym1Writes, psgWrites, ym0Writes, ym1Writes);
        List<byte[]> decoded = decodePackets(track);
        if (decoded.size() != packets.size()) throw new IOException("Race-music dictionary lost packets");
        for (int i = 0; i < packets.size(); i++) if (!Arrays.equals(packets.get(i), decoded.get(i))) {
            throw new IOException("Race-music dictionary changed packet " + i);
        }
        return track;
    }

    // A PAL frame can owe at most 882 samples. Reject streams that could need
    // more than the player's bounded 64-packet catch-up, including across loops.
    private static void validateDensity(List<byte[]> packets, int loopIndex) throws IOException {
        List<Integer> waits = new ArrayList<>();
        for (byte[] packet : packets) waits.add((packet[0] & 255) * 441);
        for (int i = 0; i < waits.size(); i++) {
            int sum = 0, next = i;
            for (int n = 0; n < 64 && sum <= 882; n++) {
                sum += waits.get(next++);
                if (next == waits.size()) next = loopIndex;
            }
            if (sum <= 882) throw new IOException("VGM is too dense for frame-based chip playback");
        }
    }

    private static void packet(List<byte[]> out, List<Integer> types,
                               ByteArrayOutputStream data, int wait) throws IOException {
        int count = types.size(), typeBytes = (count + 3) / 4;
        if (count > 255) throw new IOException("More than 255 chip writes at one timestamp");
        if (wait < 0 || wait % 441 != 0 || wait / 441 > 255) {
            throw new IOException("Race VGM waits must be 0..112455 samples in exact 441-sample units");
        }
        ByteArrayOutputStream packet = new ByteArrayOutputStream(2 + typeBytes + data.size());
        packet.write(wait / 441); packet.write(count);
        for (int i = 0; i < count; i += 4) {
            int map = 0;
            for (int n = 0; n < 4 && i + n < count; n++) map |= types.get(i + n) << (n * 2);
            packet.write(map);
        }
        packet.writeBytes(data.toByteArray());
        out.add(packet.toByteArray());
        types.clear(); data.reset();
    }

    static int packetEnd(byte[] packets, int at) throws IOException {
        requireBytes(at, 2, packets.length);
        int count = packets[at + 1] & 255;
        int typeBytes = (count + 3) / 4, dataBytes = 0;
        requireBytes(at + 2, typeBytes, packets.length);
        for (int i = 0; i < count; i++) {
            int type = packets[at + 2 + i / 4] >>> ((i & 3) * 2) & 3;
            if (type == 3) throw new IOException("Invalid chip type in encoded race music");
            dataBytes += type == 0 ? 1 : 2;
        }
        int end = at + 2 + typeBytes + dataBytes;
        requireBytes(at, end - at, packets.length);
        return end;
    }

    private static Encoded encodePackets(List<byte[]> packets, int loopPacket) throws IOException {
        Map<String, Integer> frequency = new HashMap<>();
        Map<String, byte[]> unique = new HashMap<>();
        for (byte[] packet : packets) {
            String key = HexFormat.of().formatHex(packet);
            frequency.merge(key, 1, Integer::sum);
            unique.putIfAbsent(key, packet);
        }
        List<Candidate> candidates = new ArrayList<>();
        for (var entry : unique.entrySet()) {
            int savings = (frequency.get(entry.getKey()) - 1) * entry.getValue().length - 2;
            if (savings > 0) candidates.add(new Candidate(entry.getKey(), entry.getValue(), savings));
        }
        candidates.sort(Comparator.comparingInt(Candidate::savings).reversed().thenComparing(Candidate::key));
        if (candidates.size() > 255) candidates = new ArrayList<>(candidates.subList(0, 255));

        Map<String, Integer> dictionary = new HashMap<>();
        int tableBytes = candidates.size() * 2, cursor = tableBytes;
        ByteArrayOutputStream table = new ByteArrayOutputStream(tableBytes);
        ByteArrayOutputStream entries = new ByteArrayOutputStream();
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            if (cursor >= 0x8000) throw new IOException("Race-music dictionary exceeds signed 16-bit offsets");
            table.write(cursor >>> 8); table.write(cursor);
            entries.writeBytes(candidate.packet);
            dictionary.put(candidate.key, i);
            cursor += candidate.packet.length;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(table.toByteArray());
        out.writeBytes(entries.toByteArray());
        int sequenceOffset = out.size(), loopOffset = -1;
        for (int i = 0; i < packets.size(); i++) {
            if (i == loopPacket) loopOffset = out.size();
            byte[] packet = packets.get(i);
            Integer index = dictionary.get(HexFormat.of().formatHex(packet));
            if (index != null) out.write(index);
            else { out.write(0xFF); out.writeBytes(packet); }
        }
        if (loopOffset < sequenceOffset || out.size() >= 0x8000) {
            throw new IOException("Compacted race music exceeds the low-ROM player format");
        }
        return new Encoded(out.toByteArray(), sequenceOffset, loopOffset, candidates.size());
    }

    static List<byte[]> decodePackets(Track track) throws IOException {
        List<byte[]> decoded = new ArrayList<>();
        byte[] bytes = track.packets;
        for (int sequence = track.sequenceOffset; sequence < bytes.length;) {
            int selector = bytes[sequence++] & 255, packet;
            if (selector == 0xFF) packet = sequence;
            else {
                if (selector >= track.dictionaryCount) throw new IOException("Invalid race-music dictionary index");
                int table = selector * 2;
                packet = (bytes[table] & 255) << 8 | (bytes[table + 1] & 255);
            }
            int end = packetEnd(bytes, packet);
            decoded.add(Arrays.copyOfRange(bytes, packet, end));
            if (selector == 0xFF) sequence = end;
        }
        return decoded;
    }

    static byte[] code(int base, Track track) {
        var c = new SonicSideAnimation.Code();
        c.word(0x40E7);                         // Save SR and every register used by the wrapper.
        c.word(0x48E7); c.word(0xFFFE);
        c.word(0x46FC); c.word(0x2700);         // No interrupt may use the Z80 while we own its bus.
        c.word(0x4A68); c.word(0x000C);          // Parked kart: reset our private tail, leave audio alone.
        c.branch(0x6700, "parked");
        c.word(0x0CA8); c.longword(stateMagic(track)); c.word(0x0034);
        c.branch(0x6600, "start");
        c.word(0x2268); c.word(0x0038);          // MOVEA.L $38(A0),A1
        c.word(0xB3FC); c.longword(base + DATA_OFFSET + track.sequenceOffset);
        c.branch(0x6500, "start");
        c.word(0xB3FC); c.longword(base + DATA_OFFSET + track.packets.length);
        c.branch(0x6200, "start");
        samplesPerFrame(c, "running-rate");
        c.word(0x91A8); c.word(0x003C);          // SUB.L D0,$3C(A0)
        c.branch(0x6E00, "done");
        c.word(0x33FC); c.word(0x0100); c.longword(0x00A11100); // Request the Z80/YM bus.
        c.label("wait-bus");
        c.word(0x0839); c.word(0); c.longword(0x00A11100);
        c.branch(0x6600, "wait-bus");
        c.word(0x7E3F);                         // Bounded catch-up: at most 64 packets per game frame.
        c.label("packet");
        c.word(0xB3FC); c.longword(base + DATA_OFFSET + track.packets.length);
        c.branch(0x6600, "read");
        c.word(0x43F9); c.longword(base + DATA_OFFSET + track.loopOffset);
        c.label("read");
        c.word(0x7000); c.word(0x1019);          // D0 = dictionary selector or literal marker.
        c.word(0x2849);                         // A4 = next sequence byte if this is a dictionary hit.
        c.word(0x0C40); c.word(0x00FF);
        c.branch(0x6700, "packet-data");
        c.word(0x3C00); c.word(0xE34E);          // D6 = selector * 2 (dictionary table index).
        c.word(0x47F9); c.longword(base + DATA_OFFSET);
        c.word(0x3C33); c.word(0x6000);          // D6 = table[D6], relative to data base.
        c.word(0xD6C6); c.word(0x224B);          // A1 = dictionary packet.
        c.label("packet-data");
        c.word(0x7200); c.word(0x1219);          // D1 = delay in 441-sample units.
        c.word(0xC2FC); c.word(441);
        c.word(0xD3A8); c.word(0x003C);          // ADD.L D1,$3C(A0)
        c.word(0x7400); c.word(0x1419);          // D2 = event count.
        c.branch(0x6700, "packet-done");
        c.word(0x3602);                         // D3 = ceil(event count / 4), the type-map size.
        c.word(0x5643);
        c.word(0xE44B);
        c.word(0x2449);                         // A2 = event data after the packed two-bit type map.
        c.word(0xD4C3);
        c.word(0x7800);                         // D4 = remaining types in the current map byte.
        c.label("event");
        c.word(0x4A44);
        c.branch(0x6600, "have-type");
        c.word(0x7600); c.word(0x1619);          // Fetch four packed event types.
        c.word(0x7804);
        c.label("have-type");
        c.word(0x3A03);                         // D5 = next two-bit type.
        c.word(0x0245); c.word(3);
        c.word(0xE44B);
        c.word(0x5344);
        c.word(0x4A45);
        c.branch(0x6700, "psg");
        c.word(0x0C45); c.word(1);
        c.branch(0x6700, "ym0");
        c.word(0x13DA); c.longword(0x00A04002);  // YM2612 port 1 address.
        c.branch(0x6100, "ym-wait");
        c.word(0x13DA); c.longword(0x00A04003);  // YM2612 port 1 data.
        c.branch(0x6100, "ym-wait");
        c.branch(0x6000, "event-done");
        c.label("ym0");
        c.word(0x13DA); c.longword(0x00A04000);  // YM2612 port 0 address.
        c.branch(0x6100, "ym-wait");
        c.word(0x13DA); c.longword(0x00A04001);  // YM2612 port 0 data.
        c.branch(0x6100, "ym-wait");
        c.branch(0x6000, "event-done");
        c.label("psg");
        c.word(0x13DA); c.longword(0x00C00011);  // SN76489 write.
        c.label("event-done");
        c.word(0x5342);
        c.branch(0x6600, "event");
        c.word(0x224A);                         // Literal packet end; dictionary data is never saved.
        c.label("packet-done");
        c.word(0x0C40); c.word(0x00FF);
        c.branch(0x6600, "sequence-next");
        c.word(0x2849);                         // Literal: sequence continues after its packet data.
        c.label("sequence-next");
        c.word(0x224C);
        c.label("written");
        c.word(0x2149); c.word(0x0038);          // Save next packet pointer.
        c.word(0x4AA8); c.word(0x003C);
        c.branch(0x6E00, "release-bus");
        c.branch(0x51CF, "packet");
        c.label("release-bus");
        c.word(0x33FC); c.word(0); c.longword(0x00A11100);
        c.branch(0x6000, "done");
        c.label("start");
        c.word(0x217C); c.longword(stateMagic(track)); c.word(0x0034);
        c.word(0x217C); c.longword(base + DATA_OFFSET + track.sequenceOffset); c.word(0x0038);
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
        c.label("ym-wait");
        c.word(0x7C18);                         // Fixed delay: do not poll the YM busy flag.
        c.label("ym-wait-loop");
        c.branch(0x51CE, "ym-wait-loop");
        c.word(0x4E75);
        return c.finish();
    }

    // A savestate from another edit must restart, not read the middle of a
    // different packet stream that happens to occupy the same ROM addresses.
    static int stateMagic(Track track) {
        CRC32 crc = new CRC32();
        crc.update(track.packets);
        byte[] metadata = new byte[24];
        put32(metadata,0,track.sequenceOffset); put32(metadata,4,track.loopOffset);
        put32(metadata,8,track.dictionaryCount); put32(metadata,12,track.samples);
        put32(metadata,16,track.clock); put32(metadata,20,track.ymClock);
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
        put32(out, 16, track.sequenceOffset); put32(out, 20, track.loopOffset);
        put32(out, 24, track.dictionaryCount); put32(out, 28, track.samples);
        put32(out, 32, track.clock); put32(out, 36, track.ymClock);
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
        System.out.printf("Race Mega Drive music: %.2f seconds, looping, %d writes "
                        + "(PSG %d, YM2612 p0 %d, p1 %d); original room-music selectors unchanged.%n",
                track.samples / 44100.0, track.writes, track.psgWrites, track.ym0Writes, track.ym1Writes);
        System.out.printf("Race-music compact stream: %d bytes, %d-entry packet dictionary.%n",
                track.packets.length, track.dictionaryCount);
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
        System.out.println("Race-music code, loop and PSG/YM2612 packets verified.");
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
