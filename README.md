# jLLMDebateRoom

Multi-agent adversarial debate orchestrator built in standard Java (JDK 17+) with Swing GUI and standalone CLI interfaces. The engine orchestrates structured debates between opposing AI personas and adjudicates outcomes through a judicial scoring system.

## System Architecture

```mermaid
flowchart TD

%%{init: {'theme': 'dark','flowchart': {'curve': 'bumpY'}}}%%

subgraph group_entry["Entry points"]
  direction TD
  node_swing["Swing interface<br/>[Courtroom.java]"]
  node_cli["Console interface<br/>[CourtroomCLI.java]"]
  node_gui_output["Rendered and saved result<br/>[Courtroom.java]"]
end

subgraph group_debate["Debate workflow"]
  direction LR
  node_orchestrator["Debate orchestrator<br/>[Courtroom.java]"]
  node_cli_orchestrator["CLI orchestrator<br/>[CourtroomCLI.java]"]
  node_stance["Stance clarifier<br/>[Courtroom.java]"]
  node_critic["Against agent<br/>[Courtroom.java]"]
  node_defender["For agent<br/>[Courtroom.java]"]
  node_judge["Judge and evaluator<br/>[Courtroom.java]"]
  node_cli_critic["CLI critic<br/>[CourtroomCLI.java]"]
  node_cli_defender["CLI defender<br/>[CourtroomCLI.java]"]
  node_cli_judge["CLI judge<br/>[CourtroomCLI.java]"]
end

subgraph group_services["Inference and evidence"]
  direction LR
  node_llm["LLM client layer<br/>[Courtroom.java]"]
  node_cli_llm["CLI Ollama client<br/>[CourtroomCLI.java]"]
  node_search["Web search<br/>[Courtroom.java]"]
end

subgraph group_state["Session and output"]
  direction TD
  node_session["Debate session<br/>[Courtroom.java]"]
  node_cli_session["CLI session<br/>[CourtroomCLI.java]"]
  node_transcript["Transcript and report<br/>[Courtroom.java]"]
end

node_user(("Debate user"))
node_openrouter{{"OpenRouter API"}}
node_ollama{{"Ollama service"}}
node_web{{"Web search engines"}}

node_user -->|"submits debate"| node_swing
node_user -->|"enters debate"| node_cli
node_swing -->|"starts debate"| node_orchestrator
node_cli -->|"starts debate"| node_cli_orchestrator
node_orchestrator -->|"clarifies stance"| node_stance
node_orchestrator -->|"requests argument"| node_critic
node_orchestrator -->|"requests argument"| node_defender
node_orchestrator -->|"requests evaluation"| node_judge
node_critic -.->|"queries evidence"| node_search
node_defender -.->|"queries evidence"| node_search
node_critic -->|"requests completion"| node_llm
node_defender -->|"requests completion"| node_llm
node_judge -->|"requests completion"| node_llm
node_search -.->|"fetches results"| node_web
node_llm -.->|"sends requests"| node_openrouter
node_llm -->|"sends requests"| node_ollama
node_orchestrator -->|"updates transcript and score"| node_session
node_session -->|"exports report"| node_transcript
node_swing -->|"renders and saves"| node_gui_output
node_cli_orchestrator -->|"requests argument"| node_cli_critic
node_cli_orchestrator -->|"requests argument"| node_cli_defender
node_cli_orchestrator -->|"requests evaluation"| node_cli_judge
node_cli_critic -->|"requests completion"| node_cli_llm
node_cli_defender -->|"requests completion"| node_cli_llm
node_cli_judge -->|"requests completion"| node_cli_llm
node_cli_llm -->|"sends requests"| node_ollama
node_cli_orchestrator -->|"updates transcript and score"| node_cli_session
```

## Debate Protocol

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant Orchestrator
    participant Critic as Against Agent
    participant For as For Agent
    participant Web as Web Searcher
    participant Judge as Judge Agent
    participant Session as Session State

    User->>Orchestrator: Submit Statement and Max Rounds
    Orchestrator->>Judge: clarifyStances(statement)
    Judge-->>Orchestrator: Stance Definitions (FOR / AGAINST)
    
    loop Round 1 to Max Rounds
        alt Web Evidence Enabled
            Critic->>Web: Query relevant counter-evidence
            Web-->>Critic: External citations and snippets
        end
        Critic->>Session: Deliver Against argument
        
        alt Web Evidence Enabled
            For->>Web: Query supporting evidence
            Web-->>For: External citations and snippets
        end
        For->>Session: Deliver For rebuttal
        
        Orchestrator->>Judge: Evaluate Round Transcript
        Judge-->>Orchestrator: Award Point (AGAINST or FOR) + Reasoning
        Orchestrator->>Session: Update Scoreboard and Generate Round Summary
        
        opt Decisive Lead or Final Round
            Orchestrator->>Judge: Request Final Verdict
            Judge-->>Orchestrator: Verdict (TRUE / FALSE + Justification)
            Orchestrator->>Session: Conclude Debate
        end
    end

    Session->>User: Render Formatted Transcript and Export Report
```

## Directory Structure

```
jLLMDebateRoom/
|-- Courtroom.java              # Main Swing application and orchestrator core
|-- TestCourtroom.java          # Diagnostic and regression test suite
|-- run.sh                      # Unix build and execution launcher
|-- run.bat                     # Windows build and execution launcher
|-- check.sh                    # Unix configuration and model diagnostic script
|-- check.bat                   # Windows configuration diagnostic script
|-- diag.sh                     # Headless runtime logger for diagnostics
|-- diag-run.bat                # Windows headless diagnostic launcher
|-- config.properties.example   # Configuration template for provider settings
|-- .env.example                # Environment template for API keys
|-- .gitignore                  # Security rules ignoring keys, logs, and artifacts
|-- docs/                       # Technical documentation and audits
|   `-- DEVIL_REPORT.md         # Adversarial architecture audit
|-- offline/                    # Standalone CLI mode
|   |-- CourtroomCLI.java       # Pure console debate engine
|   |-- run_offline.sh          # Unix CLI execution script
|   |-- run_offline.bat         # Windows CLI execution script
|   `-- README.md               # Offline mode usage instructions
`-- sessions/                   # Generated debate logs and session exports
```

## Requirements

- Java Development Kit (JDK 17 or later)
- Local Ollama daemon running on `http://localhost:11434` (optional for local models)
- OpenRouter API key (optional for cloud models)

## Setup and Configuration

Copy the sample configuration and environment templates:

```bash
cp .env.example .env
cp config.properties.example config.properties
```

Populate `OPENROUTER_API_KEY` inside `.env` for cloud inference. If left empty, the application defaults to local Ollama endpoints.

## Compilation and Execution

### Graphical Interface

#### Linux / macOS
```bash
chmod +x run.sh check.sh diag.sh
./run.sh
```

#### Windows
```cmd
run.bat
```

### Standalone CLI Execution

For headless or offline environments using local Ollama instances:

```bash
cd offline
chmod +x run_offline.sh
./run_offline.sh qwen2.5
```

## Automated Test Suite

Execute the regression test suite covering JSON serialization, persona prompt integrity, scoring thresholds, and summarization limits:

```bash
javac -d out Courtroom.java TestCourtroom.java
java -cp out TestCourtroom
```
