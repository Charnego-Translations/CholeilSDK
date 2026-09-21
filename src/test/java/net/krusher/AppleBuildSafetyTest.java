package net.krusher;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class AppleBuildSafetyTest {
    @Test void introHonoursPriorGraphicsAllocations(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Files.exists(Path.of(DefaultPaths.ROM))&&Files.exists(Path.of(DefaultPaths.INTRO)));
        byte[] original=Files.readAllBytes(Path.of(DefaultPaths.ROM)),rom=original.clone();
        int start=0xACF08,length=19500;
        byte[] graphic=new byte[length];new Random(21).nextBytes(graphic);
        System.arraycopy(graphic,0,rom,start,length);
        Path input=dir.resolve("input.md"),output=dir.resolve("output.md");Files.write(input,rom);
        IntroInserter.run(input.toString(),DefaultPaths.INTRO,DefaultPaths.ROM,null,output.toString(),List.of(new int[]{start,start+length}));
        byte[] built=Files.readAllBytes(output);
        assertArrayEquals(graphic,Arrays.copyOfRange(built,start,start+length));
        assertArrayEquals(Arrays.copyOfRange(original,0x3CE78,0x3CE80),Arrays.copyOfRange(built,0x3CE78,0x3CE80));
    }
    @Test void publishingFailureDoesNotClobberPreviousRom(@TempDir Path dir) throws Exception {
        Path out=dir.resolve("Choleil.md");Files.write(out,new byte[]{7,8,9});
        assertThrows(java.io.IOException.class,()->CholeilSDK.publishRom(dir.resolve("missing.md"),out));
        assertArrayEquals(new byte[]{7,8,9},Files.readAllBytes(out));
    }
    @Test void completedRomReplacesPreviousOutput(@TempDir Path dir) throws Exception {
        Path out=dir.resolve("Choleil.md"),ready=dir.resolve("building.md");
        Files.write(out,new byte[]{1});Files.write(ready,new byte[]{2});
        CholeilSDK.publishRom(ready,out);
        assertArrayEquals(new byte[]{2},Files.readAllBytes(out));assertFalse(Files.exists(ready));
    }
}
