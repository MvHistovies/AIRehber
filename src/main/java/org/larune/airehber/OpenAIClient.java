package me.mrhistories.airehber;

import com.google.gson.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class OpenAIClient {

    private final AIGuidePlugin plugin;
    private final HttpClient http;

    public OpenAIClient(AIGuidePlugin plugin) {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String ask(String instructions, String userQuestion) throws Exception {
        String apiKey = plugin.getConfig().getString("ai.api-key", "").trim();
        if (apiKey.isEmpty() || apiKey.equalsIgnoreCase("BURAYA_API_KEY")) {
            throw new IllegalStateException("ai.api-key config'te ayarlı değil.");
        }

        String endpoint = plugin.getConfig().getString("ai.endpoint", "https://api.openai.com/v1/responses").trim();
        String model = plugin.getConfig().getString("ai.model", "gpt-4.1-mini").trim();

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("instructions", instructions);
        body.addProperty("input", userQuestion);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(35))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("OpenAI HTTP " + resp.statusCode() + " => " + resp.body());
        }

        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();

        // 1) Üst seviye output_text varsa
        String out = tryGetTopLevelOutputText(json);
        if (out != null && !out.isBlank()) return out.trim();

        // 2) output[] -> content[] -> type=output_text -> text
        out = tryGetNestedOutputText(json);
        if (out != null && !out.isBlank()) return out.trim();

        throw new RuntimeException("Çıktı metni bulunamadı. Raw: " + resp.body());
    }

    private String tryGetTopLevelOutputText(JsonObject json) {
        if (json.has("output_text") && !json.get("output_text").isJsonNull()) {
            try {
                return json.get("output_text").getAsString();
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String tryGetNestedOutputText(JsonObject json) {
        if (!json.has("output") || !json.get("output").isJsonArray()) return null;

        JsonArray outputArr = json.getAsJsonArray("output");
        for (JsonElement outEl : outputArr) {
            if (!outEl.isJsonObject()) continue;

            JsonObject outObj = outEl.getAsJsonObject();
            if (!outObj.has("content") || !outObj.get("content").isJsonArray()) continue;

            JsonArray contentArr = outObj.getAsJsonArray("content");
            for (JsonElement cEl : contentArr) {
                if (!cEl.isJsonObject()) continue;

                JsonObject cObj = cEl.getAsJsonObject();
                String type = cObj.has("type") && !cObj.get("type").isJsonNull()
                        ? cObj.get("type").getAsString()
                        : "";

                if (!"output_text".equalsIgnoreCase(type)) continue;

                if (cObj.has("text") && !cObj.get("text").isJsonNull()) {
                    try {
                        return cObj.get("text").getAsString();
                    } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }
}
