package net.krusher;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Streamlined entry point: run the whole extraction or the whole insertion
 * pipeline with one letter and no further arguments, using the default
 * filenames every tool in this project already agrees on.
 *
 * Usage:
 *   x   extract everything: script.txt, pointers.txt, graphics_offsets.txt,
 *       gfx_out/, raw_gfx_out/, sprite_gfx_out/, stray_text.txt (prune
 *       stray_text.txt by hand before `i`)
 *   i   insert everything: free_space.txt (rescanned), Choleil.md (dialogue
 *       from script.txt, then whatever's left in stray_text.txt, then
 *       compressed graphics from gfx_out/, then raw graphics from
 *       raw_gfx_out/, then sprite-mosaic graphics from sprite_gfx_out/ --
 *       delete a PNG to leave that block untouched; edited PNGs must keep
 *       their original resolution), and the hero's default name
 *       (DefaultNameInserter) written over the empty-name branch, the
 *       Charnego Translations intro (IntroInserter) in front of the game,
 *       and Choleil.ips, the distributable patch
 */
public class CholeilSDK
{

    public static void main( String[] args ) throws IOException
    {
        if ( args.length < 1 || (!args[0].equals("x") && !args[0].equals("i")) )
        {
            System.out.println("usage:");
            System.out.println("  x   extract everything (text + graphics + stray text)");
            System.out.println("  i   insert everything (rebuild " + DefaultPaths.OUT_ROM + " from " + DefaultPaths.SCRIPT + " + " + DefaultPaths.STRAY_TEXT + ")");
            return;
        }

        try
        {
            run( args[0] );
        }
        catch ( Exception ex )
        {
            // Every step in the pipeline below either completes or throws --
            // none of them are allowed to silently skip writing their output
            // and let the next step carry on as if nothing happened. Stop the
            // whole run here instead of a raw stack trace.
            System.out.println();
            System.out.println("ABORTED: " + ex.getMessage());
            if (args[0].equals("i")) {
                System.out.println("Do not use Choleil.building.md: it is incomplete. "
                        + "Choleil.md is replaced only after final graphics verification.");
            }
            System.exit(1);
        }
    }

    private static void run( String mode ) throws IOException
    {
        if (mode.equals("i")) {
            System.out.println("=== arranging Iibis kart edits for compression ===");
            net.krusher.graphics.KartGraphics.sync( DefaultPaths.ROM,
                    net.krusher.graphics.KartGraphics.DEFAULT_EDIT,
                    net.krusher.graphics.KartGraphics.DEFAULT_GFX );
        }
        if ( mode.equals("x") )
        {
            System.out.println("=== extracting text ===");
            TextExtractor.run( DefaultPaths.ROM, DefaultPaths.TBL, DefaultPaths.SCRIPT, DefaultPaths.POINTERS );

            System.out.println();
            System.out.println("=== scanning for compressed graphics ===");
            net.krusher.graphics.GraphicsExtractor.scan( DefaultPaths.ROM, DefaultPaths.GRAPHICS_OFFSETS );

            System.out.println();
            System.out.println("=== extracting graphics ===");
            net.krusher.graphics.GraphicsExtractor.extract( DefaultPaths.ROM, DefaultPaths.GRAPHICS_OFFSETS, DefaultPaths.GFX_OUT );

            System.out.println();
            System.out.println("=== extracting pause-menu icons ===");
            net.krusher.graphics.PauseMenuGraphics.extract(
                    DefaultPaths.ROM,
                    net.krusher.graphics.PauseMenuGraphics.DEFAULT_EDIT,
                    net.krusher.graphics.PauseMenuGraphics.DEFAULT_VIEW );

            System.out.println();
            System.out.println("=== extracting ending Fin. graphic ===");
            net.krusher.graphics.FinGraphics.extract(
                    DefaultPaths.ROM,
                    net.krusher.graphics.FinGraphics.DEFAULT_EDIT,
                    net.krusher.graphics.FinGraphics.DEFAULT_VIEW );

            System.out.println();
            System.out.println("=== extracting Corona sword swing ===");
            net.krusher.graphics.CoronaSwordGraphics.extract(
                    DefaultPaths.ROM,
                    net.krusher.graphics.CoronaSwordGraphics.DEFAULT_EDIT,
                    net.krusher.graphics.CoronaSwordGraphics.DEFAULT_VIEW );

            System.out.println();
            System.out.println("=== extracting Sonic + hammock animation ===");
            net.krusher.graphics.SonicHammockGraphics.extract(
                    DefaultPaths.ROM,
                    net.krusher.graphics.SonicHammockGraphics.DEFAULT_EDIT,
                    net.krusher.graphics.SonicHammockGraphics.DEFAULT_VIEW );

            System.out.println();
            System.out.println("=== extracting raw (uncompressed) graphics ===");
            net.krusher.graphics.RawGraphicsExtractor.main( new String[] { DefaultPaths.ROM, DefaultPaths.RAW_GRAPHICS, DefaultPaths.RAW_GFX_OUT } );

            System.out.println();
            System.out.println("=== extracting sprite-mosaic graphics ===");
            net.krusher.graphics.SpriteGraphicsExtractor.main( new String[] { DefaultPaths.ROM, DefaultPaths.SPRITE_GRAPHICS, DefaultPaths.SPRITE_GFX_OUT } );

            System.out.println();
            System.out.println("=== extracting Iibis kart orientations and animation ===");
            net.krusher.graphics.KartGraphics.extract( DefaultPaths.ROM,
                    net.krusher.graphics.KartGraphics.DEFAULT_EDIT,
                    net.krusher.graphics.KartGraphics.DEFAULT_VIEW );

            System.out.println();
            System.out.println("=== extracting the dialogue font ===");
            net.krusher.graphics.FontExtractor.run( DefaultPaths.ROM, DefaultPaths.FONT );

            System.out.println();
            System.out.println("=== scanning for stray text ===");
            StrayTextScanner.main( new String[] { DefaultPaths.ROM, DefaultPaths.SCRIPT, DefaultPaths.GRAPHICS_OFFSETS, DefaultPaths.STRAY_TEXT } );
        }
        else
        {
            // Never expose a partially rebuilt ROM under the normal playable filename.
            String buildingRom = "Choleil.building.md";
            System.out.println("=== scanning for free space ===");
            FreeSpaceScanner.main( new String[] { DefaultPaths.ROM, "1d8000", DefaultPaths.FREE_SPACE, DefaultPaths.GRAPHICS_OFFSETS } );

            System.out.println();
            System.out.println("=== inserting credits (pointer-relocatable) ===");
            CreditsInserter.run( DefaultPaths.ROM, DefaultPaths.STRAY_TEXT, DefaultPaths.TBL, DefaultPaths.FREE_SPACE, DefaultPaths.CREDITS_POINTERS, buildingRom );

            System.out.println();
            System.out.println("=== inserting text ===");
            TextInserter.run( buildingRom, DefaultPaths.SCRIPT, DefaultPaths.TBL, DefaultPaths.FREE_SPACE, buildingRom );

            System.out.println();
            System.out.println("=== fixing map balloon widths ===");
            MapBalloonInserter.run( buildingRom, DefaultPaths.TBL, buildingRom );

            System.out.println();
            System.out.println("=== inserting stray text ===");
            StrayTextInserter.main( new String[] { buildingRom, DefaultPaths.STRAY_TEXT, DefaultPaths.TBL, buildingRom, DefaultPaths.CREDITS_POINTERS } );

            System.out.println();
            System.out.println("=== arranging pause-menu icon edits for compression ===");
            net.krusher.graphics.PauseMenuGraphics.sync(
                    DefaultPaths.ROM,
                    net.krusher.graphics.PauseMenuGraphics.DEFAULT_EDIT,
                    net.krusher.graphics.PauseMenuGraphics.DEFAULT_GFX );

            System.out.println();
            System.out.println("=== arranging Sonic + hammock edit for compression ===");
            net.krusher.graphics.SonicHammockGraphics.syncScene( DefaultPaths.ROM );

            System.out.println();
            System.out.println("=== arranging ending Fin. edit for compression ===");
            net.krusher.graphics.FinGraphics.sync(
                    DefaultPaths.ROM,
                    net.krusher.graphics.FinGraphics.DEFAULT_EDIT,
                    net.krusher.graphics.FinGraphics.DEFAULT_GFX );

            System.out.println();
            System.out.println("=== recompressing and inserting graphics ===");
            List<int[]> occupiedGraphics = net.krusher.graphics.GraphicsInserter.insert(
                    buildingRom, DefaultPaths.GFX_OUT, DefaultPaths.GRAPHICS_OFFSETS, buildingRom );

            System.out.println();
            System.out.println("=== centring expanded ending Fin. graphic ===");
            net.krusher.graphics.FinGraphics.patchLayout( buildingRom );

            System.out.println();
            System.out.println("=== placing Jesus Gil from sonic_scene_positions.txt ===");
            net.krusher.graphics.SonicHammockGraphics.patchPosition( buildingRom );

            System.out.println();
            System.out.println("=== inserting raw (uncompressed) graphics ===");
            net.krusher.graphics.RawGraphicsInserter.main( new String[] { buildingRom, DefaultPaths.RAW_GFX_OUT, DefaultPaths.RAW_GRAPHICS, buildingRom } );

            System.out.println();
            System.out.println("=== inserting sprite-mosaic graphics ===");
            net.krusher.graphics.SpriteGraphicsInserter.main( new String[] { buildingRom, DefaultPaths.SPRITE_GFX_OUT, DefaultPaths.SPRITE_GRAPHICS, buildingRom } );

            System.out.println();
            System.out.println("=== inserting Corona sword swing ===");
            net.krusher.graphics.CoronaSwordGraphics.insert(
                    buildingRom,
                    net.krusher.graphics.CoronaSwordGraphics.DEFAULT_EDIT,
                    buildingRom );

            System.out.println();
            System.out.println("=== setting the default hero name ===");
            DefaultNameInserter.run( buildingRom, DefaultPaths.TBL, buildingRom );

            System.out.println();
            System.out.println("=== inserting the dialogue font ===");
            net.krusher.graphics.FontInserter.run( buildingRom, DefaultPaths.FONT, buildingRom );

            System.out.println();
            System.out.println("=== inserting the intro ===");
            IntroInserter.run( buildingRom, DefaultPaths.INTRO, DefaultPaths.ROM,
                    DefaultPaths.FREE_SPACE, buildingRom, occupiedGraphics );

            System.out.println();
            System.out.println("=== inserting two-frame side-character animation ===");
            net.krusher.graphics.SonicSideAnimation.insert( buildingRom, DefaultPaths.ROM );

            System.out.println();
            System.out.println("=== placing SonicGil conversation detection ===");
            net.krusher.graphics.SonicTalkDetection.insert( buildingRom, DefaultPaths.ROM );

            System.out.println();
            System.out.println("=== matching side panels to SonicGil story presence ===");
            net.krusher.graphics.SonicScenePresence.insert( buildingRom, DefaultPaths.ROM );

            System.out.println();
            // The shadow step only writes its guarded hook/reservation, never the kart.
            net.krusher.graphics.KartGraphics.verifyAvailable(buildingRom);
            System.out.println("=== matching SonicGil shadow to the configured position ===");
            net.krusher.graphics.SonicShadowPosition.insert( buildingRom, DefaultPaths.ROM );

            System.out.println();
            publishRom(Path.of(buildingRom), Path.of(DefaultPaths.OUT_ROM));

            System.out.println();
            System.out.println("=== writing the IPS patch ===");
            IpsWriter.run( DefaultPaths.ROM, DefaultPaths.OUT_ROM, DefaultPaths.PATCH );
        }
    }

    static void publishRom(Path completed, Path output) throws IOException {
        try {
            Files.move(completed, output, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(completed, output, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("Verified ROM ready: " + output);
    }
}
