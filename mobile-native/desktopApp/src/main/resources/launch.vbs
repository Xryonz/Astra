' Astra launcher: abre a MAIOR versao que encontrar, preferindo a pasta fixa.
' Rodar como .vbs (WScript) nao abre janela de console — nada de terminal preto
' piscando como um .bat abriria. O atalho Astra.lnk aponta pra ca.
'
' Layout esperado (mesma pasta deste script):
'   .\atual\Astra.exe               a instalacao de verdade, endereco que NUNCA muda
'   .\atual.antiga\                 a versao anterior, guardada ate a nova abrir
'   .\versions\<versao>\Astra.exe   palco das atualizacoes e das builds de dev
'   .\zips\Astra-<versao>-win-x64.zip
'
' POR QUE A PASTA FIXA: o Windows amarra firewall, microfone e notificacao ao
' CAMINHO do executavel. Enquanto o Astra morava em versions\<versao>\, cada
' atualizacao criava um programa novo aos olhos do sistema e toda permissao
' voltava a zero. Agora a troca acontece por dentro de .\atual, e o caminho e
' sempre o mesmo.
'
' AUTO-CONSERTO: a troca de pastas e feita por um ajudante externo, com o Astra
' fechado. Se ela cair no meio, sobra .\atual.antiga e nenhuma .\atual — e este
' launcher devolve a antiga ao lugar antes de abrir. Se nem isso existir, ele
' cai de volta em versions\, que e como o app vivia antes.
'
' A versao de cada pasta e a MAIOR entre o recurso gravado no proprio Astra.exe e
' o nome da pasta. .\atual nao carrega numero no nome, entao so o recurso a
' descreve; ja em versions\<versao>\ o nome cobre o caso do recurso vir vazio ou
' desatualizado num build feito na mao.
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
Dim fso, shell, baseDir, fixaDir, reservaDir, versionsDir, melhor, melhorChave, pasta, exe, conta
Set fso = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")
baseDir = fso.GetParentFolderName(WScript.ScriptFullName)
fixaDir = fso.BuildPath(baseDir, "atual")
reservaDir = fso.BuildPath(baseDir, "atual.antiga")
versionsDir = fso.BuildPath(baseDir, "versions")

If Not fso.FileExists(fso.BuildPath(fixaDir, "Astra.exe")) Then
    If fso.FileExists(fso.BuildPath(reservaDir, "Astra.exe")) Then
        On Error Resume Next
        fso.MoveFolder reservaDir, fixaDir
        Err.Clear
        On Error GoTo 0
    End If
End If

melhor = ""
melhorChave = -1
Considerar fixaDir
If fso.FolderExists(versionsDir) Then
    For Each pasta In fso.GetFolder(versionsDir).SubFolders
        Considerar pasta.Path
    Next
End If

If melhor = "" Then
    MsgBox "Nenhuma versao do Astra encontrada em " & baseDir & ".", vbExclamation, "Astra"
    WScript.Quit 1
End If

exe = fso.BuildPath(melhor, "Astra.exe")

' A variavel de ambiente e o unico canal que atravessa o Astra.exe do jpackage sem
' mexer no Astra.cfg de dentro da instalacao -- e era mexer no cfg que obrigava a
' manter a copia separada. Escrita no ambiente DESTE processo; o app nasce dele e
' herda.
conta = ""
If WScript.Arguments.Count > 0 Then conta = Trim(WScript.Arguments(0))
If conta <> "" Then shell.Environment("Process")("ASTRA_MULTI") = conta

' 1 = janela normal do app; False = nao espera o app fechar (launcher encerra ja).
shell.Run """" & exe & """", 1, False

' Empate mantem quem foi visto primeiro, e .\atual e sempre a primeira.
Sub Considerar(caminhoDaPasta)
    Dim binario, chave
    binario = fso.BuildPath(caminhoDaPasta, "Astra.exe")
    If Not fso.FileExists(binario) Then Exit Sub
    chave = VerKey(VersaoGravada(binario))
    If VerKey(fso.GetFileName(caminhoDaPasta)) > chave Then
        chave = VerKey(fso.GetFileName(caminhoDaPasta))
    End If
    If chave > melhorChave Then
        melhorChave = chave
        melhor = caminhoDaPasta
    End If
End Sub

Function VersaoGravada(binario)
    Dim marcada
    marcada = ""
    On Error Resume Next
    marcada = fso.GetFileVersion(binario)
    Err.Clear
    On Error GoTo 0
    VersaoGravada = marcada
End Function

' semver -> chave numerica ordenavel (major.minor.patch, campos < 1000). Campos
' nao-numericos contam 0; sufixos (-beta etc.) sao ignorados no split por ".".
Function VerKey(nome)
    Dim partes, i, n, chave
    partes = Split(nome, ".")
    chave = 0
    For i = 0 To 2
        n = 0
        If i <= UBound(partes) Then
            If IsNumeric(partes(i)) Then n = CLng(partes(i))
        End If
        chave = chave * 1000 + n
    Next
    VerKey = chave
End Function
