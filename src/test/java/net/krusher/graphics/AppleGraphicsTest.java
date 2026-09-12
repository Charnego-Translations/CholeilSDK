package net.krusher.graphics;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class AppleGraphicsTest {
    static byte[] base;
    @BeforeAll static void load() throws Exception {
        Path path=Path.of(net.krusher.DefaultPaths.ROM);
        Assumptions.assumeTrue(Files.exists(path));
        base=Files.readAllBytes(path);
    }
    static void put32(byte[] rom,int at,int n) {
        for(int i=0;i<4;i++)rom[at+i]=(byte)(n>>>(24-i*8));
    }
    @Test void originalPointersResolve() {
        assertEquals(0x135322,AppleGraphics.resolveBlock(base,0x120000,0x12002C));
        assertEquals(0x14D3AE,AppleGraphics.resolveBlock(base,0x120000,0x120058));
        assertEquals(0xA5644,AppleGraphics.resolveBlock(base,0x59000,0x5938C));
    }
    @Test void invalidLivePointersFail() {
        byte[] rom=base.clone();put32(rom,0x12002C,Integer.MAX_VALUE);
        assertThrows(IllegalStateException.class,()->AppleGraphics.resolveBlock(rom,0x120000,0x12002C));
    }
    @Test void verifierUsesRelocatedCopiesNotStaleOriginals(@TempDir Path dir) throws Exception {
        Path original=dir.resolve("original.md"),edit=dir.resolve("red.png"),view=dir.resolve("view.png");
        Files.write(original,base);
        AppleGraphics.extractRed(original.toString(),edit.toString(),view.toString());
        byte[] rom=base.clone();
        int[][] copies={{0x135322,0x12002C,0xACF08},{0x14D3AE,0x120058,0xB5000}};
        for(int[] copy:copies) {
            byte[] packed=LzToshio.compress(LzToshio.decompress(base,copy[0]));
            System.arraycopy(packed,0,rom,copy[2],packed.length);
            put32(rom,copy[1],copy[2]-0x120000);
            Arrays.fill(rom,copy[0],copy[0]+8,(byte)0); // stale addresses deliberately invalid
        }
        Path built=dir.resolve("built.md");Files.write(built,rom);
        AppleGraphics.verify(built.toString(),edit.toString(),edit.toString());
        put32(rom,0x120058,0x14D3AE-0x120000);Files.write(built,rom);
        assertThrows(RuntimeException.class,()->AppleGraphics.verify(built.toString(),edit.toString(),edit.toString()));
    }
    @Test void goldenVerifierUsesLivePointer(@TempDir Path dir) throws Exception {
        Path original=dir.resolve("original.md"),edit=dir.resolve("gold.png"),view=dir.resolve("view.png");
        Files.write(original,base);
        AppleGraphics.extractGolden(original.toString(),edit.toString(),view.toString());
        byte[] rom=base.clone(),packed=LzToshio.compress(LzToshio.decompress(base,0xA5644));
        System.arraycopy(packed,0,rom,0xE5000,packed.length);put32(rom,0x5938C,0xE5000-0x59000);
        Arrays.fill(rom,0xA5644,0xA564C,(byte)0);
        Path built=dir.resolve("built.md");Files.write(built,rom);
        AppleGraphics.verifyGolden(built.toString(),edit.toString());
    }
    @Test void distinctRedAndGreenEditorsRemainIndependent(@TempDir Path dir) throws Exception {
        Path original=dir.resolve("original.md"),red=dir.resolve("red.png"),green=dir.resolve("green.png"),view=dir.resolve("view.png");
        Files.write(original,base);
        AppleGraphics.extractRed(original.toString(),red.toString(),view.toString());
        AppleGraphics.extractGreen(original.toString(),green.toString(),view.toString());
        int[] palette=TileRenderer.readGenesisPalette(base,0x548);palette[0]=0xFFFF00FF;
        byte[] column=TileRenderer.decodeSpriteSheet(TileRenderer.readPng(green.toString()),palette,2,2,1,1,4,false);
        byte[] rom=base.clone();System.arraycopy(column,0,rom,0xF4700,128);System.arraycopy(column,0,rom,0xF4780,128);
        Path built=dir.resolve("built.md");Files.write(built,rom);
        AppleGraphics.verify(built.toString(),red.toString(),green.toString());
        assertThrows(IllegalStateException.class,()->AppleGraphics.verify(built.toString(),green.toString(),red.toString()));
    }
}
