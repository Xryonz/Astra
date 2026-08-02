import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import javax.imageio.ImageIO;

// Gera as DUAS fotos do bot (Sparkle nos dias uteis, Sparxie no fim de semana) e
// imprime cada uma como data-URI, pronta pra colar no botAvatars.ts.
//
// POR QUE UM GERADOR, e nao dois PNGs jogados numa pasta: o avatar do bot mora no
// BANCO como data-URI (mesmo caminho dos avatares de usuario), nao num arquivo que
// o app abre. Sem o gerador, "mudar o tom do amarelo" viraria abrir editor de
// imagem, exportar, converter pra base64 na mao e torcer. Aqui e mudar uma cor e
// rodar de novo.
//
// Rodar:  java tools/GerarAvatarBot.java
// Sai:    tools/bot-sparkle.png, tools/bot-sparxie.png + os data-URIs no terminal.
public class GerarAvatarBot {

    static final int TAM = 128;

    public static void main(String[] args) throws Exception {
        // Sparkle (seg-sex): prata fria, sobria — e o plantao da rotina.
        gerar("sparkle",
                new Color(0x0F0F24), new Color(0x06060E),
                new Color(0xF2F4F8), new Color(0x9AA8C4),
                false);
        // Sparxie (sab-dom): ambar quente. Mesmo desenho, temperatura outra — e o
        // mesmo rosto de turno trocado, nao outra personagem.
        gerar("sparxie",
                new Color(0x1A1206), new Color(0x0B0703),
                new Color(0xFFE3B0), new Color(0xC9924E),
                true);
    }

    static void gerar(String nome, Color fundoCentro, Color fundoBorda,
                      Color brilho, Color sombra, boolean companheira) throws Exception {
        BufferedImage img = new BufferedImage(TAM, TAM, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        float c = TAM / 2f;

        // Disco de fundo: radial do centro pra borda. Recortado em circulo porque o
        // avatar aparece redondo em todo lugar — quadrado sobraria canto claro.
        g.setPaint(new RadialGradientPaint(
                new Point2D.Float(c, c * 0.82f), c,
                new float[]{0f, 1f},
                new Color[]{fundoCentro, fundoBorda}));
        g.fill(new Ellipse2D.Float(0, 0, TAM, TAM));

        // Halo: o brilho da estrela nao para na silhueta, vaza no fundo. E o que
        // separa "icone colado" de "algo que emite luz".
        g.setPaint(new RadialGradientPaint(
                new Point2D.Float(c, c), c * 0.92f,
                new float[]{0f, 0.55f, 1f},
                new Color[]{
                        new Color(brilho.getRed(), brilho.getGreen(), brilho.getBlue(), 46),
                        new Color(brilho.getRed(), brilho.getGreen(), brilho.getBlue(), 14),
                        new Color(brilho.getRed(), brilho.getGreen(), brilho.getBlue(), 0)}));
        g.fill(new Ellipse2D.Float(0, 0, TAM, TAM));

        // Estrela principal, levemente acima do centro optico.
        g.setPaint(new GradientPaint(c * 0.5f, c * 0.4f, brilho, c * 1.5f, c * 1.6f, sombra));
        g.fill(estrela(c, c * 0.96f, TAM * 0.34f));

        // Fim de semana ganha uma faisca menor de acompanhamento: mesma familia de
        // forma, so que solta. E o unico sinal de que o turno mudou.
        if (companheira) {
            g.setPaint(new Color(brilho.getRed(), brilho.getGreen(), brilho.getBlue(), 205));
            g.fill(estrela(c * 1.52f, c * 0.52f, TAM * 0.11f));
        }

        // Aro fino: assenta o avatar contra qualquer fundo da lista de mensagens.
        g.setStroke(new BasicStroke(1.5f));
        g.setPaint(new Color(brilho.getRed(), brilho.getGreen(), brilho.getBlue(), 34));
        g.draw(new Ellipse2D.Float(0.75f, 0.75f, TAM - 1.5f, TAM - 1.5f));
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        byte[] png = out.toByteArray();
        Files.write(new File("tools/bot-" + nome + ".png").toPath(), png);

        String uri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
        System.out.println("=== " + nome + " === " + png.length + " bytes png, "
                + uri.length() + " chars data-uri");
        System.out.println(uri);
        System.out.println();
    }

    // Faisca de 4 pontas: as pontas sao os vertices e o centro e o ponto de
    // controle das quadraticas, o que curva os lados PRA DENTRO. Poligono comum
    // daria uma cruz dura; a concavidade e o que faz virar brilho.
    static Shape estrela(float cx, float cy, float r) {
        Path2D.Float p = new Path2D.Float();
        p.moveTo(cx, cy - r);
        p.quadTo(cx, cy, cx + r, cy);
        p.quadTo(cx, cy, cx, cy + r);
        p.quadTo(cx, cy, cx - r, cy);
        p.quadTo(cx, cy, cx, cy - r);
        p.closePath();
        return p;
    }
}
