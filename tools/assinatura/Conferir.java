import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Conferir {

    private static final Pattern CHAVE_PUBLICA_ED25519 = Pattern.compile("MCowBQYDK2VwAyEA[A-Za-z0-9+/]{43}=");

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            falhar("uso: java Conferir.java <manifesto> <assinatura> <arquivo-com-as-chaves-do-app>");
        }
        byte[] manifesto = Files.readAllBytes(Path.of(args[0]));
        byte[] assinatura = Base64.getDecoder().decode(Files.readString(Path.of(args[1])).trim());
        Matcher chaves = CHAVE_PUBLICA_ED25519.matcher(Files.readString(Path.of(args[2])));
        int vistas = 0;
        while (chaves.find()) {
            vistas++;
            PublicKey publica = KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(chaves.group())));
            Signature verificador = Signature.getInstance("Ed25519");
            verificador.initVerify(publica);
            verificador.update(manifesto);
            if (verificador.verify(assinatura)) {
                System.out.println("a assinatura confere com a chave embutida no app");
                return;
            }
        }
        falhar(vistas == 0
            ? "nenhuma chave publica encontrada em " + args[2]
            : "a assinatura NAO confere com nenhuma das " + vistas + " chaves embutidas no app");
    }

    private static void falhar(String motivo) {
        System.err.println(motivo);
        System.exit(1);
    }
}
