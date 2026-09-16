import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Assinar {

    private static final String EXECUTAVEL = "Astra.exe";

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            falhar("uso: java Assinar.java <pacote.zip> <versao> <manifesto> <assinatura>");
        }
        String chave = System.getenv("ASTRA_CHAVE_DE_ASSINATURA");
        if (chave == null || chave.isBlank()) {
            falhar("ASTRA_CHAVE_DE_ASSINATURA vazia: a release nao sai sem assinatura");
        }
        byte[] manifesto = manifesto(Path.of(args[0]), args[1]);
        Files.write(Path.of(args[2]), manifesto);
        Files.writeString(Path.of(args[3]), Base64.getEncoder().encodeToString(assinar(manifesto, chave.trim())));
    }

    private static byte[] assinar(byte[] manifesto, String chaveEmBase64) throws GeneralSecurityException {
        PrivateKey privada = KeyFactory.getInstance("Ed25519")
            .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(chaveEmBase64)));
        Signature assinatura = Signature.getInstance("Ed25519");
        assinatura.initSign(privada);
        assinatura.update(manifesto);
        return assinatura.sign();
    }

    private static byte[] manifesto(Path pacote, String versao) throws IOException, GeneralSecurityException {
        Map<String, String> porNome = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(pacote)))) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                if (entrada.isDirectory()) continue;
                if (porNome.put(entrada.getName(), sha256(zip)) != null) {
                    falhar("entrada repetida no pacote: " + entrada.getName());
                }
            }
        }
        String prefixo = porNome.keySet().stream()
            .filter(nome -> nome.equals(EXECUTAVEL) || nome.endsWith("/" + EXECUTAVEL))
            .findFirst()
            .map(nome -> nome.substring(0, nome.length() - EXECUTAVEL.length()))
            .orElseThrow(() -> new IllegalStateException(EXECUTAVEL + " nao esta no pacote"));

        TreeMap<String, String> relativos = new TreeMap<>();
        porNome.forEach((nome, hash) -> {
            if (!nome.startsWith(prefixo)) falhar("arquivo fora da raiz do pacote: " + nome);
            relativos.put(nome.substring(prefixo.length()), hash);
        });

        StringBuilder texto = new StringBuilder("astra-manifesto 1\nversao ").append(versao).append('\n');
        relativos.forEach((rel, hash) -> texto.append(hash).append("  ").append(rel).append('\n'));
        return texto.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(InputStream entrada) throws IOException, GeneralSecurityException {
        MessageDigest resumo = MessageDigest.getInstance("SHA-256");
        byte[] balde = new byte[1 << 16];
        int lidos;
        while ((lidos = entrada.read(balde)) > 0) resumo.update(balde, 0, lidos);
        return HexFormat.of().formatHex(resumo.digest());
    }

    private static void falhar(String motivo) {
        System.err.println(motivo);
        System.exit(1);
    }
}
