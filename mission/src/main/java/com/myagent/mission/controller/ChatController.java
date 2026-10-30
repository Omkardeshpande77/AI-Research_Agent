package com.myagent.mission.controller;

import com.myagent.mission.model.ResearchReport;
import com.myagent.mission.service.DocumentIngestionService;
import com.myagent.mission.service.ResearchOrchestratorService;
import com.myagent.mission.service.WikipediaSearchService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {
    private final ChatClient chatClient;
    private final DocumentIngestionService ingestionService;
    private final VectorStore vectorStore;
    private final WikipediaSearchService wikipediaSearchService;
    private final ResearchOrchestratorService orchestratorService;
    public ChatController(ChatClient.Builder chatClientBuilder,
                          VectorStore vectorStore,
                          DocumentIngestionService ingestionService,
                          WikipediaSearchService wikipediaSearchService,
                          ResearchOrchestratorService orchestratorService) {

        this.ingestionService = ingestionService;
        this.vectorStore = vectorStore;
        this.wikipediaSearchService = wikipediaSearchService;
        this.orchestratorService = orchestratorService;
        // Build a clean, base ChatClient with NO global defaults
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping("/api/research/ping")
    public String pingLLM(@RequestParam(defaultValue = "Hello! Who are you ?") String message){
        return this.chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    // 1. RAG-Only Endpoint
    @GetMapping("/api/research/ask")
    public String askRAG(@RequestParam String message) {
        return this.chatClient.prompt()
                .user(message)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().build())
                        .build())
                .call()
                .content();
    }

    // 2. Web-Search-Only Endpoint
    @GetMapping("/api/research/web")
    public String webSearch(@RequestParam String message) {
        return this.chatClient.prompt()
                .user(message)
                .tools(wikipediaSearchService)
                .call()
                .content();
    }

    @GetMapping("/api/research/chat")
    public String researchChat(@RequestParam String message) {

        String customRagPrompt = """
                Context information is below.
                ---------------------
                {question_answer_context}
                ---------------------
                If the provided context contains the answer, use it. 
                If the context does not contain the answer, you must autonomously invoke the Wikipedia search tool to find the information. 
                Do NOT ask the user for permission or for a search topic. Extract the topic directly from the User Query and execute the tool immediately.
                
                User Query: {query}
                """;

        return this.chatClient.prompt()
                .user(message)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().build())
                        .promptTemplate(new PromptTemplate(customRagPrompt))
                        .build())
                .tools(wikipediaSearchService)
                .call()
                .content();
    }

    @PostMapping("/api/research/ingest")
    public String ingestDocument(@RequestParam String filePath) {
        try {
            ingestionService.ingestPDF(filePath);
            return "Successfully ingested document from: " + filePath;
        } catch (Exception e) {
            return "Failed to ingest document: " + e.getMessage();
        }
    }

    @GetMapping("/api/research/orchestrate")
    public ResearchReport orchestrate(@RequestParam String topic) {
        return orchestratorService.orchestrateResearch(topic);
    }
}