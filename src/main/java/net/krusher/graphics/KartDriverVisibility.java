package net.krusher.graphics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import net.krusher.TextInserter;

/** Hide only the riding-player sprite; leave kart simulation and on-foot rendering alone. */
public final class KartDriverVisibility {
    public static final String DEFAULT_SETTINGS = "kart_settings.txt";
    static final int MOUNT = 0x020740;
    static final int DRIVING = 0x0209D8;
    // move.w #3,$B0F8.w / ori.b #7,$B0A7.w, in the kart actor only.
    // Add existing hide-player bit 6, retaining all original control flags.
    private static final byte[] ORIGINAL = HexFormat.of().parseHex("31fc0003b0f800380007b0a7");
    private static final byte[] HIDDEN = HexFormat.of().parseHex("31fc0003b0f800380047b0a7");
    private static final int HIDE_TEST = 0x0079C4;
    private static final byte[] ORIGINAL_HIDE_TEST = HexFormat.of().parseHex("08380006b0a7660003bc");
    private static final int ROOM_RESET = 0x01927A;
    private static final byte[] ORIGINAL_RESET = HexFormat.of().parseHex("42b8b0a4");

    private KartDriverVisibility() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("KartDriverVisibility insert [rom] [originalRom] [settings]");
            System.out.println("KartDriverVisibility verify [rom] [settings]");
            return;
        }
        String rom = args.length > 1 ? args[1] : "Choleil.md";
        switch (args[0]) {
            case "insert" -> insert(rom, args.length > 2 ? args[2] : "Soleil (Spain).md",
                    args.length > 3 ? args[3] : DEFAULT_SETTINGS);
            case "verify" -> verify(rom, args.length > 2 ? args[2] : DEFAULT_SETTINGS);
            default -> throw new IllegalArgumentException("use insert or verify");
        }
    }

    static boolean readHidden(Path path) throws IOException {
        if (!Files.exists(path)) return false;
        Boolean hidden = null;
        for (String line : Files.readAllLines(path)) {
            String value = line.trim();
            if (value.isEmpty() || value.startsWith("#") || value.startsWith(";")) continue;
            String[] fields = value.split("=", -1);
            if (fields.length != 2 || !fields[0].trim().equals("hide_driver")
                    || hidden != null || !fields[1].trim().matches("[01]")) {
                throw new IllegalArgumentException(path + ": expected a single hide_driver=0 or hide_driver=1");
            }
            hidden = fields[1].trim().equals("1");
        }
        if (hidden == null) throw new IllegalArgumentException(path + ": missing hide_driver setting");
        return hidden;
    }

    static byte[] patchRom(byte[] input, byte[] original, boolean hidden) {
        if (!matches(original, MOUNT, ORIGINAL) || !matches(original, DRIVING, ORIGINAL)
                || !hasOriginalVisibilityGuards(original) || !hasOriginalVisibilityGuards(input)) {
            throw new IllegalStateException("Unsupported ROM: kart flags, player visibility or room reset differ");
        }
        for (int at : new int[]{MOUNT, DRIVING}) {
            if (!matches(input, at, ORIGINAL) && !matches(input, at, HIDDEN)) {
                throw new IllegalStateException("Kart driver flag hook is occupied; ROM left untouched");
            }
        }
        byte[] output = input.clone();
        byte[] flags = hidden ? HIDDEN : ORIGINAL;
        System.arraycopy(flags, 0, output, MOUNT, flags.length);
        System.arraycopy(flags, 0, output, DRIVING, flags.length);
        return output;
    }

    public static void insert(String romPath, String originalPath, String settingsPath) throws IOException {
        boolean hidden = readHidden(Path.of(settingsPath));
        Path rom = Path.of(romPath);
        byte[] input = Files.readAllBytes(rom);
        byte[] output = patchRom(input, Files.readAllBytes(Path.of(originalPath)), hidden);
        if (!Arrays.equals(input, output)) {
            TextInserter.fixChecksum(output);
            Files.write(rom, output);
        }
        System.out.println("Kart driver: " + (hidden ? "hidden while riding" : "original visible driver")
                + "; on-foot sprites and controls unchanged.");
    }

    public static void verify(String romPath, String settingsPath) throws IOException {
        boolean hidden = readHidden(Path.of(settingsPath));
        byte[] rom = Files.readAllBytes(Path.of(romPath));
        if (!hasOriginalVisibilityGuards(rom)
                || !matches(rom, MOUNT, hidden ? HIDDEN : ORIGINAL)
                || !matches(rom, DRIVING, hidden ? HIDDEN : ORIGINAL)) {
            throw new IllegalStateException("Kart driver visibility does not match " + settingsPath);
        }
        System.out.println("Kart-only driver visibility verified: hide_driver=" + (hidden ? 1 : 0));
    }

    private static boolean hasOriginalVisibilityGuards(byte[] rom) {
        // Rendering checks bit 6; room initialization clears B0A4..B0A7 on dismount.
        return matches(rom, HIDE_TEST, ORIGINAL_HIDE_TEST) && matches(rom, ROOM_RESET, ORIGINAL_RESET);
    }

    private static boolean matches(byte[] data, int at, byte[] expected) {
        return at >= 0 && at + expected.length <= data.length
                && Arrays.equals(data, at, at + expected.length, expected, 0, expected.length);
    }
}
