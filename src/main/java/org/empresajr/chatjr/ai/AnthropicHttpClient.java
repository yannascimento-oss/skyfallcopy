package org.empresajr.chatjr.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/** Chamada à API de mensagens da Anthropic. Sem SDK: um POST HTTP com os cabeçalhos exigidos. */
@Component
public class AnthropicHttpClient implements AiClient {

    private static final String API_VERSION = "2023-06-01";

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper;
    private final String baseUrl;

    public AnthropicHttpClient(ObjectMapper mapper,
                               @Value("${chatjr.ai.base-url:https://api.anthropic.com}") String baseUrl) {
        this.mapper = mapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public AiResult complete(AiRequest request) throws AiException {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", request.model());
            body.put("max_tokens", request.maxTokens());
            body.put("temperature", request.temperature());
            body.put("system", request.system());
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "user").put("content", request.user());

            HttpRequest http = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                    .timeout(Duration.ofSeconds(180))
                    .header("content-type", "application/json")
                    .header("x-api-key", request.apiKey())
                    .header("anthropic-version", API_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = this.http.send(http, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new AiException(response.statusCode(), describe(response.statusCode()));
            }
            return parse(response.body());
        } catch (HttpTimeoutException e) {
            throw new AiException(0, "a IA demorou demais para responder");
        } catch (IOException e) {
            throw new AiException(0, "não foi possível falar com o serviço da IA");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException(0, "a chamada à IA foi interrompida");
        }
    }

    private AiResult parse(String raw) throws AiException, IOException {
        JsonNode root = mapper.readTree(raw);
        StringBuilder text = new StringBuilder();
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        if (text.length() == 0) {
            throw new AiException(502, "a IA devolveu uma resposta vazia");
        }
        return new AiResult(text.toString(), root.path("usage").path("input_tokens").asInt(0),
                root.path("usage").path("output_tokens").asInt(0), "max_tokens".equals(root.path("stop_reason").asText()));
    }

    /** Tradução de status HTTP para uma frase útil, sem repetir o corpo do erro (que pode citar a chave). */
    static String describe(int status) {
        return switch (status) {
            case 400 -> "a IA recusou o pedido (formato inválido)";
            case 401, 403 -> "a chave da IA foi recusada";
            case 404 -> "o modelo de IA configurado não foi encontrado";
            case 413 -> "o documento é grande demais para a IA";
            case 429 -> "o limite de uso da API da IA foi atingido";
            case 529 -> "o serviço da IA está sobrecarregado";
            default -> status >= 500 ? "o serviço da IA está indisponível" : "a IA devolveu o erro " + status;
        };
    }
}
