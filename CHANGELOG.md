# Changelog

## [1.0.0] — 2026-03-27

### Added
- **RapidOCR integration** — Lightweight ONNX-based OCR engine (~300 MB RAM) replacing PaddleOCR (11 GB). Supports Chinese + English text recognition out of the box.
- **LLM instrument identification** — Local Ollama model (qwen2.5:7b) identifies instrument names from OCR text, including transposition key detection (e.g., Clarinet in Bb, Horn in F).
- **Instrument dictionary** — Comprehensive mapping of instrument names across Chinese, Italian, German, French, and English.
- **Smooth trackpad scrolling** — Pixel-precise scroll handling using `getPreciseWheelRotation()`, adapted from FlatLaf and NetBeans (Apache 2.0).
- **Cmd+scroll zoom** — macOS-native zoom gesture (Meta + two-finger scroll).
- **Modern toolbar icons** — Programmatically rendered Java2D icons, no external image files needed.
- **Simplified MusicXML exporter** — Alternative export path for cleaner output.

### Changed
- **Rebranded** from Audiveris to ScanScore.
- **OCR engine selection** — Automatically picks RapidOCR when available, falls back to Tesseract.
- **Scroll unit increment** — Increased from 20px to 64px for snappier scrolling.
- **CenteredViewport** — Only re-centres on viewport resize, not on every scroll event (fixes snap-back-to-top bug).
- **Dirty-region repaints** — Rubber band operations repaint only the affected area instead of the entire sheet.
- **Clip-rect section culling** — Section rendering skips off-screen elements.
- **Deferred repaint on ConcurrentModificationException** — Prevents repaint loops via `SwingUtilities.invokeLater()`.

### Fixed
- Scroll stutter caused by manual event forwarding loop in `Rubber.mouseWheelMoved`.
- Scroll snapping back to top caused by `CenteredViewport.layoutContainer` re-centering on every layout pass.
- Pinch-to-zoom failure on Java 21+ (com.apple.eawt.event blocked by module system).
- UI lag from full-component repaints on every mouse click/drag.

### Based On
- [Audiveris 5.10.1](https://github.com/Audiveris/audiveris) (AGPL-3.0)
