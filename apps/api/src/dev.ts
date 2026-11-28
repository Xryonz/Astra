import 'dotenv/config'
import { ehBancoLocal } from './lib/bancoLocal'

if (!ehBancoLocal(process.env.DATABASE_URL ?? '')) {
  console.error(
    '[dev] A API de desenvolvimento só liga num banco desta máquina (localhost, 127.0.0.1 ou [::1]). ' +
      'No banco de produção, ela rodaria os trabalhos de fundo junto com o servidor do Render.',
  )
  process.exit(1)
}

require('./index')
