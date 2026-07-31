# Migra o banco do Astra de um Postgres pra outro (Neon -> Supabase).
#
# Copia o schema E os dados. Nao muda nada na origem: so LE de la.
#
# As URLs vem por VARIAVEL DE AMBIENTE, nao por parametro, de proposito: parametro
# de linha de comando fica no historico do PowerShell e aparece na lista de
# processos do Windows. Senha de banco nao passa por ali.
#
#   $env:ORIGEM  = "postgresql://...neon.tech/...?sslmode=require"
#   $env:DESTINO = "postgresql://postgres.<ref>:<senha>@aws-0-<regiao>.pooler.supabase.com:5432/postgres"
#   .\tools\migrar-para-supabase.ps1
#
# Depois de rodar, feche o terminal (ou rode `Remove-Item Env:ORIGEM, Env:DESTINO`)
# pra as strings nao ficarem na sessao.

$ErrorActionPreference = 'Stop'

$origem  = $env:ORIGEM
$destino = $env:DESTINO

if (-not $origem)  { Write-Host "Falta \$env:ORIGEM (a URL do Neon)."   -ForegroundColor Red; exit 1 }
if (-not $destino) { Write-Host "Falta \$env:DESTINO (a URL do Supabase)." -ForegroundColor Red; exit 1 }

foreach ($exe in 'pg_dump', 'pg_restore', 'psql') {
    if (-not (Get-Command $exe -ErrorAction SilentlyContinue)) {
        Write-Host "$exe nao esta no PATH." -ForegroundColor Red
        Write-Host "Instale as ferramentas de cliente do Postgres:" -ForegroundColor Yellow
        Write-Host "  winget install PostgreSQL.PostgreSQL.17" -ForegroundColor Yellow
        Write-Host "e reabra o terminal (o instalador mexe no PATH)." -ForegroundColor Yellow
        exit 1
    }
}

# O restore recria as tabelas do zero. Se o destino ja tem tabela (ex: o
# db:migrate rodou antes), o pg_restore para em "already exists" e voce fica com
# metade migrado. Melhor descobrir isso AGORA do que no meio.
Write-Host "[0/3] Conferindo se o destino esta vazio..." -ForegroundColor Cyan
$existentes = & psql --dbname=$destino -tAc "select count(*) from pg_tables where schemaname in ('public','drizzle')"
if ($LASTEXITCODE -ne 0) { Write-Host "Nao consegui conectar no DESTINO. Confira a string (session pooler, porta 5432)." -ForegroundColor Red; exit 1 }
if ([int]$existentes.Trim() -gt 0) {
    Write-Host "O destino ja tem $($existentes.Trim()) tabelas — o restore ia colidir." -ForegroundColor Red
    Write-Host "Se elas sao so o schema recem-criado (SEM dados que voce queira), limpe no SQL Editor:" -ForegroundColor Yellow
    Write-Host "    drop schema if exists drizzle cascade;" -ForegroundColor Yellow
    Write-Host "    drop schema public cascade;" -ForegroundColor Yellow
    Write-Host "    create schema public;" -ForegroundColor Yellow
    Write-Host "e rode este script de novo. ATENCAO: isso apaga o RLS/grants — refaca o SQL de seguranca DEPOIS." -ForegroundColor Yellow
    exit 1
}
Write-Host "      destino vazio, pode seguir" -ForegroundColor Green

$dump = Join-Path $PSScriptRoot "astra-dump-$(Get-Date -Format 'yyyyMMdd-HHmm').dump"

# -n public  = as tabelas do app
# -n drizzle = o diario de migrations. SEM ele o `db:migrate` do boot acha que
#              nenhuma migration rodou e tenta recriar tudo por cima -> erro.
# --no-owner/--no-privileges: os papeis (roles) do Neon nao existem no Supabase;
#              sem isso o restore quebra em GRANT/ALTER OWNER.
Write-Host "[1/3] Lendo o banco de origem..." -ForegroundColor Cyan
& pg_dump --dbname=$origem --format=custom --no-owner --no-privileges `
          --schema=public --schema=drizzle --file=$dump
if ($LASTEXITCODE -ne 0) { Write-Host "pg_dump falhou. Se disser 'compute time quota', a cota do Neon ainda nao virou." -ForegroundColor Red; exit 1 }

$mb = [math]::Round((Get-Item $dump).Length / 1MB, 2)
Write-Host "      dump salvo: $dump ($mb MB)" -ForegroundColor Green

Write-Host "[2/3] Escrevendo no destino..." -ForegroundColor Cyan
# --no-owner de novo (o dono no destino e o usuario da conexao) e
# --exit-on-error pra parar no primeiro problema em vez de deixar meio migrado.
& pg_restore --dbname=$destino --no-owner --no-privileges --exit-on-error $dump
if ($LASTEXITCODE -ne 0) {
    Write-Host "pg_restore falhou. O dump esta salvo em $dump - da pra tentar de novo sem reler o Neon." -ForegroundColor Red
    exit 1
}

Write-Host "[3/3] Conferindo..." -ForegroundColor Cyan
& pg_restore --list $dump | Select-String -Pattern 'TABLE DATA' | Measure-Object |
    ForEach-Object { Write-Host "      $($_.Count) tabelas com dados copiadas" -ForegroundColor Green }

Write-Host ""
Write-Host "Pronto. Agora troque DATABASE_URL no Render pela URL do Supabase." -ForegroundColor Green
Write-Host "Guarde $dump ate confirmar que o app subiu - e o seu backup." -ForegroundColor Yellow
