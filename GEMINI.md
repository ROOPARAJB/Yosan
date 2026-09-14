# Project Memory & Statement Version Specification

## Version: 1.3.1-unified-sets-and-strict-categories

### Core Architecture & Capabilities
1. **Zero-Dependency PDF Stream & CMap Glyph Decoder**:
   - Standalone PDF text extractor (`extractTextFromPdfBytes` in `StatementImportService.kt`) that decompresses streams with `java.util.zip.Inflater`.
   - Parses ToUnicode CMap hex tables (`beginbfrange`, `beginbfchar`) using whitespace-agnostic regex to translate glyph IDs into UTF-8 text without external Android PDFBox dependencies.

2. **Multi-Line Transaction State Consolidation**:
   - Groups multi-line statements (such as Yes Bank PDF and Indian Bank Excel) into unified transaction records.
   - Transaction boundary starts when the previous block has date + amounts sealed and a new date/keyword begins (`UPI/`, `NEFT`, `IMPS`, `RTGS`, `Cr-`, `OUTUPI`).

3. **Narration, UTR & Cheque Reference Extraction**:
   - Preserves complete recipient/sender details, UPI handles, and narration notes.
   - Extracts 12-digit UPI reference numbers and NEFT/Cheque IDs (e.g. `IN42621355867589`) into `referenceNumber`.
   - Discards internal routing tokens (such as `ICI...OUT`, `AXL...INW`) while retaining full descriptive remarks.

4. **2-Pass Balance Delta Mathematical Verification**:
   - Calculates $\Delta Balance = Balance_i - Balance_{i-1}$.
   - If $\Delta Balance > 0$, classifies definitively as INCOME / Credit.
   - If $\Delta Balance < 0$, classifies definitively as EXPENSE / Debit.

5. **Strict Transaction-Category Semantic Restrictions**:
   - Category assignment MUST logically match the Transaction Type:
     - `EXPENSE`: Strictly restricted to Expense categories (Food, Travel, Bills, Shopping, EMI, Personal Expense, Official Expense, etc.). CANNOT be categorized as Investment, Income, or Transfer.
     - `INCOME`: Strictly restricted to Income categories (Salary, Advance, Freelance, Dividend, Interest, etc.).
     - `INVESTMENT`: Strictly restricted to Investment categories (Mutual Funds, Stocks, Fixed Deposit, Crypto, Gold, Real Estate, NPS, etc.).
     - `TRANSFER`: Strictly restricted to Transfer / Rotational categories.
     - `LENDING` & `BORROWING`: Strictly linked to loan/debt structures with unique `#LEND-X` and `#BORROW-X` tracking.

6. **Unified Set / Bundle UI Format**:
   - Multi-item tracking models (Advance `#ADV-X`, Lending `#LEND-X`, Borrowing `#BORROW-X`, Investments `#INV-X`) use the identical **Set Card Architecture**:
     - Header: Unique ID Badge + Status Badge (`₹X Left` / `Pending ₹X` / `Fully Settled`).
     - Counterparty / description details.
     - 2-Column metric row (Inflow/Principal vs Outflow/Repaid).
     - Linear progress bar with percentage.
     - Expandable accordion toggle with itemized linked transactions/expenses and individual `[✕]` unlink actions.
     - Direct `[+ Action]` CTA button.

7. **Release Protocol & In-App Changelog Requirement**:
   - Every GitHub Release tag and dispatch must include detailed user-facing release notes detailing "What's New in this Version".
   - The In-App update dialog (`UpdateDialog.kt`) renders these release notes in a dedicated "What's New" section so the user always sees what changed before updating.

8. **Reference Benchmarks & Test Suite**:
   - **Yes Bank PDF (`Yesbank August.pdf`)**: Exactly 43 transactions across 3 pages (Debits: ₹17,341.29, Credits: ₹20,195.00, Opening: ₹0.42, Closing: ₹2,854.13).
   - **Indian Bank Excel (`Indian Bank august.xlsx`)**: Exactly 151 transactions (Debits: ₹47,469.30, Credits: ₹47,472.07).
   - **Automated Tests**: Maintained in `app/src/test/java/com/example/StatementImportServiceTest.kt`.
