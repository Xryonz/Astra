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
CADEADO_DA_COPIA_UNICA = "Local\\Astra-copia-unica"


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


def fechar_de_vez(local: Path, roaming: Path) -> None:
    escrever(pasta_de_sessao(roaming) / "ui.properties", "exitOnClose=1\n")


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


TETO_PARA_SOLTAR_A_VAGA_S = 0.4
TETO_DO_CAMINHO_CURTO = 60


def porta_livre() -> bool:
    tomada = socket.socket()
    try:
        tomada.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        tomada.bind(("127.0.0.1", PORTA_DA_COPIA_UNICA))
        return True
    except OSError:
        return False
    finally:
        tomada.close()


def cadeado_livre() -> bool:
    import ctypes

    punho = ctypes.windll.kernel32.OpenMutexW(0x00100000, False, CADEADO_DA_COPIA_UNICA)
    if punho:
        ctypes.windll.kernel32.CloseHandle(punho)
        return False
    return True


def vaga_livre() -> bool:
    return porta_livre() and cadeado_livre()


def esperar_a_vaga_abrir(teto: float) -> Optional[float]:
    comeco = time.time()
    while time.time() - comeco < teto:
        if vaga_livre():
            return time.time() - comeco
        time.sleep(0.02)
    return None


def fechar_pela_janela(processo) -> bool:
    import ctypes
    from ctypes import wintypes

    user32 = ctypes.windll.user32
    kernel32 = ctypes.windll.kernel32
    WM_CLOSE = 0x0010
    PROCESS_QUERY_LIMITED = 0x1000

    familia = {processo.pid}
    achadas = []

    def mesma_arvore(pid: int) -> bool:
        if pid in familia:
            return True
        punho = kernel32.OpenProcess(PROCESS_QUERY_LIMITED, False, pid)
        if not punho:
            return False
        nome = ctypes.create_unicode_buffer(512)
        tamanho = wintypes.DWORD(512)
        ok = kernel32.QueryFullProcessImageNameW(punho, 0, nome, ctypes.byref(tamanho))
        kernel32.CloseHandle(punho)
        return bool(ok) and nome.value.lower().endswith("\\astra.exe")

    @ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    def visitar(janela, _):
        if not user32.IsWindowVisible(janela):
            return True
        dono = wintypes.DWORD()
        user32.GetWindowThreadProcessId(janela, ctypes.byref(dono))
        if mesma_arvore(dono.value):
            achadas.append(janela)
        return True

    user32.EnumWindows(visitar, 0)
    for janela in achadas:
        user32.PostMessageW(janela, WM_CLOSE, 0, 0)
    return bool(achadas)


def rodar_reabrir_na_hora(exe: Path, local: Path, roaming: Path, base_java: str, segundos: float, primeiro):
    rastro = pasta_de_dados(local) / "arranque.txt"
    recuo = pasta_de_dados(local) / "recuo.txt"
    trilha_da_primeira = ler(rastro)
    if not fechar_pela_janela(primeiro):
        return False, "nao achei a janela do Astra para fechar pelo caminho da pessoa"

    soltou = esperar_a_vaga_abrir(TETO_PARA_SOLTAR_A_VAGA_S)
    if soltou is None:
        return False, (
            "ao fechar, o Astra segurou a vaga por mais de %.1f s. Quem clicar para reabrir nesse "
            "intervalo esbarra em quem esta morrendo, ve um pisca e nada abre"
            % TETO_PARA_SOLTAR_A_VAGA_S
        )

    segundo = abrir(exe, local, roaming, base_java)
    comeco = time.time()
    try:
        limite = time.time() + segundos
        while time.time() < limite:
            agora = ler(rastro)
            if agora != trilha_da_primeira and MARCO_DESENHOU in agora:
                gasto = time.time() - comeco
                return True, "vaga solta em %.2f s; reabriu em %.1f s" % (soltou, gasto)
            if MARCO_RECUOU in ler(recuo):
                return False, "reabrir logo apos fechar RECUOU: a vaga ainda estava presa"
            if segundo.poll() is not None:
                break
            time.sleep(0.3)
        return False, relatar_falha(segundo, rastro, ler(rastro), exe.parent)
    finally:
        encerrar(segundo)


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
    reabrir_na_hora: bool = False


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
    Prova(
        "reabrir-na-hora",
        "fechar e abrir de novo no instante seguinte — sem esbarrar em quem esta fechando",
        preparar=fechar_de_vez,
        reabrir_na_hora=True,
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
        if prova.reabrir_na_hora:
            return rodar_reabrir_na_hora(exe, local, roaming, base_java, segundos, processo)
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
    parser.add_argument(
        "--java-base",
        default=os.environ.get("ASTRA_PROVAS_JAVA_BASE", ""),
        help="opcoes de JVM aplicadas a todas as provas; como comecam com '-', passe colado "
        "(--java-base=-Dx=y) ou use ASTRA_PROVAS_JAVA_BASE",
    )
    parser.add_argument("--base", help="pasta onde as provas montam os quartos; curta e melhor")
    parser.add_argument("--lista", action="store_true", help="listar as provas e sair")
    args = parser.parse_args()

    if args.lista:
        coluna = max(len(p.nome) for p in PROVAS) + 2
        for prova in PROVAS:
            print(prova.nome.ljust(coluna) + prova.conta)
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
    base = Path(args.base).resolve() if args.base else (
        Path(os.environ.get("RUNNER_TEMP") or os.environ.get("TEMP") or ".") / "provas-de-abertura"
    )
    if len(str(base)) > TETO_DO_CAMINHO_CURTO:
        print(
            "aviso: a pasta das provas tem %d caracteres. O aviso entre copias viaja por arquivo, e "
            "esse caminho tem teto de ~108 — acima disso o Astra recua para a porta de rede, e as "
            "provas deixam de exercitar o caminho principal. Use --base com algo curto." % len(str(base))
        )
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
