import java.awt.*; import java.awt.image.BufferedImage; import java.io.File;
import java.util.*; import javax.imageio.ImageIO;

public class Gerar {
    static Map<Character,Color> pal = new HashMap<>();
    static {
        pal.put('.', new Color(0,0,0,0));
        pal.put('K', new Color(0x3A3A44));   // contorno (escuro do proprio pelo)
        pal.put('B', new Color(0x9AA0B0));   // pelo base
        pal.put('D', new Color(0x767C8C));   // pelo sombra
        pal.put('W', new Color(0xF0F0F4));   // peito / focinho
        pal.put('P', new Color(0xE8A6B4));   // rosa (orelha, nariz)
        pal.put('E', new Color(0x86D8A6));   // olho
    }

    static final String[] SENTADO = {
        "........................",
        "...KK..........KK.......",
        "..KPPK........KPPK......",
        "..KBPPK......KPPBK......",
        "..KBBPPKKKKKKPPBBK......",
        ".KBBBBBBBBBBBBBBBBK.....",
        ".KBBBBBBBBBBBBBBBBK.....",
        "KBBBBBBBBBBBBBBBBBBK....",
        "KBBBKEKBBBBKEKBBBBBK....",
        "KBBBKEKBBBBKEKBBBBBK....",
        "KBBBBBBBBBBBBBBBBBBK....",
        "KBBBBBBWWPPWWBBBBBBK....",
        ".KBBBBBWWWWWWBBBBBK.....",
        ".KBBBBBBBWWBBBBBBBK.....",
        "..KKBBBBBBBBBBBBKK......",
        "....KBBBBBBBBBBK........",
        "...KBBWWWWWWWWBBKKK.....",
        "...KBWWWWWWWWWWBKDDK....",
        "...KBWWWWWWWWWWBKDDDK...",
        "...KBWWWWWWWWWWBKKDDK...",
        "...KBWWWWWWWWWWBK.KDDK..",
        "...KKWWKKKKKKWWKK.KDDK..",
        "....KKKK....KKKK...KKK..",
        "........................",
    };

    static BufferedImage render(String[] g) {
        int h=g.length, w=g[0].length();
        BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        for (int y=0;y<h;y++) for (int x=0;x<w && x<g[y].length();x++) {
            Color c=pal.get(g[y].charAt(x));
            if (c!=null && c.getAlpha()>0) img.setRGB(x,y,c.getRGB());
        }
        return img;
    }
    static BufferedImage zoom(BufferedImage s,int f){
        BufferedImage o=new BufferedImage(s.getWidth()*f,s.getHeight()*f,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<o.getHeight();y++)for(int x=0;x<o.getWidth();x++)o.setRGB(x,y,s.getRGB(x/f,y/f));
        return o;
    }
    public static void main(String[] a) throws Exception {
        BufferedImage s=render(SENTADO);
        BufferedImage big=zoom(s,11), real=zoom(s,2);
        BufferedImage f=new BufferedImage(big.getWidth()+180, big.getHeight()+50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g=f.createGraphics();
        g.setColor(new Color(0x0B,0x0B,0x0E)); g.fillRect(0,0,f.getWidth(),f.getHeight());
        g.drawImage(big,20,30,null);
        g.drawImage(real, big.getWidth()+60, 60, null);
        g.drawImage(s, big.getWidth()+60, 140, null);
        g.setColor(new Color(0x8A,0x8A,0x94)); g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,11));
        g.drawString("11x (inspecao)",20,20);
        g.drawString("2x",big.getWidth()+60,50);
        g.drawString("1x",big.getWidth()+60,130);
        g.dispose(); ImageIO.write(f,"png",new File(a[0])); System.out.println("ok");
    }
}
