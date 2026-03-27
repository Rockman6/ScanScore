//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                   R a p i d O c r E n g i n e                                  //
//                                                                                                //
//------------------------------------------------------------------------------------------------//
package org.audiveris.omr.text.rapidocr;

import org.audiveris.omr.sheet.Sheet;
import org.audiveris.omr.text.FontInfo;
import org.audiveris.omr.text.OCR;
import org.audiveris.omr.text.TextChar;
import org.audiveris.omr.text.TextLine;
import org.audiveris.omr.text.TextWord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

/**
 * Class <code>RapidOcrEngine</code> is an OCR service built on RapidOCR (ONNX runtime).
 * <p>
 * Much lighter than PaddleOCR (~300MB vs 11GB RAM) while supporting Chinese + English + Latin.
 * Calls a Python script via ProcessBuilder.
 */
public class RapidOcrEngine
        implements OCR
{
    //~ Static fields/initializers -----------------------------------------------------------------

    private static final Logger logger = LoggerFactory.getLogger(RapidOcrEngine.class);

    /** Path candidates for the Python binary. */
    private static final String[] PYTHON_CANDIDATES = {
            System.getProperty("user.home") + "/Desktop/Music/audiveris/ocr-env/bin/python3.11",
            "/opt/homebrew/opt/python@3.11/bin/python3.11",
            "/usr/local/bin/python3.11",
            "/usr/bin/python3"
    };

    /** Path candidates for the OCR script. */
    private static final String[] SCRIPT_CANDIDATES = {
            System.getProperty("user.home") + "/Desktop/Music/audiveris/ocr-env/paddle_ocr_service.py",
            System.getProperty("user.home")
                    + "/Library/Application Support/HenryDev/scanscore/paddle_ocr_service.py"
    };

    /** Regex to parse JSON items from the script output. */
    private static final Pattern ITEM_PATTERN = Pattern.compile(
            "\\{[^}]*\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"[^}]*"
                    + "\"confidence\"\\s*:\\s*([0-9.]+)[^}]*"
                    + "\"x\"\\s*:\\s*([0-9]+)[^}]*"
                    + "\"y\"\\s*:\\s*([0-9]+)[^}]*"
                    + "\"box\"\\s*:\\s*\\[\\s*"
                    + "\\[\\s*([0-9]+)\\s*,\\s*([0-9]+)\\s*\\]\\s*,\\s*"
                    + "\\[\\s*([0-9]+)\\s*,\\s*([0-9]+)\\s*\\]\\s*,\\s*"
                    + "\\[\\s*([0-9]+)\\s*,\\s*([0-9]+)\\s*\\]\\s*,\\s*"
                    + "\\[\\s*([0-9]+)\\s*,\\s*([0-9]+)\\s*\\]"
                    + "[^}]*\\}");

    /** Cached python path. */
    private String pythonPath;

    /** Cached script path. */
    private String scriptPath;

    /** Whether we've already searched for paths. */
    private boolean pathsSearched;

    //~ Constructors -------------------------------------------------------------------------------

    private RapidOcrEngine ()
    {
    }

    //~ Methods ------------------------------------------------------------------------------------

    @Override
    public SortedSet<String> getSupportedLanguages ()
    {
        // RapidOCR ships with Chinese + English models by default
        TreeSet<String> langs = new TreeSet<>();
        langs.add("chi_sim");
        langs.add("eng");
        return langs;
    }

    @Override
    public double getMinConfidence ()
    {
        return 0.65;
    }

    @Override
    public String identify ()
    {
        if (isAvailable()) {
            return "RapidOCR (ONNX runtime)";
        }
        return NO_OCR;
    }

    @Override
    public boolean isAvailable ()
    {
        findPaths();
        return pythonPath != null && scriptPath != null;
    }

    @Override
    public List<TextLine> recognize (Sheet sheet,
                                     BufferedImage image,
                                     Point topLeft,
                                     String langSpec,
                                     LayoutMode layoutMode,
                                     String label)
    {
        if (!isAvailable()) {
            return null;
        }

        File tmpImage = null;
        try {
            // Write image to a temp PNG file
            tmpImage = File.createTempFile("rapidocr_", ".png");
            ImageIO.write(image, "png", tmpImage);

            // Determine language flag
            String lang = "ch"; // default: Chinese + English
            if (langSpec != null && langSpec.contains("eng") && !langSpec.contains("chi")) {
                lang = "en";
            }

            // Call the Python script
            ProcessBuilder pb = new ProcessBuilder(
                    pythonPath, scriptPath,
                    tmpImage.getAbsolutePath(),
                    "--lang", lang);
            pb.redirectErrorStream(false);

            Process proc = pb.start();

            // Read stdout
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

            // Drain stderr
            try (BufferedReader errReader = new BufferedReader(
                    new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                String errLine;
                while ((errLine = errReader.readLine()) != null) {
                    if (errLine.contains("Error") || errLine.contains("Exception")) {
                        logger.warn("RapidOCR stderr: {}", errLine);
                    }
                }
            }

            int exitCode = proc.waitFor();
            if (exitCode != 0) {
                logger.warn("RapidOCR exited with code {}", exitCode);
                return null;
            }

            if (json == null || json.isBlank()) {
                logger.info("RapidOCR returned empty output for {}", label);
                return null;
            }

            // Parse JSON and build TextLine/TextWord/TextChar hierarchy
            List<TextLine> lines = parseResults(sheet, json, topLeft);

            if (lines != null) {
                for (TextLine textLine : lines) {
                    logger.debug("RapidOCR raw {}", textLine);
                }
            }

            return lines;

        } catch (Exception ex) {
            logger.warn("RapidOCR call failed for {}: {}", label, ex.toString());
            return null;
        } finally {
            if (tmpImage != null) {
                tmpImage.delete();
            }
        }
    }

    @Override
    public boolean supports (String langSpec)
    {
        if (langSpec == null) {
            return false;
        }
        // RapidOCR supports Chinese and English out of the box
        return true;
    }

    //~ Private methods ----------------------------------------------------------------------------

    /**
     * Parse the JSON output from the RapidOCR script into TextLine objects.
     * Each detected text region becomes one TextLine with one TextWord.
     */
    private List<TextLine> parseResults (Sheet sheet, String json, Point topLeft)
    {
        List<TextLine> lines = new ArrayList<>();
        Matcher m = ITEM_PATTERN.matcher(json);

        while (m.find()) {
            String text = m.group(1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n");
            double confidence = Double.parseDouble(m.group(2));

            // Parse bounding box corners: [x1,y1], [x2,y2], [x3,y3], [x4,y4]
            int x1 = Integer.parseInt(m.group(5));
            int y1 = Integer.parseInt(m.group(6));
            int x2 = Integer.parseInt(m.group(7));
            int y2 = Integer.parseInt(m.group(8));
            int x3 = Integer.parseInt(m.group(9));
            int y3 = Integer.parseInt(m.group(10));
            int x4 = Integer.parseInt(m.group(11));
            int y4 = Integer.parseInt(m.group(12));

            // Compute bounding rectangle
            int minX = Math.min(Math.min(x1, x4), Math.min(x2, x3));
            int minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
            int maxX = Math.max(Math.max(x1, x4), Math.max(x2, x3));
            int maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));

            // Apply topLeft offset
            int offX = (topLeft != null) ? topLeft.x : 0;
            int offY = (topLeft != null) ? topLeft.y : 0;

            Rectangle bounds = new Rectangle(
                    minX + offX, minY + offY,
                    maxX - minX, maxY - minY);

            // Baseline: bottom-left to bottom-right of the box
            Line2D baseline = new Line2D.Double(
                    x4 + offX, y4 + offY,
                    x3 + offX, y3 + offY);

            // Estimate font size from box height (roughly 72% of box height = point size)
            int pointSize = Math.max(8, (int) Math.round(bounds.height * 0.72));

            // Default font info (non-bold, non-italic)
            FontInfo fontInfo = new FontInfo(false, false, pointSize, null);

            // Create a TextLine containing one word
            TextLine line = new TextLine(sheet);

            TextWord word = new TextWord(
                    sheet,
                    bounds,
                    text,
                    baseline,
                    confidence,
                    fontInfo,
                    line);

            // Add individual characters
            int len = text.length();
            if (len > 0) {
                double charWidth = (double) bounds.width / len;
                for (int i = 0; i < len; i++) {
                    Rectangle charBounds = new Rectangle(
                            bounds.x + (int) (i * charWidth),
                            bounds.y,
                            (int) charWidth,
                            bounds.height);
                    word.addChar(new TextChar(charBounds, text.substring(i, i + 1)));
                }
            }

            line.appendWord(word);
            lines.add(line);
        }

        return lines.isEmpty() ? null : lines;
    }

    /** Find the Python binary and OCR script. */
    private void findPaths ()
    {
        if (pathsSearched) {
            return;
        }
        pathsSearched = true;

        for (String candidate : PYTHON_CANDIDATES) {
            if (Files.isExecutable(Paths.get(candidate))) {
                pythonPath = candidate;
                break;
            }
        }

        for (String candidate : SCRIPT_CANDIDATES) {
            if (Files.isRegularFile(Paths.get(candidate))) {
                scriptPath = candidate;
                break;
            }
        }

        if (pythonPath != null && scriptPath != null) {
            logger.info("RapidOCR available: python={} script={}", pythonPath, scriptPath);
        } else {
            logger.info("RapidOCR not available: python={} script={}", pythonPath, scriptPath);
        }
    }

    //~ Static methods -----------------------------------------------------------------------------

    public static RapidOcrEngine getInstance ()
    {
        return LazySingleton.INSTANCE;
    }

    //~ Inner Classes ------------------------------------------------------------------------------

    private static class LazySingleton
    {
        static final RapidOcrEngine INSTANCE = new RapidOcrEngine();
    }
}
