# Relatório N2 - Funcionalidade de Inteligência Artificial no Keeply

## 1. Identificação

Projeto: Keeply

Funcionalidade demonstrada: Keeply I.A

Aplicações demonstradas: painel web e aplicativo mobile

Objetivo da entrega: demonstrar a aplicação prática de Inteligência Artificial integrada ao projeto desenvolvido na Fábrica de Software.

## 2. Problema que a funcionalidade resolve

A funcionalidade Keeply I.A resolve o problema de orientação do usuário dentro do sistema de backup do Keeply.

O usuário pode ter dúvidas sobre:

1. Verificar se os backups estão saudáveis.
2. Entender o que fazer quando uma máquina está offline.
3. Localizar snapshots e arquivos.
4. Saber onde baixar arquivos restauráveis.
5. Entender o fluxo correto de restauração pelo Keeply Agente.

A proposta da funcionalidade é atuar como um assistente dentro do aplicativo, respondendo perguntas em linguagem natural e orientando o usuário conforme as telas e regras reais do produto.

## 3. O que a IA faz dentro do aplicativo

A IA recebe uma pergunta digitada pelo usuário no chat do Keeply I.A.

Depois disso, ela interpreta a intenção da pergunta e retorna uma orientação prática baseada no funcionamento do Keeply.

Exemplos de respostas esperadas:

1. Indicar onde consultar o Dashboard.
2. Explicar que as máquinas ficam em Dashboard > Máquinas.
3. Orientar que snapshots são acessados pela máquina selecionada.
4. Explicar que a restauração de snapshots é feita pelo Keeply Agente.
5. Explicar que o mobile permite consultar dashboards/snapshots e baixar arquivos, mas não restaurar snapshots.

## 4. Modelo ou técnica de IA empregada

A técnica utilizada foi um modelo de linguagem integrado por API de terceiros.

Modelo configurado no backend:

nvidia/nemotron-3-super-120b-a12b:free

Provedor de acesso:

OpenRouter API

Justificativa da escolha:

1. O modelo de linguagem permite conversar em linguagem natural.
2. A integração por API facilita o uso dentro do backend sem treinar um modelo próprio do zero.
3. O modelo consegue responder dúvidas operacionais do usuário usando o contexto do produto.
4. A solução é adequada para um assistente textual de suporte, orientação e navegação dentro do sistema.

## 5. Como a IA foi integrada ao aplicativo

A integração foi feita entre o mobile, o backend e a API do modelo de linguagem.

Fluxo da integração:

1. O usuário abre a aba Keeply I.A no aplicativo mobile.
2. O usuário digita uma pergunta no chat.
3. O mobile envia a pergunta para o backend pelo endpoint /api/ai/chat.
4. O backend monta o contexto com as regras do Keeply e envia a requisição para a API de IA.
5. O modelo processa a pergunta e retorna uma resposta.
6. O backend limpa e organiza a resposta.
7. O mobile exibe a resposta final para o usuário.

Fluxo simplificado:

Usuário > Mobile Keeply I.A > Backend Keeply > OpenRouter/Modelo de IA > Backend > Mobile

## 6. Contexto do produto usado pela IA

A IA foi configurada para responder usando regras específicas do Keeply.

Regras principais:

1. No painel web, o usuário pode acessar Dashboard, Máquinas, Atividades, Proteção e Snapshots.
2. Em Máquinas, o usuário visualiza os dispositivos registrados.
3. Em Máquinas > Snapshots, o usuário seleciona snapshots de uma máquina.
4. Na web, o usuário pode baixar snapshots, pastas ou arquivos.
5. Somente o Keeply Agente pode restaurar snapshots no dispositivo.
6. O Keeply Agente pode restaurar snapshots em pastas diferentes no dispositivo.
7. O agente é registrado quando o usuário faz login no dispositivo.
8. No mobile, o usuário consulta dashboards/snapshots e baixa arquivos.
9. O mobile não executa restauração de snapshots.

## 7. Processamento da resposta

O backend não apenas repassa a resposta do modelo diretamente.

Ele também:

1. Envia instruções para a IA responder em português.
2. Limita respostas longas para melhorar a leitura no mobile.
3. Orienta a IA a não inventar estados de máquinas, backups ou arquivos.
4. Remove vazamentos de raciocínio interno quando o modelo retorna texto indevido.
5. Separa uma análise resumida em um campo próprio quando necessário.

Com isso, a resposta exibida ao usuário fica mais objetiva e adequada ao aplicativo.

## 8. Demonstração prática prevista no vídeo

No vídeo, serão demonstrados:

1. O painel web do Keeply.
2. A área de máquinas e snapshots.
3. A aba Keeply I.A no mobile.
4. Uma pergunta enviada pelo usuário.
5. O processamento da IA.
6. A resposta final gerada.
7. A diferença entre o que pode ser feito na web e no mobile.

Pergunta sugerida para o vídeo:

Como faço para restaurar um snapshot?

Resposta esperada:

A IA deve explicar que a restauração é feita pelo Keeply Agente, que o usuário deve acessar Web > Máquinas > Snapshots, selecionar o snapshot e executar a restauração pelo agente, podendo restaurar em uma pasta diferente no dispositivo.

## 9. Limitações observadas

1. A IA depende da disponibilidade da API externa.
2. O modelo pode gerar respostas longas se o prompt não for bem controlado.
3. A IA não acessa automaticamente o estado real das máquinas se esses dados não forem enviados pelo backend.
4. A IA pode orientar o usuário, mas não deve inventar nomes de máquinas, arquivos ou snapshots.
5. A restauração real depende do Keeply Agente instalado e registrado no dispositivo.

## 10. Possíveis melhorias futuras

1. Enviar dados reais do Dashboard para a IA responder com status atual.
2. Integrar a IA com eventos recentes de backup e falhas.
3. Permitir que a IA explique erros específicos de uma máquina.
4. Criar respostas com botões de atalho para abrir telas do sistema.
5. Melhorar a análise resumida da IA no mobile.
6. Adicionar histórico de conversas por usuário.
7. Usar um modelo pago ou dedicado para maior estabilidade e menor vazamento de raciocínio.

## 11. Conclusão

A funcionalidade Keeply I.A aplica Inteligência Artificial ao projeto por meio de um assistente conversacional integrado ao backend e ao aplicativo mobile.

Ela ajuda o usuário a entender o funcionamento do Keeply, localizar áreas importantes do sistema e seguir o fluxo correto para consulta, download e restauração de snapshots.

A solução demonstra uso prático de modelo de linguagem em um sistema real, com entrada do usuário, processamento por IA e retorno de resposta útil dentro do aplicativo.
