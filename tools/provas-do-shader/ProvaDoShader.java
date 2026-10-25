import java.nio.file.Files;
import org.jetbrains.skia.RuntimeEffect;
import org.jetbrains.skia.RuntimeShaderBuilder;

public class ProvaDoShader {
    public static void main(String[] a) throws Exception {
        String sksl = Files.readString(java.nio.file.Path.of(a[0]));
        try {
            RuntimeEffect efeito = RuntimeEffect.Companion.makeForShader(sksl);
            System.out.println("SHADER OK — compilou, " + sksl.length() + " caracteres");
            RuntimeShaderBuilder b = new RuntimeShaderBuilder(efeito);
            System.out.println("CONSTRUTOR OK");
        } catch (Throwable t) {
            System.out.println("SHADER RECUSADO:");
            System.out.println(t.getMessage());
            System.exit(1);
        }
    }
}
