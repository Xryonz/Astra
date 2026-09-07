package app.astra.desktop.update

import app.astra.desktop.Instalacao
import java.io.File

private const val PREFIXO = "astra-troca-"
private const val SUFIXO = ".vbs"
private const val VALIDADE_DO_ROTEIRO_MS = 60L * 60_000L

private val ROTEIRO = """
Option Explicit
Dim fso, shell, alvo, nova, fixa, reserva, exe, extra, voltas
Set fso = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")
If WScript.Arguments.Count < 6 Then WScript.Quit 1
alvo = WScript.Arguments(0)
nova = WScript.Arguments(1)
fixa = WScript.Arguments(2)
reserva = WScript.Arguments(3)
exe = WScript.Arguments(4)
extra = WScript.Arguments(5)
WScript.Sleep 1500
voltas = 0
Do While Vivo(alvo) And voltas < 240
    WScript.Sleep 250
    voltas = voltas + 1
Loop
If Vivo(alvo) Then
    Abrir exe
    WScript.Quit 1
End If
On Error Resume Next
If fso.FolderExists(reserva) Then fso.DeleteFolder reserva, True
Err.Clear
If fso.FolderExists(fixa) Then fso.MoveFolder fixa, reserva
If Err.Number <> 0 Then
    Err.Clear
    On Error GoTo 0
    Abrir exe
    WScript.Quit 1
End If
fso.MoveFolder nova, fixa
If Err.Number <> 0 Or Not fso.FileExists(exe) Then
    Err.Clear
    If fso.FolderExists(fixa) Then fso.DeleteFolder fixa, True
    Err.Clear
    fso.MoveFolder reserva, fixa
    Err.Clear
End If
On Error GoTo 0
Abrir exe

Sub Abrir(caminho)
    On Error Resume Next
    If fso.FileExists(caminho) Then shell.Run Chr(34) & caminho & Chr(34) & " " & extra, 1, False
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

    fun trocar(nova: File, argumento: String): Boolean {
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
                argumento,
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
