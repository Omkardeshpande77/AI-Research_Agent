# AI Research Agent

An AI-powered research and document analysis application built with Spring Boot, Spring AI, PostgreSQL with PGVector, and Ollama. The system combines RAG-style retrieval, Wikipedia tool use, PDF ingestion, and a multi-agent research loop to generate fact-checked research reports from raw notes.

## Overview

This project exposes a research API that can:

- Query documents already ingested into a vector store
- Search the web via a Wikipedia tool
- Chat with an LLM using retrieval-augmented generation
- Ingest PDFs into PGVector for semantic search
- Orchestrate a multi-step research workflow that drafts, criticizes, and revises a report until it passes a factuality check

The service is designed for knowledge-intensive workflows where the model should ground answers in local documents and external sources before writing a final report.

## Architecture

The project is split into:

- `mission/` — Spring Boot application
- `docker-compose.yml` — local infrastructure for Ollama and PostgreSQL + PGVector

### Core components

- `ChatController` — REST API endpoints
- `DocumentIngestionService` — PDF ingestion and chunking into vector embeddings
- `WikipediaSearchService` — tool-enabled Wikipedia lookups
- `ResearchOrchestratorService` — end-to-end research workflow with draft/critic loop
- `ResearchReport` — final structured research output model
- `EvaluationResult` — fact-checking result emitted by the critic agent

## Features

### 1. Document ingestion and retrieval

- Converts PDF files into text
- Splits long content into chunks
- Stores embeddings in PostgreSQL using PGVector
- Retrieves semantically relevant chunks through Spring AI vector search

### 2. Tool-augmented research

- Uses a Wikipedia search tool when the internal document store lacks the answer
- Encourages the model to fetch factual grounding from external sources without asking the user for permission

### 3. Multi-agent drafting loop

- Research agent gathers facts
- Writing agent produces a structured JSON report
- Critic agent checks the output against raw research notes
- Rewrites the report until it passes the factuality gate

### 4. Report output

The orchestrator saves the final approved report to a Markdown file in the project working directory and returns a structured `ResearchReport` object.

## Tech Stack

- Java 21
- Spring Boot 4.1.2
- Spring AI 2.0.1
- PostgreSQL + PGVector
- Ollama
- Jackson
- Maven

## Project structure

```text
AI-Research_Agent/
├── docker-compose.yml
├── README.md
├── mission/
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── .mvn/
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   └── resources/
│       └── test/
└── .gitignore
```

## Prerequisites

Before running the app, install:

- Java 21+
- Docker Desktop or Docker Engine
- Maven (or use the included Maven wrapper in `mission/`)
- Git

## Quick start

### 1. Start infrastructure

From the project root:

```bash
docker compose up -d
```

This starts:

- Ollama on `http://localhost:11435`
- PostgreSQL on `localhost:5432`

### 2. Pull required models

The app expects the Ollama models configured in `mission/src/main/resources/application.yaml`.

```bash
docker exec -it ollama ollama pull qwen3:8b
docker exec -it ollama ollama pull qwen3-embedding:0.6b
```

### 3. Run the backend

```bash
cd mission
./mvnw spring-boot:run
```

The app will run on:

- `http://localhost:8080`

## Configuration

The main application settings are in:

```text
mission/src/main/resources/application.yaml
```

Key defaults:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/research_db
    username: admin
    password: adminpassword
  ai:
    ollama:
      base-url: http://localhost:11435
      chat:
        options:
          model: qwen3:8b
      embedding:
        options:
          model: qwen3-embedding:0.6b
```

## API endpoints

### Health check

```http
GET /api/research/ping?message=Hello%21%20Who%20are%20you%20%3F
```

### RAG ask

```http
GET /api/research/ask?message=What%20is%20in%20the%20stored%20research%20documents%3F
```

### Web search

```http
GET /api/research/web?message=What%20is%20the%20history%20of%20the%20internet%3F
```

### Chat with retrieval + Wikipedia fallback

```http
GET /api/research/chat?message=Explain%20the%20concept%20of%20vector%20search%20and%20its%20use%20in%20AI%20systems
```

### Ingest a PDF

```http
POST /api/research/ingest?filePath=/absolute/path/to/file.pdf
```

### Orchestrated research workflow

```http
GET /api/research/orchestrate?topic=Transformer%20architecture
```

This endpoint returns a structured `ResearchReport` JSON object and writes a Markdown report file to the current working directory.

## Example workflow

1. Start Docker services
2. Pull Ollama models
3. Start the Spring Boot app
4. Ingest a PDF
5. Call `/api/research/orchestrate` with a topic
6. Review the generated Markdown report and API response

## Notes

- The app is designed for local development and evaluation, not production deployment by default.
- The multi-agent critic loop explicitly rejects hallucinated claims and rewrites the report until it stays grounded in the research notes.
- If Ollama or PostgreSQL is unavailable, the app will not fully function until the services are started.

## Troubleshooting

### Database connection issues

Ensure PostgreSQL is running and the credentials match the values in `application.yaml`.

### Ollama model errors

Verify the models are installed:

```bash
docker exec -it ollama ollama list
```

### PDF ingestion fails

Check that the file path is valid and that the PDF is readable.

### Model output is not valid JSON

The orchestrator already sanitizes common JSON wrappers and retries the draft when the critic rejects it. If required, re-run the same prompt after confirming Ollama is available.

## License

This project is provided as-is for educational and research use.
