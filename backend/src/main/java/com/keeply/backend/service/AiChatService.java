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
            Use texto simples. Não use Markdown, asteriscos para negrito, títulos com #, tabelas ou blocos de código.
            Estruture respostas com frases curtas e listas numeradas simples quando ajudar.
            Nunca mostre raciocínio interno, análise, planejamento, "thinking", "let me", "okay" ou bastidores da resposta.
            Entregue somente a resposta final para o usuário.
            Ajude com backups, máquinas, snapshots, restauração, segurança e diagnóstico operacional.
            Use termos e caminhos do produto Keeply quando possível: Dashboard, Máquinas, Atividades, Proteção, snapshots e restauração.
            Para dúvidas de "como fazer", entregue primeiro um passo a passo curto e acionável.
            Faça no máximo 1 ou 2 perguntas de esclarecimento somente quando forem necessárias para evitar uma ação errada.
            Não use tom alarmista, jurídico ou excessivamente cauteloso.
            Não invente estados de máquinas, backups, snapshots ou arquivos que não foram fornecidos.
            Quando não tiver dados reais do painel, diga que ainda não tem acesso ao estado atual e oriente onde conferir no Keeply.
            Para restauração, oriente a abrir o snapshot desejado, revisar os arquivos/pastas e baixar/restaurar para um local seguro antes de substituir dados existentes.
            """;

    private static final String PRODUCT_MAP = """
            Mapa do produto Keeply para orientar o usuário:

            Web - Dashboard:
            - Caminho: Dashboard ou /dashboard.
            - Mostra visão geral com dispositivos ativos/offline, backups das últimas 24 horas, jobs em execução, falhas, taxa de sucesso, atividade dos últimos dias, snapshots recentes e maiores consumidores de armazenamento.
            - Use quando o usuário perguntar por saúde geral, resumo, falhas recentes, taxa de sucesso ou volume protegido.

            Web - Máquinas:
            - Caminho: Dashboard > Máquinas ou /dashboard/machines.
            - Lista todos os dispositivos, sistema operacional, origem principal e último backup.
            - Ao selecionar uma máquina, há abas de resumo, plano e snapshots.
            - Use quando o usuário quiser conferir uma máquina offline, ver último backup de um dispositivo, abrir detalhes da máquina ou navegar para snapshots de uma máquina.

            Web - Atividades:
            - Caminho: Dashboard > Atividades ou /dashboard/activities.
            - Mostra linha do tempo de snapshots e permite filtrar por Todos, Backup, Em andamento e Erros.
            - Use quando o usuário quiser investigar erros, acompanhar backup em execução ou auditar eventos recentes.

            Web - Proteção:
            - Caminho: Dashboard > Proteção ou /dashboard/protection.
            - Configura o plano de backup por dispositivo: pastas de origem, proteção contínua CDP, validação pós-backup, horário diário, retenção por dias ou manter todos os snapshots.
            - Use quando o usuário quiser adicionar/remover pasta protegida, mudar agendamento, ativar validação, configurar retenção ou revisar política de backup.

            Web - Explorar snapshot:
            - Caminho: Dashboard > Máquinas > selecionar máquina > Snapshots > abrir snapshot, ou /dashboard/backups/{id}.
            - Permite navegar em pastas do snapshot, ver histórico relacionado, selecionar arquivos/pastas e baixar ZIP do snapshot inteiro ou somente itens selecionados.
            - Use quando o usuário quiser restaurar, baixar, verificar arquivos dentro de um snapshot ou comparar snapshots da mesma origem.

            Mobile - Histórico:
            - Aba: Histórico.
            - Mostra snapshots/backups, permite buscar arquivos nos backups, faz busca profunda quando a consulta tem pelo menos 3 caracteres, abre detalhes do snapshot e permite excluir backup.
            - Use quando o usuário estiver no celular e quiser localizar um arquivo, abrir snapshot, consultar backups salvos ou operar em modo offline com últimos dados em cache.

            Mobile - Detalhes do snapshot:
            - Tela aberta a partir da aba Histórico.
            - Lista arquivos do snapshot, permite pesquisar dentro do snapshot e baixar arquivos.
            - Use quando o usuário quiser recuperar um arquivo específico no celular.

            Mobile - I.A:
            - Aba: I.A.
            - Chat do Keeply I.A conectado ao backend por /api/ai/chat. Usa o token do usuário e a URL pareada do backend.
            - Use quando o usuário perguntar como conversar com o assistente ou onde pedir orientação no app.

            Mobile - Configurações:
            - Aba: Configurações.
            - Mostra conta, opção de desconectar conta, pasta para salvar downloads e sair do aplicativo.
            - Use quando o usuário quiser trocar pasta de download, desconectar o dispositivo, encerrar sessão ou revisar conta.

            Login e pareamento:
            - Web usa login com e-mail e senha.
            - Mobile usa pareamento/login para salvar token JWT e URL do backend.
            - Se houver erro de sessão expirada, oriente entrar novamente ou repetir pareamento.

            Regras de orientação:
            - Quando a pessoa disser "no celular", "mobile" ou "app", responda usando primeiro as abas Mobile.
            - Quando a pessoa disser "no painel", "web", "dashboard" ou "navegador", responda usando primeiro as rotas Web.
            - Para falhas de backup, direcione para Dashboard para visão geral e Atividades para filtro Erros; se envolver uma máquina específica, direcione para Máquinas.
            - Para restauração, direcione para Explorar snapshot no web ou Detalhes do snapshot no mobile.
            - Para alterar o que é protegido, direcione para Proteção no web; no mobile, explique que a configuração do plano fica no painel web.
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
                    "max_tokens", 800,
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
