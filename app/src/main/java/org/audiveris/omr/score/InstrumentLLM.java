package org.audiveris.omr.score;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uses a local LLM (Ollama) to identify instrument names from OCR text.
 * Runs entirely offline — no API keys or internet required.
 */
public class InstrumentLLM
{
    private static final Logger logger = LoggerFactory.getLogger(InstrumentLLM.class);

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL = "qwen2.5:7b";
    private static final int TIMEOUT_MS = 30000;

    /** Result for one identified instrument. */
    public record InstrumentInfo(
            String canonicalName,   // e.g. "Clarinet", "French Horn", "Piccolo"
            String transpositionKey // e.g. "Bb", "F", "Eb", or null for concert pitch
    ) {}

    /**
     * Identify instruments from a list of raw OCR part name texts.
     *
     * @param partTexts list of raw text strings detected near each staff
     * @return list of InstrumentInfo (same size as input), or null if LLM unavailable
     */
    public static List<InstrumentInfo> identify (List<String> partTexts)
    {
        return identify(partTexts, null, null, null);
    }

    /**
     * Identify instruments with additional context.
     *
     * @param partTexts raw OCR text for each part
     * @param scoreFileName the filename of the score (helps identify the piece)
     * @param clefTypes clef type for each part (e.g. "TREBLE", "BASS", "ALTO")
     * @param keySigs key signature (fifths) for each part, or null
     * @return list of InstrumentInfo, or null if LLM unavailable
     */
    public static List<InstrumentInfo> identify (List<String> partTexts,
                                                  String scoreFileName,
                                                  List<String> clefTypes,
                                                  List<Integer> keySigs)
    {
        if (partTexts == null || partTexts.isEmpty()) {
            return null;
        }

        // Build a simple, focused prompt the 7B model can handle reliably
        StringBuilder prompt = new StringBuilder();
        prompt.append("What instruments are in this orchestral score?\n\n");

        if (scoreFileName != null && !scoreFileName.isBlank()) {
            prompt.append("Piece: ").append(scoreFileName).append("\n");
        }

        prompt.append(partTexts.size()).append(" staves, from top to bottom:\n");
        for (int i = 0; i < partTexts.size(); i++) {
            String text = partTexts.get(i);
            String clef = (clefTypes != null && i < clefTypes.size()) ? clefTypes.get(i) : "?";
            Integer ks = (keySigs != null && i < keySigs.size()) ? keySigs.get(i) : null;

            prompt.append(i + 1).append(") ");
            if (text != null && !text.isBlank()) {
                prompt.append("\"").append(text).append("\" ");
            }
            prompt.append(clef);
            if (ks != null) prompt.append(" keysig=").append(ks);
            prompt.append("\n");
        }

        prompt.append("\nFor each staff, write: instrument name | transposition key (or NONE)\n");
        prompt.append("Example: Clarinet | Bb\nExample: Violin | NONE\n");
        prompt.append("Write EXACTLY ").append(partTexts.size()).append(" lines, nothing else.");

        try {
            String response = callOllama(prompt.toString());
            if (response == null) {
                return null;
            }
            return parseResponse(response, partTexts.size());
        } catch (Exception ex) {
            logger.warn("LLM instrument identification failed: {}", ex.toString());
            return null;
        }
    }

    /**
     * Call local Ollama API.
     */
    private static String callOllama (String prompt)
    {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                    URI.create(OLLAMA_URL).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

            // Build JSON manually (no dependency needed)
            String jsonBody = "{\"model\":\"" + MODEL + "\","
                    + "\"prompt\":" + jsonEscape(prompt) + ","
                    + "\"stream\":false}";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                logger.warn("Ollama returned status {}", status);
                return null;
            }

            try (InputStream is = conn.getInputStream()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                // Extract "response" field from JSON
                Matcher m = Pattern.compile("\"response\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                        .matcher(body);
                if (m.find()) {
                    return m.group(1)
                            .replace("\\n", "\n")
                            .replace("\\t", "\t")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\");
                }
                return null;
            }
        } catch (java.net.ConnectException ex) {
            logger.info("Ollama not running at {} — skipping LLM instrument identification", OLLAMA_URL);
            return null;
        } catch (Exception ex) {
            logger.warn("Ollama call failed: {}", ex.toString());
            return null;
        }
    }

    /**
     * Parse the LLM response into InstrumentInfo list.
     * Expected format: one line per part, "INSTRUMENT_NAME | KEY" or "INSTRUMENT_NAME | NONE"
     */
    private static List<InstrumentInfo> parseResponse (String response, int expectedCount)
    {
        List<InstrumentInfo> results = new ArrayList<>();
        String[] lines = response.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            // Remove leading number like "1. " or "1) "
            line = line.replaceFirst("^\\d+[.)\\s]+", "");

            String[] parts = line.split("\\|");
            if (parts.length >= 2) {
                String name = parts[0].trim();
                String key = parts[1].trim();
                if ("NONE".equalsIgnoreCase(key) || "none".equals(key)
                        || "null".equalsIgnoreCase(key) || key.equals("-")) {
                    key = null;
                }
                results.add(new InstrumentInfo(name, key));
            } else if (!line.isEmpty()) {
                // Just a name, no key
                results.add(new InstrumentInfo(line.trim(), null));
            }

            if (results.size() >= expectedCount) break;
        }

        // Pad if LLM returned fewer lines
        while (results.size() < expectedCount) {
            results.add(new InstrumentInfo(null, null));
        }

        return results;
    }

    /** Escape a string for JSON. */
    private static String jsonEscape (String s)
    {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
