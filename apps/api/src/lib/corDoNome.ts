const HEX = '#[0-9a-fA-F]{6}'

const FORMAS = [
  HEX,
  `gradient:\\d+:${HEX}:${HEX}`,
  `anim:arcoiris:${HEX}`,
  `anim:varredura:${HEX}:${HEX}`,
  `anim:pulso:${HEX}`,
]

export const COR_DE_NOME = new RegExp(`^(${FORMAS.join('|')})$`)

export function ehCorDeNome(valor: unknown): boolean {
  return typeof valor === 'string' && COR_DE_NOME.test(valor)
}
