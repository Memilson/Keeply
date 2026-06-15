# Roteiro do Vídeo - N2 Keeply I.A

## Duração sugerida

2 a 4 minutos.

## 1. Abertura

Falar:

Neste vídeo vou demonstrar a funcionalidade de Inteligência Artificial implementada no projeto Keeply. O recurso se chama Keeply I.A e funciona como um assistente para orientar o usuário sobre backups, máquinas, snapshots, downloads e restauração pelo agente.

## 2. Mostrar o painel web

Mostrar no navegador:

1. Dashboard do Keeply (Visão geral).
2. Menu Máquinas — lista de dispositivos.
3. Seleção de uma máquina — painel com Resumo, botões Snapshots e Plano.
4. Botão Snapshots — lista de pontos de backup com botão Explorar.
5. Explorador de snapshot — navegação de pastas, seleção e download de arquivos.

Falar:

No painel web o usuário consegue visualizar as máquinas registradas, acessar os snapshots de cada máquina e navegar os arquivos pelo explorador de snapshot. É possível baixar o snapshot inteiro ou selecionar arquivos e pastas específicas para baixar. A restauração do snapshot no disco é executada pelo Keeply Agente registrado no dispositivo.

## 3. Mostrar o mobile

Mostrar no emulador:

1. Aplicativo Keeply aberto.
2. Aba Histórico (lista de snapshots).
3. Aba Keeply I.A.
4. Campo de pergunta.

Falar:

No aplicativo mobile o usuário consegue consultar snapshots e backups na aba Histórico e baixar arquivos. O mobile não tem Dashboard e não executa restauração de snapshots. A restauração é feita pelo Keeply Agente instalado no dispositivo.

## 4. Entrada de dados

Digitar no chat:

Como faço para restaurar um snapshot?

Falar:

Aqui estou enviando uma pergunta em linguagem natural para a IA.

## 5. Processamento

Mostrar o estado de carregamento ou aguardar a resposta.

Falar:

O aplicativo envia a pergunta para o backend pelo endpoint de chat. O backend adiciona o contexto do produto Keeply e consulta um modelo de linguagem por API.

## 6. Resultado gerado pela IA

Mostrar a resposta.

Falar:

A resposta orienta o usuário de acordo com as regras reais do sistema: acessar o painel web, entrar em Máquinas, selecionar a máquina, abrir Snapshots e executar a restauração pelo Keeply Agente. Também pode indicar que o agente permite restaurar em pastas diferentes no dispositivo.

## 7. Mostrar análise, se aparecer

Se aparecer a caixa Análise no mobile, abrir rapidamente.

Falar:

Quando necessário, o sistema também mostra uma análise resumida separada da resposta final, sem misturar raciocínio bruto com a orientação principal.

## 8. Fechamento

Falar:

Com isso, a funcionalidade de IA do Keeply demonstra entrada de dados pelo usuário, processamento por modelo de linguagem integrado ao backend e retorno de uma resposta útil dentro do aplicativo.

## Checklist antes de gravar

1. Backend rodando.
2. Frontend web aberto.
3. Emulador Android aberto.
4. App mobile recompilado.
5. Aba Keeply I.A funcionando.
6. Pergunta pronta: Como faço para restaurar um snapshot?
7. Permissão do vídeo liberada no Drive ou YouTube não listado.
