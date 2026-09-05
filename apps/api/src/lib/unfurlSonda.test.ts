import { describe, it, expect } from 'vitest'
import { cartaoDoLink } from './unfurl'

const ligada = process.env.ASTRA_SONDA_UNFURL === '1'
const sonda = ligada ? describe : describe.skip

const ALVOS = [
  'https://developer.mozilla.org/en-US/docs/Web/HTTP',
  'https://github.com/livekit/server-sdk-go',
  'https://www.wikipedia.org/',
  'https://kotlinlang.org/docs/coroutines-guide.html',
  'https://go.dev/blog/',
  'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
  'https://g1.globo.com/',
  'https://open.spotify.com/track/4PTG3Z6ehGkBFwjybzWkR8',
  'https://news.ycombinator.com/',
]

sonda('previa de link contra sites de verdade', () => {
  for (const alvo of ALVOS) {
    it(alvo, async () => {
      const cartao = await cartaoDoLink(alvo)
      console.log(alvo, '->', JSON.stringify(cartao, null, 2))
      expect(cartao).not.toBeNull()
      expect(cartao!.titulo?.length ?? 0).toBeGreaterThan(0)
      expect(cartao!.titulo!.length).toBeLessThanOrEqual(140)
      expect(cartao!.descricao?.length ?? 0).toBeLessThanOrEqual(240)
      if (cartao!.imagem) expect(cartao!.imagem.startsWith('http')).toBe(true)
    }, 20_000)
  }

  it('um redirecionamento comum chega ao destino', async () => {
    const cartao = await cartaoDoLink('http://go.dev/blog/')
    console.log('redirect ->', JSON.stringify(cartao, null, 2))
    expect(cartao).not.toBeNull()
    expect(cartao!.url.startsWith('https://')).toBe(true)
  }, 20_000)

  it('o encurtador do youtube chega no video', async () => {
    const cartao = await cartaoDoLink('https://youtu.be/dQw4w9WgXcQ')
    console.log('youtu.be ->', JSON.stringify(cartao, null, 2))
    expect(cartao).not.toBeNull()
    expect(cartao!.url).toContain('youtube.com/watch')
  }, 20_000)

  it('o que nao e pagina nao vira cartao', async () => {
    expect(await cartaoDoLink('https://go.dev/doc/gopher/gopher5logo.jpg')).toBeNull()
    expect(await cartaoDoLink('ftp://exemplo.invalido/arquivo')).toBeNull()
    expect(await cartaoDoLink('isto nao e um endereco')).toBeNull()
  }, 20_000)

  it('um endereco de dentro da rede nao vira cartao', async () => {
    expect(await cartaoDoLink('http://127.0.0.1:3000/')).toBeNull()
    expect(await cartaoDoLink('http://[::1]/')).toBeNull()
    expect(await cartaoDoLink('http://169.254.169.254/latest/meta-data/')).toBeNull()
  }, 20_000)
})
