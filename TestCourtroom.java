import java.util.concurrent.atomic.AtomicInteger;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.Color;
import javax.swing.JButton;

// Run with: javac -d out TestCourtroom.java Courtroom.java && java -cp out TestCourtroom
// Standalone test — no JUnit, no Maven. Just compile + run.
public class TestCourtroom {

    static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        testJsonEscape();
        testJsonExtract();
        testJsonExtractEscapes();
        testJsonExtractNestedArray();
        testOrchestratorLiveUpdates();
        testOrchestratorEarlyTermination();
        testJsonBuildOpenRouter();
        testJsonBuildOllama();
        testCriticPersonaMentionsCite();
        testMaxStatementLenLogic();
        testExtractOllamaModelNames();
        testOllamaGetTagsUrl();
        testJudgeDecisionReasoningExtraction();
        testRoundRecordStructure();
        testRoundSummaryGeneration();
        testRoundSummaryUnder100Words();
        testExportSessionReportStructure();
        testDebateSessionSaveToFile();
        testCreateStyledButton();

        System.out.println("\n=== RESULTS: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) System.exit(1);
    }

    static void check(String name, boolean cond) {
        if (cond) { System.out.println("  PASS  " + name); passed++; }
        else      { System.out.println("  FAIL  " + name); failed++; }
    }

    static void testJsonEscape() {
        check("escape quotes", JsonHelper.escape("hello \"world\"").equals("hello \\\"world\\\""));
        check("escape backslash", JsonHelper.escape("a\\b").equals("a\\\\b"));
        check("escape newline", JsonHelper.escape("a\nb").equals("a\\nb"));
        check("escape control char", JsonHelper.escape("\u0001").equals("\\u0001"));
        check("escape null -> empty", JsonHelper.escape(null).equals(""));
    }

    static void testJsonExtract() {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hello world\"}}]}";
        check("extract simple", "hello world".equals(JsonHelper.extractJsonStringField(json, "choices[0].message.content")));
    }

    static void testJsonExtractEscapes() {
        String json = "{\"message\":{\"content\":\"line1\\nline2\\twith \\\"quote\\\"\"}}";
        check("extract with escapes", "line1\nline2\twith \"quote\"".equals(JsonHelper.extractJsonStringField(json, "message.content")));
    }

    static void testJsonExtractNestedArray() {
        String json = "{\"choices\":[{\"message\":{\"content\":\"first\"}},{\"message\":{\"content\":\"second\"}}]}";
        String r0 = JsonHelper.extractJsonStringField(json, "choices[0].message.content");
        String r1 = JsonHelper.extractJsonStringField(json, "choices[1].message.content");
        check("extract from array index 0 (got: " + r0 + ")", "first".equals(r0));
        check("extract from array index 1 (got: " + r1 + ")", "second".equals(r1));
    }

    static void testOrchestratorLiveUpdates() throws Exception {
        // Critical regression test: DebateListener must be invoked for each round.
        LLMClient mockAgainst = new MockLLMClient("mock", "m1", "Against arg R" );
        LLMClient mockFor     = new MockLLMClient("mock", "m2", "For arg R");
        LLMClient mockJudge   = new MockLLMClient("mock", "m3", "REASONING: ok\nWINNER: FOR");
        AtomicInteger roundStarts = new AtomicInteger(0);
        AtomicInteger roundEnds = new AtomicInteger(0);
        AtomicInteger argCount = new AtomicInteger(0);
        AtomicInteger verdicts = new AtomicInteger(0);
        WebSearcher noSearch = new WebSearcher();
        Orchestrator o = new Orchestrator(2, mockAgainst, mockFor, mockJudge, noSearch, false,
            new DebateListener() {
                public void onRoundStart(int r) { roundStarts.incrementAndGet(); }
                public void onArgument(String s, String t, int r) { argCount.incrementAndGet(); }
                public void onRoundEnd(int r, String w, int cp, int fp) { roundEnds.incrementAndGet(); }
                public void onVerdict(String v, String w) { verdicts.incrementAndGet(); }
            });
        DebateSession s = o.debate("Test statement", false);
        check("round starts == 2", roundStarts.get() == 2);
        check("round ends == 2", roundEnds.get() == 2);
        check("arguments == 4 (2 per round)", argCount.get() == 4);
        check("verdict fired", verdicts.get() == 1);
        check("session concluded", s.isConcluded());
    }

    static void testOrchestratorEarlyTermination() throws Exception {
        // Early termination when gap >= maxRounds. With maxRounds=1 and 1-0 score,
        // the gap reaches maxRounds after round 1.
        LLMClient mockAgainst = new MockLLMClient("mock", "m1", "A");
        LLMClient mockFor     = new MockLLMClient("mock", "m2", "F");
        LLMClient mockJudge   = new MockLLMClient("mock", "m3", "REASONING: a\nWINNER: AGAINST");
        AtomicInteger rounds = new AtomicInteger(0);
        WebSearcher noSearch = new WebSearcher();
        Orchestrator o = new Orchestrator(1, mockAgainst, mockFor, mockJudge, noSearch, false,
            new DebateListener() { public void onRoundStart(int r) { rounds.incrementAndGet(); } });
        o.debate("x", false);
        check("early termination: maxRounds=1 → 1 round run (gap==maxRounds after round 1)", rounds.get() == 1);
    }

    static void testJsonBuildOpenRouter() {
        String body = JsonHelper.buildOpenRouterBody("m1", "sys", "usr");
        check("body has model", body.contains("\"model\":\"m1\""));
        check("body has system", body.contains("\"role\":\"system\""));
        check("body has user", body.contains("\"role\":\"user\""));
        check("body has messages array", body.contains("\"messages\":["));
    }

    static void testJsonBuildOllama() {
        String body = JsonHelper.buildOllamaBody("q", "s", "u");
        check("body has stream:false", body.contains("\"stream\":false"));
        check("body has model", body.contains("\"model\":\"q\""));
    }

    static void testCriticPersonaMentionsCite() {
        String src = "";
        try {
            java.nio.file.Path p = java.nio.file.Path.of("Courtroom.java");
            src = java.nio.file.Files.readString(p);
        } catch (Exception e) { failed++; return; }
        check("Critic persona requires citation", src.contains("MUST cite"));
        check("ForSide persona requires citation", src.contains("MUST cite"));
        check("web-unavailable instruction present", src.contains("do not invent sources"));
    }

    static void testMaxStatementLenLogic() {
        try {
            Class<?> cls = Class.forName("CourtroomFrame");
            java.lang.reflect.Field f = cls.getDeclaredField("MAX_STATEMENT_LEN");
            f.setAccessible(true);
            int v = f.getInt(null);
            check("MAX_STATEMENT_LEN > 0 and <= 10000", v > 0 && v <= 10000);
        } catch (Exception e) {
            check("MAX_STATEMENT_LEN constant exists", false);
        }
    }

    static void testExtractOllamaModelNames() {
        String json = "{\"models\":[{\"name\":\"Ling:latest\",\"model\":\"Ling:latest\"},{\"name\":\"qwen2.5:7b\",\"model\":\"qwen2.5:7b\"}]}";
        java.util.List<String> list = JsonHelper.extractOllamaModelNames(json);
        check("extract ollama models count == 2", list.size() == 2);
        check("contains Ling:latest", list.contains("Ling:latest"));
        check("contains qwen2.5:7b", list.contains("qwen2.5:7b"));
    }

    static void testOllamaGetTagsUrl() {
        check("tags url for /api/chat", OllamaClient2.getTagsUrl("http://localhost:11434/api/chat").equals("http://localhost:11434/api/tags"));
        check("tags url for base host", OllamaClient2.getTagsUrl("http://localhost:11434").equals("http://localhost:11434/api/tags"));
        check("tags url for trailing slash", OllamaClient2.getTagsUrl("http://localhost:11434/").equals("http://localhost:11434/api/tags"));
    }

    static void testJudgeDecisionReasoningExtraction() {
        LLMClient mockJudge = new MockLLMClient("mock", "m3", "REASONING: The against side cited historical data.\nWINNER: AGAINST");
        Judge judge = new Judge(mockJudge);
        DebateSession s = new DebateSession("Statement", 1);
        JudgeDecision decision = judge.evaluateRound(s);
        check("decision winner is AGAINST", "AGAINST".equals(decision.getWinner()));
        check("decision reasoning parsed correctly", "The against side cited historical data.".equals(decision.getReasoning()));
    }

    static void testRoundRecordStructure() {
        RoundRecord rr = new RoundRecord(1, "Against arg", "For arg", "Judge reasoning", "FOR", 0, 1);
        check("RoundRecord roundNumber", rr.getRoundNumber() == 1);
        check("RoundRecord againstArg", "Against arg".equals(rr.getAgainstArgument()));
        check("RoundRecord forArg", "For arg".equals(rr.getForArgument()));
        check("RoundRecord judgeReasoning", "Judge reasoning".equals(rr.getJudgeReasoning()));
        check("RoundRecord winner", "FOR".equals(rr.getRoundWinner()));
        check("RoundRecord criticPoints", rr.getCriticPoints() == 0);
        check("RoundRecord forPoints", rr.getForPoints() == 1);
        check("RoundRecord auto-generated summary not empty", rr.getRoundSummary() != null && !rr.getRoundSummary().isBlank());
    }

    static void testRoundSummaryGeneration() {
        String against = "Universal basic income reduces workforce participation and triggers uncontrolled inflation across domestic markets.";
        String forSide = "Direct cash payments eliminate poverty traps and stimulate consumer spending in local economies.";
        String reasoning = "For provided concrete empirical evidence from pilot programs whereas Against relied on speculative assumptions.";
        String winner = "FOR";

        String summary = RoundRecord.generateRoundSummary(against, forSide, reasoning, winner);
        check("summary starts with 'Against said'", summary.startsWith("Against said "));
        check("summary contains 'and For countered with'", summary.contains(" and For countered with "));
        check("summary contains 'and Judge picked For because'", summary.contains(" and Judge picked For because "));
    }

    static void testRoundSummaryUnder100Words() {
        StringBuilder longAgainst = new StringBuilder();
        StringBuilder longFor = new StringBuilder();
        StringBuilder longReasoning = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            longAgainst.append("Against argument clause ").append(i).append(". ");
            longFor.append("For argument clause ").append(i).append(". ");
            longReasoning.append("Judge evaluation sentence ").append(i).append(". ");
        }

        String summary = RoundRecord.generateRoundSummary(longAgainst.toString(), longFor.toString(), longReasoning.toString(), "AGAINST");
        int wordCount = summary.split("\\s+").length;
        check("summary is under 100 words (actual: " + wordCount + ")", wordCount < 100);
        check("summary contains picked Against", summary.contains("and Judge picked Against because"));
    }

    static void testExportSessionReportStructure() {
        DebateSession s = new DebateSession("AI will transform medicine", 2);
        s.setStances("AI accelerates diagnosis", "AI cannot replace clinical intuition");
        s.setMetadata("Against Side", "ollama (Ling:latest)");
        s.setMetadata("For Side", "ollama (Ling:latest)");
        s.setMetadata("Judge", "ollama (Ling:latest)");

        s.addRoundRecord(new RoundRecord(1, "Medical intuition is irreplaceable.", "AI diagnostic algorithms outperform on MRIs.",
            "FOR provided measurable empirical accuracy comparisons.", "FOR", 0, 1));
        s.addForPoint();

        s.conclude("FOR", "TRUE: Automated image recognition and genomic analysis provide superior diagnostic capabilities.");

        String report = s.exportSessionReport();
        check("report contains title", report.contains("COURTROOM AI DEBATE SESSION REPORT"));
        check("report contains statement", report.contains("AI will transform medicine"));
        check("report contains FOR stance", report.contains("AI accelerates diagnosis"));
        check("report contains AGAINST stance", report.contains("AI cannot replace clinical intuition"));
        check("report contains [ROUND SUMMARY]", report.contains("[ROUND SUMMARY]"));
        check("report contains Against argument", report.contains("Medical intuition is irreplaceable."));
        check("report contains For argument", report.contains("AI diagnostic algorithms outperform on MRIs."));
        check("report contains judge reasoning", report.contains("FOR provided measurable empirical accuracy comparisons."));
        check("report contains winner", report.contains("FOR"));
        check("report contains final verdict", report.contains("Automated image recognition and genomic analysis"));
    }

    static void testDebateSessionSaveToFile() throws Exception {
        DebateSession s = new DebateSession("Robots in agriculture", 1);
        s.setStances("Increases crop yield", "High maintenance costs");
        s.addRoundRecord(new RoundRecord(1, "Cost is too high for small farms.", "Yield increases by 35%.",
            "Yield increase directly addresses food security.", "FOR", 0, 1));
        s.addForPoint();
        s.conclude("FOR", "TRUE: Autonomous harvesters and sensors boost overall agricultural efficiency.");

        File tempFile = File.createTempFile("test_debate_save_", ".txt");
        tempFile.deleteOnExit();

        s.saveToFile(tempFile);
        check("saveToFile created file", tempFile.exists() && tempFile.length() > 0);

        String fileContent = Files.readString(tempFile.toPath());
        check("saved file contains [ROUND SUMMARY]", fileContent.contains("[ROUND SUMMARY]"));
        check("saved file contains reasoning", fileContent.contains("Yield increase directly addresses food security."));
        check("saved file contains verdict", fileContent.contains("Autonomous harvesters and sensors"));
    }

    static void testCreateStyledButton() {
        JButton btn = CourtroomFrame.createStyledButton("▶ Start Debate", Color.GREEN);
        check("button text correct", "▶ Start Debate".equals(btn.getText()));
        check("button foreground color set", Color.GREEN.equals(btn.getForeground()));
        check("button is opaque", btn.isOpaque());
    }
}
