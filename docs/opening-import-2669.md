# Opening import and capital checks

Version 2.66.9, database schema 26. No private production data is stored in this repository, the APK, CI fixtures, or release assets.

The settings backup section accepts a private `alameer-opening-v1` JSON file. The user reviews the date, totals and replacement scope, then authenticates with the manager PIN. A validated SQLite safety snapshot is retained before any import. The import replaces financial test records in one transaction, preserves device settings and authentication, and refuses repeat imports. The original file content and its hash are retained privately in the database. Safety restoration is available from the same settings section.

Opening records preserve native cash currencies, signed customer balances, independent company openings, station fuel cost and pump readings. Externally owned stock retains its own cost and location; company custody stock is non-owned and unavailable for sale. Worker openings appear separately under expenses and are excluded from capital and new expenses. An opening gas profit adjustment is an equity split, not additional cash or new shift profit.

An independent capital check compares actual book assets and liabilities with prior actual closing capital plus gross sales profit minus operating expenses. Weighted carrying costs preserve historic stock value across purchase-price changes; sale costs are snapshotted and posted separately to the journal. Gas participates in profit. Draft previews are provisional, and final checks read the posted ledgers before the enclosing transaction commits. Dates cannot precede opening or the latest closing.

A discrepancy at two-decimal currency precision aborts every posting. A manager may approve a discrepancy with a mandatory reason, preserving the unmatched amount in capital history and shift reports. The next baseline is actual closing capital; no balancing entry hides the discrepancy.

Validation uses invented data only: signed currency openings, ledger and stock totals, zero historical expenses, rollback, duplicate prevention, worker exclusion, missing cash posting, manager override, subsequent baseline, changing purchase prices, COGS consistency, native Android SQLite and backup restoration compatibility. Existing accounting and native PDF tests remain required release gates.
