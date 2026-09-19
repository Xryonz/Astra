package app.astra.desktop.update

import app.astra.desktop.Instalacao
import java.io.File

private const val PREFIXO = "astra-troca-"
private const val SUFIXO = ".vbs"
private const val VALIDADE_DO_ROTEIRO_MS = 60L * 60_000L

private val ROTEIRO = """
Option Explicit
Dim fso, shell, alvo, nova, fixa, reserva, exe, extra, falhou, voltas, guardada
Set fso = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")
If WScript.Arguments.Count < 7 Then WScript.Quit 1
alvo = WScript.Arguments(0)
nova = WScript.Arguments(1)
fixa = WScript.Arguments(2)
reserva = WScript.Arguments(3)
exe = WScript.Arguments(4)
extra = WScript.Arguments(5)
falhou = WScript.Arguments(6)
WScript.Sleep 1500
voltas = 0
Do While Vivo(alvo) And voltas < 240
    WScript.Sleep 250
    voltas = voltas + 1
Loop
If Vivo(alvo) Then
    Abrir exe, falhou
    WScript.Quit 1
End If

On Error Resume Next
guardada = NomeLivre(reserva)
If guardada = "" Then
    Err.Clear
    On Error GoTo 0
    Abrir exe, falhou
    WScript.Quit 1
End If

If Not Mover(fixa, guardada) Then
    Err.Clear
    On Error GoTo 0
    Abrir exe, falhou
    WScript.Quit 1
End If

If Not Mover(nova, fixa) Or Not fso.FileExists(exe) Then
    Err.Clear
    If fso.FolderExists(fixa) Then fso.DeleteFolder fixa, True
    Err.Clear
    fso.MoveFolder guardada, fixa
    Err.Clear
    On Error GoTo 0
    Abrir exe, falhou
    WScript.Quit 1
End If
On Error GoTo 0
Abrir exe, extra

Function NomeLivre(preferido)
    Dim candidato, sufixo
    On Error Resume Next
    NomeLivre = ""
    If Not fso.FolderExists(preferido) Then
        NomeLivre = preferido
        Exit Function
    End If
    Err.Clear
    fso.DeleteFolder preferido, True
    Err.Clear
    If Not fso.FolderExists(preferido) Then
        NomeLivre = preferido
        Exit Function
    End If
    For sufixo = 1 To 40
        candidato = preferido & "-" & sufixo
        If Not fso.FolderExists(candidato) Then
            NomeLivre = candidato
            Exit Function
        End If
    Next
End Function

Function Mover(origem, destino)
    Dim tentativa
    On Error Resume Next
    Mover = False
    For tentativa = 1 To 8
        Err.Clear
        fso.MoveFolder origem, destino
        If Err.Number = 0 And fso.FolderExists(destino) Then
            Mover = True
            Exit Function
        End If
        WScript.Sleep 500
    Next
    Err.Clear
End Function

Sub Abrir(caminho, marca)
    On Error Resume Next
    If fso.FileExists(caminho) Then shell.Run Chr(34) & caminho & Chr(34) & " " & marca, 1, False
    On Error GoTo 0
End Sub

Function Vivo(quem)
    Dim wmi, lista
    Vivo = False
    On Error Resume Next
    Set wmi = GetObject("winmgmts:\\.\root\cimv2")
    If Err.Number = 0 Then
        Set lista = wmi.ExecQuery("Select ProcessId from Win32_Process Where ProcessId = " & quem)
        If Err.Number = 0 Then Vivo = (lista.Count > 0)
    End If
    Err.Clear
    On Error GoTo 0
End Function
""".trimIndent()

internal object Trocador {

    fun precisaTrocar(nova: File): Boolean {
        if (!noWindows()) return false
        val fixa = Instalacao.fixa ?: return false
        return !Instalacao.mesmaPasta(nova, fixa)
    }

    fun trocar(nova: File, seDerCerto: String, seFalhar: String): Boolean {
        val fixa = Instalacao.fixa ?: return false
        val reserva = Instalacao.reserva ?: return false
        val exe = Instalacao.exeFixo ?: return false
        val roteiro = File(pastaDosRoteiros(), "$PREFIXO${System.currentTimeMillis()}$SUFIXO")
        return runCatching {
            roteiro.writeText(ROTEIRO)
            ProcessBuilder(
                wscript(),
                roteiro.absolutePath,
                ProcessHandle.current().pid().toString(),
                nova.absolutePath,
                fixa.absolutePath,
                reserva.absolutePath,
                exe.absolutePath,
                seDerCerto,
                seFalhar,
            ).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            true
        }.getOrDefault(false)
    }

    fun limparRoteiros() {
        val corte = System.currentTimeMillis() - VALIDADE_DO_ROTEIRO_MS
        pastaDosRoteiros().listFiles()
            ?.filter { it.isFile && it.name.startsWith(PREFIXO) && it.name.endsWith(SUFIXO) }
            ?.filter { it.lastModified() < corte }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun pastaDosRoteiros(): File =
        File(System.getProperty("java.io.tmpdir") ?: ".")

    private fun wscript(): String =
        "${System.getenv("SystemRoot") ?: "C:\\Windows"}\\System32\\wscript.exe"

    private fun noWindows(): Boolean =
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
}
