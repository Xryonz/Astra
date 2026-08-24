
import { S3Client, PutObjectCommand, DeleteObjectCommand } from '@aws-sdk/client-s3'
import path from 'path'
import fs from 'fs'
import crypto from 'crypto'
import sharp from 'sharp'

const {
  R2_ACCOUNT_ID,
  R2_ACCESS_KEY_ID,
  R2_SECRET_ACCESS_KEY,
  R2_BUCKET,
  R2_PUBLIC_URL,
  // Escapes pra NAO depender da Cloudflare.
  //
  // Isto aqui sempre foi um cliente S3 comum: a unica coisa presa ao R2 era a URL
  // do endpoint, montada a partir do account id. Com S3_ENDPOINT preenchido, o
  // mesmo codigo fala com Supabase Storage, Backblaze B2, MinIO — qualquer coisa
  // que entenda S3. Importa porque o R2 exige cartao cadastrado mesmo no plano
  // gratuito, e nem todo mundo tem cartao pra dar.
  S3_ENDPOINT,
  S3_REGION,
} = process.env

// Endpoint proprio OU account id do R2 — um dos dois basta.
const ENDPOINT = S3_ENDPOINT || (R2_ACCOUNT_ID ? `https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com` : null)
const R2_READY = !!(ENDPOINT && R2_ACCESS_KEY_ID && R2_SECRET_ACCESS_KEY && R2_BUCKET && R2_PUBLIC_URL)

const UPLOAD_DIR = path.resolve(process.cwd(), 'uploads')

const s3 = R2_READY
  ? new S3Client({
      // 'auto' e o que o R2 espera; o Supabase e o B2 querem a regiao de verdade.
      region: S3_REGION || 'auto',
      endpoint: ENDPOINT!,
      // Caminho no lugar de subdominio (bucket.host -> host/bucket). O R2 aceita os
      // dois; o Supabase so funciona assim. Ligado apenas quando ha endpoint
      // proprio, pra nao mexer em quem ja esta no R2 e funcionando.
      forcePathStyle: !!S3_ENDPOINT,
      credentials: {
        accessKeyId:     R2_ACCESS_KEY_ID!,
        secretAccessKey: R2_SECRET_ACCESS_KEY!,
      },
    })
  : null

// 's3' quando o destino nao e a Cloudflare — /health passa a dizer PRA ONDE as
// imagens vao, nao so que saem do disco.
export const storageMode = !R2_READY ? 'local' : (S3_ENDPOINT ? 's3' : 'r2')

// QUAIS variaveis faltam pro storage sair do disco. So os NOMES, nunca os valores.
//
// Existe porque "storage: local" nao diz nada acionavel: sao cinco variaveis e uma
// so que falte derruba as cinco. De fora nao havia como saber qual, e o unico jeito
// era abrir o painel do Render e conferir uma por uma — que e exatamente o passo em
// que alguem erra de novo. Nome de variavel de ambiente nao e segredo.
export const storageFalta: string[] = (() => {
  if (R2_READY) return []
  const falta: string[] = []
  if (!ENDPOINT)             falta.push('S3_ENDPOINT')
  if (!R2_ACCESS_KEY_ID)     falta.push('R2_ACCESS_KEY_ID')
  if (!R2_SECRET_ACCESS_KEY) falta.push('R2_SECRET_ACCESS_KEY')
  if (!R2_BUCKET)            falta.push('R2_BUCKET')
  if (!R2_PUBLIC_URL)        falta.push('R2_PUBLIC_URL')
  return falta
})()

// Host publico do R2 (quando configurado), derivado uma vez.
const R2_HOST = (() => {
  try { return R2_PUBLIC_URL ? new URL(R2_PUBLIC_URL).hostname : null } catch { return null }
})()

// URL que o PROPRIO app gerou (persistDataUri/putAttachment): path local /uploads/
// ou o host publico do R2. A validacao de host das rotas (isAllowedIcon do server,
// isAllowedImageUrl do perfil) precisa trata-las como confiaveis — senao reenviar um
// icone/banner JA salvo (o cliente reenvia o campo inalterado na proxima edicao) cai
// como "URL nao permitida": /uploads/x quebra o `new URL()` e o host do R2 nao esta
// no allowlist de terceiros. Bug do "troquei o icone, ai o banner nao troca mais".
export function isOwnStorageUrl(url: string | null | undefined): boolean {
  if (!url) return false
  if (url.startsWith('/uploads/')) return true
  if (!R2_HOST) return false
  try { const { hostname } = new URL(url); return hostname === R2_HOST || hostname.endsWith(`.${R2_HOST}`) }
  catch { return false }
}

// Hosts de GIF que o PROPRIO app oferece. O seletor de GIF devolve a URL da CDN
// do Giphy e o cliente manda ela como anexo — entao "so armazenamento proprio"
// quebraria o recurso. Estes sao os unicos terceiros aceitos.
const CDN_DE_GIF = ['giphy.com', 'media.giphy.com']

// ANEXO SO PODE APONTAR PRA ONDE O APP CONHECE.
//
// O AttachmentSchema aceita qualquer http(s), e anexo e a unica coisa que o
// cliente manda e todo mundo na conversa BAIXA sozinho. Com URL livre, mandar uma
// mensagem com anexo apontando pro seu proprio servidor entrega, de graca, o IP e
// o horario de leitura de cada pessoa da sala — o velho pixel de rastreio, so que
// dentro de um sussurro. E a URL pode servir uma imagem hoje e outra coisa amanha,
// porque quem hospeda e o remetente.
//
// Por isso a checagem e de HOST e nao de extensao: o que importa nao e o que o
// arquivo parece ser, e sim quem responde por ele.
export function urlDeAnexoPermitida(url: string | null | undefined): boolean {
  if (!url) return false
  if (isOwnStorageUrl(url)) return true
  try {
    const { hostname, protocol } = new URL(url)
    if (protocol !== 'https:') return false
    return CDN_DE_GIF.some((h) => hostname === h || hostname.endsWith(`.${h}`))
  } catch { return false }
}

// Devolve a primeira URL reprovada (pra mensagem de erro dizer QUAL), ou null.
export function primeiroAnexoNaoPermitido(
  anexos: ReadonlyArray<{ url?: string; thumbUrl?: string }> | undefined,
): string | null {
  for (const a of anexos ?? []) {
    if (!urlDeAnexoPermitida(a.url)) return a.url ?? '(vazia)'
    if (a.thumbUrl && !urlDeAnexoPermitida(a.thumbUrl)) return a.thumbUrl
  }
  return null
}

// So imagem/video/audio abrem no navegador; o resto BAIXA. O serving local ja
// fazia isso (Content-Disposition no express.static do /uploads), mas o bucket
// nao — e o bucket e o caminho de producao. Sem isto, o mesmo arquivo que o
// caminho local se recusa a renderizar abre inline quando vem do S3.
function abreInline(mime: string): boolean {
  const base = mime.split(';')[0].trim().toLowerCase()
  return base.startsWith('image/') || base.startsWith('video/') || base.startsWith('audio/')
}

export async function putAttachment(key: string, body: Buffer, mime: string): Promise<string> {
  if (s3) {
    await s3.send(new PutObjectCommand({
      Bucket: R2_BUCKET,
      Key: key,
      Body: body,
      ContentType: mime,
      ...(abreInline(mime) ? {} : { ContentDisposition: 'attachment' }),

      CacheControl: 'public, max-age=31536000, immutable',
    }))
    return `${R2_PUBLIC_URL!.replace(/\/$/, '')}/${key}`
  }

  if (!fs.existsSync(UPLOAD_DIR)) fs.mkdirSync(UPLOAD_DIR, { recursive: true })
  await fs.promises.writeFile(path.join(UPLOAD_DIR, key), body)
  return `/uploads/${key}`
}

// Apagar o arquivo, esteja ele onde estiver.
//
// Existe porque tudo que APAGA (emoji removido, mensagem que expirou) so sabia
// apagar do disco local: o `if (url.startsWith('/uploads/'))` espalhado pelas rotas
// simplesmente NAO FAZIA NADA quando o arquivo estava no bucket. O objeto ficava
// orfao pra sempre, e num plano de 1 GB isso enche sozinho.
//
// Engole erro de proposito: apagar arquivo e limpeza, nunca motivo pra falhar a
// acao que a pessoa pediu.
export async function removeAttachment(url: string | null | undefined): Promise<void> {
  if (!url) return
  try {
    if (url.startsWith('/uploads/')) {
      await fs.promises.unlink(path.join(UPLOAD_DIR, url.slice('/uploads/'.length)))
      return
    }
    if (!s3 || !R2_PUBLIC_URL) return
    const raiz = R2_PUBLIC_URL.replace(/\/$/, '') + '/'
    if (!url.startsWith(raiz)) return  // link de terceiro (avatar do Google, GIF do Giphy)
    const key = url.slice(raiz.length)
    if (!key) return
    await s3.send(new DeleteObjectCommand({ Bucket: R2_BUCKET, Key: key }))
  } catch { /* limpeza e best-effort */ }
}

function mimeExt(mime: string): string {
  switch (mime) {
    case 'image/png':  return 'png'
    case 'image/jpeg': return 'jpg'
    case 'image/webp': return 'webp'
    case 'image/gif':  return 'gif'
    default:           return 'bin'
  }
}

const DATA_URI_RE = /^data:([\w.+-]+\/[\w.+-]+)?(;base64)?,(.*)$/s

// Tira data-URIs do banco: decodifica, joga o binario no R2 (ou disco no
// fallback) e devolve a URL. Se ja for URL (ou vazio/null), passa direto.
// PNG/JPEG viram WebP (igual upload.ts, encolhe MUITO); GIF/WebP ficam como
// estao pra preservar animacao. Falha de decode -> guarda o original.
export async function persistDataUri<T extends string | null | undefined>(value: T): Promise<T | string> {
  if (!value || !value.startsWith('data:')) return value
  const m = DATA_URI_RE.exec(value)
  if (!m) return value

  const mime = m[1] || 'image/png'
  const input = m[2] ? Buffer.from(m[3], 'base64') : Buffer.from(decodeURIComponent(m[3]))

  let body: Buffer = input
  let outMime = mime
  let ext = mimeExt(mime)
  if (mime.startsWith('image/') && mime !== 'image/gif' && mime !== 'image/webp') {
    try {
      // ESTE E O ULTIMO ENCODE DA IMAGEM — o que sair daqui e o que todo mundo ve,
      // pra sempre. O 82 era o mesmo numero usado pra miniatura de anexo, onde a
      // imagem aparece pequena; num banner desenhado com 2560px de largura ele
      // aparece como bloco em ceu, em pele e em degrade. E a perda soma com a do
      // JPEG que o recorte ja fez: comprimir duas vezes a 82 nao da 82.
      //
      // 92 e o joelho da curva do WebP — acima disso o arquivo cresce rapido e o
      // olho para de ver diferenca. effort 6 (o padrao e 4) procura mais tempo pela
      // codificacao menor: gasta CPU no upload, uma vez, e devolve arquivo menor
      // COM a mesma qualidade. alphaQuality 100 mantem a transparencia exata (por
      // padrao o canal alfa tambem e comprimido, e borda de PNG recortado fica
      // suja). smartSubsample preserva a informacao de cor: sem ele o WebP joga
      // fora 3/4 do croma, e e por isso que vermelho e laranja saiam chapados.
      body = await sharp(input)
        .webp({ quality: 92, effort: 6, alphaQuality: 100, smartSubsample: true })
        .toBuffer()
      outMime = 'image/webp'
      ext = 'webp'
    } catch { /* imagem estranha: guarda o original */ }
  }

  const key = `${crypto.randomBytes(16).toString('hex')}.${ext}`
  return putAttachment(key, body, outMime)
}

// O MAIOR LADO EM QUE UMA IMAGEM DE IDENTIDADE É DESENHADA, em pixels de tela.
//
// Não é chute: os tamanhos foram levantados no cliente, um por um.
//
//	avatar de autor / lista de membros / faixa da chamada ...... 20 a 40dp
//	ícone de servidor na barra lateral ......................... 54dp
//	mini-imagem de cargo ....................................... 40dp
//	avatar na tela de boas-vindas (o MAIOR uso que existe) ...... 96dp
//
// Numa tela de densidade dupla, 96dp são 192 pixels reais — então 256 cobre todos eles com
// folga e ainda deixa margem para um monitor mais denso aparecer amanhã.
const LADO_DE_EXIBICAO = 256

// Guarda a imagem em DUAS versões e devolve as duas URLs.
//
// Serve tudo que é IMAGEM DE IDENTIDADE desenhada pequena: avatar de pessoa, avatar de
// bot, ícone de constelação, mini-imagem de cargo. Não serve banner — ver o porquê no
// final deste comentário.
//
// O PROBLEMA QUE ISTO RESOLVE, medido no cliente: o recorte salva com 1024 pixels de lado,
// e era esse arquivo — algumas centenas de KB — que o app baixava para desenhar o círculo
// de 22 pixels ao lado de cada mensagem. Dezenas por tela, em toda conversa aberta. E a
// barra lateral é pior ainda: ela mostra o ícone de TODAS as constelações de uma vez.
// É a maior causa de "as imagens demoram" que existe hoje.
//
// A INVERSÃO É O TRUQUE, e ela vale mais que a economia: quem vai para a coluna de sempre
// (`avatarUrl`, `iconUrl`) é a versão de EXIBIÇÃO, e a original ganha uma coluna nova.
// Assim as vinte projeções de usuário espalhadas pelas rotas continuam lendo o mesmo campo
// e passam a receber o arquivo pequeno sem nenhuma edição — e o ganho vale de graça para o
// desktop, o web e o mobile ao mesmo tempo. Fosse ao contrário, seriam vinte lugares para
// mudar, vinte DTOs para acompanhar, e um esquecido em silêncio.
//
// A ORIGINAL É PRESERVADA de propósito. Ela não é usada para desenhar nada hoje, e custa
// espaço no bucket — mas sem ela um reprocessamento futuro (outro tamanho, outro formato)
// seria impossível, e o que se teria jogado fora é justamente o que não dá para recuperar.
//
// GIF NÃO É TOCADO: redimensionar animação com o `sharp` desta configuração devolveria só
// o primeiro quadro, e um avatar animado que para de animar é regressão, não otimização.
// Nesse caso a original volta nula, e quem chama não precisa saber por quê.
//
// BANNER NÃO PASSA POR AQUI, e a diferença é onde cada imagem aparece: banner é desenhado
// GRANDE, no cartão de perfil e no topo da constelação. Encolher trocaria velocidade por
// borrão no único lugar em que ele existe.
export async function persistImagemDeExibicao(
  value: string | null | undefined,
): Promise<{ url: string | null; original: string | null }> {
  if (!value) return { url: value ?? null, original: null }

  const original = await persistDataUri(value)
  if (typeof original !== 'string') return { url: null, original: null }

  // Já era URL (reenvio do campo inalterado, avatar do Google): nada a encolher, e
  // baixar para reprocessar seria trabalho e superfície de rede por nada.
  if (!value.startsWith('data:')) return { url: original, original: null }
  if (original.endsWith('.gif')) return { url: original, original: null }

  try {
    const m = DATA_URI_RE.exec(value)
    if (!m) return { url: original, original: null }
    const bruto = m[2] ? Buffer.from(m[3], 'base64') : Buffer.from(decodeURIComponent(m[3]))

    // `withoutEnlargement` para não INVENTAR pixel: quem manda um avatar de 64px recebe
    // de volta 64px, e não um borrão de 256 com quatro vezes o peso.
    const pequeno = await sharp(bruto)
      .resize({ width: LADO_DE_EXIBICAO, height: LADO_DE_EXIBICAO, fit: 'inside', withoutEnlargement: true })
      .webp({ quality: 90, effort: 6, alphaQuality: 100, smartSubsample: true })
      .toBuffer()

    // NÃO GUARDA DOIS ARQUIVOS SE O SEGUNDO NÃO ENCOLHEU. Imagem que já era pequena sai
    // do redimensionamento do mesmo tamanho ou maior — recomprimir o que já é WebP às
    // vezes cresce. Nesse caso, guardar os dois pagaria espaço no bucket para servir o
    // arquivo pior. A comparação é contra o binário de ENTRADA porque é o que se tem em
    // mãos aqui; do que já foi gravado só existe a URL.
    if (pequeno.length >= bruto.length) return { url: original, original: null }

    const key = `${crypto.randomBytes(16).toString('hex')}.webp`
    const url = await putAttachment(key, pequeno, 'image/webp')
    return { url, original }
  } catch {
    // Imagem estranha: fica só com a original. Perder a otimização é aceitável; perder o
    // avatar da pessoa não é.
    return { url: original, original: null }
  }
}
