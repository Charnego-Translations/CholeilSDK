package net.krusher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Whether the map of the intro ROM in IntroInserter still describes the intro
 * ROM in the repo -- and whether the boot chain built from it hangs together.
 *
 * IntroInserterTest needs the game, which is gitignored, so it skips on a
 * fresh clone. This suite needs only the intro: the game is replaced by a
 * stand-in carrying the four header fields the step looks at. That is enough,
 * because everything checked here is about the intro's own pieces -- and those
 * are exactly what a rebuild of the intro moves. Rebuilding it and forgetting
 * to re-derive the offsets is the failure this suite exists to catch: the
 * addresses would still be even, still inside the ROM, and the console would
 * simply show a black screen.
 */
@DisplayName("the intro's piece map")
final class IntroMapTest {

    /** A stand-in for Soleil: 2 MB, with only the header fields checkGame reads. */
    private static final int TAM_JUEGO = 0x200000;
    private static final int PC_JUEGO  = 0x000200;

    private static byte[] intro;
    private static byte[] juego;
    private static byte[] out;

    @BeforeAll
    static void inject() throws Exception {
        Assumptions.assumeTrue(Files.exists(Paths.get(DefaultPaths.INTRO)),
                "intro ROM '" + DefaultPaths.INTRO + "' not present");
        intro = Files.readAllBytes(Paths.get(DefaultPaths.INTRO));

        juego = new byte[TAM_JUEGO];
        new Random(7).nextBytes(juego);                  // so "unchanged" means something
        IntroInserter.writeU32(juego, 0x00, 0xFFFFFE00); // SP
        IntroInserter.writeU32(juego, 0x04, PC_JUEGO);   // RESET
        byte[] serie = "GM MK-01182-00".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(serie, 0, juego, 0x180, serie.length);
        juego[0x1B0] = ' ';                              // no SRAM
        juego[0x1B1] = ' ';
        IntroInserter.writeU32(juego, IntroInserter.LOGO_SLOT, 0x00000CC4);

        out = IntroInserter.inject(juego, intro, juego, new ArrayList<IntroInserter.Region>());
    }

    /**
     * The offsets are only meaningful if the instructions they name are the
     * ones the step thinks it is overwriting. inject() calls checkIntro(),
     * which asserts exactly that, so reaching @BeforeAll at all is the check --
     * this states it out loud, and pins the size the map was measured on.
     */
    @Test
    @DisplayName("the intro in the repo is the build the map describes")
    void theIntroIsTheBuildTheMapDescribes() {
        Problems p = new Problems();
        p.check(intro.length == IntroInserter.INTRO_SIZE, String.format(
                "the intro is %d bytes, the map was measured on %d -- re-derive the offsets",
                intro.length, IntroInserter.INTRO_SIZE));
        p.check(IntroInserter.FR_OFF + IntroInserter.FR_N * IntroInserter.FR_TAM
                        <= IntroInserter.PCM_OFF,
                "the 16 frames run into the PCM sample");
        p.check(IntroInserter.PCM_OFF + IntroInserter.PCM_TAM == IntroInserter.VAC_OFF,
                "the PCM sample does not end where the empty sample starts");
        p.check(IntroInserter.VAC_OFF + IntroInserter.VAC_TAM == IntroInserter.DL_OFF,
                "the empty sample does not end where the Z80 driver starts");
        p.check(IntroInserter.DL_OFF + IntroInserter.DL_TAM == intro.length,
                "the Z80 driver does not run to the end of the intro");
        p.assertNone();
    }

    @Test
    @DisplayName("the ROM does not grow: every piece fits in the game's own filler")
    void theRomDoesNotGrow() {
        Problems p = new Problems();
        p.check(out.length == juego.length, String.format(
                "the ROM grew from %d to %d bytes -- a piece spilled past the end",
                juego.length, out.length));
        p.assertNone();
    }

    /**
     * The boot chain, walked the way the console walks it: RESET vector -> our
     * stub -> the intro's entry point. The stub sits immediately behind the
     * code piece (they are placed as one block), which is what gives us the
     * address of the code piece to check the rest against.
     */
    @Test
    @DisplayName("the RESET vector leads through the stub into the intro")
    void theResetVectorLeadsIntoTheIntro() {
        Problems p = new Problems();
        int cerca = IntroInserter.readU32(out, 0x04);
        p.check(cerca != PC_JUEGO, "the RESET vector still points at the game's own entry");
        p.check(cerca > 0 && cerca + 4 < out.length && (cerca & 1) == 0, String.format(
                "the RESET vector 0x%06X is not an even address inside the ROM", cerca));
        p.bytesAt(out, cerca, new int[]{0x46, 0xFC, 0x27, 0x00},
                "the RESET vector does not land on the stub's move.w #0x2700,sr");

        int codigo = cerca - IntroInserter.COD_TAM;
        p.check(codigo >= 0 && (codigo & 1) == 0,
                "the code piece would sit at a negative or odd address");
        // the entry block ends with the jump into the intro
        int jmp = primerJmp(out, cerca, cerca + 0x100);
        p.check(jmp == codigo + (IntroInserter.ENTRADA - IntroInserter.COD_OFF), String.format(
                "the stub jumps to 0x%06X, but the intro's entry point landed at 0x%06X",
                jmp, codigo + (IntroInserter.ENTRADA - IntroInserter.COD_OFF)));
        p.assertNone();
    }

    /**
     * The code piece has to arrive in the game as the intro's own bytes, with
     * exactly eight edits: the five absolute addresses that had to be rebased,
     * and the three hooks. A ninth would mean an offset in the map is pointing
     * at the wrong instruction.
     */
    @Test
    @DisplayName("the code piece is the intro's, changed only where the map says")
    void theCodePieceIsTheIntros() {
        Problems p = new Problems();
        int codigo = IntroInserter.readU32(out, 0x04) - IntroInserter.COD_TAM;

        Set<Integer> permitido = new HashSet<Integer>();
        for (int[] r : IntroInserter.RELOCS) {
            if (r[0] >= IntroInserter.COD_OFF && r[0] < IntroInserter.COD_OFF + IntroInserter.COD_TAM) {
                for (int i = 0; i < 4; i++) permitido.add(r[0] + i);
            }
        }
        for (int i = 0; i < 6; i++) permitido.add(IntroInserter.SUBIR + i);   // jmp descomp
        for (int i = 0; i < 6; i++) permitido.add(IntroInserter.VSYNC + i);   // jmp vsync
        for (int i = 0; i < 4; i++) permitido.add(IntroInserter.BRA + i);     // bra comprobar

        List<String> raros = new ArrayList<String>();
        for (int i = 0; i < IntroInserter.COD_TAM; i++) {
            int off = IntroInserter.COD_OFF + i;
            if (out[codigo + i] != intro[off] && !permitido.contains(off) && raros.size() < 8) {
                raros.add(String.format("0x%05X", off));
            }
        }
        p.check(raros.isEmpty(), "the code piece differs from the intro's at "
                + raros + ", which the map does not account for");

        // the two hooks: jmp <somewhere in the stub>, and the routine they reach
        p.bytesAt(out, codigo + (IntroInserter.SUBIR - IntroInserter.COD_OFF),
                new int[]{0x4E, 0xF9}, "the frame blit was not turned into a jmp");
        int descomp = IntroInserter.readU32(out, codigo + (IntroInserter.SUBIR - IntroInserter.COD_OFF) + 2);
        p.bytesAt(out, descomp, new int[]{0x23, 0xFC, 0x40, 0x00, 0x00, 0x00, 0x00, 0xC0, 0x00, 0x04},
                "the blit hook does not reach the decompressor's VRAM write");

        p.bytesAt(out, codigo + (IntroInserter.VSYNC - IntroInserter.COD_OFF),
                new int[]{0x4E, 0xF9}, "the vblank wait was not turned into a jmp");
        int vsync = IntroInserter.readU32(out, codigo + (IntroInserter.VSYNC - IntroInserter.COD_OFF) + 2);
        p.bytesAt(out, vsync, new int[]{0x13, 0xFC, 0x00, 0x00, 0x00, 0xA1, 0x00, 0x03},
                "the vblank hook does not reach the pad read");

        // and the final loop, which must still be a bra.w, now aimed at our check
        int bra = codigo + (IntroInserter.BRA - IntroInserter.COD_OFF);
        p.bytesAt(out, bra, new int[]{0x60, 0x00}, "the final loop is no longer a bra.w");
        int comprobar = bra + 2 + (short) IntroInserter.readU16(out, bra + 2);
        p.bytesAt(out, comprobar, new int[]{0x52, 0x79, 0x00, 0xFF, 0x01, 0x00},
                "the final loop does not reach the frame counter");
        p.assertNone();
    }

    /**
     * Every piece the map cuts out has to be somewhere in the output: the 16
     * frames as their compressed selves, the samples and the driver verbatim
     * (the driver with its three addresses rebased, so it is checked by its
     * unrelocated head instead).
     */
    @Test
    @DisplayName("every piece the map cuts out is in the ROM")
    void everyPieceIsInTheRom() {
        Problems p = new Problems();
        for (int i = 0; i < IntroInserter.FR_N; i++) {
            byte[] cuadro = Arrays.copyOfRange(intro,
                    IntroInserter.FR_OFF + i * IntroInserter.FR_TAM,
                    IntroInserter.FR_OFF + (i + 1) * IntroInserter.FR_TAM);
            p.check(buscar(out, IntroInserter.rleCompress(cuadro)) >= 0,
                    "frame " + i + " is not in the ROM");
        }
        p.check(buscar(out, trozo(IntroInserter.PCM_OFF, IntroInserter.PCM_TAM)) >= 0,
                "the PCM sample is not in the ROM");
        p.check(buscar(out, trozo(IntroInserter.DL_OFF, 0x40)) >= 0,
                "the Z80 driver is not in the ROM");
        p.assertNone();
    }

    @Test
    @DisplayName("nothing is written outside the declared filler")
    void nothingIsWrittenOutsideTheDeclaredFiller() {
        Problems p = new Problems();
        List<int[]> permitido = new ArrayList<int[]>();
        permitido.addAll(Arrays.asList(IntroInserter.HUECOS));
        permitido.addAll(Arrays.asList(IntroInserter.LOGO_FREED));
        permitido.add(new int[]{0x04, 4});
        permitido.add(new int[]{0x18E, 2});
        permitido.add(new int[]{IntroInserter.LOGO_SLOT, 4});

        int inicio = -1;
        for (int i = 0; i <= juego.length; i++) {
            boolean fuera = i < juego.length && out[i] != juego[i] && !cubierto(permitido, i);
            if (fuera && inicio < 0) inicio = i;
            if (!fuera && inicio >= 0) {
                p.check(false, String.format(
                        "0x%06X..0x%06X was changed but lies outside every declared free region",
                        inicio, i - 1));
                inicio = -1;
            }
        }
        p.assertNone();
    }

    // ---- helpers -----------------------------------------------------------

    private static byte[] trozo(int at, int len) {
        return Arrays.copyOfRange(intro, at, at + len);
    }

    private static boolean cubierto(List<int[]> regiones, int at) {
        for (int[] r : regiones) {
            if (at >= r[0] && at < r[0] + r[1]) return true;
        }
        return false;
    }

    /** The address the first "jmp (abs).l" in a range holds. */
    private static int primerJmp(byte[] rom, int desde, int hasta) {
        for (int i = desde; i + 6 <= Math.min(hasta, rom.length); i += 2) {
            if (IntroInserter.readU16(rom, i) == 0x4EF9) return IntroInserter.readU32(rom, i + 2);
        }
        return -1;
    }

    private static int buscar(byte[] donde, byte[] que) {
        outer:
        for (int i = 0; i + que.length <= donde.length; i++) {
            for (int j = 0; j < que.length; j++) {
                if (donde[i + j] != que[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
