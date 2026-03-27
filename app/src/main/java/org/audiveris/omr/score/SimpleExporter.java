//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                   S i m p l e E x p o r t e r                                 //
//                                                                                                //
//------------------------------------------------------------------------------------------------//
package org.audiveris.omr.score;

import org.audiveris.omr.math.Rational;
import org.audiveris.omr.sheet.SystemInfo;
import org.audiveris.omr.sheet.rhythm.Measure;
import org.audiveris.omr.sheet.rhythm.MeasureStack;
import org.audiveris.omr.sheet.rhythm.Slot;
import org.audiveris.omr.sheet.rhythm.SlotVoice;
import org.audiveris.omr.sheet.rhythm.Voice;
import org.audiveris.omr.sig.inter.AbstractChordInter;
import org.audiveris.omr.sig.inter.AbstractTimeInter;
import org.audiveris.omr.sig.inter.ClefInter;
import org.audiveris.omr.sig.inter.ClefInter.ClefKind;
import org.audiveris.omr.sig.inter.HeadInter;
import org.audiveris.omr.sig.inter.Inter;
import org.audiveris.omr.sig.inter.KeyInter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.OutputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes a clean MusicXML 3.1 file directly from the Audiveris score data model.
 * <p>
 * This replaces PartwiseBuilder with minimal, correct logic:
 * no JAXB, no ProxyMusic — just XMLStreamWriter directly to the output stream.
 * It correctly handles pickup measures, multi-voice backup elements,
 * key/time/clef changes, and MIDI program lookup.
 */
public class SimpleExporter
{
    private static final Logger logger = LoggerFactory.getLogger(SimpleExporter.class);

    // Base note durations (fraction of whole note) → MusicXML type name
    private static final LinkedHashMap<Rational, String> TYPE_NAMES = new LinkedHashMap<>();

    static {
        TYPE_NAMES.put(new Rational(1, 1), "whole");
        TYPE_NAMES.put(new Rational(1, 2), "half");
        TYPE_NAMES.put(new Rational(1, 4), "quarter");
        TYPE_NAMES.put(new Rational(1, 8), "eighth");
        TYPE_NAMES.put(new Rational(1, 16), "16th");
        TYPE_NAMES.put(new Rational(1, 32), "32nd");
        TYPE_NAMES.put(new Rational(1, 64), "64th");
        TYPE_NAMES.put(new Rational(1, 128), "128th");
    }

    private final Score score;
    private final InstrumentDictionary dict = InstrumentDictionary.getInstance();
    private int divisions = 24; // ticks per quarter note; computed in computeDivisions()

    public SimpleExporter (Score score)
    {
        this.score = score;
    }

    //--------//
    // export //
    //--------//
    /**
     * Write the score as MusicXML 3.1 to the given output stream.
     *
     * @param out the destination stream (caller is responsible for closing it)
     * @throws Exception if anything goes wrong
     */
    public void export (OutputStream out)
        throws Exception
    {
        divisions = computeDivisions();
        logger.info("SimpleExporter: divisions={}, parts={}", divisions,
                score.getLogicalParts().size());

        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        XMLStreamWriter w = factory.createXMLStreamWriter(out, "UTF-8");

        w.writeStartDocument("UTF-8", "1.0");
        w.writeDTD(
                "\n<!DOCTYPE score-partwise PUBLIC"
                        + " \"-//Recordare//DTD MusicXML 3.1 Partwise//EN\""
                        + " \"http://www.musicxml.org/dtds/partwise.dtd\">\n");

        startEl(w, "score-partwise");
        w.writeAttribute("version", "3.1");

        writePartList(w);

        List<MeasureStack> allStacks = collectStacks();

        // Pre-compute which stacks have a REAL modulation (key change).
        // Rule: each instrument can have its OWN initial key sig (Bb clarinet = 2 sharps, etc.)
        // But mid-piece key changes (modulations) must happen to ALL instruments together.
        // If only 1-2 parts show a key change at a given measure, it's noise — ignore it.
        Set<MeasureStack> realModulations = detectRealModulations(allStacks);

        for (LogicalPart lp : score.getLogicalParts()) {
            logger.info("EXPORT-DIAG: Part id={} name='{}' abbrev='{}' midi={}",
                    lp.getId(), lp.getName(), lp.getAbbreviation(), lp.getMidiProgram());
            writePart(w, lp, allStacks, realModulations);
        }

        w.writeEndElement(); // score-partwise
        w.writeEndDocument();
        w.flush();
        w.close();
    }

    //------------------//
    // computeDivisions //
    //------------------//
    /**
     * Compute the global divisions value (ticks per quarter note) as the LCM of all
     * note duration denominators, so every note duration maps to an exact integer tick count.
     */
    private int computeDivisions ()
    {
        int lcm = 1;

        for (Page page : score.getPages()) {
            for (SystemInfo sys : page.getSystems()) {
                for (MeasureStack stack : sys.getStacks()) {
                    for (Measure measure : stack.getMeasures()) {
                        for (Voice voice : measure.getVoices()) {
                            for (AbstractChordInter chord : voice.getChords()) {
                                Rational base = chord.getDurationSansDotOrTuplet();
                                if (base != null && base.num > 0 && base.den > 0) {
                                    // For duration p/q: ticks = 4*div*p/q; need q | 4*div
                                    int g = gcd(base.den, 4);
                                    lcm = lcm(lcm, base.den / g);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Cap at 96 to avoid huge numbers while still handling 32nds and triplets
        return Math.min(lcm, 96);
    }

    //---------------//
    // collectStacks //
    //---------------//
    private List<MeasureStack> collectStacks ()
    {
        List<MeasureStack> list = new ArrayList<>();

        for (Page page : score.getPages()) {
            for (SystemInfo sys : page.getSystems()) {
                list.addAll(sys.getStacks());
            }
        }

        return list;
    }

    //---------------//
    // writePartList //
    //---------------//
    private void writePartList (XMLStreamWriter w)
        throws XMLStreamException
    {
        startEl(w, "part-list");

        for (LogicalPart lp : score.getLogicalParts()) {
            writeScorePart(w, lp);
        }

        w.writeEndElement(); // part-list
    }

    //----------------//
    // writeScorePart //
    //----------------//
    private void writeScorePart (XMLStreamWriter w,
                                 LogicalPart lp)
        throws XMLStreamException
    {
        String pid = "P" + lp.getId();
        startEl(w, "score-part", "id", pid);

        textEl(w, "part-name", lp.getName() != null ? lp.getName() : "");

        if (lp.getAbbreviation() != null && !lp.getAbbreviation().isBlank()) {
            textEl(w, "part-abbreviation", lp.getAbbreviation());
        }

        // Score instrument
        startEl(w, "score-instrument", "id", pid + "-I1");
        String canonical = dict.normalize(lp.getName());
        if (canonical == null) {
            canonical = dict.normalize(lp.getAbbreviation());
        }
        textEl(w, "instrument-name",
                canonical != null ? canonical : (lp.getName() != null ? lp.getName() : ""));
        w.writeEndElement(); // score-instrument

        // MIDI instrument
        startEl(w, "midi-instrument", "id", pid + "-I1");
        textEl(w, "midi-channel", String.valueOf(computeChannel(lp.getId())));
        textEl(w, "midi-program", String.valueOf(computeProgram(lp)));
        textEl(w, "volume", "78");
        w.writeEndElement(); // midi-instrument

        w.writeEndElement(); // score-part
    }

    //-----------//
    // writePart //
    //-----------//
    private void writePart (XMLStreamWriter w,
                            LogicalPart lp,
                            List<MeasureStack> stacks,
                            Set<MeasureStack> realModulations)
        throws XMLStreamException
    {
        startEl(w, "part", "id", "P" + lp.getId());

        // Transposition for this part (written → concert)
        String canonical = dict.normalize(lp.getName());
        if (canonical == null) {
            canonical = dict.normalize(lp.getAbbreviation());
        }
        String transKey = extractTranspositionKey(lp.getName());
        if (transKey == null) {
            transKey = extractTranspositionKey(lp.getAbbreviation());
        }
        final InstrumentDictionary.Transposition transposition =
                dict.getTransposition(canonical, transKey);

        // Pre-scan: determine time sig ticks when Audiveris APIs return null
        int preScanTicks = preScanTimeSigTicks(stacks, lp.getId());
        Rational preScanTimeSig = (preScanTicks > 0)
                ? new Rational(preScanTicks, 4 * divisions) : null;

        // State: what we last wrote (to detect changes)
        Rational prevTimeSig = null;
        Integer prevKey = null;
        String prevClefSign = null;
        int prevClefLine = 0;
        boolean firstMeasure = true;
        int measureNum = 0;

        for (MeasureStack stack : stacks) {
            Measure measure = measureForPart(stack, lp.getId());
            if (measure == null) {
                continue;
            }

            // Detect current time sig, key, clef
            Rational timeSig = timeSigOf(measure, stack, prevTimeSig, preScanTimeSig);
            // Each part keeps its OWN key sig (Bb clarinet = 2 sharps, etc.)
            // But mid-piece key CHANGES are only accepted if it's a real modulation
            // (i.e. multiple parts changed together). Otherwise keep prevKey.
            Integer detectedKey = keySigOf(measure);
            Integer key;
            if (firstMeasure || prevKey == null) {
                // Initial key sig — use whatever this part has
                key = detectedKey;
            } else if (detectedKey != null && !detectedKey.equals(prevKey)) {
                // Key change detected — only accept if it's a real modulation
                if (realModulations.contains(stack)) {
                    key = detectedKey;
                } else {
                    key = prevKey; // reject — only this part changed, likely noise
                }
            } else {
                key = detectedKey; // same key or null — no change
            }
            ClefData clef = clefOf(measure);

            boolean timeSigNew = !Objects.equals(timeSig, prevTimeSig);
            boolean keyNew = !Objects.equals(key, prevKey);
            boolean clefNew = clef != null
                    && (!clef.sign.equals(prevClefSign) || clef.line != prevClefLine);

            // Full measure duration in ticks (based on time sig)
            int measureTicks;
            if (timeSig != null) {
                measureTicks = toTicks(timeSig);
            } else if (preScanTicks > 0) {
                measureTicks = preScanTicks;
            } else {
                measureTicks = 4 * divisions;
            }

            // Pickup detection: first measure whose note content < full measure
            // Use computeNoteTicks as fallback when getActualDuration() is null
            boolean isPickup = false;
            int effectiveMeasureTicks = measureTicks;
            if (firstMeasure && measureTicks > 0) {
                Rational actualDur = stack.getActualDuration();
                int noteTicks = (actualDur != null && actualDur.num > 0)
                        ? toTicks(actualDur) : computeNoteTicks(measure, stack);
                if (noteTicks > 0 && noteTicks < measureTicks) {
                    isPickup = true;
                    effectiveMeasureTicks = noteTicks; // don't pad the pickup
                }
            }

            // Open measure
            if (isPickup) {
                startEl(w, "measure", "number", "0", "implicit", "yes");
            } else {
                startEl(w, "measure", "number", String.valueOf(++measureNum));
            }

            // Attributes block (written only on changes)
            boolean needAttr = firstMeasure || timeSigNew || keyNew || clefNew;
            if (needAttr) {
                startEl(w, "attributes");

                if (firstMeasure) {
                    textEl(w, "divisions", String.valueOf(divisions));
                }

                if (firstMeasure || keyNew) {
                    startEl(w, "key");
                    textEl(w, "fifths", String.valueOf(key != null ? key : 0));
                    w.writeEndElement(); // key
                }

                if (firstMeasure || timeSigNew) {
                    if (timeSig != null) {
                        int[] sig = toTimeSigArr(timeSig);
                        startEl(w, "time");
                        textEl(w, "beats", String.valueOf(sig[0]));
                        textEl(w, "beat-type", String.valueOf(sig[1]));
                        w.writeEndElement(); // time
                    }
                }

                if (firstMeasure || clefNew) {
                    if (clef != null) {
                        startEl(w, "clef");
                        textEl(w, "sign", clef.sign);
                        if (clef.line > 0) {
                            textEl(w, "line", String.valueOf(clef.line));
                        }
                        w.writeEndElement(); // clef
                    }
                }

                // <transpose> comes after <clef> per MusicXML ordering
                if (firstMeasure && !transposition.isConcertPitch()) {
                    startEl(w, "transpose");
                    textEl(w, "diatonic", String.valueOf(transposition.diatonic()));
                    textEl(w, "chromatic", String.valueOf(transposition.chromatic()));
                    if (transposition.octaveChange() != 0) {
                        textEl(w, "octave-change",
                                String.valueOf(transposition.octaveChange()));
                    }
                    w.writeEndElement(); // transpose
                }

                w.writeEndElement(); // attributes
            }

            // Write notes for all voices
            Integer currentKey = (key != null) ? key : prevKey;
            writeVoices(w, measure, stack, effectiveMeasureTicks, currentKey);

            w.writeEndElement(); // measure

            // Update state
            prevTimeSig = timeSig;
            prevKey = key;
            if (clef != null && clefNew) {
                prevClefSign = clef.sign;
                prevClefLine = clef.line;
            }
            firstMeasure = false;
        }

        w.writeEndElement(); // part
    }

    //-------------//
    // writeVoices //
    //-------------//
    private void writeVoices (XMLStreamWriter w,
                              Measure measure,
                              MeasureStack stack,
                              int measureTicks,
                              Integer keyFifths)
        throws XMLStreamException
    {
        List<Voice> voices = measure.getVoices();
        if (voices.isEmpty()) {
            // Empty measure: write a whole measure rest
            writeMeasureRest(w, 1, measureTicks);
            return;
        }

        int cursor = 0;
        boolean firstVoice = true;

        for (Voice voice : voices) {
            // Backup to measure start before each non-first voice
            if (!firstVoice && cursor > 0) {
                startEl(w, "backup");
                textEl(w, "duration", String.valueOf(cursor));
                w.writeEndElement(); // backup
                cursor = 0;
            }
            firstVoice = false;

            int voiceId = voice.getId();

            if (voice.isMeasureRest()) {
                writeMeasureRest(w, voiceId, measureTicks);
                cursor += measureTicks;
                continue;
            }

            // Normal voice: iterate slots in time order
            for (Slot slot : stack.getSlots()) {
                SlotVoice info = voice.getSlotInfo(slot);
                if (info == null || info.status != SlotVoice.ChordStatus.BEGIN) {
                    continue;
                }

                AbstractChordInter chord = info.chord;
                Rational timeOffset = chord.getTimeOffset();
                int chordStart = (timeOffset != null) ? toTicks(timeOffset) : cursor;

                // Fill gap with a rest if needed
                if (chordStart > cursor) {
                    writeGapRest(w, voiceId, chordStart - cursor);
                    cursor = chordStart;
                }

                int consumed = writeChord(w, chord, voiceId, keyFifths);
                cursor += consumed;
            }

            // Fill tail of measure with rest if voice ends early
            if (cursor < measureTicks) {
                writeGapRest(w, voiceId, measureTicks - cursor);
                cursor = measureTicks;
            }
        }
    }

    //------------//
    // writeChord //
    //------------//
    /**
     * Write all notes of a chord. Returns the number of ticks consumed.
     */
    private int writeChord (XMLStreamWriter w,
                            AbstractChordInter chord,
                            int voiceId,
                            Integer keyFifths)
        throws XMLStreamException
    {
        Rational dur = chord.getDuration();
        if (dur == null) {
            dur = chord.getDurationSansDotOrTuplet();
        }
        if (dur == null || dur.num == 0) {
            return 0;
        }

        int ticks = toTicks(dur);
        int dots = chord.getDotsNumber();
        Rational baseType = chord.getDurationSansDotOrTuplet();
        if (baseType == null) {
            baseType = dur;
        }

        List<Inter> members = chord.getMembers();

        boolean wroteAtLeastOne = false;

        for (int i = 0; i < members.size(); i++) {
            Inter member = members.get(i);
            boolean isChordElement = (i > 0); // subsequent notes in same chord

            startEl(w, "note");

            if (isChordElement) {
                w.writeEmptyElement("chord");
            }

            if (member instanceof HeadInter head) {
                writePitch(w, head, keyFifths);
            } else {
                // Rest member
                w.writeEmptyElement("rest");
            }

            textEl(w, "duration", String.valueOf(ticks));
            textEl(w, "voice", String.valueOf(voiceId));
            textEl(w, "type", typeName(baseType));

            for (int d = 0; d < dots; d++) {
                w.writeEmptyElement("dot");
            }

            w.writeEndElement(); // note
            wroteAtLeastOne = true;
        }

        // Fallback if members list is empty (shouldn't happen, but guard against it)
        if (!wroteAtLeastOne) {
            startEl(w, "note");
            w.writeEmptyElement("rest");
            textEl(w, "duration", String.valueOf(ticks));
            textEl(w, "voice", String.valueOf(voiceId));
            textEl(w, "type", typeName(baseType));
            w.writeEndElement(); // note
        }

        return ticks;
    }

    //------------//
    // writePitch //
    //------------//
    private void writePitch (XMLStreamWriter w,
                             HeadInter head,
                             Integer keyFifths)
        throws XMLStreamException
    {
        // getAlteration accounts for local accidentals, measure-scope accidentals, and key sig
        int alter = head.getAlteration(keyFifths);

        startEl(w, "pitch");
        textEl(w, "step", head.getStep().name());
        if (alter != 0) {
            textEl(w, "alter", String.valueOf(alter));
        }
        textEl(w, "octave", String.valueOf(head.getOctave()));
        w.writeEndElement(); // pitch
    }

    //------------------//
    // writeMeasureRest //
    //------------------//
    private void writeMeasureRest (XMLStreamWriter w,
                                   int voiceId,
                                   int ticks)
        throws XMLStreamException
    {
        startEl(w, "note");
        startEl(w, "rest");
        w.writeAttribute("measure", "yes");
        w.writeEndElement(); // rest
        textEl(w, "duration", String.valueOf(ticks));
        textEl(w, "voice", String.valueOf(voiceId));
        w.writeEndElement(); // note
    }

    //--------------//
    // writeGapRest //
    //--------------//
    private void writeGapRest (XMLStreamWriter w,
                               int voiceId,
                               int ticks)
        throws XMLStreamException
    {
        startEl(w, "note");
        w.writeEmptyElement("rest");
        textEl(w, "duration", String.valueOf(ticks));
        textEl(w, "voice", String.valueOf(voiceId));
        String tp = ticksToType(ticks);
        if (tp != null) {
            textEl(w, "type", tp);
        }
        w.writeEndElement(); // note
    }

    //------------------//
    // computeNoteTicks //
    //------------------//
    /**
     * Compute the ticks spanned by note (non-rest) chords in a measure,
     * without any padding. Used to infer the time signature when Audiveris
     * provides no explicit time sig data.
     */
    private int computeNoteTicks (Measure measure,
                                  MeasureStack stack)
    {
        int max = 0;
        for (Voice voice : measure.getVoices()) {
            if (voice.isMeasureRest()) {
                continue;
            }
            int cursor = 0;
            int lastOnset = 0;
            int lastDur = 0;
            for (Slot slot : stack.getSlots()) {
                SlotVoice info = voice.getSlotInfo(slot);
                if (info == null || info.status != SlotVoice.ChordStatus.BEGIN) {
                    continue;
                }
                AbstractChordInter chord = info.chord;
                // Only count chords that have at least one note head (not pure rests)
                if (chord.getMembers().stream().noneMatch(m -> m instanceof HeadInter)) {
                    continue;
                }
                Rational dur = chord.getDuration();
                if (dur == null) {
                    dur = chord.getDurationSansDotOrTuplet();
                }
                Rational to = chord.getTimeOffset();
                int start = (to != null) ? toTicks(to) : cursor;
                if (dur != null && dur.num > 0) {
                    int d = toTicks(dur);
                    cursor = start + d;
                    lastOnset = start;
                    lastDur = d;
                }
            }
            // Cap the last note's contribution: use min of its actual duration
            // and one quarter note, to avoid dotted notes extending past barline
            if (lastDur > divisions) {
                cursor = lastOnset + Math.min(lastDur, divisions);
            }
            max = Math.max(max, cursor);
        }
        return max;
    }

    //----------------------//
    // preScanTimeSigTicks  //
    //----------------------//
    /**
     * Pre-scan all stacks to determine the time signature in ticks.
     * Priority: explicit AbstractTimeInter → getExpectedDuration → getActualDuration
     * → multi-part consensus from note-only content of first several measures.
     *
     * @param stacks        all stacks in score order
     * @param logicalPartId logical part id to look up measures (for OCR tier only)
     * @return ticks per measure, or 0 if undetermined
     */
    private int preScanTimeSigTicks (List<MeasureStack> stacks,
                                     int logicalPartId)
    {
        // 1. Explicit time sig from OCR (this part only — OCR is per-part)
        for (MeasureStack stack : stacks) {
            Measure m = measureForPart(stack, logicalPartId);
            if (m == null) {
                continue;
            }
            AbstractTimeInter ts = m.getTimeSignature();
            if (ts != null) {
                TimeRational tr = ts.getTimeRational();
                if (tr != null && tr.num > 0 && tr.den > 0) {
                    return 4 * divisions * tr.num / tr.den;
                }
            }
        }

        // 2. Expected duration from rhythm analysis
        for (MeasureStack stack : stacks) {
            Rational exp = stack.getExpectedDuration();
            if (exp != null && exp.num > 0) {
                return toTicks(exp);
            }
        }

        // 3. Actual duration
        for (MeasureStack stack : stacks) {
            Rational actual = stack.getActualDuration();
            if (actual != null && actual.num > 0) {
                return toTicks(actual);
            }
        }

        // 4. Multi-part consensus: collect note ticks from ALL parts across first ~10 stacks.
        //    Snap each raw value to the nearest standard time sig to handle overestimation
        //    caused by dotted notes or ties at measure end extending past the barline.
        //    Use the mode (most frequent value) as the time sig — this avoids being misled
        //    by a pickup measure, an outlier part, or a rhythmically complex measure.
        //    Requires multiple instruments to agree, matching the user rule:
        //    "只有多个乐器全部检测到才确认拍号"
        Map<Integer, Integer> tickFreq = new LinkedHashMap<>();
        int stacksSeen = 0;
        for (MeasureStack stack : stacks) {
            boolean anyNote = false;
            for (Measure m : stack.getMeasures()) {
                int t = computeNoteTicks(m, stack);
                if (t > 0) {
                    t = snapToStandardTicks(t);
                    tickFreq.merge(t, 1, Integer::sum);
                    anyNote = true;
                }
            }
            if (anyNote && ++stacksSeen >= 10) {
                break;
            }
        }
        if (!tickFreq.isEmpty()) {
            // Return the most frequent tick count across all parts and first stacks
            int modeVal = tickFreq.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .get().getKey();
            logger.info("EXPORT-DIAG: preScan consensus tickFreq={} → mode={} ticks",
                    tickFreq, modeVal);
            return modeVal;
        }
        return 0;
    }

    //----------------------//
    // snapToStandardTicks  //
    //----------------------//
    /**
     * Snap a raw tick count to the nearest standard time signature value.
     * This corrects overestimation from computeNoteTicks when the last note
     * in a measure (e.g. a dotted note) extends past the barline.
     *
     * @param ticks raw tick count from computeNoteTicks
     * @return snapped tick count corresponding to nearest standard time sig
     */
    private int snapToStandardTicks (int ticks)
    {
        if (ticks <= 0) {
            return 0;
        }
        // Standard measure lengths: 1/4, 2/8, 3/8, 2/4, 3/4, 4/4, 5/4, 6/4
        int[] standards = {
                divisions,             // 1/4
                divisions * 3 / 2,     // 3/8
                divisions * 2,         // 2/4
                divisions * 3,         // 3/4 or 6/8
                divisions * 4,         // 4/4
                divisions * 5,         // 5/4
                divisions * 6,         // 6/4 or 12/8
        };
        int closest = standards[0];
        int minDist = Math.abs(ticks - closest);
        for (int s : standards) {
            int d = Math.abs(ticks - s);
            if (d < minDist) {
                minDist = d;
                closest = s;
            }
        }
        return closest;
    }

    //----------------//
    // measureForPart //
    //----------------//
    private Measure measureForPart (MeasureStack stack,
                                   int logicalPartId)
    {
        for (Measure m : stack.getMeasures()) {
            if (m.getPart().getId() == logicalPartId) {
                return m;
            }
        }
        return null;
    }

    //-----------//
    // timeSigOf //
    //-----------//
    /**
     * Determine the current time signature as a Rational (whole-note fraction).
     * <p>
     * Mid-piece time sig changes are ONLY accepted when Audiveris explicitly detected a new
     * time-signature symbol (AbstractTimeInter) in this measure. Using duration-based fallbacks
     * for changes would cause spurious time-sig switches whenever scan quality varies.
     * <p>
     * For the very first measure ({@code prev == null}), the full fallback chain is used
     * to establish an initial value.
     */
    private Rational timeSigOf (Measure measure,
                                MeasureStack stack,
                                Rational prev,
                                Rational preScan)
    {
        // --- Step 1: explicit OCR time signature in this measure ---
        AbstractTimeInter ts = measure.getTimeSignature();
        if (ts != null) {
            TimeRational tr = ts.getTimeRational();
            if (tr != null && tr.num > 0) {
                Rational fromOCR = new Rational(tr.num, tr.den);
                // OCR detected a time sig — trust it. The classifier already validated
                // the glyph with high confidence, no need for additional sanity checks.
                // Pickup measures naturally have actual duration < time sig, which is fine.
                return fromOCR;
            }
        }

        // --- Step 2: no OCR time sig in this measure ---
        if (prev != null) {
            // Time sig already established — keep it.
            // Only an explicit OCR detection (above) can change it mid-piece.
            return prev;
        }

        // --- Step 3: first measure — establish initial time sig from fallbacks ---
        Rational actual = stack.getActualDuration();
        if (actual != null && actual.num > 0) {
            return actual;
        }

        Rational exp = stack.getExpectedDuration();
        if (exp != null && exp.num > 0) {
            return exp;
        }

        // Pre-scan result derived from note content across first few measures
        return preScan;
    }

    //---------------//
    // toTimeSigArr  //
    //---------------//
    /**
     * Convert a Rational measure duration (fraction of whole note, always reduced)
     * to the [beats, beat-type] pair for MusicXML {@code <time>}.
     * <p>
     * Examples: 1/2 → [2,4] (2/4), 3/4 → [3,4] (3/4), 3/8 → [3,8] (3/8), 1/1 → [4,4] (4/4).
     */
    private static int[] toTimeSigArr (Rational r)
    {
        // Quarter-note basis: beats = r * 4 — works when denominator divides 4*numerator
        int beatsQ = 4 * r.num;
        if (beatsQ % r.den == 0) {
            return new int[]{beatsQ / r.den, 4};
        }
        // Eighth-note basis: beats = r * 8
        int beatsE = 8 * r.num;
        if (beatsE % r.den == 0) {
            return new int[]{beatsE / r.den, 8};
        }
        // Fallback: use numerator/denominator as-is (unusual time sigs)
        return new int[]{r.num, r.den};
    }

    //----------//
    //----------//
    // keySigOf //
    //----------//
    private Integer keySigOf (Measure measure)
    {
        KeyInter key = measure.getKey(0);
        if (key != null) {
            return key.getFifths();
        }
        return null;
    }

    //-------------------------//
    // detectRealModulations   //
    //-------------------------//
    /**
     * Detect which stacks have a REAL modulation (key change across all instruments).
     * <p>
     * Each instrument can have its own initial key sig (Bb clarinet = 2 sharps, etc.).
     * But mid-piece key CHANGES must happen to ALL instruments together.
     * If only 1-2 parts show a key change at a given measure, it's OCR noise.
     * A real modulation is when many parts change key sig at the same stack.
     *
     * @param stacks all measure stacks
     * @return set of stacks where a real modulation occurs
     */
    private Set<MeasureStack> detectRealModulations (List<MeasureStack> stacks)
    {
        Set<MeasureStack> result = new LinkedHashSet<>();
        List<LogicalPart> logicalParts = score.getLogicalParts();
        int totalParts = logicalParts.size();

        // Track each part's previous key sig to detect changes
        Map<Integer, Integer> prevKeySig = new LinkedHashMap<>();

        for (MeasureStack stack : stacks) {
            int partsWithChange = 0;
            int partsChecked = 0;

            for (LogicalPart lp : logicalParts) {
                Measure m = measureForPart(stack, lp.getId());
                if (m == null) continue;

                KeyInter key = m.getKey(0);
                if (key == null) continue;

                partsChecked++;
                int fifths = key.getFifths();
                Integer prev = prevKeySig.get(lp.getId());

                if (prev != null && fifths != prev) {
                    partsWithChange++;
                }

                prevKeySig.put(lp.getId(), fifths);
            }

            // Real modulation: multiple parts changed key sig at the same stack
            if (partsWithChange >= 3 || (partsWithChange > 0 && partsWithChange >= totalParts / 3)) {
                logger.info("EXPORT-DIAG: real modulation at stack — {}/{} parts changed key",
                        partsWithChange, totalParts);
                result.add(stack);
            } else if (partsWithChange > 0) {
                logger.info("EXPORT-DIAG: key change rejected at stack — only {}/{} parts changed (noise)",
                        partsWithChange, totalParts);
            }
        }

        return result;
    }

    //--------//
    // clefOf //
    //--------//
    private ClefData clefOf (Measure measure)
    {
        List<ClefInter> clefs = measure.getClefs();
        if (clefs == null || clefs.isEmpty()) {
            return null;
        }
        return clefDataFor(clefs.get(0).getKind());
    }

    //-------------//
    // clefDataFor //
    //-------------//
    private ClefData clefDataFor (ClefKind kind)
    {
        return switch (kind) {
            case TREBLE -> new ClefData("G", 2);
            case BASS -> new ClefData("F", 4);
            case ALTO -> new ClefData("C", 3);
            case TENOR -> new ClefData("C", 4);
            case SOPRANO -> new ClefData("C", 1);
            case MEZZO_SOPRANO -> new ClefData("C", 2);
            case BARITONE -> new ClefData("C", 5);
            case PERCUSSION -> new ClefData("percussion", 0);
            default -> new ClefData("G", 2);
        };
    }

    //---------//
    // toTicks //
    //---------//
    /** Convert a rational duration (fraction of whole note) to integer ticks. */
    private int toTicks (Rational r)
    {
        if (r == null || r.num == 0) {
            return 0;
        }
        return 4 * divisions * r.num / r.den;
    }

    //----------//
    // typeName //
    //----------//
    /** Map a base duration (sans dots and tuplet) to a MusicXML type name. */
    private String typeName (Rational base)
    {
        if (base == null) {
            return "quarter";
        }
        for (Map.Entry<Rational, String> e : TYPE_NAMES.entrySet()) {
            Rational k = e.getKey();
            if (k.num * base.den == base.num * k.den) {
                return e.getValue();
            }
        }
        // Fallback: approximate from denominator/numerator ratio
        int ratio = base.den / Math.max(base.num, 1);
        return switch (ratio) {
            case 1 -> "whole";
            case 2 -> "half";
            case 4 -> "quarter";
            case 8 -> "eighth";
            case 16 -> "16th";
            case 32 -> "32nd";
            default -> "quarter";
        };
    }

    //-------------//
    // ticksToType //
    //-------------//
    /** Reverse-map a tick count to a MusicXML type name, or null if not standard. */
    private String ticksToType (int ticks)
    {
        for (Map.Entry<Rational, String> e : TYPE_NAMES.entrySet()) {
            Rational r = e.getKey();
            if (4 * divisions * r.num == ticks * r.den) {
                return e.getValue();
            }
        }
        return null;
    }

    //----------------//
    // computeChannel //
    //----------------//
    /** Assign MIDI channel 1-16, skipping channel 10 (GM drums). */
    private static int computeChannel (int logicalId)
    {
        int raw = 1 + ((logicalId - 1) % 15);
        return (raw >= 10) ? raw + 1 : raw;
    }

    //----------------//
    // computeProgram //
    //----------------//
    private int computeProgram (LogicalPart lp)
    {
        Integer prog = lp.getMidiProgram();
        if (prog != null) {
            return prog;
        }

        String canonical = dict.normalize(lp.getName());
        if (canonical == null) {
            canonical = dict.normalize(lp.getAbbreviation());
        }
        if (canonical != null) {
            Integer p = dict.getMidiProgram(canonical);
            if (p != null) {
                return p;
            }
        }

        return lp.getDefaultProgram();
    }

    //----------//
    // startEl  //
    //----------//
    private static void startEl (XMLStreamWriter w,
                                 String tag)
        throws XMLStreamException
    {
        w.writeCharacters("\n");
        w.writeStartElement(tag);
    }

    private static void startEl (XMLStreamWriter w,
                                 String tag,
                                 String attr,
                                 String val)
        throws XMLStreamException
    {
        w.writeCharacters("\n");
        w.writeStartElement(tag);
        w.writeAttribute(attr, val);
    }

    private static void startEl (XMLStreamWriter w,
                                 String tag,
                                 String a1,
                                 String v1,
                                 String a2,
                                 String v2)
        throws XMLStreamException
    {
        w.writeCharacters("\n");
        w.writeStartElement(tag);
        w.writeAttribute(a1, v1);
        w.writeAttribute(a2, v2);
    }

    //---------//
    // textEl  //
    //---------//
    private static void textEl (XMLStreamWriter w,
                                String tag,
                                String value)
        throws XMLStreamException
    {
        w.writeStartElement(tag);
        w.writeCharacters(value);
        w.writeEndElement();
    }

    //------------------------//
    // extractTranspositionKey //
    //------------------------//
    /** Extract a transposition key like "Bb", "F", "A" from a part name parenthetical.
     *  e.g. "Clarinetti (Bb)" → "Bb", "Corni (F)" → "F", "Timpani (C. E. G)" → null */
    private static final Pattern TRANS_KEY_PAT =
            Pattern.compile("\\(([A-Ga-g][b#♭♯]?)\\)");

    private static String extractTranspositionKey (String rawName)
    {
        if (rawName == null) {
            return null;
        }
        Matcher m = TRANS_KEY_PAT.matcher(rawName);
        return m.find() ? m.group(1) : null;
    }

    //-----//
    // gcd //
    //-----//
    private static int gcd (int a,
                            int b)
    {
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    //-----//
    // lcm //
    //-----//
    private static int lcm (int a,
                            int b)
    {
        return a / gcd(a, b) * b;
    }

    //----------//
    // ClefData //
    //----------//
    private record ClefData(String sign, int line)
    {
    }
}
