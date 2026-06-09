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

            String answer = cleanAnswer(extractAnswer(response.body()));
            return new AiDtos.ChatResponse(answer, model);
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

    private String cleanAnswer(String raw) {
        String answer = raw == null ? "" : raw.trim();
        String lower = answer.toLowerCase();
        if (lower.startsWith("okay,") || lower.startsWith("let me") || lower.startsWith("we need") || lower.contains("the user wants")) {
            int marker = firstMarker(answer,
                    "Para restaurar",
                    "Para fazer",
                    "No Keeply",
                    "Siga estes passos",
                    "Você pode");
            if (marker > 0) {
                answer = answer.substring(marker).trim();
            }
        }
        return answer;
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
}
