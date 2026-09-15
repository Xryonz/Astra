import argparse
import os
import shutil
import socket
import stat
import subprocess
import sys
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional

MARCO_DESENHOU = "primeiro quadro desenhado"
MARCO_RECUOU = "ja havia outro Astra aberto"
MARCO_CHAMADO = "um segundo Astra pediu a frente"
PORTA_DA_COPIA_UNICA = 47821


def achar_astra(raiz: Path) -> Path:
    for achado in raiz.rglob("Astra.exe"):
        return achado
    raise SystemExit("nao achei o Astra dentro de " + str(raiz))


def ler(caminho: Path) -> str:
    try:
        return caminho.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def escrever(caminho: Path, texto: str) -> None:
    caminho.parent.mkdir(parents=True, exist_ok=True)
    caminho.write_text(texto, encoding="utf-8")


def pasta_de_dados(local: Path) -> Path:
    return local / "Astra"


def pasta_de_sessao(roaming: Path) -> Path:
    return roaming / "Astra"


def apagar(caminho: Path) -> None:
    if not caminho.exists():
        return
    for item in caminho.rglob("*"):
        if item.is_file():
            try:
                os.chmod(item, stat.S_IWRITE)
            except OSError:
                pass
    shutil.rmtree(caminho, ignore_errors=True)


def porta_ocupada() -> bool:
    with socket.socket() as tomada:
        tomada.settimeout(0.5)
        return tomada.connect_ex(("127.0.0.1", PORTA_DA_COPIA_UNICA)) == 0


def esperar_porta_livre(segundos: float = 20) -> bool:
    limite = time.time() + segundos
    while time.time() < limite:
        if not porta_ocupada():
            return True
        time.sleep(0.5)
    return False


def armar_modo_seguro(local: Path, roaming: Path) -> None:
    escrever(pasta_de_dados(local) / "modo-seguro.txt", "armado pela prova de abertura\n")


def sujar_preferencias(local: Path, roaming: Path) -> None:
    escrever(
        pasta_de_sessao(roaming) / "ui.properties",
        "theme=\\uZZZZ\ndegrau=isto-nao-e-numero\nvolumeDoMicrofone=\nemojiRecentes=\\u00\n",
    )


def sujar_sessao(local: Path, roaming: Path) -> None:
    escrever(
        pasta_de_sessao(roaming) / "session.properties",
        "accessToken=\\uFFFG\nrefreshToken=\nuserId=\nexpiraEm=amanha\n",
    )


def travar_preferencias(local: Path, roaming: Path) -> None:
    alvo = pasta_de_sessao(roaming) / "ui.properties"
    escrever(alvo, "theme=obsidiana\n")
    os.chmod(alvo, stat.S_IREAD)


def rastro_de_queda(local: Path, roaming: Path) -> None:
    escrever(
        pasta_de_dados(local) / "arranque.txt",
        "Astra 1.0.0 — por onde o arranque passou\n"
        "     1 ms  main\n"
        "   120 ms  janela principal criada\n",
    )


def economia_imposta(local: Path, roaming: Path) -> None:
    escrever(
        pasta_de_sessao(roaming) / "ui.properties",
        "performanceMode=1\nperfAutomatico=2 nucleos de processador\ndegrauAprendido=3\n",
    )


def economia_desfeita(local: Path, roaming: Path) -> Optional[str]:
    texto = ler(pasta_de_sessao(roaming) / "ui.properties")
    sobrou = [
        linha for linha in texto.splitlines()
        if linha.startswith("performanceMode=1")
        or linha.startswith("perfAutomatico=")
        or linha.startswith("degrauAprendido=")
    ]
    if sobrou:
        return "a economia imposta sobreviveu: " + " | ".join(sobrou)
    return None


class VagaTomada:
    def __init__(self) -> None:
        self.servidor = socket.socket()
        self.servidor.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.servidor.bind(("127.0.0.1", PORTA_DA_COPIA_UNICA))
        self.servidor.listen(2)
        self.presos: list = []
        self.vivo = True
        threading.Thread(target=self._aceitar, daemon=True).start()

    def _aceitar(self) -> None:
        while self.vivo:
            try:
                cliente, _ = self.servidor.accept()
            except OSError:
                return
            self.presos.append(cliente)

    def fechar(self) -> None:
        self.vivo = False
        for cliente in self.presos:
            try:
                cliente.close()
            except OSError:
                pass
        try:
            self.servidor.close()
        except OSError:
            pass


@dataclass
class Prova:
    nome: str
    conta: str
    preparar: Optional[Callable[[Path, Path], None]] = None
    conferir: Optional[Callable[[Path, Path], Optional[str]]] = None
    opcoes_java: str = ""
    segunda_copia: bool = False
    vaga_tomada: bool = False


PROVAS = [
    Prova("padrao", "a maquina do dia a dia, sem nada fora do lugar"),
    Prova(
        "computador-fraco",
        "dois nucleos e pouca memoria — o mesmo Astra de todo mundo, sem tratamento especial",
        opcoes_java="-XX:ActiveProcessorCount=2 -Xmx512m",
    ),
    Prova(
        "sem-placa-de-video",
        "desenho por processador, como em maquina sem placa decente",
        opcoes_java="-Dskiko.renderApi=SOFTWARE",
    ),
    Prova("modo-seguro", "o socorro ligado: integracoes desligadas e janela opaca", preparar=armar_modo_seguro),
    Prova("preferencias-corrompidas", "arquivo de preferencias ilegivel", preparar=sujar_preferencias),
    Prova("sessao-corrompida", "credenciais gravadas pela metade", preparar=sujar_sessao),
    Prova("preferencias-travadas", "preferencias existem mas nao aceitam gravacao", preparar=travar_preferencias),
    Prova(
        "sem-internet",
        "a API inalcancavel desde o primeiro instante",
        opcoes_java="-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=1 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=1",
    ),
    Prova("abertura-apos-queda", "a abertura anterior criou a janela e nao desenhou", preparar=rastro_de_queda),
    Prova(
        "economia-imposta-some",
        "quem teve economia ligada pelo app recebe o visual de volta",
        preparar=economia_imposta,
        conferir=economia_desfeita,
    ),
    Prova(
        "segunda-copia",
        "abrir de novo com um Astra ja aberto — a primeira tem de vir para a frente",
        segunda_copia=True,
    ),
    Prova(
        "vaga-tomada-por-quem-nao-responde",
        "a vaga da copia unica presa por quem nunca responde — o Astra abre assim mesmo",
        vaga_tomada=True,
    ),
]


def abrir(exe: Path, local: Path, roaming: Path, opcoes_java: str):
    ambiente = os.environ.copy()
    ambiente["LOCALAPPDATA"] = str(local)
    ambiente["APPDATA"] = str(roaming)
    if opcoes_java:
        ambiente["JAVA_TOOL_OPTIONS"] = opcoes_java
    else:
        ambiente.pop("JAVA_TOOL_OPTIONS", None)
    return subprocess.Popen(
        [str(exe)],
        env=ambiente,
        cwd=str(exe.parent),
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def encerrar(processo) -> None:
    if processo.poll() is None:
        subprocess.run(
            ["taskkill", "/T", "/F", "/PID", str(processo.pid)],
            capture_output=True,
        )
    subprocess.run(["taskkill", "/F", "/IM", "astra-voz.exe"], capture_output=True)
    try:
        processo.wait(timeout=10)
    except subprocess.TimeoutExpired:
        pass


def esperar_marco(processo, rastro: Path, marco: str, segundos: float):
    limite = time.time() + segundos
    while time.time() < limite:
        texto = ler(rastro)
        if marco in texto:
            return True, texto
        if processo.poll() is not None:
            return False, texto
        time.sleep(0.5)
    return False, ler(rastro)


def relatar_falha(processo, rastro: Path, texto: str, pasta_do_app: Path) -> str:
    partes = []
    if processo.poll() is not None:
        partes.append("o processo saiu com codigo " + str(processo.returncode))
    else:
        partes.append("o processo continuou vivo e nunca chegou la")
    if texto.strip():
        partes.append("rastro:\n" + texto.strip())
    else:
        partes.append("o rastro nem chegou a ser escrito")
    falhas = rastro.parent / "falhas.txt"
    if falhas.exists():
        partes.append("falhas.txt:\n" + ler(falhas).strip())
    for log in sorted(pasta_do_app.rglob("falha-jvm-*.log")):
        partes.append(log.name + " (a maquina virtual caiu):\n" + ler(log).strip()[:2000])
    return "\n".join(partes)


def rodar_segunda_copia(exe: Path, local: Path, roaming: Path, base_java: str, segundos: float):
    rastro = pasta_de_dados(local) / "arranque.txt"
    recuo = pasta_de_dados(local) / "recuo.txt"
    trilha_da_primeira = ler(rastro)
    segundo = abrir(exe, local, roaming, base_java)
    try:
        recuou, _ = esperar_marco(segundo, recuo, MARCO_RECUOU, segundos)
        depois = ler(rastro)
        if not recuou:
            if MARCO_DESENHOU in depois.replace(trilha_da_primeira, ""):
                return False, "a segunda copia ABRIU do lado da primeira — a copia unica nao segurou"
            return False, relatar_falha(segundo, rastro, depois, exe.parent)
        if not depois.startswith(trilha_da_primeira):
            return False, "a segunda copia recuou, mas apagou a trilha da primeira"

        limite = time.time() + segundos
        while MARCO_CHAMADO not in ler(rastro) and time.time() < limite:
            time.sleep(0.5)
        if MARCO_CHAMADO not in ler(rastro):
            return False, (
                "a segunda copia saiu, mas a primeira nunca soube que foi chamada — "
                "a janela ficaria escondida atras das outras"
            )
        return True, "a segunda copia recuou e a primeira foi trazida para a frente"
    finally:
        encerrar(segundo)


def rodar(prova: Prova, exe: Path, base: Path, base_java: str, segundos: float):
    quarto = base / prova.nome
    apagar(quarto)
    local = quarto / "local"
    roaming = quarto / "roaming"
    pasta_de_dados(local).mkdir(parents=True, exist_ok=True)
    pasta_de_sessao(roaming).mkdir(parents=True, exist_ok=True)
    if prova.preparar:
        prova.preparar(local, roaming)

    opcoes = (base_java + " " + prova.opcoes_java).strip()
    rastro = pasta_de_dados(local) / "arranque.txt"
    intrusa = VagaTomada() if prova.vaga_tomada else None
    processo = abrir(exe, local, roaming, opcoes)
    try:
        desenhou, texto = esperar_marco(processo, rastro, MARCO_DESENHOU, segundos)
        if not desenhou:
            return False, relatar_falha(processo, rastro, texto, exe.parent)
        if prova.conferir:
            queixa = prova.conferir(local, roaming)
            if queixa:
                return False, queixa
        if prova.segunda_copia:
            return rodar_segunda_copia(exe, local, roaming, base_java, segundos)
        return True, primeira_linha_do_tempo(texto)
    finally:
        if intrusa:
            intrusa.fechar()
        encerrar(processo)
        esperar_porta_livre()


def primeira_linha_do_tempo(texto: str) -> str:
    for linha in texto.splitlines():
        if MARCO_DESENHOU in linha:
            return linha.strip()
    return "desenhou"


def main() -> int:
    parser = argparse.ArgumentParser(description="abre o Astra em varias condicoes e exige que ele desenhe")
    parser.add_argument("--de", default=r"C:\Astra\atual", help="pasta onde o Astra empacotado esta")
    parser.add_argument("--apenas", action="append", help="rodar so estas provas")
    parser.add_argument("--tempo", type=float, default=90, help="segundos de espera por prova")
    parser.add_argument("--java-base", default="", help="opcoes de JVM aplicadas a todas as provas")
    parser.add_argument("--lista", action="store_true", help="listar as provas e sair")
    args = parser.parse_args()

    if args.lista:
        for prova in PROVAS:
            print(prova.nome.ljust(26) + prova.conta)
        return 0

    escolhidas = PROVAS
    if args.apenas:
        pedidas = set(args.apenas)
        escolhidas = [p for p in PROVAS if p.nome in pedidas]
        faltando = pedidas - {p.nome for p in escolhidas}
        if faltando:
            print("nao conheco: " + ", ".join(sorted(faltando)))
            return 2

    if porta_ocupada():
        print("ha um Astra aberto nesta maquina. Feche-o (inclusive na bandeja) antes de rodar as provas.")
        return 2

    exe = achar_astra(Path(args.de).resolve())
    base = Path(os.environ.get("RUNNER_TEMP") or os.environ.get("TEMP") or ".") / "provas-de-abertura"
    apagar(base)
    base.mkdir(parents=True, exist_ok=True)

    print("Astra: " + str(exe))
    print("")

    reprovadas = []
    for prova in escolhidas:
        print("- " + prova.nome + " (" + prova.conta + ")")
        comeco = time.time()
        ok, relato = rodar(prova, exe, base, args.java_base, args.tempo)
        gasto = "%.0f s" % (time.time() - comeco)
        if ok:
            print("  PASSOU em " + gasto + " — " + relato)
        else:
            reprovadas.append(prova.nome)
            print("  REPROVOU em " + gasto)
            for linha in relato.splitlines():
                print("    " + linha)
        print("")

    total = len(escolhidas)
    if reprovadas:
        print("REPROVOU " + str(len(reprovadas)) + " de " + str(total) + ": " + ", ".join(reprovadas))
        return 1
    print("PASSOU " + str(total) + " de " + str(total) + ".")
    return 0


if __name__ == "__main__":
    sys.exit(main())
