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
Option Explicit
Dim fso, shell, baseDir, versionsDir, best, bestKey, f, k, exe
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
