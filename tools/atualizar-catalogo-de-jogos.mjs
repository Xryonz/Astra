import { writeFileSync } from 'node:fs'

const FONTE = 'https://discord.com/api/v9/applications/detectable'
const DESTINO = new URL(
  '../mobile-native/desktopApp/src/main/resources/jogos.tsv',
  import.meta.url,
)

const resposta = await fetch(FONTE)
if (!resposta.ok) {
  console.error(`a fonte respondeu ${resposta.status}`)
  process.exit(1)
}

const bruto = await resposta.json()
console.log(`${bruto.length} aplicacoes na fonte`)

const vistos = new Set()
const linhas = []

for (const app of bruto) {
  const nome = (app.name ?? '').replace(/\s+/g, ' ').trim()
  if (!nome) continue

  for (const exe of app.executables ?? []) {
    if (exe.os !== 'win32' || exe.is_launcher) continue

    const sufixo = (exe.name ?? '').toLowerCase().replace(/\\/g, '/').trim()
    if (!sufixo.endsWith('.exe')) continue
    if (sufixo.startsWith('>')) continue

    const chave = `${sufixo}\t${nome}`
    if (vistos.has(chave)) continue
    vistos.add(chave)
    linhas.push(chave)
  }
}

linhas.sort()

const cabecalho = [
  `# catalogo de jogos do Astra — nome de executavel -> titulo`,
  `# destilado de ${FONTE}`,
  `# ${linhas.length} entradas · instantaneo de ${new Date().toISOString().slice(0, 10)}`,
  `# refazer com: node tools/atualizar-catalogo-de-jogos.mjs`,
]

const texto = [...cabecalho, ...linhas].join('\n') + '\n'
writeFileSync(DESTINO, texto, 'utf8')

const bases = new Map()
for (const l of linhas) {
  const [sufixo, jogo] = l.split('\t')
  const base = sufixo.split('/').pop()
  if (!bases.has(base)) bases.set(base, new Set())
  bases.get(base).add(jogo)
}
const ambiguas = [...bases.values()].filter((s) => s.size > 1).length

console.log(`${linhas.length} entradas · ${bases.size} nomes de executavel`)
console.log(`${ambiguas} ambiguos (exigem casar o caminho)`)
console.log(`${(Buffer.byteLength(texto) / 1024).toFixed(0)} KB em ${DESTINO.pathname}`)
