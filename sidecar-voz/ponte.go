package main

type Comando struct {
	Cmd string `json:"cmd"`

	Url   string `json:"url,omitempty"`
	Token string `json:"token,omitempty"`

	Par   string `json:"par,omitempty"`
	Tipo  string `json:"tipo,omitempty"`
	Dados string `json:"dados,omitempty"`

	Ligado bool `json:"ligado,omitempty"`

	Eco   bool `json:"eco,omitempty"`
	Ruido bool `json:"ruido,omitempty"`
	Ganho bool `json:"ganho,omitempty"`

	Monitor int    `json:"monitor,omitempty"`
	Janela  uint64 `json:"janela,omitempty"`
	Largura int    `json:"largura,omitempty"`
	Altura  int    `json:"altura,omitempty"`
	Fps     int    `json:"fps,omitempty"`
	Kbps    int    `json:"kbps,omitempty"`

	Volume  int    `json:"volume,omitempty"`

	Sentido string `json:"sentido,omitempty"`
	Id      string `json:"id,omitempty"`
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

	Janelas []JanelaDaTela `json:"janelas,omitempty"`

	Caminho *LeituraDoCaminho `json:"caminho,omitempty"`
}

const (
	CmdEntrarNaSala = "sala"
	CmdDeixarSala   = "deixar"

	CmdMudo  = "mudo"
	CmdSurdo = "surdo"

	CmdTratamento = "tratamento"

	CmdTransmitir = "transmitir"

	CmdAparelhos = "aparelhos"

	CmdMonitores = "monitores"
	CmdJanelas   = "janelas"

	CmdAssistir = "assistir"

	CmdVolume = "volume"

	AlvoDoMicrofone = "meu-microfone"
	AlvoDaEscuta    = "minha-escuta"

	CmdUsarAparelho = "usar"
	CmdSair         = "sair"
)

const (
	EvPronto = "pronto"
	EvEstado = "estado"

	EvFala = "fala"

	EvAparelhos = "aparelhos"

	EvMonitores = "monitores"
	EvJanelas   = "janelas"
	EvErro      = "erro"

	EvCaminho = "caminho"

	EvTransmissao = "transmissao"

	EvTelaDeOutro = "tela"
)
