import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public class GerarChave {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("uso: java GerarChave.java <arquivo-da-chave-privada>");
            System.exit(1);
        }
        Path privada = Path.of(args[0]);
        if (Files.exists(privada)) {
            System.err.println("ja existe um arquivo em " + privada + ": nao vou sobrescrever uma chave");
            System.exit(1);
        }
        KeyPair par = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Files.writeString(privada, Base64.getEncoder().encodeToString(par.getPrivate().getEncoded()));
        System.out.println(Base64.getEncoder().encodeToString(par.getPublic().getEncoded()));
    }
}
