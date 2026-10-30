package com.myagent.mission.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Iterator;
import java.util.Map;

@Service
public class WikipediaSearchService {
    private final RestClient restClient;

    public WikipediaSearchService(){
        this.restClient = RestClient.create();
    }

    @Tool(description = "Searches Wikipedia for a given topic and returns a factual summary. Use this when you need external facts or background information not found in your internal documents.")
    public String SearchWikipedia(@ToolParam(description = "The exact topic, person or concept to search for") String topic){
        System.out.println("🤖 LLM decided to call Wikipedia for topic: " + topic);
        try {
            String url = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&exsentences=5&exlimit=1&titles={topic}&explaintext=1&format=json";
            JsonNode response = restClient.get()
                    .uri(url, topic)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode pages = response.path("query").path("pages");
            if (pages == null || pages.isMissingNode() || pages.isEmpty()) {
                return "Wikipedia search failed: Topic not found.";
            }

            Iterator<Map.Entry<String, JsonNode>> fields = pages.fields();
            if (!fields.hasNext()) {
                return "Wikipedia search failed: Topic not found.";
            }

            Map.Entry<String, JsonNode> pageEntry = fields.next();
            String pageId = pageEntry.getKey();
            JsonNode page = pageEntry.getValue();

            if ("-1".equals(pageId) || !page.has("extract")) {
                return "Wikipedia search failed: Topic not found.";
            }

            return page.path("extract").asText();
        } catch (Exception e) {
            return "Web search is currently unavailable: " + e.getMessage();
        }
    }
}
