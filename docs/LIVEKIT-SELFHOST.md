# Self-host do LiveKit no Oracle Cloud (grátis) — runbook do Astra

Guia passo-a-passo pra sair do plano grátis do LiveKit Cloud e rodar o **teu
próprio** servidor de chamadas numa VM grátis do Oracle Cloud. Feito pra quem
**não** tem background de devops. Segue na ordem, sem pular.

## O que você precisa saber antes

- **Zero mudança de código.** O backend (`apps/api/src/routes/voice.ts`) e o app
  já pegam a URL + token do servidor dinamicamente. Self-host = subir o servidor
  LiveKit + trocar **3 variáveis de ambiente** na Railway. Nada de recompilar.
- **Por que uma VM (e não a Railway):** o LiveKit é um **SFU** — a mídia (voz/vídeo)
  trafega por **UDP** direto no IP público da máquina. A Railway é HTTP-only, não
  abre UDP. Por isso precisa de uma VM com IP público onde você controla o firewall.
- **Qualidade/60fps:** o servidor **só encaminha pacote**, nunca recodifica — então
  fps e qualidade saem dos aparelhos, não dele. O app já publica screenshare em
  **H264 (hardware) + 60fps + 8Mbps + MAINTAIN_FRAMERATE**. O servidor só precisa
  ter **UDP aberto** e estar **perto de você** (região São Paulo). Os dois este guia
  entrega.
- **Custo:** R$ 0. A VM ARM "Always Free" do Oracle é grátis pra sempre (10TB de
  saída/mês — gigante).

---

## Parte 1 — Conta Oracle + região São Paulo (5 min)

1. Cria conta em <https://www.oracle.com/cloud/free/>. Pede cartão (só verificação,
   não cobra no Always Free).
2. **CRÍTICO:** na hora do cadastro, escolhe a **Home Region = Brazil East (São
   Paulo)**. Isso **não dá pra trocar depois** e define a latência das tuas calls.
   Perto = qualidade boa.

---

## Parte 2 — Criar a VM grátis (10 min)

1. No console Oracle: menu ☰ → **Compute** → **Instances** → **Create Instance**.
2. **Image and shape** → **Edit**:
   - Image: **Canonical Ubuntu 22.04**.
   - Shape: **Ampere** → **VM.Standard.A1.Flex** (ARM, "Always Free eligible").
     Coloca **2 OCPU / 12 GB** (sobra de mais pra um SFU pessoal; ainda dentro do
     free de 4 OCPU/24GB).
   - Se der **"Out of capacity"** (comum no ARM de SP): tenta de novo mais tarde,
     ou usa a shape AMD **VM.Standard.E2.1.Micro** (também free, mais fraca mas
     serve). Se travar de vez, o plano B é Hetzner (~$5) — mesmo guia, sem o drama.
3. **Add SSH keys**: deixa gerar e **baixa a chave privada** (guarda bem — é como
   você entra na VM).
4. **Create**. Anota o **Public IP address** que aparece quando ela sobe.

---

## Parte 3 — Abrir as portas (o passo que todo mundo erra) (10 min)

No Oracle, o tráfego passa por **DOIS** filtros. Você tem que abrir nos **dois**,
senão a call não conecta.

### 3a. Security List da rede (VCN)

1. Na página da instância → clica na **Virtual Cloud Network** (VCN) → **Security
   Lists** → a "Default Security List".
2. **Add Ingress Rules** — adiciona uma regra pra cada linha (Source CIDR `0.0.0.0/0`):

   | Porta / Range   | Protocolo | Pra quê                          |
   |-----------------|-----------|----------------------------------|
   | 80              | TCP       | emissão do certificado TLS       |
   | 443             | TCP       | sinalização `wss` + TURN/TLS     |
   | 7881            | TCP       | WebRTC fallback (redes restritas)|
   | 3478            | UDP       | TURN/UDP                         |
   | 50000–60000     | UDP       | mídia WebRTC (voz/vídeo)         |

   (SSH 22/TCP já vem aberto.)

### 3b. Firewall da própria VM (iptables)

A imagem Ubuntu do Oracle vem com um iptables que **bloqueia tudo** menos SSH.
Conecta via SSH e roda:

```bash
ssh -i sua-chave.key ubuntu@SEU_IP_PUBLICO

sudo iptables -I INPUT -p tcp --dport 80    -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 443   -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 7881  -j ACCEPT
sudo iptables -I INPUT -p udp --dport 3478  -j ACCEPT
sudo iptables -I INPUT -p udp --dport 50000:60000 -j ACCEPT
sudo netfilter-persistent save
```

> `-I INPUT` **insere no topo** (antes da regra de REJECT do Oracle). `netfilter-persistent
> save` faz sobreviver a reboot. Sem isso, as portas ficam fechadas mesmo com a
> Security List aberta.

---

## Parte 4 — Domínio grátis (DuckDNS) (5 min)

O `wss` (sinalização segura) exige um **domínio** com certificado TLS. Se você não
tem um, usa o **DuckDNS** (grátis):

1. Entra em <https://www.duckdns.org> (login com Google/GitHub).
2. Cria um subdomínio, ex.: `astra-livekit` → vira `astra-livekit.duckdns.org`.
3. No campo **current ip**, coloca o **IP público da VM** e salva.

Pronto — `astra-livekit.duckdns.org` agora aponta pra tua VM. O Caddy vai emitir o
certificado Let's Encrypt automático pra esse nome.

---

## Parte 5 — Subir o LiveKit (Docker + gerador oficial) (10 min)

Ainda no SSH da VM:

1. Instala o Docker:
   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker $USER
   ```
   Sai (`exit`) e conecta de novo pro grupo `docker` valer.

2. Gera a config oficial (ele monta Caddy + LiveKit + Redis já certinho):
   ```bash
   mkdir ~/livekit && cd ~/livekit
   docker run --rm -it -v$PWD:/output livekit/generate
   ```
   - Quando perguntar o **domain**, põe `astra-livekit.duckdns.org`.
   - No fim ele imprime **API Key** e **API Secret** e gera 4 arquivos
     (`caddy.yaml`, `docker-compose.yaml`, `livekit.yaml`, `redis.conf`).
   - **Copia a API Key e a API Secret** — você usa na Parte 6.

3. Sobe tudo:
   ```bash
   docker compose up -d
   ```

4. Confere que subiu e que o TLS saiu:
   ```bash
   docker compose logs --tail=50 caddy   # tem que emitir o cert sem erro
   docker compose logs --tail=50 livekit
   ```

---

## Parte 6 — Apontar o backend pro teu servidor (Railway) (2 min)

No painel da Railway, serviço da **API** (`umbra-api-production`) → **Variables** →
troca as 3:

| Variável             | Novo valor                                  |
|----------------------|---------------------------------------------|
| `LIVEKIT_URL`        | `wss://astra-livekit.duckdns.org`           |
| `LIVEKIT_API_KEY`    | *(a API Key que o gerador imprimiu)*        |
| `LIVEKIT_API_SECRET` | *(a API Secret que o gerador imprimiu)*     |

Salva → a Railway **redeploya** sozinha. (Se não redeployar, força um redeploy.)

> Enquanto testa, **não commita** essas chaves em lugar nenhum. Elas vivem só na
> Railway e no `livekit.yaml` da VM.

---

## Parte 7 — Testar

1. Entra num canal de voz por **duas** contas (ou app + web).
2. Liga câmera / compartilha tela. Tem que ver/ouvir ao vivo.
3. Screenshare: o app já manda **60fps H264**; num device real (A55) o encoder de
   hardware sustenta. Em rede fraca, ele derruba **resolução** (não fps) — é de
   propósito, pra não congelar.

---

## Se der errado (troubleshooting)

- **Conecta mas trava/sem áudio-vídeo** → quase sempre é **UDP fechado**. Reconfere
  a Parte 3 (as DUAS: Security List **e** iptables). Testa a porta:
  `nc -vzu SEU_IP 3478` de outra máquina.
- **`wss` não conecta / erro de certificado** → DNS do DuckDNS não propagou ou porta
  80/443 fechada. `docker compose logs caddy` mostra o erro de emissão do cert.
- **App diz "Chamadas desativadas no servidor" (503)** → alguma das 3 env vars da
  Railway está vazia/errada. Confere a `/api/voice/config`.
- **"Out of capacity" ao criar a VM** → tenta outra hora ou a shape AMD Micro; plano
  B é Hetzner (~$5/mês), mesmo passo-a-passo a partir da Parte 3.

---

## Manutenção

- Atualizar o LiveKit: `cd ~/livekit && docker compose pull && docker compose up -d`.
- A VM reinicia sozinha rara vez; o `docker compose` tem `restart: unless-stopped`,
  então volta sozinho. As regras de iptables persistem via `netfilter-persistent`.
