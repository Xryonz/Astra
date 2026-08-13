' Astra launcher (instalacao portatil): SEMPRE abre a MAIOR versao dentro de
' versions\. Rodar como .vbs (WScript) nao abre janela de console — nada de
' terminal preto piscando como um .bat abriria. O atalho Astra.lnk aponta pra ca.
'
' Layout esperado (mesma pasta deste script):
'   .\versions\<versao>\Astra.exe   uma pasta por versao
'   .\zips\Astra-<versao>-win-x64.zip
'
' O auto-update do app baixa versoes novas em versions\; este launcher garante
' que abrir o Astra sempre cai na mais nova, sem depender do swap in-place.
'
' SEGUNDA CONTA:  wscript launch.vbs 2
' Abre uma segunda janela com sessao propria, pra testar "o outro ve na hora?" sem
' um segundo PC. O numero vira o apelido da sessao (%APPDATA%\Astra-teste2), entao
' da pra ter quantas quiser: 2, 3, 4...
'
' ANTES ISTO ERA UMA COPIA INTEIRA DO APP em C:\Astra\multi, espelhada por um script
' a cada abertura. Nao era so o desperdicio de 300 MB: a copia so se atualizava se a
' pessoa abrisse pelo atalho certo, e abrir pelo .exe direto (o caminho obvio) deixava
' ela parada numa versao velha, calada. Comparar duas janelas so vale se as duas
' rodam o MESMO build -- entao a segunda janela passou a ser o proprio app instalado,
' aberto com uma variavel de ambiente a mais. Nao ha copia pra ficar pra tras.
Option Explicit
Dim fso, shell, baseDir, versionsDir, best, bestKey, f, k, exe, conta
Set fso = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")
baseDir = fso.GetParentFolderName(WScript.ScriptFullName)
versionsDir = fso.BuildPath(baseDir, "versions")

If Not fso.FolderExists(versionsDir) Then
    MsgBox "Pasta 'versions' nao encontrada em " & baseDir & ".", vbExclamation, "Astra"
    WScript.Quit 1
End If

best = ""
bestKey = -1
For Each f In fso.GetFolder(versionsDir).SubFolders
    ' So considera pastas que realmente tem o Astra.exe (ignora staging/lixo).
    If fso.FileExists(fso.BuildPath(f.Path, "Astra.exe")) Then
        k = VerKey(f.Name)
        If k > bestKey Then
            bestKey = k
            best = f.Path
        End If
    End If
Next

If best = "" Then
    MsgBox "Nenhuma versao do Astra encontrada em versions\.", vbExclamation, "Astra"
    WScript.Quit 1
End If

exe = fso.BuildPath(best, "Astra.exe")

' A variavel de ambiente e o unico canal que atravessa o Astra.exe do jpackage sem
' mexer no Astra.cfg de dentro da instalacao -- e era mexer no cfg que obrigava a
' manter a copia separada. Escrita no ambiente DESTE processo; o app nasce dele e
' herda.
conta = ""
If WScript.Arguments.Count > 0 Then conta = Trim(WScript.Arguments(0))
If conta <> "" Then shell.Environment("Process")("ASTRA_MULTI") = conta

' 1 = janela normal do app; False = nao espera o app fechar (launcher encerra ja).
shell.Run """" & exe & """", 1, False

' semver -> chave numerica ordenavel (major.minor.patch, campos < 1000). Campos
' nao-numericos contam 0; sufixos (-beta etc.) sao ignorados no split por ".".
Function VerKey(name)
    Dim parts, i, n, key
    parts = Split(name, ".")
    key = 0
    For i = 0 To 2
        n = 0
        If i <= UBound(parts) Then
            If IsNumeric(parts(i)) Then n = CLng(parts(i))
        End If
        key = key * 1000 + n
    Next
    VerKey = key
End Function
