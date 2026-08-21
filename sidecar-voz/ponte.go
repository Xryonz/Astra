package main

// A PONTE entre o Astra (Kotlin) e este processo.
//
// O transporte é a entrada e a saída padrão, uma mensagem JSON por linha. Foi
// escolhido em cima de socket TCP local por três motivos concretos:
//
//  1. Não abre porta. Um socket local acorda o Firewall do Windows, e o usuário
//     recebe um alerta de segurança por causa de um app de conversa querendo
//     escutar na rede. Isso assusta, e com razão.
//  2. A vida do processo já vem amarrada. Se o Astra morre, a entrada padrão
//     fecha, e este processo termina sozinho — sem sidecar órfão comendo
//     microfone depois que o app fechou.
//  3. Dá pra depurar com o teclado. `astra-voz.exe` e digitar uma linha de JSON
//     é um teste completo, sem nenhuma ferramenta.
//
// A saída de ERRO padrão é livre: vai tudo que for log. O Astra captura e guarda
// junto do diagnóstico de rede. Só a saída padrão carrega protocolo — misturar as
// duas quebraria o leitor de linha do outro lado.

// Comando é o que o Astra manda pra cá.
//
// Um struct só, com campos opcionais, em vez de um por comando: o JSON chega sem
// tipo conhecido, e discriminar antes de decodificar exigiria duas passadas de
// parse. O custo é alguns campos vazios por mensagem, o que num canal que carrega
// uns poucos KB por chamada não significa nada.
type Comando struct {
	Cmd string `json:"cmd"`

	// config
	Stun []string   `json:"stun,omitempty"`
	Turn []ServTurn `json:"turn,omitempty"`

	// conectar / sinal / desconectar
	Par     string `json:"par,omitempty"`
	Iniciar bool   `json:"iniciar,omitempty"`
	Tipo    string `json:"tipo,omitempty"`
	Dados   string `json:"dados,omitempty"`

	// mudo / surdo
	Ligado bool `json:"ligado,omitempty"`

	// tratamento: os três ajustes do microfone, JUNTOS num comando só.
	//
	// Juntos porque moram no mesmo objeto do Windows e mudar qualquer um deles obriga
	// a reabrir a fonte. Em comandos separados, mexer em dois interruptores seguidos
	// cortaria o som duas vezes — e quem está ajustando o microfone costuma mexer em
	// mais de um.
	Eco   bool `json:"eco,omitempty"`
	Ruido bool `json:"ruido,omitempty"`
	Ganho bool `json:"ganho,omitempty"`

	// transmitir: qual monitor e em que qualidade. Zero em largura/altura/fps quer
	// dizer "o que a tela der"; o Astra manda o preset escolhido.
	Monitor int `json:"monitor,omitempty"`
	Largura int `json:"largura,omitempty"`
	Altura  int `json:"altura,omitempty"`
	Fps     int `json:"fps,omitempty"`
	Kbps    int `json:"kbps,omitempty"`

	// aparelho: qual sentido trocar e para qual id. `Id` vazio volta ao aparelho de
	// comunicação padrão do Windows.
	Sentido string `json:"sentido,omitempty"` // "entrada" ou "saida"
	Id      string `json:"id,omitempty"`
}

type ServTurn struct {
	URL   string `json:"url"`
	User  string `json:"user"`
	Senha string `json:"senha"`
}

// Evento é o que este processo manda de volta pro Astra.
type Evento struct {
	Ev string `json:"ev"`

	// `Par` vazio significa EU. Vale para o evento de fala, onde a alternativa
	// seria o processo saber o próprio id — coisa que ele não sabe e não precisa
	// saber: quem cuida de identidade é o Astra, aqui só existe "o outro" e "eu".
	Par   string `json:"par,omitempty"`
	Tipo  string `json:"tipo,omitempty"`
	Dados string `json:"dados,omitempty"`
	V     string `json:"v,omitempty"`
	Msg   string `json:"msg,omitempty"`

	// A lista de aparelhos, no evento EvAparelhos. `Tipo` diz de que sentido é.
	Aparelhos []Aparelho `json:"aparelhos,omitempty"`
}

// Os comandos aceitos. Constante em vez de texto solto no switch porque o outro
// lado é Kotlin: um erro de digitação aqui vira silêncio em produção, não erro de
// compilação em lugar nenhum.
const (
	CmdConfig      = "config"
	CmdConectar    = "conectar"
	CmdSinal       = "sinal"
	CmdDesconectar = "desconectar"
	CmdMudo        = "mudo"
	CmdSurdo       = "surdo"
	// Cancelamento de eco, supressão de ruído e ganho automático — os três de uma vez.
	CmdTratamento = "tratamento"
	// Começa ou para de transmitir a tela. Mandar de novo com `Ligado` troca o preset.
	CmdTransmitir = "transmitir"
	// Pede a lista de microfones e saídas; a resposta vem no evento EvAparelhos.
	CmdAparelhos = "aparelhos"
	// Escolhe qual usar num dos sentidos.
	CmdUsarAparelho = "usar"
	CmdSair         = "sair"
)

const (
	EvPronto = "pronto"
	EvSinal  = "sinal"
	EvEstado = "estado"
	// Alguém começou ou parou de falar. `V` é "1" ou "0"; `Par` vazio sou eu.
	EvFala = "fala"
	// A lista de microfones ou de saídas, em resposta a CmdAparelhos.
	EvAparelhos = "aparelhos"
	EvErro      = "erro"
	// A transmissão de tela. `V` é "1" no ar e "0" fora dele. Enquanto está no ar,
	// `Tipo` diz de que notícia se trata e `Msg` a carrega:
	//
	//	Tipo = <nome do compressor>  ->  Msg = "1280x720 @30", o que de fato subiu
	//	Tipo = "perfil"              ->  Msg = "42e01f", lido de dentro do fluxo
	//	Tipo = "ritmo"               ->  Msg = "58 fps · 3,4 Mbps", uma vez por segundo
	EvTransmissao = "transmissao"
	// A tela de OUTRA pessoa. `Par` diz de quem, `V` é "1" enquanto chega imagem.
	// `Tipo` = nome do descompressor na abertura, "ritmo" nos relatórios de segundo.
	//
	// Os QUADROS não vêm por aqui — eles têm cano próprio (ver `entrega.go`). Este
	// evento é só o aviso de que há tela chegando, que é o que a interface precisa para
	// abrir o espaço antes do primeiro pixel.
	EvTelaDeOutro = "tela"
)

// Os tipos de envelope do aperto de mão. Iguais dos dois lados da ponte e iguais
// aos do relay no servidor (apps/api/src/config/socket.ts) — os três lugares têm
// que concordar, então os nomes são em português nos três.
const (
	SinalOferta    = "oferta"
	SinalResposta  = "resposta"
	SinalCandidato = "candidato"
)
