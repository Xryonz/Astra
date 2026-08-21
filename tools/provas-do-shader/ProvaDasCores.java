import java.nio.file.Files;
import org.jetbrains.skia.*;

// A CONVERSÃO DE COR DO SHADER, CONFERIDA COM NÚMERO.
//
// Monta um quadro NV12 sintético com cores conhecidas, passa pelo MESMO SkSL que o app
// usa, e lê os pixels que saem. É o único jeito de conferir três coisas que só falham
// visualmente — e que, falhando, mandam procurar defeito no decodificador:
//
//   1. a conta de coluna do plano de cor (`floor(x/2)*2`);
//   2. as dimensões da imagem de cor (largura CHEIA, altura pela metade);
//   3. a faixa: estúdio (16..235) contra cheia (0..255).
public class ProvaDasCores {

    static final int L = 8, A = 4; // pequeno de propósito: dá para conferir à mão
    static final int PASSO = 16;   // MAIOR que a largura, que é o caso que quebra

    public static void main(String[] args) throws Exception {
        String sksl = Files.readString(java.nio.file.Path.of(args[0]));

        // Quatro cores, uma por bloco 2x2 na horizontal, para cada par de linhas.
        int[][] cores = {
            {255, 0, 0}, {0, 255, 0}, {0, 0, 255}, {255, 255, 255},
        };

        byte[] nv12 = new byte[PASSO * A * 3 / 2];
        for (int y = 0; y < A; y++) {
            for (int x = 0; x < L; x++) {
                int[] c = cores[(x / 2) % cores.length];
                nv12[y * PASSO + x] = (byte) luma(c);
            }
        }
        for (int y = 0; y < A / 2; y++) {
            for (int x = 0; x < L / 2; x++) {
                int[] c = cores[x % cores.length];
                nv12[PASSO * A + y * PASSO + x * 2] = (byte) cb(c);
                nv12[PASSO * A + y * PASSO + x * 2 + 1] = (byte) cr(c);
            }
        }

        RuntimeEffect efeito = RuntimeEffect.Companion.makeForShader(sksl);
        RuntimeShaderBuilder b = new RuntimeShaderBuilder(efeito);

        Data dBrilho = Data.Companion.makeFromBytes(nv12, 0, PASSO * A);
        Data dCor = Data.Companion.makeFromBytes(nv12, PASSO * A, PASSO * (A / 2));
        Image brilho = Image.Companion.makeRaster(
            new ImageInfo(L, A, ColorType.ALPHA_8, ColorAlphaType.OPAQUE, null), dBrilho, PASSO);
        Image cor = Image.Companion.makeRaster(
            new ImageInfo(L, A / 2, ColorType.ALPHA_8, ColorAlphaType.OPAQUE, null), dCor, PASSO);

        FilterMipmap vizinho = new FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE);
        b.child("brilho", brilho.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, vizinho, null));
        b.child("cor", cor.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, vizinho, null));

        Surface s = Surface.Companion.makeRasterN32Premul(L, A);
        Paint tinta = new Paint();
        tinta.setShader(b.makeShader(null));
        s.getCanvas().drawRect(Rect.Companion.makeWH(L, A), tinta);

        Bitmap saida = new Bitmap();
        saida.allocPixels(new ImageInfo(L, A, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL, null));
        s.readPixels(saida, 0, 0);

        int erros = 0;
        System.out.println("  bloco       esperado          veio");
        for (int i = 0; i < 4; i++) {
            int x = i * 2;
            int px = saida.getColor(x, 1);
            int r = (px >> 16) & 0xFF, g = (px >> 8) & 0xFF, bl = px & 0xFF;
            int[] c = cores[i];
            int dr = Math.abs(r - c[0]), dg = Math.abs(g - c[1]), db = Math.abs(bl - c[2]);
            // TOLERÂNCIA DE 10: o caminho passa por arredondamento em três lugares (a
            // conta de luma, o byte, e a volta no shader). Fora disso não é
            // arredondamento — é a faixa errada ou o plano lido do lugar errado.
            boolean ok = dr <= 10 && dg <= 10 && db <= 10;
            if (!ok) erros++;
            System.out.printf("  x=%d  %3d,%3d,%3d   ->   %3d,%3d,%3d  %s%n",
                x, c[0], c[1], c[2], r, g, bl, ok ? "ok" : "ERRADO");
        }
        System.out.println(erros == 0 ? "CORES OK" : "CORES ERRADAS: " + erros + " de 4");
        if (erros != 0) System.exit(1);
    }

    // BT.709, faixa estúdio — o inverso exato do que o shader faz.
    static double yf(int[] c) {
        return 0.2126 * c[0] / 255.0 + 0.7152 * c[1] / 255.0 + 0.0722 * c[2] / 255.0;
    }
    static int luma(int[] c) { return (int) Math.round(16 + 219 * yf(c)); }
    static int cb(int[] c) { return (int) Math.round(128 + 224 * (c[2] / 255.0 - yf(c)) / 1.8556); }
    static int cr(int[] c) { return (int) Math.round(128 + 224 * (c[0] / 255.0 - yf(c)) / 1.5748); }
}
