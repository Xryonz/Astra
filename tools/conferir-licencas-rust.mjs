import { execFileSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'

const PROIBIDAS = /^(A?GPL|LGPL|CC-BY-SA)/i

function fichas(expressao) {
  return expressao.replaceAll('/', ' OR ').match(/[()]|[^\s()]+/g) ?? []
}

export function permitida(expressao) {
  const lista = fichas(expressao)
  let i = 0
  const olhar = () => lista[i]?.toUpperCase()
  const termo = () => {
    if (lista[i] === '(') {
      i++
      const valor = alguma()
      i++
      return valor
    }
    const licenca = lista[i++] ?? ''
    if (olhar() === 'WITH') i += 2
    return !PROIBIDAS.test(licenca)
  }
  const todas = () => {
    let valor = termo()
    while (olhar() === 'AND') {
      i++
      const outra = termo()
      valor = valor && outra
    }
    return valor
  }
  const alguma = () => {
    let valor = todas()
    while (olhar() === 'OR') {
      i++
      const outra = todas()
      valor = valor || outra
    }
    return valor
  }
  return alguma()
}

function conferir() {
  const metadados = JSON.parse(
    execFileSync('cargo', ['metadata', '--format-version', '1', '--locked'], { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 }),
  )
  const nossos = new Set(metadados.workspace_members)
  const problemas = metadados.packages
    .filter((pacote) => !nossos.has(pacote.id))
    .flatMap((pacote) => {
      const nome = `${pacote.name} ${pacote.version}`
      if (!pacote.license) return [`${nome}: sem licença declarada${pacote.license_file ? ` (arquivo ${pacote.license_file})` : ''}`]
      return permitida(pacote.license) ? [] : [`${nome}: ${pacote.license}`]
    })
  console.log(`${metadados.packages.length - nossos.size} pacotes de terceiros conferidos`)
  if (problemas.length) {
    console.error(['Licença fora do permitido (GPL, AGPL, LGPL e CC-BY-SA não entram):', ...problemas].join('\n'))
    process.exit(1)
  }
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) conferir()
