//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                  S c o r e R e d u c t i o n                                   //
//                                                                                                //
//------------------------------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">
//
//  Copyright © Audiveris 2026. All rights reserved.
//
//  This program is free software: you can redistribute it and/or modify it under the terms of the
//  GNU Affero General Public License as published by the Free Software Foundation, either version
//  3 of the License, or (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
//  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//  See the GNU Affero General Public License for more details.
//
//  You should have received a copy of the GNU Affero General Public License along with this
//  program.  If not, see <http://www.gnu.org/licenses/>.
//------------------------------------------------------------------------------------------------//
// </editor-fold>
package org.audiveris.omr.score;

import org.audiveris.omr.score.PartCollation.Record;
import org.audiveris.omr.sheet.Part;
import org.audiveris.omr.sheet.SheetStub;
import org.audiveris.omr.sheet.rhythm.Measure;
import org.audiveris.omr.sig.inter.ClefInter;
import org.audiveris.omr.sig.inter.ClefInter.ClefKind;
import org.audiveris.omr.sig.inter.KeyInter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class <code>ScoreReduction</code> reduces the logical parts for a score.
 * <p>
 * <b>Features not yet implemented:</b>
 * <ul>
 * <li>Connection of slurs between pages</li>
 * <li>In part-list, handling of part-group beside score-part</li>
 * </ul>
 *
 * @author Hervé Bitteur
 */
public class ScoreReduction
{
    //~ Static fields/initializers -----------------------------------------------------------------

    private static final Logger logger = LoggerFactory.getLogger(ScoreReduction.class);

    //~ Instance fields ----------------------------------------------------------------------------

    /** Related score. */
    private final Score score;

    //~ Constructors -------------------------------------------------------------------------------

    /**
     * Creates a new ScoreReduction object.
     *
     * @param score the score to process
     */
    public ScoreReduction (Score score)
    {
        this.score = score;
    }

    //~ Methods ------------------------------------------------------------------------------------

    //--------------//
    // applyRecords //
    //--------------//
    /**
     * Apply the collation results.
     * <ol>
     * <li>Within each record, all the affiliated PartRef's are assigned the LogicalPart id.
     * <li>If score logicals are not locked, they get replaced by records logicals.
     * </ol>
     *
     * @param records results from part collation
     * @return true if PartRef's have really been modified
     */
    private boolean applyRecords (List<Record> records)
    {
        // Propagate to affiliates
        boolean anyModified = false;
        for (Record record : records) {
            final int logId = record.logical.getId();

            for (PartRef partRef : record.partRefs) {
                if (partRef.setLogicalId(logId)) {
                    anyModified = true;

                    // Update Part immediately if containing sheet is loaded
                    // If not, it will get updated the next time sheet is loaded
                    final SheetStub stub = partRef.getSystem().getPage().getStub();
                    if (stub.hasSheet()) {
                        try {
                            final Part part = partRef.getRealPart();
                            part.setId(logId);
                        } catch (Exception ex) {
                            logger.warn(
                                    "Error in applyRecords {}\n  {} system#{} {}\n  {}",
                                    ex.getMessage(),
                                    stub,
                                    partRef.getSystem().getId(),
                                    partRef,
                                    partRef.getSystem(),
                                    ex);
                        }
                    }
                }
            }
        }

        if (!score.isLogicalsLocked()) {
            // (Over-)write score logicals
            final List<LogicalPart> newLogicals = new ArrayList<>();
            for (Record record : records) {
                newLogicals.add(record.logical);
            }

            score.setLogicalParts(newLogicals);
        }

        return anyModified;
    }

    //----------------//
    // buildSequences //
    //----------------//
    /**
     * Build the system-sequences of parts, collected system per system, page per page.
     *
     * @param pageRefs the sequence of PageRef's
     * @return the sequences of PartRef's
     */
    private List<List<PartRef>> buildSequences (List<PageRef> pageRefs)
    {
        final List<List<PartRef>> sequences = new ArrayList<>();

        for (PageRef pageRef : pageRefs) {
            pageRef.getStub().checkSystems(); // For old OMRs

            for (SystemRef system : pageRef.getSystems()) {
                sequences.add(system.getParts());
            }
        }

        return sequences;
    }

    //--------//
    // reduce //
    //--------//
    /**
     * Build the score LogicalPart instances by connecting the pages systems parts.
     *
     * @param stubs valid selected stubs
     * @return the count of modifications done
     */
    public int reduce (List<SheetStub> stubs)
    {
        final List<List<PartRef>> sequences = buildSequences(score.getPageRefs(stubs));
        final PartCollation collation = new PartCollation(
                sequences,
                score.isLogicalsLocked() ? score.getLogicalParts() : null);
        final List<Record> records = collation.getRecords();

        collation.dumpRecords(); // COLLATE-DIAG: always dump to help diagnose part merging

        // Store the list of LogicalPart instances into score
        boolean modified = applyRecords(records);

        if (!score.isLogicalsLocked()) {
            // Step 1: Try LLM identification first (most accurate, handles any language)
            if (inferFromLLM(records)) {
                modified = true;
            }
            // Step 2: Clef-based fallback for parts LLM didn't identify
            if (inferNamesFromClefs(records)) {
                modified = true;
            }
            // Step 3: Key-signature + position based inference for remaining parts
            if (inferFromKeySigAndLayout(records)) {
                modified = true;
            }
        }

        return modified ? 1 : 0;
    }

    //----------------//
    // inferFromLLM   //
    //----------------//
    /**
     * Use a local LLM (Ollama) to identify instruments from the raw OCR text
     * detected near each staff. Handles any language, abbreviations, etc.
     * Falls back gracefully if Ollama is not running.
     *
     * @param records collation records
     * @return true if any logical was updated
     */
    private boolean inferFromLLM (List<Record> records)
    {
        final InstrumentDictionary dict = InstrumentDictionary.getInstance();

        // Collect raw part name texts, clef types, and key signatures
        List<String> rawTexts = new ArrayList<>();
        List<String> clefTypes = new ArrayList<>();
        List<Integer> keySigs = new ArrayList<>();

        for (Record record : records) {
            String name = record.logical.getName();
            rawTexts.add(name != null ? name : "");

            // Get clef and key sig from first loaded PartRef
            String clef = "unknown";
            Integer ks = null;
            for (PartRef partRef : record.partRefs) {
                final SheetStub stub = partRef.getSystem().getPage().getStub();
                if (!stub.hasSheet()) continue;
                try {
                    final Part part = partRef.getRealPart();
                    final Measure firstMeasure = part.getFirstMeasure();
                    if (firstMeasure != null) {
                        final ClefInter c = firstMeasure.getFirstMeasureClef(0);
                        if (c != null) clef = c.getKind().toString();
                        final KeyInter k = firstMeasure.getKey(0);
                        if (k != null) ks = k.getFifths();
                    }
                } catch (Exception ex) { /* ignore */ }
                break;
            }
            clefTypes.add(clef);
            keySigs.add(ks);
        }

        // Check if there's anything to identify (skip if all names already recognized)
        boolean anyUnknown = false;
        boolean allEmpty = true;
        for (String text : rawTexts) {
            if (text.isEmpty() || dict.normalize(text) == null) {
                anyUnknown = true;
            }
            if (!text.isEmpty()) {
                allEmpty = false;
            }
        }
        if (!anyUnknown) {
            return false; // all parts already identified
        }

        // If Tesseract failed (all names empty), try PaddleOCR on the first page image
        if (allEmpty) {
            rawTexts = tryPaddleOCR(records, rawTexts);
            // Recheck — maybe PaddleOCR filled some names
            allEmpty = rawTexts.stream().allMatch(String::isEmpty);
        }

        // Get score filename for context
        String scoreFileName = null;
        try {
            scoreFileName = score.getBook().getRadix();
        } catch (Exception ex) { /* ignore */ }

        // Call LLM with full context (PaddleOCR text + filename + clef + keySig)
        List<InstrumentLLM.InstrumentInfo> results = InstrumentLLM.identify(
                rawTexts, scoreFileName, clefTypes, keySigs);
        if (results == null) {
            return false; // LLM unavailable
        }

        boolean anyModified = false;
        for (int i = 0; i < records.size() && i < results.size(); i++) {
            LogicalPart logical = records.get(i).logical;
            InstrumentLLM.InstrumentInfo info = results.get(i);

            // Skip if LLM returned nothing or part already has a recognized name
            if (info.canonicalName() == null || info.canonicalName().isBlank()) {
                continue;
            }
            if (dict.normalize(logical.getName()) != null) {
                continue; // already identified
            }

            // Build display name with transposition key
            String displayName = info.canonicalName();
            if (info.transpositionKey() != null) {
                displayName += " (" + info.transpositionKey() + ")";
            }

            logger.info("LLM identified part {}: '{}' → '{}' (key={})",
                    i + 1, rawTexts.get(i), displayName, info.transpositionKey());

            logical.setName(displayName);

            // Set MIDI program from our dictionary
            String canon = dict.normalize(displayName);
            if (canon == null) {
                canon = dict.normalize(info.canonicalName());
            }
            if (canon != null && logical.getMidiProgram() == null) {
                logical.setMidiProgram(dict.getMidiProgram(canon));
            }

            anyModified = true;
        }

        return anyModified;
    }

    //----------------//
    // tryPaddleOCR   //
    //----------------//
    /**
     * When Tesseract failed (all part names empty), try PaddleOCR on the first page image
     * to read instrument names from the left margin of the score.
     *
     * @param records collation records (to count parts and get staff y positions)
     * @param rawTexts current (empty) text list, same size as records
     * @return updated text list with PaddleOCR results, or original if PaddleOCR unavailable
     */
    private List<String> tryPaddleOCR (List<Record> records, List<String> rawTexts)
    {
        // Find the first page's binary image
        Path imagePath = null;
        int imageWidth = 0;
        for (Record record : records) {
            for (PartRef partRef : record.partRefs) {
                final SheetStub stub = partRef.getSystem().getPage().getStub();
                if (!stub.hasSheet()) continue;
                try {
                    // Get the binary image path from the .omr stored data
                    Path bookPath = score.getBook().getBookPath();
                    if (bookPath != null) {
                        // The binary image is at sheet#N/BINARY.png inside the .omr zip
                        // But we need the actual file — extract it or use the input image
                        String inputPath = score.getBook().getInputPath().toString();
                        // For PDF input, render the first page as an image
                        // Use the stored BINARY.png if available
                        Path omrFolder = bookPath.getParent();
                        // Try to find the image in the temp/cache directory
                        // Try input path directly
                        imagePath = score.getBook().getInputPath();
                    }
                    if (imagePath == null) {
                        // Fallback: use the original input file
                        imagePath = score.getBook().getInputPath();
                    }
                    imageWidth = stub.getSheet().getWidth();
                } catch (Exception ex) {
                    logger.debug("Could not get image path: {}", ex.toString());
                }
                break;
            }
            if (imagePath != null) break;
        }

        if (imagePath == null) {
            logger.info("PaddleOCR: no image path found for first page");
            return rawTexts;
        }

        // OCR the left margin (roughly 1/5 of the image width)
        int leftMarginX = Math.max(imageWidth / 5, 400);
        logger.info("PaddleOCR: scanning left margin (x<{}) of {}", leftMarginX, imagePath);

        List<MarginOcrService.TextResult> ocrResults =
                MarginOcrService.ocrLeftMargin(imagePath, leftMarginX);
        if (ocrResults == null || ocrResults.isEmpty()) {
            logger.info("PaddleOCR: no text detected in left margin");
            return rawTexts;
        }

        logger.info("PaddleOCR: detected {} text regions in left margin", ocrResults.size());
        for (MarginOcrService.TextResult tr : ocrResults) {
            logger.info("  PaddleOCR: y={} conf={} \"{}\"", tr.y(), tr.confidence(), tr.text());
        }

        // Now match OCR text to parts by y-position.
        // Each part's staff has a known y range; find OCR texts nearest to each part.
        int n = records.size();
        int[] partYs = new int[n]; // approximate y center of each part's first staff
        for (int i = 0; i < n; i++) {
            partYs[i] = -1;
            for (PartRef partRef : records.get(i).partRefs) {
                final SheetStub stub = partRef.getSystem().getPage().getStub();
                if (!stub.hasSheet()) continue;
                try {
                    Part part = partRef.getRealPart();
                    // Get vertical center of the part's staves
                    int top = part.getFirstStaff().getFirstLine().yAt(0);
                    int bot = part.getLastStaff().getLastLine().yAt(0);
                    partYs[i] = (top + bot) / 2;
                } catch (Exception ex) { /* ignore */ }
                break;
            }
        }

        // For each part, find the closest OCR text(s)
        List<String> updatedTexts = new ArrayList<>(rawTexts);
        for (int i = 0; i < n; i++) {
            if (partYs[i] < 0) continue;

            // Find all OCR texts within a reasonable y-range of this part
            int staffY = partYs[i];
            int yTolerance = (i + 1 < n && partYs[i + 1] > 0)
                    ? (partYs[i + 1] - partYs[i]) / 2
                    : 80;

            StringBuilder partText = new StringBuilder();
            for (MarginOcrService.TextResult tr : ocrResults) {
                if (Math.abs(tr.y() - staffY) < yTolerance && tr.confidence() > 0.5) {
                    if (!partText.isEmpty()) partText.append(" ");
                    partText.append(tr.text());
                }
            }

            if (!partText.isEmpty()) {
                updatedTexts.set(i, partText.toString());
                logger.info("PaddleOCR: part {} → \"{}\"", i + 1, partText);
            }
        }

        return updatedTexts;
    }

    //----------------------//
    // inferNamesFromClefs  //
    //----------------------//
    /**
     * For logical parts that have no recognized instrument name, infer the name from the
     * clef of the first staff of the first loaded PartRef.
     * <ul>
     * <li>Alto clef → Viola</li>
     * <li>Tenor clef → Cello</li>
     * <li>Baritone clef → Trombone</li>
     * </ul>
     *
     * @param records collation records
     * @return true if any logical was updated
     */
    private boolean inferNamesFromClefs (List<Record> records)
    {
        final InstrumentDictionary dict = InstrumentDictionary.getInstance();
        boolean anyModified = false;

        for (Record record : records) {
            final LogicalPart logical = record.logical;

            // Skip if name is already recognized
            if (dict.normalize(logical.getName()) != null) {
                continue;
            }

            // Find the first PartRef in a loaded sheet
            for (PartRef partRef : record.partRefs) {
                final SheetStub stub = partRef.getSystem().getPage().getStub();
                if (!stub.hasSheet()) {
                    continue;
                }

                try {
                    final Part part = partRef.getRealPart();
                    final Measure firstMeasure = part.getFirstMeasure();
                    if (firstMeasure == null) {
                        continue;
                    }

                    final ClefInter clef = firstMeasure.getFirstMeasureClef(0);
                    if (clef == null) {
                        continue;
                    }

                    final ClefKind kind = clef.getKind();
                    final String inferredName = switch (kind) {
                        case ALTO -> "Viola";
                        case TENOR -> "Cello";
                        case BARITONE -> "Trombone";
                        default -> null;
                    };

                    if (inferredName != null) {
                        logger.info("Inferred instrument '{}' from {} clef for {}",
                                inferredName, kind, logical);
                        logical.setName(inferredName);
                        if (logical.getMidiProgram() == null) {
                            logical.setMidiProgram(dict.getMidiProgram(inferredName));
                        }
                        anyModified = true;
                    }
                } catch (Exception ex) {
                    logger.warn("Error inferring name from clef for {}: {}", partRef, ex.toString());
                }

                break; // Only need the first loaded PartRef
            }
        }

        return anyModified;
    }

    //---------------------------//
    // inferFromKeySigAndLayout  //
    //---------------------------//
    /**
     * Infer instrument names from key signatures, clef types, and standard orchestral layout.
     * <p>
     * The key insight: transposing instruments have different key signatures from concert-pitch
     * instruments in the same score. By comparing each part's key sig to the majority (concert)
     * key sig, we can determine the transposition:
     * <ul>
     * <li>+2 sharps (or -2 flats) relative to concert → Bb instrument (clarinet, trumpet, tenor sax)</li>
     * <li>+1 sharp (or -1 flat) relative to concert → F instrument (horn, English horn)</li>
     * <li>+3 sharps (or -3 flats) relative to concert → Eb instrument (alto sax, Eb clarinet)</li>
     * </ul>
     * Combined with clef type and position in the score, we can identify the specific instrument.
     *
     * @param records collation records (one per logical part)
     * @return true if any logical was updated
     */
    private boolean inferFromKeySigAndLayout (List<Record> records)
    {
        final InstrumentDictionary dict = InstrumentDictionary.getInstance();
        boolean anyModified = false;

        // Step 1: Collect key sig and clef info for each record
        final int n = records.size();
        int[] keySigs = new int[n];       // fifths value from first measure
        boolean[] hasKeySig = new boolean[n];
        ClefKind[] clefs = new ClefKind[n];
        boolean[] needsName = new boolean[n];

        for (int i = 0; i < n; i++) {
            Record record = records.get(i);
            LogicalPart logical = record.logical;
            needsName[i] = (dict.normalize(logical.getName()) == null);

            for (PartRef partRef : record.partRefs) {
                SheetStub stub = partRef.getSystem().getPage().getStub();
                if (!stub.hasSheet()) {
                    continue;
                }
                try {
                    Part part = partRef.getRealPart();
                    Measure m = part.getFirstMeasure();
                    if (m == null) continue;

                    KeyInter key = m.getKey(0);
                    if (key != null) {
                        keySigs[i] = key.getFifths();
                        hasKeySig[i] = true;
                    }
                    ClefInter clef = m.getFirstMeasureClef(0);
                    if (clef != null) {
                        clefs[i] = clef.getKind();
                    }
                } catch (Exception ex) {
                    // skip
                }
                break; // first loaded sheet is enough
            }
        }

        // Step 2: Find the "concert" key sig — the most common key sig among parts.
        // Parts with no detected key sig are treated as 0 sharps/flats (C major / no key sig printed).
        //
        // IMPORTANT: If only a tiny number of parts have detected key sigs (e.g. 1-2 out of 16),
        // those are likely OCR noise / outliers — ignore them entirely for transposition inference.
        // Only trust key sig differences when enough parts have them to form a reliable consensus.
        Map<Integer, Integer> keySigFreq = new HashMap<>();
        int detectedCount = 0;
        for (int i = 0; i < n; i++) {
            int ks = hasKeySig[i] ? keySigs[i] : 0;
            keySigFreq.merge(ks, 1, Integer::sum);
            if (hasKeySig[i]) detectedCount++;
        }
        int concertKeySig = keySigFreq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .get().getKey();

        // If only 1-2 parts have detected key sigs out of many, treat as outliers
        boolean keySigReliable = (detectedCount >= 3) || (detectedCount > 0 && n <= 4);
        if (!keySigReliable) {
            logger.info("Instrument inference: only {}/{} parts have key sigs — treating as outliers, ignoring key sig differences",
                    detectedCount, n);
        }

        logger.info("Instrument inference: concertKeySig={} fifths, keySigFreq={}, keySigReliable={}",
                concertKeySig, keySigFreq, keySigReliable);

        // Step 3: For each part, compute key sig delta and infer instrument
        for (int i = 0; i < n; i++) {
            Record record = records.get(i);
            LogicalPart logical = record.logical;

            int partKeySig = hasKeySig[i] ? keySigs[i] : 0;
            // Only use key sig delta when detection is reliable (enough parts have key sigs)
            int delta = keySigReliable ? (partKeySig - concertKeySig) : 0;
            ClefKind clef = clefs[i];
            boolean isTreble = (clef == ClefKind.TREBLE);
            boolean isBass = (clef == ClefKind.BASS);

            // Determine position zone (rough: top third = woodwinds, middle = brass, bottom = strings)
            double posRatio = (double) i / n;
            boolean upperZone = posRatio < 0.4;
            boolean middleZone = posRatio >= 0.4 && posRatio < 0.7;

            String inferredName = null;

            if (delta == 2 && isTreble) {
                // +2 sharps → Bb transposition
                if (upperZone) {
                    inferredName = "Clarinetti (Bb)";
                } else if (middleZone) {
                    inferredName = "Trombe (Bb)";
                } else {
                    inferredName = "Clarinetti (Bb)"; // default Bb
                }
            } else if (delta == -2 && isTreble) {
                // -2 sharps = +2 flats → Bb transposition (in flat key scores)
                if (upperZone) {
                    inferredName = "Clarinetti (Bb)";
                } else {
                    inferredName = "Trombe (Bb)";
                }
            } else if (delta == 1 && isTreble) {
                // +1 sharp → F transposition (Horn in F)
                inferredName = "Corni (F)";
            } else if (delta == -1 && isTreble) {
                // -1 sharp → F transposition (in flat key scores)
                inferredName = "Corni (F)";
            } else if (delta == 3 && isTreble) {
                // +3 sharps → Eb transposition (alto sax, Eb clarinet)
                inferredName = "Eb Instrument";
            } else if (delta == 0 && needsName[i]) {
                // Concert pitch — infer from clef + position using standard orchestral order:
                // Woodwinds (Piccolo, Flute, Oboe, [Clarinet=transposing], Bassoon)
                // Brass (Horn [often no key sig in modern notation], Trumpet, Trombone, Tuba)
                // Percussion (Timpani)
                // Strings (Violin I, Violin II, Viola, Cello, Contrabass)

                if (isTreble && upperZone && i <= 2) {
                    // Upper treble = woodwinds (first 3 treble parts)
                    if (i == 0) {
                        inferredName = "Piccolo";
                    } else if (i == 1) {
                        inferredName = "Flauti";
                    } else if (i == 2) {
                        inferredName = "Oboi";
                    }
                } else if (isBass && upperZone) {
                    inferredName = "Fagotti";
                } else if (isTreble && (middleZone || (upperZone && i >= 3))) {
                    // Treble clef after the woodwind section: likely Horn (F) or Trumpet
                    // In standard orchestral layout, horns come right after woodwinds
                    // (position ~5-7 in a 16-part score). Horns in F often have no key sig
                    // in modern notation, so delta=0 even though they're transposing.
                    // If this part is between the last woodwind and the trumpets, it's Horn.
                    boolean likelyHorn = (i >= 5 && i <= 7);
                    if (likelyHorn) {
                        inferredName = "Corni (F)";
                    }
                } else if (isBass && middleZone) {
                    inferredName = "Tromboni";
                }
                // Percussion zone: between brass and strings, single treble part
                // Timpani is typically the last part before strings
                if (inferredName == null && middleZone && isTreble) {
                    // Check if surrounded by bass-clef parts (brass) and treble-clef parts (strings)
                    boolean prevIsBrass = (i > 0 && clefs[i - 1] == ClefKind.BASS);
                    if (prevIsBrass || posRatio > 0.6) {
                        inferredName = "Timpani";
                    }
                }
                // Strings zone (lower third)
                if (inferredName == null && !middleZone && !upperZone) {
                    if (isTreble) {
                        inferredName = "Violini";
                    } else if (isBass) {
                        // Distinguish Violoncelli from Contrabbassi
                        // Cello typically comes before Contrabass
                        boolean nextIsBass = (i + 1 < n && clefs[i + 1] == ClefKind.BASS);
                        if (nextIsBass) {
                            inferredName = "Violoncelli";
                        } else {
                            inferredName = "Contrabbassi";
                        }
                    }
                }
            }

            if (inferredName != null && needsName[i]) {
                logger.info("Inferred instrument '{}' from keySig delta={} clef={} position={}/{} for {}",
                        inferredName, delta, clef, i, n, logical);
                logical.setName(inferredName);
                if (logical.getMidiProgram() == null) {
                    // Normalize to canonical English name for MIDI lookup
                    String canon = dict.normalize(inferredName);
                    if (canon != null) {
                        logical.setMidiProgram(dict.getMidiProgram(canon));
                    }
                }
                anyModified = true;
            } else if (delta != 0 && !needsName[i]) {
                // Part already has a name but verify transposition is set
                logger.info("Part '{}' has keySig delta={} from concert — transposition likely {}",
                        logical.getName(), delta,
                        delta == 2 ? "Bb" : delta == 1 ? "F" : delta == 3 ? "Eb" : "unknown");
            }
        }

        return anyModified;
    }
}
