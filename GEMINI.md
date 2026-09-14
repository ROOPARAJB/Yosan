# Project Memory & Statement Version Specification

## Version: 1.3.0-statement-v1 (Bank Statement Processing Engine)

### Core Architecture & Capabilities
1. **Zero-Dependency PDF Stream & CMap Glyph Decoder**:
   - Standalone PDF text extractor (extractTextFromPdfBytes in StatementImportService.kt) that decompresses streams with java.util.zip.Inflater.
   - Parses ToUnicode CMap hex tables (eginbfrange, eginbfchar) using whitespace-agnostic regex to translate glyph IDs into UTF-8 text without external Android PDFBox dependencies.

2. **Multi-Line Transaction State Consolidation**:
   - Groups multi-line statements (such as Yes Bank PDF and Indian Bank Excel) into unified transaction records.
   - Transaction boundary starts when the previous block has date + amounts sealed and a new date/keyword begins (UPI/, NEFT, IMPS, RTGS, Cr-, OUTUPI).

3. **Narration, UTR & Cheque Reference Extraction**:
   - Preserves complete recipient/sender details, UPI handles, and narration notes.
   - Extracts 12-digit UPI reference numbers and NEFT/Cheque IDs (e.g. IN42621355867589) into eferenceNumber.
   - Discards internal routing tokens (such as ICI...OUT, AXL...INW) while retaining full descriptive remarks.

4. **2-Pass Balance Delta Mathematical Verification**:
   - Calculates \Delta Balance = Balance_i - Balance_{i-1}.
   - If \Delta Balance > 0, classifies definitively as INCOME / Credit.
   - If \Delta Balance < 0, classifies definitively as EXPENSE / Debit.

5. **Reference Benchmarks & Test Suite**:
   - **Yes Bank PDF (Yesbank August.pdf)**: Exactly 43 transactions across 3 pages (Debits: ₹17,341.29, Credits: ₹20,195.00, Opening: ₹0.42, Closing: ₹2,854.13).
   - **Indian Bank Excel (Indian Bank august.xlsx)**: Exactly 151 transactions (Debits: ₹47,469.30, Credits: ₹47,472.07).
   - **Automated Tests**: Maintained in pp/src/test/java/com/example/StatementImportServiceTest.kt.
