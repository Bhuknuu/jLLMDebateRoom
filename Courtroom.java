import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import javax.swing.text.html.*;
import javax.swing.event.*;
import javax.swing.plaf.basic.*;

// ══════════════════════════════════════════════════════════════════════════════
//  COURTROOM — AI Debate Simulator with Swing GUI + OpenRouter + Web Search
//  Single-file Java. No external libraries. javac Courtroom.java
// ══════════════════════════════════════════════════════════════════════════════

public class Courtroom {

    private static final String CONFIG_FILE = "config.properties";

    public static void main(String[] args) {
        // Load saved config and .env
        Properties config = loadConfig();
        Properties env = loadEnv();

        // ── Resolve API key: .env → env var → args[0] → config ──
        // If blank, Ollama at localhost:11434 is used automatically (no popup prompt ever).
        String apiKey = env.getProperty("OPENROUTER_API_KEY", env.getProperty("openrouter.api.key", ""));
        if (apiKey.isBlank()) {
            apiKey = System.getenv("OPENROUTER_API_KEY");
        }
        if ((apiKey == null || apiKey.isBlank()) && args.length > 0 && !args[0].isBlank() && !args[0].startsWith("--")) {
            apiKey = args[0];
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = config.getProperty("openrouter.api.key", "");
        }
        if (apiKey == null) {
            apiKey = "";
        }

        // --check: print resolved configuration and exit (useful for debugging from headless / CI).
        if (args.length > 0 && "--check".equals(args[0])) {
            String ollamaUrl = config.getProperty("ollama.url", OllamaClient2.DEFAULT_URL);
            List<String> ollamaModels = OllamaClient2.fetchModels(ollamaUrl);
            StringBuilder sb = new StringBuilder();
            sb.append("[Courtroom] Configuration check\n");
            sb.append("  .env present          : ").append(Files.exists(Path.of(".env"))).append('\n');
            sb.append("  config.properties      : ").append(Path.of(CONFIG_FILE).toAbsolutePath()).append('\n');
            sb.append("  config present         : ").append(Files.exists(Path.of(CONFIG_FILE))).append('\n');
            sb.append("  API key source         : ").append(apiKey.isBlank() ? "NOT SET (Ollama fallback)" : "SET (hidden)").append('\n');
            sb.append("  env var OPENROUTER_API_KEY: ").append(System.getenv("OPENROUTER_API_KEY") != null ? "SET" : "not set").append('\n');
            sb.append("  Ollama URL             : ").append(ollamaUrl).append('\n');
            sb.append("  Ollama models (auto)   : ").append(ollamaModels.isEmpty() ? "none found / offline" : String.join(", ", ollamaModels)).append('\n');
            sb.append("  Graphical environment  : ").append(GraphicsEnvironment.isHeadless() ? "headless" : "available").append('\n');
            System.out.print(sb);
            System.exit(0);
        }

        if (apiKey.isBlank()) {
            System.out.println("[Courtroom] No OpenRouter API key found in .env, environment, args, or config.");
            String ollamaUrl = config.getProperty("ollama.url", OllamaClient2.DEFAULT_URL);
            List<String> detected = OllamaClient2.fetchModels(ollamaUrl);
            if (!detected.isEmpty()) {
                System.out.println("[Courtroom] Auto-fetched " + detected.size() + " Ollama model(s): " + String.join(", ", detected));
            } else {
                System.out.println("[Courtroom] Automatically using local Ollama at " + ollamaUrl + " as fallback.");
            }
        }
        // If headless and we're about to start a Swing GUI, explain and exit cleanly
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("[Courtroom] Headless environment detected (no display). The Swing GUI cannot run.");
            System.err.println("[Courtroom] To run the GUI, start this app in an environment with a display (e.g., desktop, VNC, RDP, or a virtual display like xvfb).");
            System.err.println("[Courtroom] Config loaded from: " + Path.of(CONFIG_FILE).toAbsolutePath());
            System.err.println("[Courtroom] API key source: " + (apiKey.isBlank() ? "NOT SET" : "SET (hidden)"));
            System.exit(0);
        }

        String apiKeyForFrame = apiKey;
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {}
            applyGlobalDarkDefaults();
            new CourtroomFrame(config, apiKeyForFrame);
        });
    }

    public static void applyGlobalDarkDefaults() {
        Color bg = new Color(24, 28, 36);
        Color inputBg = new Color(28, 32, 42);
        Color text = new Color(248, 250, 252);
        Color border = new Color(55, 65, 81);

        UIManager.put("Panel.background", bg);
        UIManager.put("Label.foreground", text);
        UIManager.put("TextField.background", inputBg);
        UIManager.put("TextField.foreground", text);
        UIManager.put("TextField.caretForeground", Color.WHITE);
        UIManager.put("TextArea.background", inputBg);
        UIManager.put("TextArea.foreground", text);
        UIManager.put("TextArea.caretForeground", Color.WHITE);
        UIManager.put("ComboBox.background", inputBg);
        UIManager.put("ComboBox.foreground", text);
        UIManager.put("CheckBox.background", bg);
        UIManager.put("CheckBox.foreground", text);
        UIManager.put("ScrollPane.background", bg);
        UIManager.put("Viewport.background", inputBg);
        UIManager.put("MenuBar.background", bg);
        UIManager.put("MenuBar.foreground", text);
        UIManager.put("Menu.background", bg);
        UIManager.put("Menu.foreground", text);
        UIManager.put("MenuItem.background", inputBg);
        UIManager.put("MenuItem.foreground", text);
        UIManager.put("TitledBorder.titleColor", text);
        UIManager.put("TitledBorder.border", new LineBorder(border, 1));
        UIManager.put("OptionPane.background", bg);
        UIManager.put("OptionPane.messageForeground", text);
        UIManager.put("Button.background", new Color(36, 42, 54));
        UIManager.put("Button.foreground", text);
        UIManager.put("Spinner.background", inputBg);
        UIManager.put("Spinner.foreground", text);
    }

    private static Properties loadEnv() {
        Properties p = new Properties();
        Path envPath = Path.of(".env");
        if (Files.exists(envPath)) {
            try {
                for (String line : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int idx = line.indexOf('=');
                    if (idx > 0) {
                        String key = line.substring(0, idx).trim();
                        String val = line.substring(idx + 1).trim();
                        if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                            if (val.length() >= 2) val = val.substring(1, val.length() - 1);
                        }
                        p.put(key, val);
                    }
                }
            } catch (IOException ignored) {}
        }
        return p;
    }

    private static Properties loadConfig() {
        Properties p = new Properties();
        Path cfg = Path.of(CONFIG_FILE);
        if (Files.exists(cfg)) {
            try (var in = Files.newInputStream(cfg)) {
                p.load(in);
            } catch (IOException ignored) {}
        }
        return p;
    }

    public static void saveConfig(Properties config) {
        try (var out = Files.newOutputStream(Path.of(CONFIG_FILE))) {
            config.store(out, "Courtroom Debate Simulator Config");
        } catch (IOException ignored) {}
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  LLM CLIENT INTERFACE + IMPLEMENTATIONS
// ══════════════════════════════════════════════════════════════════════════════

interface LLMClient {
    String chat(String system, String user) throws IOException;
    String getModel();
    String getProvider();
}

class OpenRouterClient implements LLMClient {
    private static final String DEFAULT_URL = "https://openrouter.ai/api/v1/chat/completions";
    private final String url;
    private final String apiKey;
    private final String model;
    private final HttpClient http;

    public OpenRouterClient(String apiKey, String model) {
        this(apiKey, model, DEFAULT_URL);
    }

    public OpenRouterClient(String apiKey, String model, String url) {
        this.apiKey = apiKey;
        this.model = (model == null || model.isBlank()) ? "meta-llama/llama-3-70b-instruct" : model;
        this.url = (url == null || url.isBlank()) ? DEFAULT_URL : url;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override
    public String chat(String system, String user) throws IOException {
        String jsonBody = JsonHelper.buildOpenRouterBody(model, system, user);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(180))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .header("HTTP-Referer", "https://github.com/Bhuknuu/jLLMDebateRoom")
            .header("X-Title", "jLLMDebateRoom")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        // Retry on network errors and 5xx; do NOT retry on 4xx (auth/key/quota = caller's fault).
        HttpResponse<String> res = null;
        IOException lastErr = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                res = http.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            } catch (IOException e) {
                lastErr = e;
                try { Thread.sleep(1000L * (1L << attempt)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                continue;
            }
            if (res.statusCode() >= 500 && res.statusCode() < 600) {
                lastErr = new IOException("OpenRouter HTTP " + res.statusCode());
                try { Thread.sleep(1000L * (1L << attempt)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                continue;
            }
            break; // 2xx or 4xx — done
        }
        if (res == null) throw lastErr != null ? lastErr : new IOException("Request failed after retries");

        if (res.statusCode() != 200) {
            String errDetail = res.body();
            if (errDetail.length() > 200) errDetail = errDetail.substring(0, 200) + "...";
            throw new IOException("OpenRouter HTTP " + res.statusCode() + ": " + errDetail);
        }

        String content = JsonHelper.extractJsonStringField(res.body(), "choices[0].message.content");
        if (content == null || content.isBlank()) {
            throw new IOException("Empty response from OpenRouter: " + res.body());
        }
        return content.trim();
    }

    @Override public String getModel() { return model; }
    @Override public String getProvider() { return "openrouter"; }
}

class OllamaClient2 implements LLMClient {
    public static final String DEFAULT_URL = "http://localhost:11434/api/chat";
    private final String url;
    private final String model;
    private final HttpClient http;

    public OllamaClient2(String model) {
        this(model, DEFAULT_URL);
    }

    public OllamaClient2(String model, String url) {
        this.model = (model == null || model.isBlank()) ? "qwen2.5:latest" : model;
        this.url = (url == null || url.isBlank()) ? DEFAULT_URL : url;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    public static String getTagsUrl(String chatOrBaseUrl) {
        if (chatOrBaseUrl == null || chatOrBaseUrl.isBlank()) {
            return "http://localhost:11434/api/tags";
        }
        String clean = chatOrBaseUrl.trim();
        if (clean.endsWith("/api/chat")) {
            return clean.substring(0, clean.length() - "/api/chat".length()) + "/api/tags";
        }
        if (clean.endsWith("/api/generate")) {
            return clean.substring(0, clean.length() - "/api/generate".length()) + "/api/tags";
        }
        if (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (!clean.endsWith("/api/tags")) {
            return clean + (clean.endsWith("/api") ? "/tags" : "/api/tags");
        }
        return clean;
    }

    public static List<String> fetchModels(String ollamaUrl) {
        List<String> list = new ArrayList<>();
        try {
            String tagsUrl = getTagsUrl(ollamaUrl);
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(tagsUrl))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200 && res.body() != null) {
                list = JsonHelper.extractOllamaModelNames(res.body());
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static String pickDefaultModel(String ollamaUrl) {
        List<String> available = fetchModels(ollamaUrl);
        if (!available.isEmpty()) {
            for (String pref : new String[]{"qwen2.5", "llama3.2", "llama3.1", "llama3", "mistral", "gemma2", "phi3"}) {
                for (String m : available) {
                    if (m.toLowerCase().startsWith(pref)) return m;
                }
            }
            return available.get(0);
        }
        return "qwen2.5:latest";
    }

    @Override
    public String chat(String system, String user) throws IOException {
        String jsonBody = JsonHelper.buildOllamaBody(model, system, user);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(180))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }

        if (res.statusCode() != 200) {
            String errDetail = res.body();
            if (errDetail.length() > 200) errDetail = errDetail.substring(0, 200) + "...";
            throw new IOException("Ollama HTTP " + res.statusCode() + " at " + url + ": " + errDetail);
        }

        String content = JsonHelper.extractJsonStringField(res.body(), "message.content");
        if (content == null || content.isBlank()) {
            throw new IOException("Empty response from Ollama: " + res.body());
        }
        return content.trim();
    }

    @Override public String getModel() { return model; }
    @Override public String getProvider() { return "ollama"; }
}

// ══════════════════════════════════════════════════════════════════════════════
//  JSON HELPER — Handcrafted, Zero External Dependencies
// ══════════════════════════════════════════════════════════════════════════════

class JsonHelper {

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 32);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    public static String buildOpenRouterBody(String model, String system, String user) {
        return "{"
            + "\"model\":\"" + escape(model) + "\","
            + "\"messages\":["
            + "{\"role\":\"system\",\"content\":\"" + escape(system) + "\"},"
            + "{\"role\":\"user\",\"content\":\"" + escape(user) + "\"}"
            + "]"
            + "}";
    }

    public static String buildOllamaBody(String model, String system, String user) {
        return "{"
            + "\"model\":\"" + escape(model) + "\","
            + "\"stream\":false,"
            + "\"messages\":["
            + "{\"role\":\"system\",\"content\":\"" + escape(system) + "\"},"
            + "{\"role\":\"user\",\"content\":\"" + escape(user) + "\"}"
            + "]"
            + "}";
    }

    public static List<String> extractOllamaModelNames(String json) {
        List<String> list = new ArrayList<>();
        if (json == null || json.isBlank()) return list;
        Pattern pat = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = pat.matcher(json);
        while (m.find()) {
            String name = m.group(1).trim();
            if (!name.isEmpty() && !list.contains(name)) {
                list.add(name);
            }
        }
        return list;
    }

    public static String extractJsonStringField(String json, String path) {
        if (json == null || path == null) return null;

        String[] parts = path.split("\\.");
        int cur = 0;

        for (int pIdx = 0; pIdx < parts.length; pIdx++) {
            String part = parts[pIdx];
            int arrayIdx = -1;
            if (part.contains("[") && part.endsWith("]")) {
                int bOpen = part.indexOf('[');
                try {
                    arrayIdx = Integer.parseInt(part.substring(bOpen + 1, part.length() - 1));
                } catch (NumberFormatException e) {
                    return null;
                }
                part = part.substring(0, bOpen);
            }

            int keyPos = findJsonKey(json, part, cur);
            if (keyPos == -1) return null;

            int colon = json.indexOf(':', keyPos + part.length() + 2);
            if (colon == -1) return null;

            cur = skipWhitespace(json, colon + 1);
            if (cur >= json.length()) return null;

            if (arrayIdx >= 0) {
                if (json.charAt(cur) != '[') return null;
                cur = seekArrayElement(json, cur, arrayIdx);
                if (cur == -1) return null;
            }

            if (pIdx == parts.length - 1) {
                if (cur < json.length() && json.charAt(cur) == '"') {
                    return parseJsonString(json, cur);
                } else {
                    return parsePrimitive(json, cur);
                }
            }
        }
        return null;
    }

    private static int findJsonKey(String json, String key, int start) {
        String needle = "\"" + key + "\"";
        int pos = start;
        while ((pos = json.indexOf(needle, pos)) != -1) {
            int after = skipWhitespace(json, pos + needle.length());
            if (after < json.length() && json.charAt(after) == ':') {
                return pos;
            }
            pos += needle.length();
        }
        return -1;
    }

    private static int skipWhitespace(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    private static int seekArrayElement(String json, int startBracket, int targetIndex) {
        int i = startBracket + 1;
        int currentIdx = 0;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        i = skipWhitespace(json, i);
        if (i < json.length() && json.charAt(i) == ']') return -1;
        if (targetIndex == 0) return i;

        while (i < json.length()) {
            char c = json.charAt(i);
            if (escape) {
                escape = false;
            } else if (c == '\\' && inString) {
                escape = true;
            } else if (c == '"') {
                inString = !inString;
            } else if (!inString) {
                if (c == '{' || c == '[') {
                    depth++;
                } else if (c == '}' || c == ']') {
                    if (depth == 0) return -1;
                    depth--;
                } else if (c == ',' && depth == 0) {
                    currentIdx++;
                    if (currentIdx == targetIndex) {
                        return skipWhitespace(json, i + 1);
                    }
                }
            }
            i++;
        }
        return -1;
    }

    private static String parseJsonString(String json, int quoteStart) {
        StringBuilder sb = new StringBuilder();
        int i = quoteStart + 1;
        boolean escape = false;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (escape) {
                switch (c) {
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/'  -> sb.append('/');
                    case 'b'  -> sb.append('\b');
                    case 'f'  -> sb.append('\f');
                    case 'n'  -> sb.append('\n');
                    case 'r'  -> sb.append('\r');
                    case 't'  -> sb.append('\t');
                    case 'u'  -> {
                        if (i + 4 < json.length()) {
                            try {
                                int hex = Integer.parseInt(json.substring(i + 1, i + 5), 16);
                                sb.append((char) hex);
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append('?');
                            }
                        }
                    }
                    default -> sb.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
            i++;
        }
        return sb.toString();
    }

    private static String parsePrimitive(String json, int valStart) {
        StringBuilder sb = new StringBuilder();
        while (valStart < json.length()) {
            char c = json.charAt(valStart);
            if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) break;
            sb.append(c);
            valStart++;
        }
        return sb.toString().trim();
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  WEB SEARCH & SCRAPING (DuckDuckGo + Bing HTML fallback)
// ══════════════════════════════════════════════════════════════════════════════

class WebSearcher {
    private final HttpClient http;

    public WebSearcher() {
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    public List<SearchResult> search(String query, int maxResults) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);

        // Try DuckDuckGo HTML first
        try {
            results = searchDuckDuckGo(encoded, maxResults);
        } catch (IOException e) {
            // fallback to Bing
            try {
                results = searchBing(encoded, maxResults);
            } catch (IOException e2) {
                throw new IOException("Web search failed (DDG: " + e.getMessage() + "; Bing: " + e2.getMessage() + ")");
            }
        }
        return results;
    }

    private List<SearchResult> searchDuckDuckGo(String encoded, int max) throws IOException {
        String url = "https://html.duckduckgo.com/html/?q=" + encoded;
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .GET()
            .build();

        HttpResponse<String> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Search interrupted", e);
        }

        if (res.statusCode() != 200)
            throw new IOException("DuckDuckGo HTTP " + res.statusCode());

        List<SearchResult> results = new ArrayList<>();
        Pattern aPat = Pattern.compile("<a[^>]+class=\"result__a\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Pattern snippetPat = Pattern.compile("<a[^>]+class=\"result__snippet\"[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        Matcher aMatch = aPat.matcher(res.body());
        List<String> urls = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (aMatch.find() && urls.size() < max) {
            String href = aMatch.group(1);
            if (href != null && href.startsWith("http")) {
                urls.add(href);
                titles.add(stripHtml(aMatch.group(2)));
            }
        }

        Matcher snMatch = snippetPat.matcher(res.body());
        List<String> snippets = new ArrayList<>();
        while (snMatch.find() && snippets.size() < urls.size()) {
            snippets.add(stripHtml(snMatch.group(1)));
        }

        for (int i = 0; i < urls.size() && results.size() < max; i++) {
            String snippet = i < snippets.size() ? snippets.get(i) : "";
            results.add(new SearchResult(titles.get(i), urls.get(i), snippet));
        }

        return results;
    }

    private List<SearchResult> searchBing(String encoded, int max) throws IOException {
        String url = "https://www.bing.com/search?q=" + encoded;
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .GET()
            .build();

        HttpResponse<String> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Search interrupted", e);
        }

        if (res.statusCode() != 200)
            throw new IOException("Bing HTTP " + res.statusCode());

        List<SearchResult> results = new ArrayList<>();
        Pattern pat = Pattern.compile("<h2[^>]*>\\s*<a[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = pat.matcher(res.body());
        while (m.find() && results.size() < max) {
            String href = m.group(1);
            if (href != null && (href.startsWith("http") || href.startsWith("https"))
                && !href.contains("bing.com") && !href.contains("microsoft.com")) {
                results.add(new SearchResult(stripHtml(m.group(2)), href, ""));
            }
        }
        return results;
    }

    public String fetchPage(String url, int maxChars) throws IOException {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .GET()
            .build();

        HttpResponse<String> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Fetch interrupted", e);
        }

        if (res.statusCode() != 200)
            throw new IOException("HTTP " + res.statusCode());

        String text = stripHtml(res.body());
        if (text.length() > maxChars) {
            text = text.substring(0, maxChars) + "\n\n[... content truncated ...]";
        }
        return text;
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("(?s)<script[^>]*>.*?</script>", "")
                  .replaceAll("(?s)<style[^>]*>.*?</style>", "")
                  .replaceAll("<[^>]+>", " ")
                  .replaceAll("&nbsp;", " ")
                  .replaceAll("&amp;", "&")
                  .replaceAll("&lt;", "<")
                  .replaceAll("&gt;", ">")
                  .replaceAll("&quot;", "\"")
                  .replaceAll("&#39;", "'")
                  .replaceAll("\\s+", " ")
                  .trim();
    }

    public static class SearchResult {
        public final String title;
        public final String url;
        public final String snippet;

        public SearchResult(String title, String url, String snippet) {
            this.title = title;
            this.url = url;
            this.snippet = snippet;
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  DEBATE DOMAIN CLASSES
// ══════════════════════════════════════════════════════════════════════════════

class Message {
    private final String speaker;
    private final String content;
    private final int round;

    public Message(String speaker, String content, int round) {
        this.speaker = speaker;
        this.content = content;
        this.round = round;
    }
    public String getSpeaker() { return speaker; }
    public String getContent() { return content; }
    public int getRound() { return round; }
    @Override public String toString() {
        return "[R" + round + "] " + speaker + ": " + content;
    }
}

class RoundRecord {
    private final int roundNumber;
    private final String againstArgument;
    private final String forArgument;
    private final String judgeReasoning;
    private final String roundWinner;
    private final int criticPoints;
    private final int forPoints;
    private final String roundSummary;

    public RoundRecord(int roundNumber, String againstArgument, String forArgument,
                       String judgeReasoning, String roundWinner, int criticPoints, int forPoints) {
        this(roundNumber, againstArgument, forArgument, judgeReasoning, roundWinner, criticPoints, forPoints, null);
    }

    public RoundRecord(int roundNumber, String againstArgument, String forArgument,
                       String judgeReasoning, String roundWinner, int criticPoints, int forPoints,
                       String roundSummary) {
        this.roundNumber = roundNumber;
        this.againstArgument = againstArgument != null ? againstArgument : "";
        this.forArgument = forArgument != null ? forArgument : "";
        this.judgeReasoning = judgeReasoning != null ? judgeReasoning : "";
        this.roundWinner = roundWinner != null ? roundWinner : "";
        this.criticPoints = criticPoints;
        this.forPoints = forPoints;
        if (roundSummary != null && !roundSummary.isBlank()) {
            this.roundSummary = trimToWordLimit(roundSummary.trim(), 95);
        } else {
            this.roundSummary = generateRoundSummary(this.againstArgument, this.forArgument, this.judgeReasoning, this.roundWinner);
        }
    }

    public int getRoundNumber() { return roundNumber; }
    public String getAgainstArgument() { return againstArgument; }
    public String getForArgument() { return forArgument; }
    public String getJudgeReasoning() { return judgeReasoning; }
    public String getRoundWinner() { return roundWinner; }
    public int getCriticPoints() { return criticPoints; }
    public int getForPoints() { return forPoints; }
    public String getRoundSummary() { return roundSummary; }

    /**
     * Generates a concise summary under 100 words following the exact flow:
     * "Against said ... and For countered with ... and Judge picked (Against/For) because ..."
     */
    public static String generateRoundSummary(String againstArg, String forArg, String judgeReasoning, String winner) {
        String againstGist = extractGist(againstArg, 25);
        String forGist = extractGist(forArg, 25);
        String judgeGist = extractGist(judgeReasoning, 25);
        String winnerText = ("AGAINST".equalsIgnoreCase(winner)) ? "Against" : "For";

        String summary = String.format("Against said %s and For countered with %s and Judge picked %s because %s",
            againstGist, forGist, winnerText, judgeGist);
        return trimToWordLimit(summary, 95);
    }

    public static String trimToWordLimit(String text, int maxWords) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) return "";
        String[] words = clean.split(" ");
        if (words.length <= maxWords) return clean;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxWords; i++) {
            if (i > 0) sb.append(" ");
            sb.append(words[i]);
        }
        return sb.toString().trim();
    }

    public static String extractGist(String text, int maxWords) {
        if (text == null || text.isBlank()) return "no substantive argument was given";
        String clean = text.replaceAll("(?i)\\[nonce:[^\\]]+\\]", "")
                           .replaceAll("(?i)\\[autonomous web[^\\]]*\\]", "")
                           .replaceAll("(?i)\\[web evidence[^\\]]*\\]", "")
                           .replaceAll("https?://\\S+", "")
                           .replaceAll("[\\r\\n]+", " ")
                           .replaceAll("\\s+", " ")
                           .trim();
        if (clean.isBlank()) return "no substantive argument was given";

        String[] sentences = clean.split("(?<=[.!?])\\s+");
        String first = sentences[0].trim();
        first = first.replaceAll("^(?i)(against|for|judge|reasoning|winner)\\s*:\\s*", "").trim();
        if (first.length() < 15 && sentences.length > 1) {
            first = first + " " + sentences[1].trim();
        }
        first = first.replaceAll("^[\\\"']+|[\\\"']+$", "").trim();
        first = first.replaceAll("[.;,]+$", "").trim();
        if (first.isEmpty()) first = "a substantive argument was made";
        return trimToWordLimit(first, maxWords);
    }
}

class JudgeDecision {
    private final String winner;
    private final String reasoning;

    public JudgeDecision(String winner, String reasoning) {
        this.winner = (winner != null && !winner.isBlank()) ? winner.trim() : "FOR";
        this.reasoning = (reasoning != null && !reasoning.isBlank()) ? reasoning.trim() : "No specific reasoning provided.";
    }

    public String getWinner() { return winner; }
    public String getReasoning() { return reasoning; }

    @Override
    public String toString() {
        return "Reasoning: " + reasoning + " | Winner: " + winner;
    }
}

class DebateSession {
    private final String statement;
    private final List<Message> transcript;
    private final List<RoundRecord> roundRecords;
    private final Map<String, String> metadata;
    private final int maxRounds;
    private int currentRound;
    private boolean concluded;
    private String winner;
    private String verdictText;
    private int criticPoints;
    private int forPoints;
    private String forMeans;
    private String againstMeans;
    private final String timestamp;

    public DebateSession(String statement, int maxRounds) {
        this.statement = statement;
        this.maxRounds = maxRounds;
        this.transcript = new ArrayList<>();
        this.roundRecords = new ArrayList<>();
        this.metadata = new LinkedHashMap<>();
        this.currentRound = 1;
        this.concluded = false;
        this.criticPoints = 0;
        this.forPoints = 0;
        this.timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public void addMessage(String speaker, String content) {
        transcript.add(new Message(speaker, content, currentRound));
    }
    public void addRoundRecord(RoundRecord record) {
        roundRecords.add(record);
    }
    public void setMetadata(String key, String value) {
        metadata.put(key, value);
    }
    public Map<String, String> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }
    public List<RoundRecord> getRoundRecords() {
        return Collections.unmodifiableList(roundRecords);
    }
    public void addCriticPoint() { criticPoints++; }
    public void addForPoint() { forPoints++; }
    public void nextRound() { currentRound++; }

    public void conclude(String winner, String verdictText) {
        this.winner = winner;
        this.verdictText = verdictText;
        this.concluded = true;
    }

    public void setStances(String forMeans, String againstMeans) {
        this.forMeans = forMeans;
        this.againstMeans = againstMeans;
    }

    public String buildTranscriptContext() {
        if (transcript.isEmpty()) return "No arguments yet. Opening round.";
        StringBuilder sb = new StringBuilder();
        for (Message m : transcript) sb.append(m.toString()).append("\n\n");
        return sb.toString().trim();
    }

    public String buildSideTranscript(String speakerName) {
        StringBuilder sb = new StringBuilder();
        for (Message m : transcript)
            if (m.getSpeaker().equals(speakerName)) sb.append(m.getContent()).append("\n\n");
        return sb.toString().trim();
    }

    public String getStatement() { return statement; }
    public int getCurrentRound() { return currentRound; }
    public int getMaxRounds() { return maxRounds; }
    public boolean isConcluded() { return concluded; }
    public String getWinner() { return winner; }
    public String getVerdictText() { return verdictText; }
    public int getCriticPoints() { return criticPoints; }
    public int getForPoints() { return forPoints; }
    public List<Message> getTranscript() { return Collections.unmodifiableList(transcript); }
    public String getForMeans() { return forMeans; }
    public String getAgainstMeans() { return againstMeans; }
    public String getTimestamp() { return timestamp; }

    /**
     * Generates a comprehensive, cleanly structured plain-text / markdown debate report
     * containing metadata, stances, all arguments, round summaries, per-round judge reasoning, scores, and final verdict.
     */
    public String exportSessionReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("================================================================================\n");
        sb.append("                   COURTROOM AI DEBATE SESSION REPORT\n");
        sb.append("================================================================================\n");
        sb.append(String.format("Timestamp        : %s\n", timestamp));
        sb.append(String.format("Debate Statement : \"%s\"\n", statement != null ? statement : ""));
        sb.append(String.format("Rounds Configured: %d\n", maxRounds));
        sb.append(String.format("Status           : %s\n", concluded ? "Concluded" : "In Progress / Interrupted"));
        if (concluded) {
            sb.append(String.format("Overall Winner   : %s (Score: Against %d — For %d)\n", winner, criticPoints, forPoints));
        }
        sb.append("================================================================================\n\n");

        if ((forMeans != null && !forMeans.isBlank()) || (againstMeans != null && !againstMeans.isBlank())) {
            sb.append("--- DEBATE STANCES ---\n");
            if (forMeans != null && !forMeans.isBlank()) {
                sb.append("  [FOR Side]     : ").append(forMeans).append("\n");
            }
            if (againstMeans != null && !againstMeans.isBlank()) {
                sb.append("  [AGAINST Side] : ").append(againstMeans).append("\n");
            }
            sb.append("\n");
        }

        if (!metadata.isEmpty()) {
            sb.append("--- CONFIGURATION & PERSONAS ---\n");
            for (var entry : metadata.entrySet()) {
                sb.append(String.format("  • %-16s : %s\n", entry.getKey(), entry.getValue()));
            }
            sb.append("\n");
        }

        sb.append("================================================================================\n");
        sb.append("                              DEBATE PROCEEDINGS\n");
        sb.append("================================================================================\n\n");

        if (!roundRecords.isEmpty()) {
            for (RoundRecord rr : roundRecords) {
                sb.append(String.format("─── ROUND %d ──────────────────────────────────────────────────────────────────\n\n", rr.getRoundNumber()));
                if (rr.getRoundSummary() != null && !rr.getRoundSummary().isBlank()) {
                    sb.append("[ROUND SUMMARY]\n").append(rr.getRoundSummary()).append("\n\n");
                }
                sb.append("[AGAINST ARGUMENT]\n").append(rr.getAgainstArgument()).append("\n\n");
                sb.append("[FOR ARGUMENT]\n").append(rr.getForArgument()).append("\n\n");
                sb.append("[JUDGE EVALUATION]\n");
                if (rr.getJudgeReasoning() != null && !rr.getJudgeReasoning().isBlank()) {
                    sb.append("Reasoning : ").append(rr.getJudgeReasoning()).append("\n");
                }
                sb.append("Winner    : ").append(rr.getRoundWinner()).append("\n");
                sb.append("Score     : Against ").append(rr.getCriticPoints()).append(" — For ").append(rr.getForPoints()).append("\n\n");
            }
        } else {
            for (Message m : transcript) {
                sb.append(m.toString()).append("\n\n");
            }
        }

        if (concluded || (verdictText != null && !verdictText.isBlank())) {
            sb.append("================================================================================\n");
            sb.append("                          FINAL VERDICT & SUMMARY\n");
            sb.append("================================================================================\n");
            sb.append(String.format("Overall Winner : %s\n", winner != null ? winner : "N/A"));
            sb.append(String.format("Final Score    : Against %d — For %d\n", criticPoints, forPoints));
            if (verdictText != null && !verdictText.isBlank()) {
                sb.append(String.format("Verdict        : %s\n", verdictText));
            }
            sb.append("================================================================================\n");
        }

        return sb.toString();
    }

    /** Save the formatted session report to a local file. */
    public void saveToFile(File file) throws IOException {
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        Files.writeString(file.toPath(), exportSessionReport(), StandardCharsets.UTF_8);
    }
}

interface Agent {
    String getName();
    String getSystemPrompt();
    String argue(DebateSession session);
}

/** Callback for live debate progress — used by SwingWorker to repaint the GUI per round. */
interface DebateListener {
    default void onStances(String forMeans, String againstMeans) {}
    default void onRoundStart(int round) {}
    default void onArgument(String side, String text, int round) {}
    default void onRoundEnd(int round, String winner, int criticPoints, int forPoints) {}
    default void onRoundEnd(int round, String winner, String reasoning, int criticPoints, int forPoints) {
        onRoundEnd(round, winner, criticPoints, forPoints);
    }
    default void onRoundEnd(int round, String winner, String reasoning, String summary, int criticPoints, int forPoints) {
        onRoundEnd(round, winner, reasoning, criticPoints, forPoints);
    }
    default void onVerdict(String verdict, String overallWinner) {}
}

class Critic implements Agent {
    private final LLMClient client;
    private final WebSearcher webSearcher;
    private final boolean webSearchEnabled;

    public Critic(LLMClient client, WebSearcher webSearcher, boolean webSearchEnabled) {
        this.client = client;
        this.webSearcher = webSearcher;
        this.webSearchEnabled = webSearchEnabled;
    }

    public LLMClient getClient() { return client; }

    private static final String PERSONA =
        "You are arguing AGAINST the statement in a structured debate.\n" +
        "Make the strongest logical and factual case against it.\n" +
        "Build your own argument — do not just react to the other side.\n" +
        "4-6 sentences max.\n" +
        "When autonomous web evidence or news is provided below, you MUST cite it as the basis for your claims.\n" +
        "If web evidence is unavailable, rely strictly on reasoning and do not invent sources.";

    @Override public String getName() { return "Against"; }
    @Override public String getSystemPrompt() { return PERSONA; }

    @Override
    public String argue(DebateSession s) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Statement: \"").append(s.getStatement()).append("\"\n\n")
              .append("FOR stance: ").append(s.getForMeans()).append("\n\n")
              .append("AGAINST stance: ").append(s.getAgainstMeans()).append("\n\n")
              .append("Round: ").append(s.getCurrentRound()).append(" / ").append(s.getMaxRounds()).append("\n\n")
              .append("Transcript so far:\n").append(s.buildTranscriptContext()).append("\n\n");
        if (webSearchEnabled) {
            try {
                String query = s.getStatement() + " counter arguments against news debate facts";
                List<WebSearcher.SearchResult> hits = webSearcher.search(query, 3);
                if (!hits.isEmpty()) {
                    prompt.append("\n[Autonomous Web & News Evidence for AGAINST side — cite these]:\n");
                    for (WebSearcher.SearchResult r : hits) {
                        prompt.append("• ").append(r.title).append(" (").append(r.url).append(")\n");
                        if (r.snippet != null && !r.snippet.isBlank()) {
                            prompt.append("  Snippet: ").append(r.snippet).append("\n");
                        }
                    }
                } else {
                    prompt.append("\n[Web evidence unavailable; rely on reasoning only — do not invent sources]:\n");
                }
            } catch (IOException e) {
                prompt.append("\n[Web evidence UNAVAILABLE; rely on reasoning only — do not invent sources]:\n");
            }
        }
        // Cache-bust: append per-call nonce to user prompt
        try { return client.chat(PERSONA, prompt.toString() + "\n[nonce:" + System.nanoTime() + "]"); }
        catch (IOException e) { return "Against offline: " + e.getMessage(); }
    }
}

class ForSide implements Agent {
    private final LLMClient client;
    private final WebSearcher webSearcher;
    private final boolean webSearchEnabled;

    public ForSide(LLMClient client, WebSearcher webSearcher, boolean webSearchEnabled) {
        this.client = client;
        this.webSearcher = webSearcher;
        this.webSearchEnabled = webSearchEnabled;
    }

    public LLMClient getClient() { return client; }

    private static final String PERSONA =
        "You are arguing FOR the statement in a structured debate.\n" +
        "Make the strongest logical and factual case in favour of it.\n" +
        "Build your own argument — do not just react to the other side.\n" +
        "4-6 sentences max.\n" +
        "When autonomous web evidence or news is provided below, you MUST cite it as the basis for your claims.\n" +
        "If web evidence is unavailable, rely strictly on reasoning and do not invent sources.";

    @Override public String getName() { return "For"; }
    @Override public String getSystemPrompt() { return PERSONA; }

    @Override
    public String argue(DebateSession s) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Statement: \"").append(s.getStatement()).append("\"\n\n")
              .append("FOR stance: ").append(s.getForMeans()).append("\n\n")
              .append("AGAINST stance: ").append(s.getAgainstMeans()).append("\n\n")
              .append("Round: ").append(s.getCurrentRound()).append(" / ").append(s.getMaxRounds()).append("\n\n")
              .append("Transcript so far:\n").append(s.buildTranscriptContext()).append("\n\n");
        if (webSearchEnabled) {
            try {
                String query = s.getStatement() + " supporting evidence arguments for news debate facts";
                List<WebSearcher.SearchResult> hits = webSearcher.search(query, 3);
                if (!hits.isEmpty()) {
                    prompt.append("\n[Autonomous Web & News Evidence for FOR side — cite these]:\n");
                    for (WebSearcher.SearchResult r : hits) {
                        prompt.append("• ").append(r.title).append(" (").append(r.url).append(")\n");
                        if (r.snippet != null && !r.snippet.isBlank()) {
                            prompt.append("  Snippet: ").append(r.snippet).append("\n");
                        }
                    }
                } else {
                    prompt.append("\n[Web evidence unavailable; rely on reasoning only — do not invent sources]:\n");
                }
            } catch (IOException e) {
                prompt.append("\n[Web evidence UNAVAILABLE; rely on reasoning only — do not invent sources]:\n");
            }
        }
        try { return client.chat(PERSONA, prompt.toString() + "\n[nonce:" + System.nanoTime() + "]"); }
        catch (IOException e) { return "For offline: " + e.getMessage(); }
    }
}

class Judge {
    private final LLMClient client;

    public Judge(LLMClient client) { this.client = client; }
    public LLMClient getClient() { return client; }

    public JudgeDecision evaluateRound(DebateSession s) {
        String system =
            "You are an impartial judge. Evaluate both arguments purely on logical strength, factual accuracy, and clarity.\n" +
            "Ignore which side you personally agree with. The better argument wins the round regardless of topic.\n" +
            "Respond in EXACTLY this format, two lines, nothing else:\n" +
            "REASONING: <one sentence on which argument was logically stronger>\n" +
            "WINNER: <AGAINST or FOR>";
        String prompt = "Statement: \"" + s.getStatement() + "\"\n\n"
                      + "Full transcript:\n" + s.buildTranscriptContext() + "\n\n"
                      + "Judge both arguments on logic and evidence only. Output REASONING: then WINNER:";
        try {
            String response = client.chat(system, prompt + "\n[nonce:" + System.nanoTime() + "]").trim();
            String winner = "FOR";
            String reasoning = "";
            for (String line : response.split("\n")) {
                String trimmed = line.trim();
                String up = trimmed.toUpperCase();
                if (up.startsWith("REASONING:")) {
                    reasoning = trimmed.substring(10).trim();
                } else if (up.startsWith("WINNER:")) {
                    if (up.contains("AGAINST")) winner = "AGAINST";
                    else if (up.contains("FOR")) winner = "FOR";
                }
            }
            if (reasoning.isBlank()) {
                reasoning = response;
            }
            return new JudgeDecision(winner, reasoning);
        } catch (IOException e) {
            return new JudgeDecision("FOR", "Judge offline: " + e.getMessage());
        }
    }

    public String pickRoundWinner(DebateSession s) {
        return evaluateRound(s).getWinner();
    }

    public String finalVerdict(DebateSession s) {
        String winnerName  = s.getCriticPoints() > s.getForPoints() ? "Against" : "For";
        String winnerSide  = s.getCriticPoints() > s.getForPoints() ? "AGAINST" : "FOR";
        String winnerArgs  = s.buildSideTranscript(winnerName);

        String system =
            "You are the Judge delivering a final verdict on a debated statement.\n" +
            "Respond in EXACTLY this format, one line only:\n" +
            "TRUE: <one sentence summary of why the statement holds, using the winning side's arguments>\n" +
            "or\n" +
            "FALSE: <one sentence summary of why the statement does not hold, using the winning side's arguments>\n" +
            "No extra text.";

        String prompt = "Statement: \"" + s.getStatement() + "\"\n\n"
                      + "The winning side was: " + winnerSide + "\n\n"
                      + "Winning side's arguments across all rounds:\n" + winnerArgs + "\n\n"
                      + "Final score: Against " + s.getCriticPoints() + " — For " + s.getForPoints() + "\n\n"
                      + "Deliver the final verdict.";
        try {
            return client.chat(system, prompt + "\n[nonce:" + System.nanoTime() + "]").trim();
        } catch (IOException e) {
            return "Verdict unavailable: " + e.getMessage();
        }
    }
}

class Orchestrator {
    private final Critic critic;
    private final ForSide forSide;
    private final Judge judge;
    private final int maxRounds;
    private final boolean webSearchEnabled;
    private final DebateListener listener;

    public Orchestrator(int maxRounds,
                        LLMClient againstClient, LLMClient forClient, LLMClient judgeClient,
                        WebSearcher webSearcher, boolean webSearchEnabled) {
        this(maxRounds, againstClient, forClient, judgeClient, webSearcher, webSearchEnabled, null);
    }

    public Orchestrator(int maxRounds,
                        LLMClient againstClient, LLMClient forClient, LLMClient judgeClient,
                        WebSearcher webSearcher, boolean webSearchEnabled,
                        DebateListener listener) {
        this.critic  = new Critic(againstClient, webSearcher, webSearchEnabled);
        this.forSide = new ForSide(forClient,     webSearcher, webSearchEnabled);
        this.judge   = new Judge(judgeClient);
        this.maxRounds = maxRounds;
        this.webSearchEnabled = webSearchEnabled;
        this.listener = listener;
    }

    public DebateSession debate(String statement, boolean verbose) {
        DebateSession session = new DebateSession(statement, maxRounds);
        session.setMetadata("Against Side", critic.getClient().getProvider() + " (" + critic.getClient().getModel() + ")");
        session.setMetadata("For Side", forSide.getClient().getProvider() + " (" + forSide.getClient().getModel() + ")");
        session.setMetadata("Judge", judge.getClient().getProvider() + " (" + judge.getClient().getModel() + ")");
        session.setMetadata("Web Search", webSearchEnabled ? "Autonomous / Enabled" : "Disabled");

        String line = "_ + .".repeat(20);

        String[] sides = clarifyStances(statement);
        String forMeans     = sides[0];
        String againstMeans = sides[1];
        session.setStances(forMeans, againstMeans);

        if (listener != null) listener.onStances(forMeans, againstMeans);

        if (verbose) {
            System.out.println("\n" + line);
            System.out.println("Statement : \"" + statement + "\"");
            System.out.println("FOR       : " + forMeans);
            System.out.println("AGAINST   : " + againstMeans);
            System.out.println("MaxRounds : " + maxRounds);
            System.out.println(line);
        }

        for (int round = 1; round <= maxRounds; round++) {
            if (verbose) System.out.println("\nRound:" + round);
            if (listener != null) listener.onRoundStart(round);

            String cArg = critic.argue(session);
            session.addMessage(critic.getName(), cArg);
            if (listener != null) listener.onArgument("AGAINST", cArg, round);
            if (verbose) System.out.println("\nAgainst:\n" + cArg);

            String fArg = forSide.argue(session);
            session.addMessage(forSide.getName(), fArg);
            if (listener != null) listener.onArgument("FOR", fArg, round);
            if (verbose) System.out.println("\nFor:\n" + fArg);

            if (verbose) System.out.println("\nJudge...");

            JudgeDecision decision = judge.evaluateRound(session);
            String roundWinner = decision.getWinner();
            String roundReasoning = decision.getReasoning();

            if (roundWinner.equals("AGAINST")) {
                session.addCriticPoint();
            } else {
                session.addForPoint();
            }

            String roundSummary = RoundRecord.generateRoundSummary(cArg, fArg, roundReasoning, roundWinner);

            session.addRoundRecord(new RoundRecord(
                round, cArg, fArg, roundReasoning, roundWinner,
                session.getCriticPoints(), session.getForPoints(), roundSummary
            ));

            if (listener != null) {
                listener.onRoundEnd(round, roundWinner, roundReasoning, roundSummary, session.getCriticPoints(), session.getForPoints());
            }

            if (verbose) {
                System.out.println("Judge Reasoning: " + roundReasoning);
                System.out.println("Round Summary  : " + roundSummary);
                System.out.println("Round winner   : " + roundWinner);
                System.out.println("Score          : Against " + session.getCriticPoints()
                                 + " — For " + session.getForPoints());
            }

            boolean finalRound  = (round == maxRounds);
            boolean pointsGap   = Math.abs(session.getCriticPoints() - session.getForPoints()) == maxRounds;

            if (finalRound || pointsGap) {
                String verdict = judge.finalVerdict(session);
                String overallWinner = session.getCriticPoints() > session.getForPoints() ? "AGAINST" : "FOR";
                session.conclude(overallWinner, verdict);
                if (listener != null) listener.onVerdict(verdict, overallWinner);

                if (verbose) {
                    System.out.println("\n" + line);
                    System.out.println("Final Score : Against " + session.getCriticPoints()
                                     + " — For " + session.getForPoints());
                    System.out.println("Verdict     : " + verdict);
                    System.out.println("Winner      : " + overallWinner);
                    System.out.println(line);
                }
                break;
            } else {
                session.nextRound();
            }
        }
        return session;
    }

    private String[] clarifyStances(String statement) {
        String system = "You clarify debate positions. Be brief and direct.";
        String prompt = "For the statement: \"" + statement + "\"\n"
                      + "Respond in EXACTLY this format, one line each, no extra text:\n"
                      + "FOR: <what the FOR side is arguing in one short phrase>\n"
                      + "AGAINST: <what the AGAINST side is arguing in one short phrase>";
        try {
            // Cache-bust by appending a nonce; LLM providers cache identical prompts.
            String nonce = Long.toString(System.nanoTime());
            String response = judge.getClient().chat(system, prompt + "\n[nonce:" + nonce + "]");
            String forMeans     = "agrees with the statement";
            String againstMeans = "disagrees with the statement";
            for (String l : response.split("\n")) {
                if (l.startsWith("FOR:"))     forMeans     = l.substring(4).trim();
                if (l.startsWith("AGAINST:")) againstMeans = l.substring(8).trim();
            }
            return new String[]{forMeans, againstMeans};
        } catch (IOException e) {
            return new String[]{"agrees with the statement", "disagrees with the statement"};
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  GUI CLASSES (High-Contrast Dark Theme + Switchable Controls)
// ══════════════════════════════════════════════════════════════════════════════

class CourtroomFrame extends JFrame {
    private static final int WIDTH = 1100;
    private static final int HEIGHT = 850;

    // Dark theme color palette
    private static final Color BG_DARK      = new Color(18, 20, 24);   // Deep background
    private static final Color PANEL_DARK   = new Color(24, 28, 36);   // Component background
    private static final Color INPUT_DARK   = new Color(28, 32, 42);   // Textfield & area background
    private static final Color TEXT_WHITE   = new Color(248, 250, 252);// Crisp white text
    private static final Color BORDER_GRAY  = new Color(55, 65, 81);   // Subtle contrast border

    // Vibrant button & accent colors
    private static final Color COLOR_GREEN  = new Color(52, 211, 153); // #34d399 (Start / For)
    private static final Color COLOR_RED    = new Color(248, 113, 113);// #f87171 (Stop / Against)
    private static final Color COLOR_AMBER  = new Color(251, 191, 36); // #fbbf24 (Clear)
    private static final Color COLOR_CYAN   = new Color(56, 189, 248); // #38bdf8 (Save / Round)
    private static final Color COLOR_PURPLE = new Color(192, 132, 252);// #c084fc (Judge)

    private final Properties config;
    private final String apiKey;
    private final JSpinner roundsSpinner;
    private final JTextArea againstArea;
    private final JTextArea forArea;
    private final JTextArea judgeArea;
    private final JTextArea transcriptArea;
    private final JLabel againstScore;
    private final JLabel forScore;
    private final JLabel roundLabel;
    private final JLabel statusLabel;
    private final JTextField statementField;
    private final JButton startStopButton;
    private final JButton clearBtn;
    private final JButton saveSessionBtn;

    private boolean isDebateRunning = false;
    private DebateSession currentSession;
    private SwingWorker<DebateSession, String> worker;

    public CourtroomFrame(Properties config, String apiKey) {
        this.config = config;
        this.apiKey = apiKey;

        setTitle("Courtroom — AI Debate Simulator");
        setSize(WIDTH, HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);

        // Initialize status and score labels
        this.statusLabel = new JLabel("Ready — enter statement and press Start");
        this.statusLabel.setForeground(TEXT_WHITE);
        this.statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        this.againstScore = new JLabel("Against: 0");
        this.againstScore.setForeground(COLOR_RED);
        this.againstScore.setFont(new Font("SansSerif", Font.BOLD, 13));

        this.forScore = new JLabel("For: 0");
        this.forScore.setForeground(COLOR_GREEN);
        this.forScore.setFont(new Font("SansSerif", Font.BOLD, 13));

        this.roundLabel = new JLabel("Round: 1 / 3");
        this.roundLabel.setForeground(COLOR_CYAN);
        this.roundLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

        this.statementField = new JTextField();
        this.statementField.setBackground(INPUT_DARK);
        this.statementField.setForeground(TEXT_WHITE);
        this.statementField.setCaretColor(Color.WHITE);
        this.statementField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        this.statementField.setBorder(new CompoundBorder(
            new LineBorder(BORDER_GRAY, 1, true),
            new EmptyBorder(6, 8, 6, 8)
        ));

        // Auto-switchable Start/Stop button + Action buttons
        this.startStopButton = createStyledButton("▶ Start Debate", COLOR_GREEN);
        this.clearBtn = createStyledButton("🗑 Clear", COLOR_AMBER);
        this.saveSessionBtn = createStyledButton("💾 Save Session", COLOR_CYAN);

        this.transcriptArea = new JTextArea(8, 60);
        this.roundsSpinner = new JSpinner();

        // Dark-styled Menu Bar
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(PANEL_DARK);
        menuBar.setBorder(new LineBorder(BORDER_GRAY, 1));

        JMenu fileMenu = new JMenu("File");
        fileMenu.setForeground(TEXT_WHITE);
        JMenuItem saveSessionItem = new JMenuItem("Save Full Session to File...");
        saveSessionItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        saveSessionItem.addActionListener(e -> saveCurrentSessionPrompt());
        styleMenuItem(saveSessionItem);

        JMenuItem saveStmtItem = new JMenuItem("Save Current Statement");
        saveStmtItem.addActionListener(e -> {
            if (currentSession != null) {
                try {
                    Files.writeString(Path.of("last_statement.txt"),
                        currentSession.getStatement());
                    statusLabel.setText("Statement saved to last_statement.txt");
                } catch (IOException ex) {
                    statusLabel.setText("Failed to save statement.");
                }
            } else if (!statementField.getText().isBlank()) {
                try {
                    Files.writeString(Path.of("last_statement.txt"), statementField.getText().trim());
                    statusLabel.setText("Statement saved to last_statement.txt");
                } catch (IOException ex) {
                    statusLabel.setText("Failed to save statement.");
                }
            }
        });
        styleMenuItem(saveStmtItem);

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        styleMenuItem(exitItem);

        fileMenu.add(saveSessionItem);
        fileMenu.add(saveStmtItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        JMenu settingsMenu = new JMenu("Settings");
        settingsMenu.setForeground(TEXT_WHITE);
        JMenuItem settingsItem = new JMenuItem("Settings...");
        settingsItem.addActionListener(e -> openSettings());
        styleMenuItem(settingsItem);
        settingsMenu.add(settingsItem);

        JMenu aboutMenu = new JMenu("About");
        aboutMenu.setForeground(TEXT_WHITE);
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(this,
            "Courtroom Debate Simulator v2.0\n" +
            "Autonomous AI-powered structured debate with OpenRouter / Ollama.\n" +
            "Each agent autonomously accesses internet search for live facts, debates, and news evidence.\n" +
            "Includes comprehensive local session saving, dark mode contrast, and round summaries.",
            "About", JOptionPane.INFORMATION_MESSAGE));
        styleMenuItem(aboutItem);
        aboutMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(settingsMenu);
        menuBar.add(aboutMenu);
        setJMenuBar(menuBar);

        // Top panel: statement input and switchable controls
        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.setBackground(PANEL_DARK);
        topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel stmtLabel = new JLabel("Statement for Debate:");
        stmtLabel.setForeground(TEXT_WHITE);
        stmtLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        topPanel.add(stmtLabel, BorderLayout.NORTH);

        statementField.setText(config.getProperty("last.statement", ""));
        statementField.addActionListener(e -> {
            if (isDebateRunning) stopDebate(); else startDebate();
        });
        topPanel.add(statementField, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnPanel.setBackground(PANEL_DARK);
        btnPanel.add(startStopButton);
        btnPanel.add(clearBtn);
        btnPanel.add(saveSessionBtn);
        topPanel.add(btnPanel, BorderLayout.SOUTH);

        JPanel roundsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        roundsPanel.setBackground(PANEL_DARK);
        JLabel roundsLbl = new JLabel("Rounds:");
        roundsLbl.setForeground(TEXT_WHITE);
        roundsLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        roundsPanel.add(roundsLbl);

        roundsSpinner.setModel(new SpinnerNumberModel(
            Integer.parseInt(config.getProperty("rounds", "3")), 1, 20, 1));
        roundsSpinner.setBackground(INPUT_DARK);
        roundsSpinner.setForeground(TEXT_WHITE);
        JComponent spinnerEditor = roundsSpinner.getEditor();
        if (spinnerEditor instanceof JSpinner.DefaultEditor de) {
            de.getTextField().setBackground(INPUT_DARK);
            de.getTextField().setForeground(TEXT_WHITE);
            de.getTextField().setCaretColor(Color.WHITE);
        }
        roundsPanel.add(roundsSpinner);
        topPanel.add(roundsPanel, BorderLayout.EAST);

        // Three response panels (Against, For, Judge)
        JPanel responsesPanel = new JPanel(new GridLayout(1, 3, 8, 8));
        responsesPanel.setBackground(BG_DARK);
        responsesPanel.setBorder(new EmptyBorder(0, 10, 10, 10));
        againstArea = makeResponseArea("Against");
        forArea = makeResponseArea("For");
        judgeArea = makeResponseArea("Judge");
        responsesPanel.add(wrapTitled("Against (Critic)", COLOR_RED, againstArea));
        responsesPanel.add(wrapTitled("For (Proponent)", COLOR_GREEN, forArea));
        responsesPanel.add(wrapTitled("Judge (Evaluation & Summary)", COLOR_PURPLE, judgeArea));

        // Transcript + scoreboard
        JPanel bottomPanel = new JPanel(new BorderLayout(6, 6));
        bottomPanel.setBackground(PANEL_DARK);
        bottomPanel.setBorder(new EmptyBorder(0, 10, 10, 10));

        transcriptArea.setEditable(false);
        transcriptArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        transcriptArea.setBackground(new Color(16, 19, 26));
        transcriptArea.setForeground(TEXT_WHITE);
        transcriptArea.setCaretColor(Color.WHITE);
        transcriptArea.setMargin(new Insets(8, 8, 8, 8));

        JScrollPane transcriptScroll = new JScrollPane(transcriptArea);
        transcriptScroll.setBackground(PANEL_DARK);
        transcriptScroll.getViewport().setBackground(new Color(16, 19, 26));
        transcriptScroll.setBorder(new TitledBorder(
            new LineBorder(BORDER_GRAY, 1),
            "Transcript & Session Report",
            TitledBorder.LEADING,
            TitledBorder.DEFAULT_POSITION,
            new Font("SansSerif", Font.BOLD, 12),
            COLOR_CYAN
        ));
        bottomPanel.add(transcriptScroll, BorderLayout.CENTER);

        JPanel scorePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 25, 5));
        scorePanel.setBackground(PANEL_DARK);
        scorePanel.setBorder(new EmptyBorder(4, 4, 4, 4));
        scorePanel.add(againstScore);
        scorePanel.add(forScore);
        scorePanel.add(roundLabel);
        scorePanel.add(statusLabel);
        bottomPanel.add(scorePanel, BorderLayout.NORTH);

        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(responsesPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Action listeners
        startStopButton.addActionListener(e -> {
            if (isDebateRunning) {
                stopDebate();
            } else {
                startDebate();
            }
        });

        saveSessionBtn.addActionListener(e -> saveCurrentSessionPrompt());
        clearBtn.addActionListener(e -> {
            statementField.setText("");
            againstArea.setText("");
            forArea.setText("");
            judgeArea.setText("");
            transcriptArea.setText("");
            againstScore.setText("Against: 0");
            forScore.setText("For: 0");
            roundLabel.setText("Round: 1 / 3");
            statusLabel.setText("Cleared. Enter a statement and press Start.");
            currentSession = null;
        });

        // Keyboard shortcuts
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "saveSession");
        getRootPane().getActionMap().put("saveSession", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { saveCurrentSessionPrompt(); }
        });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "startDebate");
        getRootPane().getActionMap().put("startDebate", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (isDebateRunning) stopDebate(); else startDebate();
            }
        });

        setVisible(true);
    }

    public static JButton createStyledButton(String text, Color textColor) {
        JButton btn = new JButton(text);
        btn.setForeground(textColor);
        btn.setBackground(new Color(36, 42, 54));
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorder(new CompoundBorder(
            new LineBorder(new Color(60, 72, 94), 1, true),
            new EmptyBorder(6, 12, 6, 12)
        ));
        return btn;
    }

    private void styleMenuItem(JMenuItem item) {
        item.setBackground(INPUT_DARK);
        item.setForeground(TEXT_WHITE);
    }

    private JTextArea makeResponseArea(String title) {
        JTextArea ta = new JTextArea(8, 20);
        ta.setEditable(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setFont(new Font("SansSerif", Font.PLAIN, 12));
        ta.setBackground(new Color(22, 26, 35));
        ta.setForeground(TEXT_WHITE);
        ta.setCaretColor(Color.WHITE);
        ta.setSelectedTextColor(Color.WHITE);
        ta.setSelectionColor(new Color(59, 130, 246, 160));
        ta.setMargin(new Insets(6, 6, 6, 6));
        return ta;
    }

    private JPanel wrapTitled(String title, Color titleColor, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PANEL_DARK);
        TitledBorder tb = new TitledBorder(
            new LineBorder(BORDER_GRAY, 1),
            title,
            TitledBorder.LEADING,
            TitledBorder.DEFAULT_POSITION,
            new Font("SansSerif", Font.BOLD, 12),
            titleColor
        );
        p.setBorder(tb);
        JScrollPane sp = new JScrollPane(comp);
        sp.setBackground(PANEL_DARK);
        sp.getViewport().setBackground(comp.getBackground());
        sp.setBorder(null);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private static final int MAX_STATEMENT_LEN = 2000;

    private void startDebate() {
        String stmt = statementField.getText().trim();
        if (stmt.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a debate statement first.",
                "No Statement", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (stmt.length() > MAX_STATEMENT_LEN) {
            stmt = stmt.substring(0, MAX_STATEMENT_LEN) + "\n[... truncated ...]";
            statementField.setText(stmt);
            statusLabel.setText("Statement truncated to " + MAX_STATEMENT_LEN + " chars.");
        }
        final String finalStmt = stmt;
        int rounds = (Integer) roundsSpinner.getValue();
        config.setProperty("last.statement", stmt);
        config.setProperty("rounds", String.valueOf(rounds));
        Courtroom.saveConfig(config);

        againstArea.setText("");
        forArea.setText("");
        judgeArea.setText("");
        transcriptArea.setText("Statement: " + stmt + "\n\n");
        againstScore.setText("Against: 0");
        forScore.setText("For: 0");
        roundLabel.setText("Round: 1 / " + rounds);
        statusLabel.setText("Debate running (autonomous web search active)...");

        // Switch button to Stop mode
        isDebateRunning = true;
        startStopButton.setText("⏹ Stop Debate");
        startStopButton.setForeground(COLOR_RED);

        worker = new SwingWorker<DebateSession, String>() {
            @Override
            protected DebateSession doInBackground() throws Exception {
                boolean webSearch = Boolean.parseBoolean(
                    config.getProperty("websearch.enabled", "true"));
                WebSearcher searcher = new WebSearcher();
                String orKey = apiKey;

                // Per-persona clients — fall back to Ollama if OpenRouter key missing/invalid.
                String againstProvider = config.getProperty("against.provider", "ollama");
                String againstModel    = config.getProperty("against.model", "");
                String forProvider     = config.getProperty("for.provider", "ollama");
                String forModel        = config.getProperty("for.model", "");
                String judgeProvider   = config.getProperty("judge.provider", "ollama");
                String judgeModel      = config.getProperty("judge.model", "");
                String orUrl           = config.getProperty("openrouter.url", "https://openrouter.ai/api/v1/chat/completions");
                String ollamaUrl       = config.getProperty("ollama.url", "http://localhost:11434/api/chat");

                boolean keyEmpty = (orKey == null || orKey.isBlank());
                LLMClient againstClient, forClient, judgeClient;

                if (keyEmpty && "openrouter".equalsIgnoreCase(againstProvider)) {
                    System.out.println("[Courtroom] Against: no OpenRouter key, using Ollama " + ollamaUrl);
                    againstClient = new OllamaClient2(againstModel, ollamaUrl);
                } else if ("openrouter".equalsIgnoreCase(againstProvider)) {
                    againstClient = new OpenRouterClient(orKey, againstModel, orUrl);
                } else {
                    againstClient = new OllamaClient2(againstModel, ollamaUrl);
                }

                if (keyEmpty && "openrouter".equalsIgnoreCase(forProvider)) {
                    System.out.println("[Courtroom] For: no OpenRouter key, using Ollama " + ollamaUrl);
                    forClient = new OllamaClient2(forModel, ollamaUrl);
                } else if ("openrouter".equalsIgnoreCase(forProvider)) {
                    forClient = new OpenRouterClient(orKey, forModel, orUrl);
                } else {
                    forClient = new OllamaClient2(forModel, ollamaUrl);
                }

                if (keyEmpty && "openrouter".equalsIgnoreCase(judgeProvider)) {
                    System.out.println("[Courtroom] Judge: no OpenRouter key, using Ollama " + ollamaUrl);
                    judgeClient = new OllamaClient2(judgeModel, ollamaUrl);
                } else if ("openrouter".equalsIgnoreCase(judgeProvider)) {
                    judgeClient = new OpenRouterClient(orKey, judgeModel, orUrl);
                } else {
                    judgeClient = new OllamaClient2(judgeModel, ollamaUrl);
                }

                Orchestrator court = new Orchestrator(rounds, againstClient, forClient, judgeClient, searcher, webSearch,
                    new DebateListener() {
                        @Override public void onArgument(String side, String text, int round) {
                            publish(side + ":" + text.trim());
                        }
                        @Override public void onRoundEnd(int round, String winner, String reasoning, String summary, int cp, int fp) {
                            String safeReasoning = (reasoning != null) ? reasoning.replace("\n", " ").replace("|", ";") : "";
                            String safeSummary = (summary != null) ? summary.replace("\n", " ").replace("|", ";") : "";
                            publish("ROUND_END:" + round + "|" + winner + "|" + cp + "|" + fp + "|" + safeReasoning + "|" + safeSummary);
                        }
                        @Override public void onVerdict(String verdict, String overallWinner) {
                            publish("VERDICT:" + overallWinner + "|" + verdict.trim());
                        }
                        @Override public void onStances(String forM, String againstM) {
                            publish("STANCES:FOR=" + forM + "|AGAINST=" + againstM);
                        }
                        @Override public void onRoundStart(int round) {
                            publish("ROUND_START:" + round);
                        }
                    });
                DebateSession result = court.debate(finalStmt, false); // verbose false for GUI
                return result;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String ch : chunks) {
                    if (ch.startsWith("AGAINST:")) {
                        againstArea.append(ch.substring(8) + "\n\n");
                    } else if (ch.startsWith("FOR:")) {
                        forArea.append(ch.substring(5) + "\n\n");
                    } else if (ch.startsWith("ROUND_START:")) {
                        statusLabel.setText("Round " + ch.substring(12) + " starting...");
                    } else if (ch.startsWith("ROUND_END:")) {
                        String[] parts = ch.substring(10).split("\\|", 6);
                        if (parts.length >= 4) {
                            int r = Integer.parseInt(parts[0]);
                            String w = parts[1];
                            int cp = Integer.parseInt(parts[2]);
                            int fp = Integer.parseInt(parts[3]);
                            String reasoning = (parts.length >= 5) ? parts[4] : "";
                            String summary = (parts.length >= 6) ? parts[5] : "";
                            if (!reasoning.isBlank()) {
                                judgeArea.append("Round " + r + " Reasoning: " + reasoning + "\n");
                            }
                            judgeArea.append("Round " + r + " Winner: " + w + " (Score: Against " + cp + " — For " + fp + ")\n\n");
                            if (!summary.isBlank()) {
                                transcriptArea.append("── Round " + r + " Summary ──\n" + summary + "\n\n");
                            }
                            againstScore.setText("Against: " + cp);
                            forScore.setText("For: " + fp);
                            roundLabel.setText("Round: " + r);
                        }
                    } else if (ch.startsWith("VERDICT:")) {
                        String[] parts = ch.substring(8).split("\\|", 2);
                        if (parts.length == 2) {
                            judgeArea.append("FINAL VERDICT (" + parts[0] + "): " + parts[1] + "\n\n");
                        }
                    } else if (ch.startsWith("STANCES:")) {
                        transcriptArea.append(ch.substring(8).replace("|", "\n") + "\n\n");
                    } else {
                        transcriptArea.append(ch + "\n\n");
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    currentSession = get();
                    updateUIFromSession(currentSession);
                    autoSaveSession(currentSession);
                    statusLabel.setText("Debate complete — Winner: " + currentSession.getWinner() + " (Auto-saved)");
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                    againstArea.append("\nError: " + e.getMessage() + "\n");
                } finally {
                    resetStartStopButton();
                }
            }
        };

        worker.execute();
    }

    private void stopDebate() {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
            statusLabel.setText("Debate stopped by user.");
        }
        resetStartStopButton();
    }

    private void resetStartStopButton() {
        isDebateRunning = false;
        startStopButton.setText("▶ Start Debate");
        startStopButton.setForeground(COLOR_GREEN);
        startStopButton.setEnabled(true);
    }

    private void updateUIFromSession(DebateSession s) {
        if (s == null) return;
        againstScore.setText("Against: " + s.getCriticPoints());
        forScore.setText("For: " + s.getForPoints());
        roundLabel.setText("Round: " + s.getCurrentRound() + " / " + s.getMaxRounds());
        transcriptArea.setText(s.exportSessionReport());
    }

    /** Prompts user with a file chooser dialog to save the entire debate session locally. */
    private void saveCurrentSessionPrompt() {
        if (currentSession == null) {
            JOptionPane.showMessageDialog(this,
                "No debate session to save yet.\nPlease start and complete a debate first.",
                "No Session", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Debate Session Locally");

        File sessionsDir = new File("sessions");
        if (!sessionsDir.exists()) {
            sessionsDir.mkdirs();
        }
        chooser.setCurrentDirectory(sessionsDir.exists() ? sessionsDir : new File("."));

        String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .format(java.time.LocalDateTime.now());
        String slug = sanitizeFilename(currentSession.getStatement(), 25);
        String defaultName = "debate_" + timestamp + (slug.isEmpty() ? "" : "_" + slug) + ".txt";
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), defaultName));

        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Text and Markdown Files (*.txt, *.md)", "txt", "md"));

        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().contains(".")) {
                file = new File(file.getParentFile(), file.getName() + ".txt");
            }
            try {
                currentSession.saveToFile(file);
                statusLabel.setText("Session saved to: " + file.getName());
                JOptionPane.showMessageDialog(this,
                    "Debate session successfully saved to:\n" + file.getAbsolutePath(),
                    "Session Saved", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                    "Failed to save session: " + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("Failed to save session.");
            }
        }
    }

    /** Automatically writes the session report to the sessions/ directory and last_debate_session.txt. */
    private void autoSaveSession(DebateSession session) {
        if (session == null) return;
        try {
            Path sessionsDir = Path.of("sessions");
            if (!Files.exists(sessionsDir)) {
                Files.createDirectories(sessionsDir);
            }
            Files.writeString(Path.of("last_debate_session.txt"), session.exportSessionReport(), StandardCharsets.UTF_8);
            String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                .format(java.time.LocalDateTime.now());
            String slug = sanitizeFilename(session.getStatement(), 20);
            Path autoFilePath = sessionsDir.resolve("debate_" + timestamp + (slug.isEmpty() ? "" : "_" + slug) + ".txt");
            Files.writeString(autoFilePath, session.exportSessionReport(), StandardCharsets.UTF_8);
            System.out.println("[Courtroom] Session auto-saved to " + autoFilePath);
        } catch (Exception ignored) {}
    }

    private static String sanitizeFilename(String name, int maxLen) {
        if (name == null) return "";
        String clean = name.replaceAll("[^a-zA-Z0-9_\\-]", "_").replaceAll("_+", "_");
        if (clean.length() > maxLen) clean = clean.substring(0, maxLen);
        if (clean.endsWith("_")) clean = clean.substring(0, clean.length() - 1);
        return clean;
    }

    private void openSettings() {
        SettingsDialog dlg = new SettingsDialog(this, config, apiKey);
        dlg.setVisible(true);
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  TEST MOCK (same file; no external test framework needed)
// ══════════════════════════════════════════════════════════════════════════════

class MockLLMClient implements LLMClient {
    private final String fixedResponse;
    private final String provider;
    private final String model;
    public MockLLMClient(String provider, String model, String fixedResponse) {
        this.provider = provider; this.model = model; this.fixedResponse = fixedResponse;
    }
    @Override public String chat(String system, String user) throws IOException {
        return (fixedResponse == null || fixedResponse.isBlank()) ? "Mock response for: " + user.substring(0, Math.min(30, user.length())) : fixedResponse;
    }
    @Override public String getModel() { return model; }
    @Override public String getProvider() { return provider; }
}

class SettingsDialog extends JDialog {
    private static final String[] OPENROUTER_MODELS = {
        "meta-llama/llama-3-70b-instruct",
        "openai/gpt-4-turbo",
        "anthropic/claude-3-opus",
        "google/gemini-pro",
        "deepseek/deepseek-chat",
        "mistralai/mixtral-8x7b-instruct"
    };

    private static final Color PANEL_DARK   = new Color(24, 28, 36);
    private static final Color INPUT_DARK   = new Color(28, 32, 42);
    private static final Color TEXT_WHITE   = new Color(248, 250, 252);
    private static final Color BORDER_GRAY  = new Color(55, 65, 81);

    private final Properties config;
    private final String currentApiKey;
    private final JTextField orKeyField;
    private final JCheckBox saveKeyBox;
    private final JComboBox<String> againstProvider;
    private final JComboBox<String> againstModel;
    private final JComboBox<String> forProvider;
    private final JComboBox<String> forModel;
    private final JComboBox<String> judgeProvider;
    private final JComboBox<String> judgeModel;
    private final JCheckBox webSearchBox;
    private final JTextField orUrlField;
    private final JTextField ollamaUrlField;
    private final JLabel ollamaStatusLabel;
    private final JButton fetchOllamaBtn;

    private List<String> cachedOllamaModels = new ArrayList<>();

    public SettingsDialog(JFrame parent, Properties config, String apiKey) {
        super(parent, "Settings", true);
        this.config = config;
        this.currentApiKey = apiKey;
        setSize(640, 580);
        setLocationRelativeTo(parent);
        getContentPane().setBackground(PANEL_DARK);
        setLayout(new BorderLayout(10, 10));

        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
        panel.setBackground(PANEL_DARK);
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        panel.add(makeLabel("OpenRouter API Key:"));
        orKeyField = new JTextField(apiKey == null ? "" : apiKey);
        styleInput(orKeyField);
        panel.add(orKeyField);

        panel.add(makeLabel("Persist API key to config.properties?"));
        saveKeyBox = new JCheckBox("Save key to disk", false);
        saveKeyBox.setBackground(PANEL_DARK);
        saveKeyBox.setForeground(TEXT_WHITE);
        panel.add(saveKeyBox);

        panel.add(makeLabel("OpenRouter URL (advanced):"));
        orUrlField = new JTextField(config.getProperty("openrouter.url", "https://openrouter.ai/api/v1/chat/completions"));
        styleInput(orUrlField);
        panel.add(orUrlField);

        panel.add(makeLabel("Ollama URL (local/remote):"));
        ollamaUrlField = new JTextField(config.getProperty("ollama.url", OllamaClient2.DEFAULT_URL));
        styleInput(ollamaUrlField);
        panel.add(ollamaUrlField);

        // Ollama auto-fetch controls
        panel.add(makeLabel("Ollama Auto-Fetch:"));
        JPanel fetchPanel = new JPanel(new BorderLayout(5, 0));
        fetchPanel.setBackground(PANEL_DARK);
        fetchOllamaBtn = CourtroomFrame.createStyledButton("↻ Auto-Fetch Models", new Color(192, 132, 252));
        ollamaStatusLabel = new JLabel("Click to fetch", SwingConstants.LEFT);
        ollamaStatusLabel.setForeground(new Color(56, 189, 248));
        ollamaStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        fetchPanel.add(fetchOllamaBtn, BorderLayout.WEST);
        fetchPanel.add(ollamaStatusLabel, BorderLayout.CENTER);
        panel.add(fetchPanel);

        panel.add(makeLabel("Against Provider:"));
        againstProvider = new JComboBox<>(new String[]{"ollama", "openrouter"});
        styleCombo(againstProvider);
        panel.add(againstProvider);

        panel.add(makeLabel("Against Model:"));
        againstModel = new JComboBox<>();
        againstModel.setEditable(true);
        styleCombo(againstModel);
        panel.add(againstModel);

        panel.add(makeLabel("For Provider:"));
        forProvider = new JComboBox<>(new String[]{"ollama", "openrouter"});
        styleCombo(forProvider);
        panel.add(forProvider);

        panel.add(makeLabel("For Model:"));
        forModel = new JComboBox<>();
        forModel.setEditable(true);
        styleCombo(forModel);
        panel.add(forModel);

        panel.add(makeLabel("Judge Provider:"));
        judgeProvider = new JComboBox<>(new String[]{"ollama", "openrouter"});
        styleCombo(judgeProvider);
        panel.add(judgeProvider);

        panel.add(makeLabel("Judge Model:"));
        judgeModel = new JComboBox<>();
        judgeModel.setEditable(true);
        styleCombo(judgeModel);
        panel.add(judgeModel);

        panel.add(makeLabel("Autonomous Web Search (Per-Persona):"));
        webSearchBox = new JCheckBox("Enabled", Boolean.parseBoolean(config.getProperty("websearch.enabled", "true")));
        webSearchBox.setBackground(PANEL_DARK);
        webSearchBox.setForeground(TEXT_WHITE);
        panel.add(webSearchBox);

        // Pre-populate provider from config
        againstProvider.setSelectedItem(config.getProperty("against.provider", "ollama"));
        forProvider.setSelectedItem(config.getProperty("for.provider", "ollama"));
        judgeProvider.setSelectedItem(config.getProperty("judge.provider", "ollama"));

        // Setup provider listeners
        againstProvider.addActionListener(e -> {
            String prov = (String) againstProvider.getSelectedItem();
            populateModelCombo(againstModel, prov, getComboValue(againstModel));
        });
        forProvider.addActionListener(e -> {
            String prov = (String) forProvider.getSelectedItem();
            populateModelCombo(forModel, prov, getComboValue(forModel));
        });
        judgeProvider.addActionListener(e -> {
            String prov = (String) judgeProvider.getSelectedItem();
            populateModelCombo(judgeModel, prov, getComboValue(judgeModel));
        });

        fetchOllamaBtn.addActionListener(e -> triggerOllamaFetch(true));

        add(panel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel();
        btnPanel.setBackground(PANEL_DARK);
        JButton saveBtn = CourtroomFrame.createStyledButton("Save", new Color(52, 211, 153));
        JButton cancelBtn = CourtroomFrame.createStyledButton("Cancel", new Color(248, 113, 113));
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);
        add(btnPanel, BorderLayout.SOUTH);

        saveBtn.addActionListener(e -> {
            config.setProperty("against.provider", (String) againstProvider.getSelectedItem());
            config.setProperty("against.model", getComboValue(againstModel));
            config.setProperty("for.provider", (String) forProvider.getSelectedItem());
            config.setProperty("for.model", getComboValue(forModel));
            config.setProperty("judge.provider", (String) judgeProvider.getSelectedItem());
            config.setProperty("judge.model", getComboValue(judgeModel));
            config.setProperty("websearch.enabled", String.valueOf(webSearchBox.isSelected()));
            config.setProperty("openrouter.url", orUrlField.getText().trim());
            config.setProperty("ollama.url", ollamaUrlField.getText().trim());

            String newKey = orKeyField.getText().trim();
            if (!newKey.isEmpty() && saveKeyBox.isSelected()) {
                config.setProperty("openrouter.api.key", newKey);
            }

            Courtroom.saveConfig(config);
            setVisible(false);
        });

        cancelBtn.addActionListener(e -> setVisible(false));

        triggerOllamaFetch(false);
    }

    private JLabel makeLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_WHITE);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return l;
    }

    private void styleInput(JTextField tf) {
        tf.setBackground(INPUT_DARK);
        tf.setForeground(TEXT_WHITE);
        tf.setCaretColor(Color.WHITE);
        tf.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        tf.setBorder(new CompoundBorder(new LineBorder(BORDER_GRAY, 1), new EmptyBorder(3, 5, 3, 5)));
    }

    private void styleCombo(JComboBox<String> cb) {
        cb.setBackground(INPUT_DARK);
        cb.setForeground(TEXT_WHITE);
    }

    private String getComboValue(JComboBox<String> combo) {
        if (combo.isEditable()) {
            Object ed = combo.getEditor().getItem();
            if (ed != null && !ed.toString().isBlank()) return ed.toString().trim();
        }
        Object sel = combo.getSelectedItem();
        return sel != null ? sel.toString().trim() : "";
    }

    private void populateModelCombo(JComboBox<String> combo, String provider, String preferredValue) {
        String currentSelection = preferredValue;
        if (currentSelection == null || currentSelection.isBlank()) {
            currentSelection = getComboValue(combo);
        }

        combo.removeAllItems();
        if ("ollama".equalsIgnoreCase(provider)) {
            if (!cachedOllamaModels.isEmpty()) {
                for (String m : cachedOllamaModels) {
                    combo.addItem(m);
                }
            } else {
                combo.addItem("qwen2.5");
                combo.addItem("llama3.2");
                combo.addItem("mistral");
            }
        } else {
            for (String m : OPENROUTER_MODELS) {
                combo.addItem(m);
            }
        }

        if (currentSelection != null && !currentSelection.isBlank()) {
            boolean found = false;
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (currentSelection.equalsIgnoreCase(combo.getItemAt(i))) {
                    combo.setSelectedIndex(i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                combo.insertItemAt(currentSelection, 0);
                combo.setSelectedIndex(0);
            }
        } else if (combo.getItemCount() > 0) {
            combo.setSelectedIndex(0);
        }
    }

    private void triggerOllamaFetch(boolean showProgress) {
        String rawUrl = ollamaUrlField.getText().trim();
        final String url = rawUrl.isEmpty() ? OllamaClient2.DEFAULT_URL : rawUrl;
        if (showProgress) {
            ollamaStatusLabel.setText("Fetching models...");
            ollamaStatusLabel.setForeground(new Color(56, 189, 248));
            fetchOllamaBtn.setEnabled(false);
        }

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return OllamaClient2.fetchModels(url);
            }

            @Override
            protected void done() {
                try {
                    List<String> models = get();
                    if (!models.isEmpty()) {
                        cachedOllamaModels = models;
                        ollamaStatusLabel.setText("✓ " + models.size() + " model(s) auto-fetched");
                        ollamaStatusLabel.setForeground(new Color(52, 211, 153));
                    } else {
                        ollamaStatusLabel.setText("⚠ Offline / no models");
                        ollamaStatusLabel.setForeground(new Color(248, 113, 113));
                    }

                    String againstInit = config.getProperty("against.model", "");
                    String forInit     = config.getProperty("for.model", "");
                    String judgeInit   = config.getProperty("judge.model", "");

                    populateModelCombo(againstModel, (String) againstProvider.getSelectedItem(), againstInit);
                    populateModelCombo(forModel, (String) forProvider.getSelectedItem(), forInit);
                    populateModelCombo(judgeModel, (String) judgeProvider.getSelectedItem(), judgeInit);
                } catch (Exception e) {
                    ollamaStatusLabel.setText("Error: " + e.getMessage());
                    ollamaStatusLabel.setForeground(new Color(248, 113, 113));
                } finally {
                    fetchOllamaBtn.setEnabled(true);
                }
            }
        }.execute();
    }
}
