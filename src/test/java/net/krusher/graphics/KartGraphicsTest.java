package net.krusher.graphics;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class KartGraphicsTest {
    private static byte[] original, tiles;
    private static int[] palette;

    @BeforeAll static void load() throws Exception {
        Path path=Path.of("Soleil (Spain).md");
        Assumptions.assumeTrue(Files.exists(path), "original ROM required");
        original=Files.readAllBytes(path);
        tiles=LzToshio.decompress(original,0x67F58);
        palette=KartGraphics.editPalette(original);
    }

    private static Path rom(Path dir) throws Exception {
        return Files.write(dir.resolve("rom.md"),original);
    }
    private static Path editor(Path dir,byte[] bytes) throws Exception {
        Path path=dir.resolve("edit.png");
        Png.write(KartGraphics.render(bytes,palette,1),path.toString());
        return path;
    }

    @Test void extractionHasExactShapePaletteAndTileOrder(@TempDir Path dir) throws Exception {
        Path input=rom(dir),edit=dir.resolve("edit.png"),view=dir.resolve("view.png");
        KartGraphics.extract(input.toString(),edit.toString(),view.toString());
        Bitmap image=Png.read(edit.toString());
        assertEquals(298,image.getWidth());assertEquals(67,image.getHeight());
        assertEquals(1192,Png.read(view.toString()).getWidth());
        assertArrayEquals(tiles,KartGraphics.decode(image,palette));
        // Independent hardware-layout oracle, not just renderer/inverse cancellation.
        for(int f=0;f<18;f++)for(int t=0;t<16;t++)for(int row=0;row<8;row++) {
            int packed=tiles[f*512+t*32+row*4]&255;
            int x=1+(f%9)*33+(t/4)*8, y=1+(f/9)*33+(t%4)*8+row;
            assertEquals(palette[packed>>>4],image.getRgb(x,y));
            assertEquals(palette[packed&15],image.getRgb(x+1,y));
        }
        assertEquals(0xFFFF00FF,palette[0]);
    }

    @Test void all32MappingsCoverOnlyThe18StoredFrames() {
        KartGraphics.validateMapping(original);
        Set<Integer> starts=new HashSet<>();
        for(int i=0;i<32;i++) {
            int at=0x20D1A+i*4+2;
            int attr=(original[at]&255)<<8 | original[at+1]&255;
            starts.add(attr&0x7FF);
            assertEquals(i%16>8,(attr&0x800)!=0);
        }
        assertEquals(18,starts.size());assertTrue(starts.contains(272));
    }

    @Test void editsReachEveryFrameAndGridNeverBecomesRomData() {
        Bitmap image=KartGraphics.render(tiles,palette,1);
        byte[] expected=tiles.clone();
        for(int f=0;f<18;f++) {
            image.setIndex(1+(f%9)*33,1+(f/9)*33,15);
            image.setIndex(32+(f%9)*33,32+(f/9)*33,13);
            expected[f*512]=(byte)((expected[f*512]&15)|0xF0);
            expected[f*512+511]=(byte)((expected[f*512+511]&0xF0)|13);
        }
        for(int x=0;x<298;x++)image.setIndex(x,33,7);
        for(int y=0;y<67;y++)image.setIndex(33,y,2);
        assertArrayEquals(expected,KartGraphics.decode(image,palette));
    }

    @Test void missingEditorLeavesGfxUntouched(@TempDir Path dir) throws Exception {
        Path gfx=Files.write(dir.resolve("gfx.png"),new byte[]{1,2,3});
        KartGraphics.sync("does-not-exist.md",dir.resolve("absent.png").toString(),gfx.toString());
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(gfx));
    }

    @Test void unchangedEditorKeepsGfxFileByteIdentical(@TempDir Path dir) throws Exception {
        Path input=rom(dir),edit=editor(dir,tiles),gfx=dir.resolve("gfx.png");
        Png.write(TileRenderer.renderTileSheet(tiles,TileRenderer.defaultGrayscalePalette(),16,1),gfx.toString());
        byte[] before=Files.readAllBytes(gfx);
        KartGraphics.sync(input.toString(),edit.toString(),gfx.toString());
        assertArrayEquals(before,Files.readAllBytes(gfx));
    }

    @Test void changedEditorSyncsAll288Tiles(@TempDir Path dir) throws Exception {
        byte[] edited=tiles.clone();new Random(22).nextBytes(edited);
        Path input=rom(dir),edit=editor(dir,edited),gfx=dir.resolve("gfx.png");
        KartGraphics.sync(input.toString(),edit.toString(),gfx.toString());
        assertArrayEquals(edited,TileRenderer.decodeTileSheet(Png.read(gfx.toString()),
                TileRenderer.defaultGrayscalePalette(),16,1,288));
        assertArrayEquals(original,Files.readAllBytes(input));
    }

    @Test void wrongSizeFailsBeforeTouchingTheGfx(@TempDir Path dir) throws Exception {
        Path input=rom(dir),edit=dir.resolve("edit.png"),gfx=Files.write(dir.resolve("gfx.png"),new byte[]{4,5});
        Png.write(Bitmap.indexed(288,64,palette),edit.toString());
        assertThrows(IllegalStateException.class,()->KartGraphics.sync(input.toString(),edit.toString(),gfx.toString()));
        assertArrayEquals(new byte[]{4,5},Files.readAllBytes(gfx));
    }

    @Test void offPaletteInkIsRejectedButTransparentPixelsAreIndexZero() {
        Bitmap source=KartGraphics.render(tiles,palette,1);
        int[] pixels=new int[298*67];
        for(int y=0;y<67;y++)for(int x=0;x<298;x++)pixels[y*298+x]=source.getRgb(x,y);
        pixels[299]=0xFF123456;
        assertThrows(IllegalStateException.class,()->KartGraphics.decode(Bitmap.trueColor(298,67,pixels),palette));
        pixels[299]=0x00123456;
        assertEquals(0,KartGraphics.decode(Bitmap.trueColor(298,67,pixels),palette)[0]&0xF0);
        pixels[299]=0x80123456;
        assertThrows(IllegalStateException.class,()->KartGraphics.decode(Bitmap.trueColor(298,67,pixels),palette));
    }

    @Test void invalidPointerMappingOrCompressedLengthAreRejected() {
        byte[] broken=original.clone();put32(broken,0x590DC,0x7FFFFFFF);
        assertThrows(IllegalStateException.class,()->KartGraphics.readTiles(broken));
        byte[] mapping=original.clone();mapping[0x20D1A]=0;
        assertThrows(IllegalStateException.class,()->KartGraphics.readTiles(mapping));
        byte[] shortBlock=original.clone();byte[] packed=LzToshio.compress(new byte[128]);
        System.arraycopy(packed,0,shortBlock,0x67F58,packed.length);
        assertThrows(IllegalStateException.class,()->KartGraphics.readTiles(shortBlock));
    }

    @Test void largerEditRelocatesAndVerifierFollowsTheNewPointer(@TempDir Path dir) throws Exception {
        byte[] edited=new byte[9216];new Random(71).nextBytes(edited);
        Path edit=editor(dir,edited),gfx=dir.resolve("gfx.png"),output=dir.resolve("patched.md");
        Png.write(TileRenderer.renderTileSheet(edited,TileRenderer.defaultGrayscalePalette(),16,1),gfx.toString());
        byte[] data=original.clone();
        var ctx=new GraphicsInserter.Context(data,"graphics_offsets.txt");
        assertEquals(GraphicsInserter.Outcome.RELOCATED,GraphicsInserter.processBlock(ctx,
                new GraphicsInserter.Block(0x67F58,5729,9216),gfx.toString(),TileRenderer.defaultGrayscalePalette(),16));
        assertNotEquals(0x67F58,KartGraphics.resolveBlock(data));
        assertEquals(1,ctx.usedThisRun.size());
        assertArrayEquals(edited,KartGraphics.readTiles(data));
        assertArrayEquals(tiles,LzToshio.decompress(data,0x67F58));
        assertArrayEquals(Arrays.copyOfRange(original,0x548,0x568),Arrays.copyOfRange(data,0x548,0x568));
        assertArrayEquals(Arrays.copyOfRange(original,0x20D1A,0x20D9A),Arrays.copyOfRange(data,0x20D1A,0x20D9A));
        Files.write(output,data);KartGraphics.verify(output.toString(),edit.toString());
        put32(data,0x590DC,0x67F58-0x59000);Files.write(output,data);
        assertThrows(IllegalStateException.class,()->KartGraphics.verify(output.toString(),edit.toString()));
    }

    @Test void cannotSilentlySkipAnUnplaceableCompressedEdit(@TempDir Path dir) throws Exception {
        byte[] data=new byte[0x200000];new Random(8).nextBytes(data);
        byte[] compressed=LzToshio.compress(new byte[128]);System.arraycopy(compressed,0,data,0x800,compressed.length);
        Path input=Files.write(dir.resolve("in.md"),data),out=Files.write(dir.resolve("out.md"),new byte[]{7,8,9});
        Path registry=Files.writeString(dir.resolve("gfx.txt"),"0x800,"+compressed.length+",128\n");
        byte[] edited=new byte[128];new Random(2).nextBytes(edited);
        Png.write(TileRenderer.renderTileSheet(edited,TileRenderer.defaultGrayscalePalette(),16,1),dir.resolve("gfx_000800.png").toString());
        assertThrows(IllegalStateException.class,()->GraphicsInserter.insert(input.toString(),dir.toString(),registry.toString(),out.toString()));
        assertArrayEquals(new byte[]{7,8,9},Files.readAllBytes(out));
    }

    static void put32(byte[] data,int at,int value) {
        for(int i=0;i<4;i++)data[at+i]=(byte)(value>>>(24-i*8));
    }
}
