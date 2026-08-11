package net.krusher;

import java.io.IOException;

/**
 * Streamlined entry point: run the whole extraction or the whole insertion
 * pipeline with one letter and no further arguments, using the default
 * filenames every tool in this project already agrees on.
 *
 * Usage:
 *   x   extract everything: script.txt, pointers.txt, graphics_offsets.txt, gfx_out/
 *   i   insert everything: free_space.txt (rescanned), Choleil.md
 */
public class CholeilSDK
{
    static final String ROM = "Soleil (Spain).md";
    static final String TBL = "soleil.tbl";
    static final String SCRIPT = "script.txt";
    static final String POINTERS = "pointers.txt";
    static final String GRAPHICS_OFFSETS = "graphics_offsets.txt";
    static final String GFX_OUT = "gfx_out";
    static final String FREE_SPACE = "free_space.txt";
    static final String OUT_ROM = "Choleil.md";

    public static void main( String[] args ) throws IOException
    {
        if ( args.length < 1 || (!args[0].equals("x") && !args[0].equals("i")) )
        {
            System.out.println("usage:");
            System.out.println("  x   extract everything (text + graphics)");
            System.out.println("  i   insert everything (rebuild " + OUT_ROM + " from " + SCRIPT + ")");
            return;
        }

        if ( args[0].equals("x") )
        {
            System.out.println("=== extracting text ===");
            TextExtractor.run( ROM, TBL, SCRIPT, POINTERS );

            System.out.println();
            System.out.println("=== scanning for compressed graphics ===");
            net.krusher.graphics.GraphicsExtractor.scan( ROM, GRAPHICS_OFFSETS );

            System.out.println();
            System.out.println("=== extracting graphics ===");
            net.krusher.graphics.GraphicsExtractor.extract( ROM, GRAPHICS_OFFSETS, GFX_OUT );
        }
        else
        {
            System.out.println("=== scanning for free space ===");
            FreeSpaceScanner.main( new String[] { ROM, "1d8000", FREE_SPACE, GRAPHICS_OFFSETS } );

            System.out.println();
            System.out.println("=== inserting text ===");
            TextInserter.run( ROM, SCRIPT, TBL, FREE_SPACE, OUT_ROM );
        }
    }
}
