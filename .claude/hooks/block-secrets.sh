#!/usr/bin/env bash
# Bloqueia QUALQUER ferramenta de leitura de tocar arquivo de segredo.
#
# Por que existe: em 2026-07-31 uma auditoria achou um dump do apps/api/.env
# (com DATABASE_URL real) dentro de uma transcricao. A causa foi um SUBAGENTE
# grepando o .env inteiro. Regra de comportamento nao basta — subagente nasce
# sem o contexto da conversa. Hook e executado pelo harness, entao vale pra
# todo mundo: sessao principal, subagente, skill.
#
# Recebe o JSON do PreToolUse no stdin e varre o payload cru: assim uma so
# regra cobre file_path (Read), command (Bash), path/pattern (Grep/Glob) sem
# depender de jq (que NAO existe nesta maquina).
#
# O casamento e ".env NAO seguido de letra/digito/ponto" — foi preciso ser
# assim porque a primeira versao usava [[:space:]] dentro da alternacao e
# silenciosamente NAO pegava "cat .env | head". Excluir o ponto e o que mantem
# .env.example legivel; os .env.<ambiente> reais entram na lista explicita.
#
# Saida vazia = liberado. Nao bloqueia Write/Edit de proposito: documentacao
# (o proprio ESTADO.md) fala de ".env" o tempo todo e seria falso positivo.

payload=$(cat)

SECRETS_RE='\.env([^A-Za-z0-9.]|$)'
SECRETS_RE="$SECRETS_RE"'|\.env\.(local|production|development|test)'
SECRETS_RE="$SECRETS_RE"'|\.(keystore|pem|p12|jks)([^A-Za-z0-9.]|$)'
SECRETS_RE="$SECRETS_RE"'|keystore\.properties|firebase-adminsdk|id_rsa|\.npmrc|credentials\.json'

if printf '%s' "$payload" | grep -qEi "$SECRETS_RE"; then
  printf '%s' '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"Arquivo de segredo bloqueado pela politica do projeto (.claude/hooks/block-secrets.sh). Se precisa saber QUAIS chaves existem, peca so os NOMES (ex: grep -o para as chaves do .env.example), nunca os valores."}}'
fi

exit 0
