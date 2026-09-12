package net.krusher.graphics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.krusher.DefaultPaths;

/** Iibis kart: 9 stored orientations x 2 frames, mirrored into 16 directions. */
public final class KartGraphics {
    public static final int BLOCK_OFFSET = 0x067F58;
    public static final int POINTER_FIELD = 0x0590DC;
    public static final int TABLE_BASE = 0x059000;
    public static final int MAPPING = 0x020D1A;
    public static final int PALETTE_OFFSET = 0x000548;
    public static final int FRAME_COUNT = 18;
    public static final int TILE_COUNT = 288;
    public static final int BYTE_COUNT = TILE_COUNT * 32;
    public static final int WIDTH = 298;
    public static final int HEIGHT = 67;
    public static final String DEFAULT_EDIT = "special_gfx_out/kart_EDITAME.png";
    public static final String DEFAULT_VIEW = "special_gfx_out/kart_x4_VISTA.png";
    public static final String DEFAULT_GFX = "gfx_out/gfx_067f58.png";

    private KartGraphics() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("KartGraphics extract [rom] [editPng] [viewPng]");
            System.out.println("KartGraphics sync [originalRom] [editPng] [gfxPng]");
            System.out.println("KartGraphics verify [patchedRom] [editPng]");
            return;
        }
        String rom = args.length > 1 ? args[1] : args[0].equals("verify")
                ? DefaultPaths.OUT_ROM : DefaultPaths.ROM;
        String edit = args.length > 2 ? args[2] : DEFAULT_EDIT;
        switch (args[0]) {
            case "extract" -> extract(rom, edit, args.length > 3 ? args[3] : DEFAULT_VIEW);
            case "sync" -> sync(rom, edit, args.length > 3 ? args[3] : DEFAULT_GFX);
            case "verify" -> verify(rom, edit);
            default -> throw new IllegalArgumentException("unknown mode: " + args[0]);
        }
    }

    public static void extract(String romPath, String editPath, String viewPath) throws IOException {
        byte[] rom = Files.readAllBytes(Path.of(romPath));
        byte[] tiles = readTiles(rom);
        int[] palette = editPalette(rom);
        Bitmap edit = render(tiles, palette, 1);
        write(edit, editPath);
        write(render(tiles, palette, 4), viewPath);
        System.out.println("Extracted kart: 18 boxed 32x32 drawings, 298x67 indexed editor.");
    }

    /** Adapter to the existing compressor; the friendly PNG owns this whole block. */
    public static void sync(String originalPath, String editPath, String gfxPath) throws IOException {
        if (!Files.exists(Path.of(editPath))) {
            System.out.println("Kart editor absent; leaving the ordinary graphics sheet untouched.");
            return;
        }
        byte[] rom = Files.readAllBytes(Path.of(originalPath));
        readTiles(rom); // Reject unsupported pointer/mapping layouts before writing anything.
        byte[] tiles = decode(Png.read(editPath), editPalette(rom));
        Integer paletteOffset = KnownPalettes.load(DefaultPaths.KNOWN_PALETTES).get(BLOCK_OFFSET);
        int[] fullPalette = paletteOffset == null ? TileRenderer.defaultGrayscalePalette()
                : TileRenderer.readGenesisPalette(rom, paletteOffset);
        Path gfx = Path.of(gfxPath);
        if (Files.exists(gfx)) {
            Bitmap current = Png.read(gfxPath);
            if (current.getWidth() == 128 && current.getHeight() == 144
                    && Arrays.equals(tiles, TileRenderer.decodeTileSheet(current, fullPalette, 16, 1, TILE_COUNT))) {
                System.out.println("Kart sheet unchanged: " + gfxPath);
                return;
            }
        }
        write(TileRenderer.renderTileSheet(tiles, fullPalette, 16, 1), gfxPath);
        System.out.println("Synced all 18 kart drawings into " + gfxPath);
    }

    public static void verifyAvailable(String romPath) throws IOException {
        if (Files.exists(Path.of(DEFAULT_EDIT))) verify(romPath, DEFAULT_EDIT);
    }

    /** Follows the LIVE relative pointer, including after compression relocation. */
    public static void verify(String romPath, String editPath) throws IOException {
        byte[] rom = Files.readAllBytes(Path.of(romPath));
        byte[] expected = decode(Png.read(editPath), editPalette(rom));
        if (!Arrays.equals(expected, readTiles(rom))) {
            throw new IllegalStateException("Kart in the ROM does not match its editor (live pointer checked)");
        }
        System.out.printf("Kart verified at 0x%06X: all 18 drawings / 32 direction-frame mappings.%n", resolveBlock(rom));
    }

    static int resolveBlock(byte[] rom) {
        if (rom.length < POINTER_FIELD + 4) throw new IllegalStateException("ROM too short for kart pointer");
        long relative = ((long)(rom[POINTER_FIELD]&255)<<24) | ((rom[POINTER_FIELD+1]&255)<<16)
                | ((rom[POINTER_FIELD+2]&255)<<8) | (rom[POINTER_FIELD+3]&255);
        long address = (TABLE_BASE + relative) & 0xFFFFFFFFL;
        if ((address & 1) != 0 || address < 0x200 || address + 8 > rom.length) {
            throw new IllegalStateException("Invalid live kart block pointer");
        }
        return (int)address;
    }

    static byte[] readTiles(byte[] rom) {
        validateMapping(rom);
        int address = resolveBlock(rom);
        LzToshio.Result block = LzToshio.tryDecompress(rom, address, BYTE_COUNT * 2, BYTE_COUNT);
        if (block == null || block.data.length != BYTE_COUNT) {
            throw new IllegalStateException("Kart block must decompress to exactly 9216 bytes");
        }
        return block.data;
    }

    static void validateMapping(byte[] rom) {
        if (rom.length < MAPPING + 128) throw new IllegalStateException("ROM too short for kart mapping");
        for (int i = 0; i < 32; i++) {
            int direction = i % 16;
            int tile = (i / 16) * 144 + (direction <= 8 ? direction : 16 - direction) * 16;
            int expected = tile | (direction > 8 ? 0x0800 : 0);
            if (word(rom, MAPPING+i*4) != 0x0F00 || word(rom, MAPPING+i*4+2) != expected) {
                throw new IllegalStateException("Unsupported kart direction mapping at entry " + i);
            }
        }
    }

    static int[] editPalette(byte[] rom) {
        if (rom.length < PALETTE_OFFSET + 32) throw new IllegalStateException("ROM too short for kart palette");
        int[] result = TileRenderer.readGenesisPalette(rom, PALETTE_OFFSET);
        result[0] = 0xFFFF00FF; // Editor-only transparency marker; NEVER written to CRAM.
        return result;
    }

    static Bitmap render(byte[] tiles, int[] palette, int scale) {
        if (tiles.length != BYTE_COUNT || scale < 1) throw new IllegalArgumentException("Invalid kart rendering size");
        Bitmap result = Bitmap.indexed(WIDTH*scale, HEIGHT*scale, palette);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int index = 14; // One-pixel guide in an EXISTING palette color.
            if (x%33 != 0 && y%33 != 0) {
                int frame = (y/33)*9+x/33, sx = x%33-1, sy = y%33-1;
                int at = frame*512 + (sx/8*4+sy/8)*32 + sy%8*4 + sx%8/2;
                int packed = tiles[at]&255;
                index = (sx&1)==0 ? packed>>>4 : packed&15;
            }
            for (int dy = 0; dy < scale; dy++) for (int dx = 0; dx < scale; dx++)
                result.setIndex(x*scale+dx, y*scale+dy, index);
        }
        return result;
    }

    /** Strip the guides and encode exact indices, without nearest-color guessing. */
    static byte[] decode(Bitmap image, int[] palette) {
        if (image.getWidth()!=WIDTH || image.getHeight()!=HEIGHT) {
            throw new IllegalStateException("Kart editor must stay 298x67 (18 boxed 32x32 drawings)");
        }
        byte[] result = new byte[BYTE_COUNT];
        for (int frame=0; frame<FRAME_COUNT; frame++) for (int y=0; y<32; y++) for (int x=0; x<32; x+=2) {
            int px=1+(frame%9)*33+x, py=1+(frame/9)*33+y;
            int left=index(image.getRgb(px,py),palette,px,py);
            int right=index(image.getRgb(px+1,py),palette,px+1,py);
            result[frame*512+(x/8*4+y/8)*32+y%8*4+x%8/2]=(byte)(left<<4|right);
        }
        return result;
    }

    private static int index(int argb, int[] palette, int x, int y) {
        if ((argb>>>24)==0) return 0;
        for (int i=0; i<16; i++) if (argb==palette[i]) return i;
        throw new IllegalStateException("Kart pixel ("+x+","+y+") is outside the original palette; disable antialiasing");
    }

    private static int word(byte[] data, int at) { return (data[at]&255)<<8 | data[at+1]&255; }
    private static void write(Bitmap image, String path) throws IOException {
        Path parent = Path.of(path).toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Png.write(image,path);
    }
}
