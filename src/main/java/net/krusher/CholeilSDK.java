package net.krusher;

import java.io.IOException;

public class CholeilSDK
{
    public static void main( String[] args ) throws IOException
    {
        String romPath = args.length > 0 ? args[0] : "Soleil (Spain).md";
        String tblPath = args.length > 1 ? args[1] : "soleil.tbl";
        String outPath = args.length > 2 ? args[2] : "script.txt";
        String ptrPath = args.length > 3 ? args[3] : "pointers.txt";

        TextExtractor.run( romPath, tblPath, outPath, ptrPath );
    }
}
