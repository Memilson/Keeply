package com.keeply.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.backend.dto.AiDtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiChatService {
    private static final String SYSTEM_PROMPT = """
            Você é o Keeply I.A, assistente do painel Keeply.
            Responda sempre em português do Brasil, de forma direta, prática e sem emojis.
            Use texto simples. Não use Markdown, asteriscos, negrito, títulos com #, tabelas ou blocos de código.
            Responda em no máximo 5 linhas no mobile. Use no máximo 3 itens numerados.
            Não use subitens, travessões recuados ou explicações repetidas.
            Estruture respostas com frases curtas e listas numeradas simples quando ajudar.
            Nunca mostre raciocínio interno, análise, planejamento, "thinking", "let me", "okay" ou bastidores da resposta.
            Entregue somente a resposta final para o usuário.
            Ajude com backups, máquinas, snapshots, restauração, segurança e diagnóstico operacional.
            Use termos e caminhos reais do produto Keeply: Web, Máquinas, Snapshots, Backups, Arquivos, Mobile e Keeply Agente.
            Para dúvidas de "como fazer", entregue primeiro um passo a passo curto e acionável.
            Faça no máximo 1 ou 2 perguntas de esclarecimento somente quando forem necessárias para evitar uma ação errada.
            Não use tom alarmista, jurídico ou excessivamente cauteloso.
            Não invente estados de máquinas, backups, snapshots ou arquivos que não foram fornecidos.
            Quando não tiver dados reais do painel, diga que ainda não tem acesso ao estado atual e oriente onde conferir no Keeply.
            Para restauração, explique que somente o Keeply Agente restaura snapshots no dispositivo.
            Não diga que o mobile restaura snapshots ou executa backup. No mobile o usuário consulta snapshots/backups e baixa arquivos.
            """;

    private static final String PRODUCT_MAP = """
            Mapa do produto Keeply para orientar o usuário:

            Web - Dashboard (Visão geral):
            - Caminho: Visão geral ou /dashboard. Item "Visão geral" na sidebar esquerda.
            - Widgets visíveis: "Dispositivos Ativos" (conta online e offline), "Backups 24h", "Em Execução" (jobs ativos agora), "Falhas" (com taxa de sucesso).
            - Seção "Saúde do ambiente": gráfico donut com distribuição por Máquinas, Backups 24h, Executando e Falhas — mostra percentual de saúde geral.
            - Seção "Atividade de backups": gráfico de linha dos snapshots nos últimos 7 dias.
            - Seção "Snapshots recentes": tabela com colunas Máquina, Caminho, Status, Tamanho e Iniciado — exibe as últimas execuções do ambiente com link direto para explorar o snapshot.
            - Seção "Top por volume": ranking de dispositivos por armazenamento total usado.
            - Keeply I.A está no botão no canto superior direito da topbar, não na sidebar.
            - Use apenas se o usuário pedir resumo geral do painel, saúde do ambiente ou atividade recente de backups.
            - Não diga que o mobile tem Dashboard.

            Web - Máquinas:
            - Caminho: Máquinas ou /dashboard/machines. Item "Máquinas" na sidebar esquerda.
            - Lista "Todos os dispositivos" com colunas: Tipo (ícone Linux/Android/Windows), Nome, Origem (pasta raiz) e Último Backup.
            - Dispositivos Linux aparecem com ícone de computador. Dispositivos Android mostram "Android" abaixo do nome.
            - Ao clicar em uma máquina, abre painel lateral com três abas:
              - Aba "Resumo": último backup, último contato, armazenamento usado, total de snapshots.
              - Aba "Plano": mostra e permite editar o plano de backup (origem, CDP, validação, horário, retenção, criptografia).
              - Aba "Snapshots": lista pontos de backup daquela máquina com data, caminho de origem, tipo e status.
            - Na aba Snapshots, o botão "Explorar" abre o snapshot para navegar arquivos.
            - Use quando o usuário quiser ver máquinas cadastradas, verificar último backup, acessar snapshots de um dispositivo específico ou ir para o explorador de arquivos.

            Web - Atividades:
            - Caminho: Atividades ou /dashboard/activities. Item "Atividades" na sidebar esquerda.
            - Mostra linha do tempo de snapshots em ordem cronológica reversa.
            - Filtros disponíveis (botões no topo): "Todos", "Backup", "Em andamento", "Erros".
            - Cada item exibe: status com ponto colorido (verde = Completado, vermelho = Falha, amarelo = Em andamento), badge "BACKUP", nome da máquina, caminho de origem, data/hora e ID do snapshot.
            - Use quando o usuário quiser investigar falhas, acompanhar backup em execução, ver histórico de eventos ou auditar execuções recentes.

            Web - Proteção:
            - Caminho: Proteção ou /dashboard/protection. Item "Proteção" na sidebar esquerda.
            - Tela "Plano de Backup" com seletor de dispositivo no topo (dropdown) e toggle para ativar/desativar o plano.
            - Campos configuráveis:
              - "O que fazer backup": lista de pastas de origem com botão "+ Adicionar" — o usuário digita o caminho e adiciona.
              - "Proteção contínua (CDP)": toggle — backup incremental em tempo real.
              - "Validação pós-backup": toggle — verifica integridade após cada snapshot.
              - "Agendamento": campo de horário — define o horário diário de execução (ex: 02:00).
              - "Retenção": dropdown com "Manter todos" ou número de dias.
              - "Criptografia": toggle — AES-256 com SHA-256.
            - Seção "Informações do dispositivo": mostra ID do dispositivo e URL do servidor backend configurada no agente.
            - Botões "Cancelar" e "Salvar alterações" no rodapé.
            - A execução real do backup depende do Keeply Agente instalado no dispositivo.
            - Use quando o usuário quiser revisar ou alterar o plano de backup, ativar CDP, mudar horário, adicionar pasta ou configurar retenção.

            Web - Explorar snapshot:
            - Caminho: Máquinas > selecionar máquina > aba Snapshots > botão Explorar, ou /dashboard/backups/{id}.
            - Permite navegar pastas e arquivos dentro do snapshot com breadcrumb de navegação.
            - Sidebar esquerda mostra snapshots relacionados (mesma origem) para comparar pontos de backup.
            - Header mostra: nome da máquina, status, tipo, data, total de arquivos e tamanho comprimido.
            - Ações disponíveis: "Download snapshot" (baixa o snapshot inteiro) e "Download selecionados" (baixa apenas itens marcados com checkbox).
            - Use quando o usuário quiser navegar arquivos de um backup, baixar um arquivo ou pasta específica, ou comparar snapshots da mesma origem.

            Keeply Agente:
            - O Keeply Agente é quem executa backups no dispositivo conforme o plano configurado em Proteção.
            - O Keeply Agente é o único componente que restaura snapshots no disco do dispositivo.
            - O agente conecta ao backend pela URL configurada (ex: http://servidor:8080) e se registra automaticamente no login.
            - O agente aparece no painel web dentro de Máquinas como um dispositivo registrado.
            - Se o usuário pedir backup ou restauração real no disco, explique que a execução acontece pelo agente instalado na máquina.

            Mobile - Splash e autenticação:
            - Tela inicial do app: verifica sessão existente. Se há token válido, vai direto para o app. Se não, vai para login.
            - Login/Pareamento: formulário com e-mail, senha e URL do backend. Salva token JWT e URL de forma segura.
            - Tela de segurança: após login, pode solicitar resposta a uma pergunta de segurança para liberar acesso ao app.
            - Se houver erro de sessão expirada, oriente fazer login novamente no app.

            Mobile - Histórico (Tab 0):
            - Aba: Histórico — primeira aba do app, ícone de arquivo.
            - Lista todos os snapshots/backups disponíveis com paginação (50 por página, scroll infinito).
            - Barra de busca no topo: busca por nome de arquivo. Com 3 ou mais caracteres, ativa busca profunda (deep search) que pesquisa dentro do conteúdo dos backups.
            - Modo offline: se sem conexão, exibe os últimos dados em cache automaticamente.
            - Toque em um snapshot abre a tela de detalhes do snapshot.
            - Swipe ou ação de delete remove o snapshot com confirmação.
            - Pull-to-refresh atualiza a lista.
            - No mobile não há Dashboard, execução de backup nem restauração de snapshots.
            - Use quando o usuário estiver no celular e quiser localizar um arquivo, consultar backups salvos, abrir um snapshot ou baixar arquivos.

            Mobile - Detalhes do snapshot:
            - Tela aberta ao tocar em um snapshot na aba Histórico.
            - Lista todos os arquivos do snapshot com tipo, tamanho e data.
            - Botão de download por arquivo: solicita permissão de armazenamento e salva na pasta configurada.
            - Cache por snapshot: arquivos já visualizados ficam em cache para acesso offline.
            - Não restaura snapshots. Apenas permite baixar arquivos individuais.
            - Use quando o usuário quiser recuperar ou baixar um arquivo específico pelo celular.

            Mobile - I.A (Tab 1):
            - Aba: I.A — segunda aba do app.
            - Chat com o Keeply I.A conectado ao backend por /api/ai/chat.
            - Mantém histórico das últimas 8 mensagens como contexto.
            - Mostra 3 sugestões de perguntas rápidas na tela inicial do chat.
            - Botão de limpar chat no canto superior.
            - Painel de raciocínio expansível quando a I.A usa análise estendida.
            - Use quando o usuário perguntar como conversar com o assistente ou onde pedir orientação no app.

            Mobile - Configurações (Tab 2):
            - Aba: Configurações — terceira aba do app.
            - Seção Conta: avatar com iniciais, nome, e-mail e badge de status ativo. Botão "Desconectar conta" encerra sessão e volta para login.
            - Seção Armazenamento: exibe e permite trocar a pasta de destino dos downloads.
            - Seção Sessão: botão "Sair do aplicativo" fecha o app.
            - Rodapé mostra versão do app.
            - Use quando o usuário quiser trocar pasta de download, desconectar conta, encerrar sessão ou revisar dados da conta.

            Regras de orientação:
            - Quando a pessoa disser "no celular", "mobile" ou "app", responda usando primeiro as abas Mobile.
            - Quando a pessoa disser "no painel", "web" ou "navegador", responda usando primeiro Web > Máquinas e Snapshots.
            - Evite citar "Dashboard" por padrão. Só use se o usuário pedir resumo geral, saúde do ambiente ou atividade de backups.
            - Para falhas de backup, oriente conferir Web > Atividades (filtro "Erros") ou Web > Máquinas > aba Resumo da máquina afetada.
            - Para restauração de snapshot no disco, direcione para Web > Máquinas > Snapshots e explique que a restauração é executada pelo Keeply Agente.
            - Para baixar arquivos, use Web > Explorar snapshot ou Mobile > Detalhes do snapshot.
            - Para alterar o que é protegido, direcione para Web > Proteção; no mobile, explique que a configuração do plano fica no painel web.
            - Keeply I.A no web fica no botão no canto superior direito da topbar, não na sidebar.
            - Não invente nomes de máquinas, IDs, snapshots, arquivos ou status. Se precisar desses dados, diga onde o usuário deve conferir.
            """;

    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String referer;
    private final String title;
    private final Duration timeout;

    public AiChatService(
            ObjectMapper mapper,
            @Value("${keeply.ai.api-key:}") String apiKey,
            @Value("${keeply.ai.base-url:https://openrouter.ai/api/v1}") String baseUrl,
            @Value("${keeply.ai.model:nvidia/nemotron-3-super-120b-a12b:free}") String model,
            @Value("${keeply.ai.referer:}") String referer,
            @Value("${keeply.ai.title:Keeply}") String title,
            @Value("${keeply.ai.timeout-seconds:60}") long timeoutSeconds
    ) {
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.model = model;
        this.referer = referer == null ? "" : referer.trim();
        this.title = title;
        this.timeout = Duration.ofSeconds(Math.max(10, timeoutSeconds));
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public AiDtos.ChatResponse chat(AiDtos.ChatRequest request) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Keeply I.A não configurada. Defina KEEPLY_AI_API_KEY no backend.");
        }
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("Mensagem é obrigatória");
        }

        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "temperature", 0.4,
                    "max_tokens", 320,
                    "reasoning", Map.of("exclude", true),
                    "messages", buildMessages(request)
            ));

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-Title", title)
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            if (!referer.isBlank()) {
                builder.header("HTTP-Referer", referer);
            }

            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Falha ao consultar o modelo de I.A. HTTP " + response.statusCode());
            }

            CleanedAnswer cleaned = cleanAnswer(extractAnswer(response.body()));
            return new AiDtos.ChatResponse(cleaned.answer(), model, cleaned.reasoning());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Consulta de I.A interrompida", ex);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao consultar o modelo de I.A", ex);
        }
    }

    private List<Map<String, String>> buildMessages(AiDtos.ChatRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "system", "content", PRODUCT_MAP));

        if (request.history() != null) {
            for (AiDtos.ChatMessage item : request.history()) {
                if (item == null || item.content() == null || item.content().isBlank()) {
                    continue;
                }
                String role = "assistant".equals(item.role()) ? "assistant" : "user";
                messages.add(Map.of("role", role, "content", item.content().trim()));
            }
        }

        messages.add(Map.of("role", "user", "content", request.message().trim()));
        return messages;
    }

    private String extractAnswer(String body) throws Exception {
        JsonNode root = mapper.readTree(body);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new IllegalStateException("Resposta vazia do modelo de I.A");
        }
        return content.asText();
    }

    private CleanedAnswer cleanAnswer(String raw) {
        String answer = raw == null ? "" : raw.trim();
        String lower = answer.toLowerCase();
        boolean leaked = looksLikeReasoning(lower);
        int finalMarker = firstMarker(answer, "Final response:", "Resposta final:", "Final:");
        if (finalMarker >= 0) {
            answer = answer.substring(finalMarker).replaceFirst("(?is)^(Final response:|Resposta final:|Final:)\\s*", "").trim();
        } else {
            int revisedMarker = firstMarker(answer, "Revised:", "Revisado:");
            if (revisedMarker >= 0) {
                answer = answer.substring(revisedMarker).replaceFirst("(?is)^(Revised:|Revisado:)\\s*", "").trim();
            } else if (leaked) {
                int marker = firstMarker(answer, "Para restaurar", "Para fazer", "No Keeply", "Siga estes passos", "Você pode", "Abra o Keeply", "Acesse o Keeply");
                if (marker > 0) {
                    answer = answer.substring(marker).trim();
                }
            }
        }
        answer = stripAfterReasoning(answer).trim();
        answer = stripWrappingQuotes(answer);
        answer = stripMarkdown(answer);
        if (answer.isBlank()) {
            answer = "Não consegui gerar uma resposta final limpa. Tente perguntar de novo com mais contexto.";
        }
        return new CleanedAnswer(answer, leaked ? reasoningSummary(lower) : "");
    }

    private boolean looksLikeReasoning(String lower) {
        return lower.contains("the user")
                || lower.contains("i should")
                || lower.contains("we need")
                || lower.contains("looking at the query")
                || lower.contains("possible steps")
                || lower.contains("final response:")
                || lower.contains("revised:")
                || lower.contains("the instructions say")
                || lower.contains("i don't have access");
    }

    private String stripAfterReasoning(String answer) {
        int marker = firstMarker(answer,
                "\n\nThat's",
                "\n\nThe user",
                "\n\nThe instructions",
                "\n\nSince ",
                "\n\nLooking at",
                "\n\nPossible steps",
                "\n\nCheck if",
                "\n\nWait,");
        return marker > 0 ? answer.substring(0, marker) : answer;
    }

    private String stripWrappingQuotes(String value) {
        String cleaned = value.trim();
        while ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    private String stripMarkdown(String value) {
        return value
                .replace("**", "")
                .replace("__", "")
                .replace("###", "")
                .replace("##", "")
                .replace("#", "")
                .trim();
    }

    private String reasoningSummary(String lower) {
        if (lower.contains("i don't have access") || lower.contains("não tenho acesso")) {
            return "A I.A identificou que não tem acesso direto ao estado atual do painel e escolheu orientar onde verificar no Keeply.";
        }
        if (lower.contains("without more info") || lower.contains("more context") || lower.contains("clarification")) {
            return "A I.A percebeu que faltam detalhes para agir com segurança e preferiu pedir contexto antes de assumir um cenário.";
        }
        return "A I.A analisou a pergunta, aplicou as regras do Keeply e separou a orientação final sem inventar dados do painel.";
    }

    private int firstMarker(String answer, String... markers) {
        int found = -1;
        for (String marker : markers) {
            int index = answer.indexOf(marker);
            if (index >= 0 && (found == -1 || index < found)) {
                found = index;
            }
        }
        return found;
    }

    private String stripTrailingSlash(String value) {
        String cleaned = value == null || value.isBlank() ? "https://openrouter.ai/api/v1" : value.trim();
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private record CleanedAnswer(String answer, String reasoning) {}
}
