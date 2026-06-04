package com.nano.monitor.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nano.monitor.model.AnalysisResult;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class AiAnalysisService {
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public AiAnalysisService(String apiKey, String apiUrl, String model) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model != null ? model : "qwen-turbo";
    }

    public void analyze(AnalysisResult result, String prompt) {
        log.info("🤖 [Agent] 启动深度诊断: {}", result.getFingerprint());

        try {
            String aiSummary = callLLM(prompt);
            result.setAiSummary(aiSummary);
            result.setStatus(AnalysisResult.AnalysisStatus.AI_ANALYZED);
        } catch (Exception e) {
            log.error(" [Agent] AI 诊断失败", e);
            result.setAiSummary("AI 分析异常: " + e.getMessage());
            result.setStatus(AnalysisResult.AnalysisStatus.FAILED);
        }
    }

    private String callLLM(String prompt) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            return "[提示] 请配置 nano.monitor.ai.api-key 以启用真实 AI 诊断";
        }

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> input = new HashMap<>();
        input.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
        ));
        body.put("input", input);
        body.put("model", model);

        String jsonBody = MAPPER.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode rootNode = MAPPER.readTree(response.body());
            JsonNode outputNode = rootNode.get("output");
            if (outputNode != null && outputNode.has("text")) {
                return outputNode.get("text").asText();
            }
            return "AI 响应格式异常";
        }
        return "AI 服务调用失败，状态码: " + response.statusCode();
    }
}