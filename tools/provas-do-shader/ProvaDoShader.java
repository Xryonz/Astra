import java.nio.file.Files;
import org.jetbrains.skia.RuntimeEffect;
import org.jetbrains.skia.RuntimeShaderBuilder;

// Compila o SkSL do Astra fora do app.
//
// O SkSL só é validado em TEMPO DE EXECUÇÃO: `RuntimeEffect.makeForShader` devolve nulo
// (ou lança) quando o programa tem erro, e o componente cai no silêncio — nada desenha e
// nada reclama. Compilar o Kotlin não pega isso. Isto pega.
public class ProvaDoShader {
    public static void main(String[] a) throws Exception {
        String sksl = Files.readString(java.nio.file.Path.of(a[0]));
        try {
            RuntimeEffect efeito = RuntimeEffect.Companion.makeForShader(sksl);
            System.out.println("SHADER OK — compilou, " + sksl.length() + " caracteres");
            // Um construtor prova que os filhos declarados existem com os nomes usados.
            RuntimeShaderBuilder b = new RuntimeShaderBuilder(efeito);
            System.out.println("CONSTRUTOR OK");
        } catch (Throwable t) {
            System.out.println("SHADER RECUSADO:");
            System.out.println(t.getMessage());
            System.exit(1);
        }
    }
}
