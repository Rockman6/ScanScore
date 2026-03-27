package org.audiveris.omr.score;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calls the RapidOCR Python script to read text from the left margin of score images.
 * Used to identify instrument names when the main OCR pipeline returns empty results.
 */
public class MarginOcrService
{
    private static final Logger logger = LoggerFactory.getLogger(MarginOcrService.class);

    private static final String[] PYTHON_CANDIDATES = {
            System.getProperty("user.home") + "/Desktop/Music/audiveris/ocr-env/bin/python3.11",
            "/opt/homebrew/opt/python@3.11/bin/python3.11",
            "/usr/local/bin/python3.11",
            "/usr/bin/python3"
    };

    private static final String[] SCRIPT_CANDIDATES = {
            System.getProperty("user.home") + "/Desktop/Music/audiveris/ocr-env/paddle_ocr_service.py",
            System.getProperty("user.home")
                    + "/Library/Application Support/HenryDev/scanscore/paddle_ocr_service.py"
    };

    public record TextResult(String text, double confidence, int x, int y) {}

    public static List<TextResult> ocrLeftMargin (Path imagePath, int leftMarginMaxX)
    {
        String pythonPath = findExecutable(PYTHON_CANDIDATES);
        String scriptPath = findFile(SCRIPT_CANDIDATES);
        if (pythonPath == null || scriptPath == null) {
            logger.info("MarginOCR not available — python={} script={}", pythonPath, scriptPath);
            return null;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    pythonPath, scriptPath,
                    imagePath.toString(),
                    "--lang", "ch",
                    "--left-margin-only", String.valueOf(leftMarginMaxX));
            pb.redirectErrorStream(false);

            Process proc = pb.start();

            String json;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                json = sb.toString();
            }

            try (BufferedReader errReader = new BufferedReader(
                    new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                String errLine;
                while ((errLine = errReader.readLine()) != null) {
                    if (errLine.contains("Error") || errLine.contains("Exception")) {
                        logger.warn("MarginOCR stderr: {}", errLine);
                    }
                }
            }

            int exitCode = proc.waitFor();
            if (exitCode != 0) {
                logger.warn("MarginOCR exited with code {}", exitCode);
                return null;
            }

            if (json == null || json.isBlank()) {
                return null;
            }

            return parseJson(json);

        } catch (Exception ex) {
            logger.warn("MarginOCR call failed: {}", ex.toString());
            return null;
        }
    }

    private static List<TextResult> parseJson (String json)
    {
        List<TextResult> results = new ArrayList<>();
        Pattern itemPattern = Pattern.compile(
                "\\{[^}]*\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"[^}]*"
                        + "\"confidence\"\\s*:\\s*([0-9.]+)[^}]*"
                        + "\"x\"\\s*:\\s*([0-9]+)[^}]*"
                        + "\"y\"\\s*:\\s*([0-9]+)[^}]*\\}");

        Matcher m = itemPattern.matcher(json);
        while (m.find()) {
            String text = m.group(1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n");
            double confidence = Double.parseDouble(m.group(2));
            int x = Integer.parseInt(m.group(3));
            int y = Integer.parseInt(m.group(4));
            results.add(new TextResult(text, confidence, x, y));
        }

        return results;
    }

    private static String findExecutable (String[] candidates)
    {
        for (String c : candidates) {
            if (Files.isExecutable(Paths.get(c))) return c;
        }
        return null;
    }

    private static String findFile (String[] candidates)
    {
        for (String c : candidates) {
            if (Files.isRegularFile(Paths.get(c))) return c;
        }
        return null;
    }
}
