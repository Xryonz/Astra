import { readFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'

const TROCAS = {
  nao: 'não', voce: 'você', voces: 'vocês', ninguem: 'ninguém', alguem: 'alguém',
  orbita: 'órbita', orbitas: 'órbitas',
  constelacao: 'constelação', constelacoes: 'constelações',
  codigo: 'código', codigos: 'códigos', icone: 'ícone', icones: 'ícones',
  permissao: 'permissão', permissoes: 'permissões',
  usuario: 'usuário', usuarios: 'usuários',
  ultima: 'última', ultimo: 'último', ultimas: 'últimas', ultimos: 'últimos',
  proxima: 'próxima', proximo: 'próximo',
  sera: 'será', serao: 'serão', sao: 'são', estao: 'estão', entao: 'então',
  tambem: 'também', apos: 'após', ate: 'até',
  numero: 'número', numeros: 'números', minimo: 'mínimo', maximo: 'máximo',
  sessao: 'sessão', sessoes: 'sessões', conexao: 'conexão',
  notificacao: 'notificação', notificacoes: 'notificações',
  informacao: 'informação', informacoes: 'informações',
  descricao: 'descrição', descricoes: 'descrições',
  aparencia: 'aparência', memoria: 'memória', historico: 'histórico',
  publico: 'público', publica: 'pública', publicas: 'públicas', publicos: 'públicos',
  anonimo: 'anônimo', anonima: 'anônima',
  opcao: 'opção', opcoes: 'opções', botao: 'botão', botoes: 'botões',
  secao: 'seção', secoes: 'seções', versoes: 'versões',
  atualizacao: 'atualização', atualizacoes: 'atualizações',
  configuracao: 'configuração', configuracoes: 'configurações',
  visao: 'visão', gestao: 'gestão', edicao: 'edição', exibicao: 'exibição',
  posicao: 'posição', resolucao: 'resolução', duracao: 'duração',
  reacao: 'reação', reacoes: 'reações', mencao: 'menção', mencoes: 'menções',
  transmissao: 'transmissão', traducao: 'tradução', supressao: 'supressão',
  possivel: 'possível', impossivel: 'impossível',
  disponivel: 'disponível', indisponivel: 'indisponível',
  visivel: 'visível', invisivel: 'invisível', niveis: 'níveis',
  unico: 'único', unica: 'única', facil: 'fácil', dificil: 'difícil',
  util: 'útil', inutil: 'inútil', rapido: 'rápido', rapida: 'rápida',
  automatico: 'automático', automatica: 'automática', basico: 'básico',
  grafico: 'gráfico', graficos: 'gráficos', diagnostico: 'diagnóstico',
  silencio: 'silêncio', experiencia: 'experiência', referencia: 'referência',
  transparencia: 'transparência',
  presenca: 'presença', licenca: 'licença', seguranca: 'segurança',
  comeco: 'começo', coracao: 'coração', servico: 'serviço', endereco: 'endereço',
  espaco: 'espaço', espacosa: 'espaçosa', confortavel: 'confortável',
  pagina: 'página', paginas: 'páginas',
  insignia: 'insígnia', insignias: 'insígnias',
  camera: 'câmera', musica: 'música', video: 'vídeo', audio: 'áudio',
  ruido: 'ruído', saida: 'saída', saidas: 'saídas',
  medio: 'médio', maiuscula: 'maiúscula', minuscula: 'minúscula',
  relatorio: 'relatório', familia: 'família',
  amanha: 'amanhã', atras: 'atrás',
  obrigatorio: 'obrigatório', criacao: 'criação',
  expiracao: 'expiração', excecao: 'exceção', orfao: 'órfão',
  historia: 'história', calendario: 'calendário',
  acao: 'ação', acoes: 'ações', proprio: 'próprio', propria: 'própria',
  invalido: 'inválido', padrao: 'padrão', padroes: 'padrões',
  sugestao: 'sugestão', sugestoes: 'sugestões',
  credito: 'crédito', creditos: 'créditos',
  cabecalho: 'cabeçalho', identica: 'idêntica', identico: 'idêntico',
  copia: 'cópia', nucleos: 'núcleos', instancia: 'instância',
  ambar: 'âmbar', carvao: 'carvão', onix: 'ônix', artico: 'ártico',
  galaxia: 'galáxia', crepusculo: 'crepúsculo', petala: 'pétala',
  acafrao: 'açafrão', indigo: 'índigo', ardosia: 'ardósia', lilas: 'lilás',
}

const PERDOADAS = new Set([
  'versao ',
  'versao $versao',
  '--versao=',
  'Local\\\\Astra-copia-unica',
  'orbita:${alvo.id}',
  'espaco',
  'nao',
  'nasceu escondido na bandeja — sem quadro por decisao',
  'ja havia outro Astra aberto — este saiu',
  'voce@exemplo.com',
  'prime video',
  'media player',
  'audio/',
  'audio/*',
  'audio/wav',
  'app.astra.mobile.update.VERSAO',
  'Erro que a interface engoliu? saida.txt guarda tudo que o app imprimiu.',
  'nasceu escondido na bandeja — sem quadro por decisao',
  '(?iU)\\\\b(não|nao|caiu|negou|negado|erro|desisti|falhou|failed|ilegível|reiniciou|reiniciar)\\\\b',
])

const LITERAL = /"(?:[^"\\\n]|\\.)*"/g
const PALAVRA = /[A-Za-zÀ-ÿ]+/g
const INTERPOLACAO = /\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*/g
const CHAVE_TECNICA = /^[a-z0-9_.\-/*+]*$/
const SO_MINUSCULA_OU_CAPITAL = (t) => t === t.toLowerCase() || t[0] + t.slice(1).toLowerCase() === t

const arquivos = execFileSync('git', ['ls-files', 'mobile-native/**/*.kt'], { encoding: 'utf8' })
  .split('\n')
  .filter(Boolean)

const achados = []

for (const caminho of arquivos) {
  const linhas = readFileSync(caminho, 'utf8').split('\n')
  linhas.forEach((linha, i) => {
    const corte = linha.trimStart()
    if (corte.startsWith('//') || corte.startsWith('*')) return
    for (const m of linha.matchAll(LITERAL)) {
      const corpo = m[0]
      const miolo = corpo.slice(1, -1)
      if (PERDOADAS.has(miolo) || miolo.includes('://') || CHAVE_TECNICA.test(miolo)) continue
      const prosa = miolo.replace(INTERPOLACAO, ' ')
      for (const p of prosa.matchAll(PALAVRA)) {
        const token = p[0]
        if (!SO_MINUSCULA_OU_CAPITAL(token)) continue
        const certo = TROCAS[token.toLowerCase()]
        if (certo) achados.push({ caminho, linha: i + 1, token, certo, corpo })
      }
    }
  })
}

if (achados.length === 0) {
  console.log(`acentos conferidos em ${arquivos.length} arquivos Kotlin — nada a corrigir`)
  process.exit(0)
}

console.error(`\nPortuguês sem acento em ${achados.length} lugar(es):\n`)
for (const a of achados) {
  console.error(`  ${a.caminho}:${a.linha}  ${a.token} -> ${a.certo}`)
  console.error(`    ${a.corpo.slice(0, 100)}`)
}
console.error(`
Se alguma destas for PROTOCOLO e nao texto de tela — nome de arquivo, argumento de
linha de comando, chave de cache, marco de trilha relido entre versoes, ou palavra
que outro trecho procura com contains/startsWith — acrescente o texto exato ao
conjunto PERDOADAS em tools/conferir-acentos.mjs, com o motivo no commit.

Ja aconteceu de acentuar protocolo por engano: "versao " e o que o atualizador
procura no versao.txt publicado, e acentuar quebra a busca por versao nova.
`)
process.exit(1)
