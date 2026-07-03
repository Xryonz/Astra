# Verificação de email — setup do Gmail (5 min)

O backend manda o código de 6 dígitos pelo **seu Gmail** (grátis, ~500 emails/dia).
Sem essas 2 variáveis na Railway, o registro continua funcionando — só nasce
verificado direto (sem código).

## 1. Criar a "senha de app" no Google

1. Acesse https://myaccount.google.com/security
2. Confirme que a **verificação em duas etapas** está LIGADA (obrigatório pra senha de app existir)
3. Acesse https://myaccount.google.com/apppasswords
4. Nome do app: `Astra` → **Criar**
5. Copie a senha de 16 letras (formato `abcd efgh ijkl mnop`) — ela só aparece uma vez

> Senha de app ≠ sua senha do Gmail. Ela só permite ENVIAR email, e você pode
> revogar a qualquer momento nessa mesma página.

## 2. Colar na Railway

No serviço da API → **Variables** → adicionar:

| Variável | Valor |
|---|---|
| `GMAIL_USER` | `seuemail@gmail.com` |
| `GMAIL_APP_PASSWORD` | a senha de 16 letras (**sem espaços**: `abcdefghijklmnop`) |

A Railway redeploya sozinha. No log de deploy deve aparecer `[Mail] Gmail configurado`.

## 3. Testar

1. Crie uma conta nova no app com um email seu de verdade
2. A tela "Confirme seu email" abre e o código chega na caixa de entrada
   (olha o spam na primeira vez — remetente é você mesmo)
3. Código expira em 15 min; "reenviar código" gera outro

## Como funciona (resumo)

- Registro → conta nasce sem `emailVerifiedAt` + código salvo → email enviado
- App (gate na Home) → tela de código → `POST /api/auth/email/verify`
- Logar com Google **carimba o email como verificado** (o Google já provou a posse)
- Contas antigas foram todas carimbadas na migration (ninguém é travado retroativamente)
- Mailer desligado → registro auto-verifica; "reenviar" também destrava contas presas
