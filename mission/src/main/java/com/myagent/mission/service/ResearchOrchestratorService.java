package com.myagent.mission.service;

import com.myagent.mission.model.EvaluationResult;
import com.myagent.mission.model.ResearchReport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class ResearchOrchestratorService {

    private final ChatClient researcherClient;
    private final ChatClient writerClient;
    private final ChatClient criticClient;

    public ResearchOrchestratorService(ChatClient.Builder chatClientBuilder,
                                       VectorStore vectorStore,
                                       WikipediaSearchService wikipediaSearchService) {

        String researcherPrompt = """
                You are a Senior Research Analyst.
                Context information is below.
                ---------------------
                {question_answer_context}
                ---------------------
                If the context contains the answer, use it. 
                If not, you MUST autonomously invoke the Wikipedia search tool to find the information. 
                Do NOT ask the user for permission. Extract the topic directly from the User Query and execute the tool immediately.
                Your output must be strictly raw facts and data points. Do not format it into a final report.
                
                User Query: {query}
                """;

        this.researcherClient = chatClientBuilder.build().mutate()
                .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().build())
                        .promptTemplate(new PromptTemplate(researcherPrompt))
                        .build())
                .defaultTools(wikipediaSearchService)
                .build();

        // 2. Configure the Writer Agent with no tools, just synthesis instructions
        this.writerClient = chatClientBuilder.build().mutate()
                .defaultSystem("""
                        You are an expert Technical Writer analyzing complex system architectures.
                        Your job is to take raw research notes and synthesize them into a structured report.
                        Never invent facts; rely strictly on the provided research notes.
                        
                        CRITICAL INSTRUCTION: You MUST output valid, well-formed JSON that perfectly matches the requested schema. 
                        Do not include markdown blocks like ```json. Output ONLY the raw JSON object.
                        """)
                .build();
        this.criticClient = chatClientBuilder.build().mutate()
                .defaultSystem("""
                        You are a strict Fact-Checking Critic.
                        Your job is to compare a Generated Report against the Raw Research Notes.
                        If the Generated Report contains ANY claims, names, or statistics not found in the Raw Research Notes, mark isFactuallyAccurate as false and list the hallucinations.
                        Be ruthlessly objective.
                        """)
                .build();
    }

    public ResearchReport orchestrateResearch(String topic) {
        System.out.println("🚀 [Orchestrator] Starting research phase for: " + topic);

        // Step 1: Research (This only happens once to save time and API/compute costs)
        String rawResearch = researcherClient.prompt().user(topic).call().content();
        System.out.println("✅ [Orchestrator] Research complete. Entering drafting loop...");

        BeanOutputConverter<ResearchReport> reportConverter = new BeanOutputConverter<>(ResearchReport.class);
        BeanOutputConverter<EvaluationResult> evalConverter = new BeanOutputConverter<>(EvaluationResult.class);

        int maxRetries = 3;
        int attempt = 1;
        String criticFeedback = ""; // Holds the error messages for the Writer
        ResearchReport approvedReport = null;

        // The Self-Healing Loop
        while (attempt <= maxRetries) {
            System.out.println("🔄 [Orchestrator] Draft Attempt " + attempt + " of " + maxRetries);

            // Step 2: Write Draft
            String draftPrompt = "Raw notes:\n" + rawResearch + "\n\nTopic: " + topic + "\n\n" + reportConverter.getFormat();

            // Inject the Critic's feedback if this is a retry
            if (!criticFeedback.isEmpty()) {
                draftPrompt += "\n\nCRITICAL FEEDBACK FROM PREVIOUS DRAFT. YOU MUST FIX THESE ERRORS:\n" + criticFeedback;
            }

            String rawDraftJson = writerClient.prompt()
                    .user(draftPrompt)
                    .options(OllamaChatOptions.builder().format("json"))
                    .call()
                    .content();

            String sanitizedDraftJson = sanitizeJson(rawDraftJson);
            ResearchReport draftReport = reportConverter.convert(sanitizedDraftJson);

            // Step 3: Evaluate Draft
            String evalPrompt = String.format("""
                    Raw Research Notes:
                    %s
                    
                    Generated Report Draft:
                    %s
                    
                    Evaluate the draft for hallucinations based strictly on the raw notes.
                    %s
                    """, rawResearch, sanitizedDraftJson, evalConverter.getFormat());

            String rawEvalJson = criticClient.prompt()
                    .user(evalPrompt)
                    .options(OllamaChatOptions.builder().format("json"))
                    .call()
                    .content();

            EvaluationResult evaluation = evalConverter.convert(sanitizeJson(rawEvalJson));

            boolean isFactuallyAccurate = Boolean.TRUE.equals(evaluation.isFactuallyAccurate());
            List<String> hallucinations = evaluation.identifiedHallucinations() == null
                    ? java.util.List.of()
                    : evaluation.identifiedHallucinations();
            String explanation = evaluation.explanation() == null ? "No explanation provided." : evaluation.explanation();

            System.out.println("⚖️ [Critic] Factually Accurate? " + isFactuallyAccurate);

            if (isFactuallyAccurate) {
                approvedReport = draftReport;
                System.out.println("✅ [Orchestrator] Draft approved by Critic!");
                break; // Exit the retry loop successfully
            } else {
                System.err.println("🚨 Hallucinations Detected: " + hallucinations);
                System.err.println("🚨 Explanation: " + explanation);

                // Format the feedback to pass back to the Writer on the next iteration
                criticFeedback = "The Critic rejected your previous draft for the following reasons:\n" +
                        "Hallucinations: " + String.join("; ", hallucinations) + "\n" +
                        "Explanation: " + explanation + "\n" +
                        "You MUST remove these unverified claims and strictly use the raw notes.";
                attempt++;
            }
        }

        // Failsafe: if the loop exhausts all retries without passing the Critic
        if (approvedReport == null) {
            throw new RuntimeException("Report failed quality gate after " + maxRetries + " attempts. Terminating pipeline.");
        }

        System.out.println("💾 [Orchestrator] Quality gate passed. Saving report...");
        saveReportToFile(approvedReport);

        return approvedReport;
    }
    private String sanitizeJson(String raw) {
        String sanitized = raw.trim();
        if (sanitized.startsWith("```json")) sanitized = sanitized.substring(7);
        if (sanitized.startsWith("```")) sanitized = sanitized.substring(3);
        if (sanitized.endsWith("```")) sanitized = sanitized.substring(0, sanitized.length() - 3);
        return sanitized.trim();
    }
    private void saveReportToFile(ResearchReport report) {
        try {
            // Defensively handle potential nulls from incomplete LLM JSON generation
            String safeTitle = report.title() != null ? report.title() : "Healthcare_Architecture_Analysis";
            String safeSummary = report.executiveSummary() != null ? report.executiveSummary() : "No summary provided.";

            java.util.List<String> safeFindings = report.keyFindings() != null && !report.keyFindings().isEmpty()
                    ? report.keyFindings()
                    : java.util.List.of("No specific findings extracted.");

            java.util.List<String> safeSources = report.sources() != null && !report.sources().isEmpty()
                    ? report.sources()
                    : java.util.List.of("No sources cited.");

            String markdownContent = String.format("""
                    # %s
                    
                    ## Executive Summary
                    %s
                    
                    ## Key Findings
                    - %s
                    
                    ## Sources
                    - %s
                    """,
                    safeTitle,
                    safeSummary,
                    String.join("\n- ", safeFindings),
                    String.join("\n- ", safeSources));

            // Sanitize the filename to prevent path errors
            String fileName = safeTitle.replaceAll("[^a-zA-Z0-9\\s]", "").replaceAll("\\s+", "_") + "_Report.md";
            Path path = Paths.get(fileName);

            Files.writeString(path, markdownContent);
            System.out.println("💾 [Orchestrator] Report saved to disk: " + path.toAbsolutePath());

        } catch (Exception e) {
            System.err.println("Failed to save report: " + e.getMessage());
            e.printStackTrace();
        }
    }
}