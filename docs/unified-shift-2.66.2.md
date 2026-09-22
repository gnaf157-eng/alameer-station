# Unified shift candidate 2.66.2 (154)

One worker opens/resumes a durable shift. Three bottom tabs contain worker reconciliation, cashbox operations and material operations. Explicit review is required in every section, including empty sections. Changes invalidate review of that section. Closed shifts are not automatically replaced; the worker opens the next shift explicitly.

SQLite schema 21 adds shift_workspace, shift_operations and shift_links without rewriting existing amounts. Staged cash and material operations do not change official ledger balances. Closing uses the existing outer transaction for validation, pump handover, worker postings, all extra operations, linked journals and archive status. A failure rolls everything back; retry cannot duplicate a published shift. Each generated ledger/journal row is linked to the shift ID. New published shifts cannot be reopened, unposted or reversed from the ledger UI/API.

Official ledger navigation is read-only; prior-version records remain visible and identified as legacy when no source shift exists. New money-entry paths are in the shift tabs. Cash operations: expenses, customer collections/loans, box-to-box transfers and supplier payments (YER). Materials: cash/credit receipts and cost-valued losses. Cash delivered by the worker is posted to the selected receiving box once, not entered again as an extra operation. Additional operations must not repeat movements in worker reconciliation. Customer/cashbox definitions can be added with zero opening balances.

PDF/Excel share the expanded ReportTable, including shift code, worker readings/movements, automatic worker cash receipt, additional cashbox and material details. Official report generation for new workspaces requires completed posting. Legacy reports retain existing behavior.

Validation: database tests cover staging durability, review gates, exact reconciliation, idempotence, rollback of late supplier failure, shared source links, read-only posting guards, combined reports, transfer and cash-purchase double-entry. UI smoke test opens the three tabs and read-only ledger. CI compares APK package, increasing version code and signing certificate against published 2.66.1 (153), SHA-256 4d096dfd64312ea2ad6d8faecae39e087ed12a902b89a194256497108d6cf17e.

Candidate only: physical-device layout, install-over-existing-data and live sync service end-to-end have not been validated. No public release or update manifest is published by the audit branch.
