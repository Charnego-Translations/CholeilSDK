package net.krusher.graphics;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class GraphicsInsertionSafetyTest {
    @Test void skippedEditCannotProduceSuccessfulMixedRom(@TempDir Path dir) throws Exception {
        byte[] rom=new byte[0x200000];new Random(8).nextBytes(rom);
        byte[] old=LzToshio.compress(new byte[128]);System.arraycopy(old,0,rom,0x800,old.length);
        Path input=dir.resolve("in.md"),out=dir.resolve("out.md"),registry=dir.resolve("gfx.txt");
        Files.write(input,rom);Files.write(out,new byte[]{1,2,3});
        Files.writeString(registry,"0x800,"+old.length+",128\n");
        byte[] edited=new byte[128];new Random(2).nextBytes(edited);
        TileRenderer.writePng(TileRenderer.renderTileSheet(edited,TileRenderer.defaultGrayscalePalette(),16,1),dir.resolve("gfx_000800.png").toString());
        assertThrows(IllegalStateException.class,()->GraphicsInserter.insert(input.toString(),dir.toString(),registry.toString(),out.toString()));
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(out));
    }
    @Test void relocationCannotClaimLiveTableDisguisedAsFiller(@TempDir Path dir) throws Exception {
        byte[] rom=new byte[0x200000];new Random(8).nextBytes(rom);
        Arrays.fill(rom,0x3CE78,0x40000,(byte)0xFF);
        Arrays.fill(rom,0xACF08,0xB6000,(byte)0xFF);
        byte[] old=LzToshio.compress(new byte[128]);System.arraycopy(old,0,rom,0x135322,old.length);
        AppleGraphicsTest.put32(rom,0x12002C,0x135322-0x120000);
        byte[] edited=new byte[128];new Random(2).nextBytes(edited);
        Path png=dir.resolve("edit.png");
        TileRenderer.writePng(TileRenderer.renderTileSheet(edited,TileRenderer.defaultGrayscalePalette(),16,1),png.toString());
        var ctx=new GraphicsInserter.Context(rom,null);
        assertEquals(GraphicsInserter.Outcome.RELOCATED,GraphicsInserter.processBlock(ctx,new GraphicsInserter.Block(0x135322,old.length,128),png.toString(),TileRenderer.defaultGrayscalePalette(),16));
        assertEquals(0xACF08,AppleGraphics.resolveBlock(rom,0x120000,0x12002C));
        for(int i=0x3CE78;i<0x3CE80;i++)assertEquals((byte)0xFF,rom[i]);
    }
}
