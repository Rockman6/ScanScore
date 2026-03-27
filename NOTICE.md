# Third-Party Notices

ScanScore incorporates code and techniques from the following open-source projects:

## Audiveris
- **Source:** https://github.com/Audiveris/audiveris
- **License:** GNU Affero General Public License v3.0
- **Copyright:** Herve Bitteur and contributors
- **Usage:** ScanScore is a fork of Audiveris. The core OMR engine, editor, and processing pipeline originate from this project.

## RapidOCR
- **Source:** https://github.com/RapidAI/RapidOCR
- **License:** Apache License 2.0
- **Usage:** OCR engine for text recognition in score images (Chinese + English).

## FlatLaf
- **Source:** https://github.com/JFormDesigner/FlatLaf
- **License:** Apache License 2.0
- **Copyright:** FormDev Software GmbH
- **Usage:** Modern Swing look & feel. Smooth scrolling technique adapted from `FlatScrollPaneUI.java`.

## Apache NetBeans
- **Source:** https://github.com/apache/netbeans
- **License:** Apache License 2.0
- **Copyright:** The Apache Software Foundation
- **Usage:** Smooth scrolling technique adapted from `SmoothScrollPaneUI.java` (originally by Eirik Bakke, based on Pavel Fatin's IntelliJ patch).

## Tesseract OCR (via JavaCPP)
- **Source:** https://github.com/tesseract-ocr/tesseract
- **License:** Apache License 2.0
- **Usage:** Fallback OCR engine when RapidOCR is not available.

## Ollama
- **Source:** https://github.com/ollama/ollama
- **License:** MIT License
- **Usage:** Local LLM runtime for instrument name identification (optional dependency).
