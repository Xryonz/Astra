package main

type Comando struct {
	Cmd string `json:"cmd"`

	Stun []string   `json:"stun,omitempty"`
	Turn []ServTurn `json:"turn,omitempty"`

	Par     string `json:"par,omitempty"`
	Iniciar bool   `json:"iniciar,omitempty"`
	Tipo    string `json:"tipo,omitempty"`
	Dados   string `json:"dados,omitempty"`

	Ligado bool `json:"ligado,omitempty"`

	Eco   bool `json:"eco,omitempty"`
	Ruido bool `json:"ruido,omitempty"`
	Ganho bool `json:"ganho,omitempty"`

	Monitor int `json:"monitor,omitempty"`
	Largura int `json:"largura,omitempty"`
	Altura  int `json:"altura,omitempty"`
	Fps     int `json:"fps,omitempty"`
	Kbps    int `json:"kbps,omitempty"`

	Sentido string `json:"sentido,omitempty"`
	Id      string `json:"id,omitempty"`
}

type ServTurn struct {
	URL   string `json:"url"`
	User  string `json:"user"`
	Senha string `json:"senha"`
}

type Evento struct {
	Ev string `json:"ev"`

	Par   string `json:"par,omitempty"`
	Tipo  string `json:"tipo,omitempty"`
	Dados string `json:"dados,omitempty"`
	V     string `json:"v,omitempty"`
	Msg   string `json:"msg,omitempty"`

	Aparelhos []Aparelho `json:"aparelhos,omitempty"`

	Monitores []MonitorDaTela `json:"monitores,omitempty"`
}

const (
	CmdConfig      = "config"
	CmdConectar    = "conectar"
	CmdSinal       = "sinal"
	CmdDesconectar = "desconectar"
	CmdMudo        = "mudo"
	CmdSurdo       = "surdo"

	CmdTratamento = "tratamento"

	CmdTransmitir = "transmitir"

	CmdAparelhos = "aparelhos"

	CmdMonitores = "monitores"

	CmdAssistir = "assistir"

	CmdUsarAparelho = "usar"
	CmdSair         = "sair"
)

const (
	EvPronto = "pronto"
	EvSinal  = "sinal"
	EvEstado = "estado"

	EvFala = "fala"

	EvAparelhos = "aparelhos"

	EvMonitores = "monitores"
	EvErro      = "erro"

	EvTransmissao = "transmissao"

	EvTelaDeOutro = "tela"
)

const (
	SinalOferta    = "oferta"
	SinalResposta  = "resposta"
	SinalCandidato = "candidato"
)
