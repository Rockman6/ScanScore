//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                              I n s t r u m e n t D i c t i o n a r y                          //
//                                                                                                //
//------------------------------------------------------------------------------------------------//
package org.audiveris.omr.score;

import java.util.HashMap;
import java.util.Map;

/**
 * Class <code>InstrumentDictionary</code> provides lookup services for orchestral instrument
 * names and abbreviations.
 * <p>
 * It normalizes abbreviations and multilingual names (English, Italian, German, French) to
 * canonical English names, and maps canonical names to MIDI program numbers.
 * <p>
 * Used by {@link PartCollation} (cross-page part matching) and
 * {@link PartwiseBuilder} (MIDI program assignment in MusicXML export).
 */
public class InstrumentDictionary
{
    //~ Static fields ------------------------------------------------------------------------------

    private static final InstrumentDictionary INSTANCE = new InstrumentDictionary();

    //~ Instance fields ----------------------------------------------------------------------------

    /** canonical name (lowercase) → MIDI program (1-128) */
    private final Map<String, Integer> programByCanonical = new HashMap<>();

    /** alias / abbreviation (lowercase, no trailing punct) → canonical name */
    private final Map<String, String> canonicalByAlias = new HashMap<>();

    /** "canonical_lower|key_lower" → Transposition (chromatic/diatonic offset from written to concert) */
    private final Map<String, Transposition> transpositionMap = new HashMap<>();

    //~ Constructors -------------------------------------------------------------------------------

    private InstrumentDictionary ()
    {
        buildProgramTable();
        buildAliasTable();
        buildTranspositionTable();
    }

    //~ Methods ------------------------------------------------------------------------------------

    //-------------//
    // getInstance //
    //-------------//
    public static InstrumentDictionary getInstance ()
    {
        return INSTANCE;
    }

    //-----------//
    // normalize //
    //-----------//
    /**
     * Normalize a raw instrument name (possibly an abbreviation or foreign-language name)
     * to its canonical English name.
     * <p>
     * Strips trailing Roman numeral suffixes (e.g. "Violin I" → looks up "Violin"),
     * strips trailing punctuation, lowercases, then checks the alias table.
     *
     * @param rawName the OCR-extracted or user-provided name; may be null
     * @return canonical English name (e.g. "Violin", "Flute"), or null if not recognized
     */
    public String normalize (String rawName)
    {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }

        String s = rawName.trim();

        // Strip parenthetical content like "(Bb)", "(F)", "(C. E. G)"
        s = s.replaceAll("\\s*\\([^)]*\\)", "").trim();

        // Strip trailing Roman numeral suffix and range patterns, e.g.:
        //   "Violin I", "Horn III-IV", "Trombone I,II", "Vl. II"
        s = s.replaceAll(
                "\\s+(I{1,3}|IV|VI{0,3}|IX|X?I{0,3})([,\\-]+(I{1,3}|IV|VI{0,3}|IX))?\\s*$",
                "").trim();

        // Strip trailing punctuation (period, comma)
        s = s.replaceAll("[.,]+$", "").trim();

        // Lowercase for lookup
        final String key = s.toLowerCase();

        // 1. Check alias table
        final String canonical = canonicalByAlias.get(key);
        if (canonical != null) {
            return canonical;
        }

        // 2. Check if it's already a canonical name (e.g. "Violin" direct)
        if (programByCanonical.containsKey(key)) {
            // Return the properly-cased canonical (capitalize first letter)
            return Character.toUpperCase(key.charAt(0)) + key.substring(1);
        }

        return null;
    }

    //----------------//
    // getMidiProgram //
    //----------------//
    /**
     * Return the MIDI program number (1-128) for a canonical instrument name.
     *
     * @param canonicalName the canonical English name (case-insensitive)
     * @return MIDI program 1-128, or null if not found
     */
    public Integer getMidiProgram (String canonicalName)
    {
        if (canonicalName == null) {
            return null;
        }

        return programByCanonical.get(canonicalName.toLowerCase());
    }

    //-------------------//
    // buildProgramTable //
    //-------------------//
    private void buildProgramTable ()
    {
        // Strings
        put("violin",       41);
        put("viola",        42);
        put("cello",        43);
        put("double bass",  44);
        put("harp",         47);

        // Percussion
        put("timpani",      48);
        put("percussion",   49);
        put("drums",        49);

        // Brass
        put("trumpet",      57);
        put("trombone",     58);
        put("tuba",         59);
        put("french horn",  61);
        put("horn",         61);

        // Woodwinds
        put("oboe",         69);
        put("english horn", 70);
        put("bassoon",      71);
        put("clarinet",     72);
        put("piccolo",      73);
        put("flute",        74);
        put("recorder",     75);
        put("pan flute",    76);

        // Keyboards
        put("piano",        1);
        put("harpsichord",  7);
        put("celesta",      9);
        put("organ",        20);

        // Others
        put("guitar",       25);
        put("lute",         25);
        put("mandolin",     26);
        put("banjo",        106);
    }

    private void put (String canonical, int midiProgram)
    {
        programByCanonical.put(canonical, midiProgram);
    }

    //-----------------//
    // buildAliasTable //
    //-----------------//
    private void buildAliasTable ()
    {
        // ---- English abbreviations ----

        // Strings
        alias("vl",          "Violin");
        alias("vln",         "Violin");
        alias("violin",      "Violin");
        alias("violins",     "Violin");
        alias("vla",         "Viola");
        alias("va",          "Viola");
        alias("viola",       "Viola");
        alias("violas",      "Viola");
        alias("vc",          "Cello");
        alias("vlc",         "Cello");
        alias("vcl",         "Cello");
        alias("cello",       "Cello");
        alias("cellos",      "Cello");
        alias("cb",          "Double Bass");
        alias("d.b",         "Double Bass");
        alias("db",          "Double Bass");
        alias("bass",        "Double Bass");
        alias("double bass", "Double Bass");
        alias("cont",        "Double Bass");
        alias("contrabass",  "Double Bass");
        alias("hp",          "Harp");
        alias("hrp",         "Harp");
        alias("harp",        "Harp");

        // Woodwinds
        alias("fl",          "Flute");
        alias("flt",         "Flute");
        alias("flute",       "Flute");
        alias("flutes",      "Flute");
        alias("picc",        "Piccolo");
        alias("piccolo",     "Piccolo");
        alias("ob",          "Oboe");
        alias("oboe",        "Oboe");
        alias("e.h",         "English Horn");
        alias("eh",          "English Horn");
        alias("cor anglais", "English Horn");
        alias("cl",          "Clarinet");
        alias("clar",        "Clarinet");
        alias("clt",         "Clarinet");
        alias("clarinet",    "Clarinet");
        alias("clarinets",   "Clarinet");
        alias("bsn",         "Bassoon");
        alias("fg",          "Bassoon");
        alias("fag",         "Bassoon");
        alias("bssn",        "Bassoon");
        alias("bassoon",     "Bassoon");
        alias("bassoons",    "Bassoon");
        alias("contrabsn",   "Bassoon");
        alias("c.bsn",       "Bassoon");

        // Brass
        alias("hn",          "French Horn");
        alias("hr",          "French Horn");
        alias("hrn",         "French Horn");
        alias("french horn", "French Horn");
        alias("tpt",         "Trumpet");
        alias("tr",          "Trumpet");
        alias("trpt",        "Trumpet");
        alias("trum",        "Trumpet");
        alias("trumpet",     "Trumpet");
        alias("trumpets",    "Trumpet");
        alias("tbn",         "Trombone");
        alias("trb",         "Trombone");
        alias("trbn",        "Trombone");
        alias("trombone",    "Trombone");
        alias("trombones",   "Trombone");
        alias("tba",         "Tuba");
        alias("tb",          "Tuba");
        alias("tuba",        "Tuba");

        // Percussion
        alias("timp",        "Timpani");
        alias("tp",          "Timpani");
        alias("timpani",     "Timpani");
        alias("perc",        "Percussion");
        alias("percussion",  "Percussion");
        alias("drums",       "Percussion");

        // ---- Italian names ----

        alias("violino",           "Violin");
        alias("violini",           "Violin");
        alias("viole",             "Viola");
        alias("violoncello",       "Cello");
        alias("violoncelli",       "Cello");
        alias("contrabbasso",      "Double Bass");
        alias("contrabbassi",      "Double Bass");
        alias("arpa",              "Harp");
        alias("flauto",            "Flute");
        alias("flauti",            "Flute");
        alias("flauto piccolo",    "Piccolo");
        alias("ottavino",          "Piccolo");
        alias("oboe",              "Oboe");
        alias("oboi",              "Oboe");
        alias("corno inglese",     "English Horn");
        alias("clarinetto",        "Clarinet");
        alias("clarinetti",        "Clarinet");
        alias("fagotto",           "Bassoon");
        alias("fagotti",           "Bassoon");
        alias("contrafagotto",     "Bassoon");
        alias("corno",             "French Horn");
        alias("corni",             "French Horn");
        alias("tromba",            "Trumpet");
        alias("trombe",            "Trumpet");
        alias("trombone",          "Trombone");
        alias("tromboni",          "Trombone");
        alias("tuba",              "Tuba");
        alias("timpani",           "Timpani");
        alias("piatti",            "Percussion");
        alias("percussione",       "Percussion");

        // ---- German names ----

        alias("violine",       "Violin");
        alias("violinen",      "Violin");
        alias("geige",         "Violin");
        alias("geigen",        "Violin");
        alias("bratsche",      "Viola");
        alias("bratschen",     "Viola");
        alias("violoncell",    "Cello");
        alias("violoncello",   "Cello");  // same in German
        alias("kontrabass",    "Double Bass");
        alias("kontrabässe",   "Double Bass");
        alias("kontrabaesse",  "Double Bass");
        alias("harfe",         "Harp");
        alias("flöte",         "Flute");
        alias("flote",         "Flute");   // without umlaut
        alias("flöten",        "Flute");
        alias("floten",        "Flute");
        alias("kl. flöte",     "Piccolo");
        alias("kl. flote",     "Piccolo");
        alias("oboe",          "Oboe");    // same in German
        alias("englisches horn","English Horn");
        alias("klarinette",    "Clarinet");
        alias("klarinetten",   "Clarinet");
        alias("fagott",        "Bassoon");
        alias("fagotte",       "Bassoon");
        alias("horn",          "French Horn");
        alias("hörner",        "French Horn");
        alias("hoerner",       "French Horn");
        alias("trompete",      "Trumpet");
        alias("trompeten",     "Trumpet");
        alias("posaune",       "Trombone");
        alias("posaunen",      "Trombone");
        alias("tuba",          "Tuba");    // same in German
        alias("pauke",         "Timpani");
        alias("pauken",        "Timpani");
        alias("schlagzeug",    "Percussion");

        // ---- French names ----

        alias("violon",        "Violin");
        alias("violons",       "Violin");
        alias("alto",          "Viola");   // French term for viola
        alias("altos",         "Viola");
        alias("violoncelle",   "Cello");
        alias("violoncelles",  "Cello");
        alias("contrebasse",   "Double Bass");
        alias("contrebasses",  "Double Bass");
        alias("harpe",         "Harp");
        alias("flûte",         "Flute");
        alias("flute",         "Flute");
        alias("hautbois",      "Oboe");
        alias("cor anglais",   "English Horn");
        alias("clarinette",    "Clarinet");
        alias("clarinettes",   "Clarinet");
        alias("basson",        "Bassoon");
        alias("bassons",       "Bassoon");
        alias("cor",           "French Horn");
        alias("cors",          "French Horn");
        alias("trompette",     "Trumpet");
        alias("trompettes",    "Trumpet");
        alias("trombone",      "Trombone");
        alias("trombones",     "Trombone");
        alias("timbales",      "Timpani");
        alias("percussion",    "Percussion");
    }

    private void alias (String raw, String canonical)
    {
        canonicalByAlias.put(raw.toLowerCase(), canonical);
    }

    //------------------------//
    // buildTranspositionTable //
    //------------------------//
    private void buildTranspositionTable ()
    {
        // Key format: "canonical_lower|transposition_key_lower"
        // Transposition(diatonic, chromatic, octaveChange):
        //   chromatic = concert_semitones - written_semitones
        //   e.g. Bb clarinet: concert = written - 2  →  chromatic = -2

        // Piccolo: sounds one octave HIGHER than written
        trans("piccolo|",   7,  12, 0);
        trans("piccolo|c",  7,  12, 0);

        // English Horn (Cor Anglais): always in F, perfect 5th lower
        trans("english horn|",  -4, -7, 0);
        trans("english horn|f", -4, -7, 0);

        // Clarinet
        trans("clarinet|bb", -1, -2, 0);
        trans("clarinet|b",  -1, -2, 0);   // B♭ sometimes written just "B" (German)
        trans("clarinet|a",  -2, -3, 0);
        trans("clarinet|eb",  2,  3, 0);
        trans("clarinet|es",  2,  3, 0);   // German: Es = E♭
        trans("clarinet|c",   0,  0, 0);

        // Trumpet
        trans("trumpet|bb", -1, -2, 0);
        trans("trumpet|b",  -1, -2, 0);
        trans("trumpet|c",   0,  0, 0);
        trans("trumpet|d",   1,  2, 0);
        trans("trumpet|eb",  2,  3, 0);
        trans("trumpet|es",  2,  3, 0);
        trans("trumpet|a",  -2, -3, 0);    // rare

        // French Horn (default = F when no key specified)
        trans("french horn|",   -4, -7, 0);
        trans("french horn|f",  -4, -7, 0);
        trans("french horn|eb", -5, -9, 0);
        trans("french horn|es", -5, -9, 0);
        trans("french horn|bb", -6,-10, 0);
        trans("french horn|b",  -6,-10, 0);
        trans("french horn|c",   0,  0, 0);
        trans("french horn|d",   1,  2, 0);
        trans("french horn|e",   2,  4, 0);
        trans("french horn|g",   4,  7, 0);
        trans("french horn|a",   5,  9, 0);
        trans("horn|",   -4, -7, 0);
        trans("horn|f",  -4, -7, 0);
        trans("horn|eb", -5, -9, 0);
        trans("horn|es", -5, -9, 0);
        trans("horn|bb", -6,-10, 0);
        trans("horn|b",  -6,-10, 0);
        trans("horn|c",   0,  0, 0);
        trans("horn|d",   1,  2, 0);
        trans("horn|e",   2,  4, 0);
        trans("horn|g",   4,  7, 0);
        trans("horn|a",   5,  9, 0);

        // Saxophone (for completeness)
        trans("soprano saxophone|bb", -1, -2, 0);
        trans("alto saxophone|eb",    -5, -9, 0);
        trans("tenor saxophone|bb",   -8,-14, 0);
        trans("baritone saxophone|eb",-12,-21, 0);
    }

    private void trans (String key, int diatonic, int chromatic, int octaveChange)
    {
        transpositionMap.put(key, new Transposition(diatonic, chromatic, octaveChange));
    }

    //------------------//
    // getTransposition //
    //------------------//
    /**
     * Return the transposition (written → concert) for the given canonical instrument name
     * and optional transposition key extracted from the part name parenthetical (e.g. "Bb", "F").
     *
     * @param canonicalName canonical English name (case-insensitive), or null
     * @param transpositionKey  key string like "Bb", "F", "A", "Eb" (case-insensitive), or null
     * @return Transposition; never null — returns {@code Transposition.CONCERT} if not found
     */
    public Transposition getTransposition (String canonicalName,
                                           String transpositionKey)
    {
        if (canonicalName == null) {
            return Transposition.CONCERT;
        }

        String cn = canonicalName.toLowerCase();
        String tk = (transpositionKey != null) ? transpositionKey.toLowerCase() : "";

        // Try canonical + key
        Transposition t = transpositionMap.get(cn + "|" + tk);
        if (t != null) {
            return t;
        }

        // Try canonical with empty key (default for instrument)
        t = transpositionMap.get(cn + "|");
        if (t != null) {
            return t;
        }

        return Transposition.CONCERT;
    }

    //~ Inner Classes ------------------------------------------------------------------------------

    //--------------//
    // Transposition //
    //--------------//
    /**
     * Represents the transposition from written pitch to concert (sounding) pitch.
     * <p>
     * In MusicXML {@code <transpose>}: concert = written + chromatic semitones.
     * Negative chromatic means the instrument sounds lower than written
     * (e.g. Bb clarinet: chromatic=-2, written C → concert Bb).
     */
    public record Transposition(int diatonic, int chromatic, int octaveChange)
    {
        public static final Transposition CONCERT = new Transposition(0, 0, 0);

        public boolean isConcertPitch ()
        {
            return diatonic == 0 && chromatic == 0 && octaveChange == 0;
        }
    }
}
