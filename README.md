# ScanScore — Music Scanner

**ScanScore** is an open-source optical music recognition (OMR) application that converts scanned sheet music images into editable digital formats (MusicXML, MIDI).

Built on [Audiveris](https://github.com/Audiveris/audiveris), ScanScore adds modern OCR, LLM-powered instrument identification, a smoother UI, and better support for multilingual scores (Chinese, English, Italian, etc.).

## Features

- **Optical Music Recognition** — Detects notes, rests, clefs, time signatures, dynamics, slurs, and more from score images or PDFs
- **RapidOCR Integration** — Lightweight ONNX-based text recognition (~300 MB) replacing Tesseract for better multilingual support
- **LLM Instrument Identification** — Uses a local Ollama model to identify instrument names from OCR text, including transposition detection
- **MusicXML & MIDI Export** — Export recognized scores to MusicXML 4.0 or MIDI for use in notation software (MuseScore, Finale, Sibelius, etc.)
- **Interactive Editor** — Fix recognition errors with a visual editor; add, remove, or modify detected symbols
- **Multi-page Scores** — Handle orchestral scores with hundreds of pages
- **Smooth UI** — Pixel-precise trackpad scrolling, Cmd+scroll zoom, optimized rendering with clip-rect culling
- **Cross-platform** — macOS, Windows, Linux

## Quick Start

### Prerequisites

- **Java 21+** (JDK)
- **Gradle** (included via wrapper)
- **Python 3.11** + RapidOCR (optional, for enhanced text recognition)

### Build & Run

```bash
git clone https://github.com/YourUsername/scanscore.git
cd scanscore
./gradlew :app:run
```

### Set Up RapidOCR (Optional)

```bash
python3.11 -m venv ocr-env
source ocr-env/bin/activate
pip install rapidocr-onnxruntime
```

The app will automatically detect and use RapidOCR if the virtualenv is present. Falls back to Tesseract otherwise.

### Set Up LLM Instrument ID (Optional)

```bash
# Install Ollama (https://ollama.com)
ollama pull qwen2.5:7b
```

When running, the app connects to `localhost:11434` to identify instrument names from OCR text.

## Usage

1. **Open** a score image (PNG, JPG, TIFF) or PDF via `File > Input`
2. **Transcribe** — the OMR engine runs through detection steps automatically
3. **Edit** — fix any recognition errors in the interactive editor
4. **Export** — `File > Export` to MusicXML or MIDI

### Keyboard Shortcuts

| Action | Shortcut |
|--------|----------|
| Zoom in/out | `Cmd + scroll` or `Ctrl + +/-` |
| Scroll | Two-finger trackpad scroll |
| Fit page | `Cmd + F` |

## Architecture

```
app/src/main/java/org/audiveris/omr/
├── score/          # MusicXML export, instrument LLM, score reduction
├── sheet/          # Sheet processing pipeline (20 steps: LOAD → PAGE)
├── sig/            # Symbol Interpretation Graph (SIG) — detected elements
├── text/           # OCR layer (RapidOCR / Tesseract)
│   └── rapidocr/   # RapidOCR engine (ONNX runtime)
├── ui/             # Swing UI, smooth scrolling, rendering
└── step/           # Processing step definitions
```

### Processing Pipeline

```
LOAD → BINARY → SCALE → GRID → HEADERS → STEM_SEEDS → BEAMS →
LEDGERS → HEADS → STEMS → REDUCTION → CUE_BEAMS → TEXTS →
MEASURES → CHORDS → CURVES → SYMBOLS → LINKS → RHYTHMS → PAGE
```

## Credits

- **[Audiveris](https://github.com/Audiveris/audiveris)** — The original OMR engine by Herve Bitteur (AGPL-3.0)
- **[RapidOCR](https://github.com/RapidAI/RapidOCR)** — ONNX-based OCR (Apache 2.0)
- **[FlatLaf](https://github.com/JFormDesigner/FlatLaf)** — Modern Swing look & feel (Apache 2.0)
- **[Apache NetBeans](https://github.com/apache/netbeans)** — Smooth scroll implementation reference (Apache 2.0)
- **[Ollama](https://ollama.com)** — Local LLM runtime
- Smooth scrolling adapted from [Pavel Fatin — "Scrolling with Pleasure"](https://pavelfatin.com/scrolling-with-pleasure/)

## License

This project is licensed under the **GNU Affero General Public License v3.0** — see [LICENSE](LICENSE) for details.

ScanScore is a fork of [Audiveris](https://github.com/Audiveris/audiveris), which is also AGPL-3.0 licensed.
