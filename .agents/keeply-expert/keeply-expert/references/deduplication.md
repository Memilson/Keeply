# Deduplication Reference: CDC & Rolling Hash

Keeply uses **Content-Defined Chunking (CDC)** to achieve superior deduplication compared to fixed-size chunking.

## ContentDefinedChunker.java

The algorithm is based on a rolling hash that determines "cut points" based on the byte content.

### Parameters
- **MIN_SIZE:** 512 KB (Minimum chunk size to avoid tiny chunks).
- **AVG_SIZE:** 1 MB (Target average size).
- **MAX_SIZE:** 4 MB (Safety limit to prevent memory spikes).
- **CUT_MASK:** `AVG_SIZE - 1` (Used to trigger a cut when `(rollingHash & mask) == 0`).

### Advantages
- **Resistance to Shifts:** If a byte is inserted at the beginning of a file, only the first chunk changes. Fixed-size chunking would change *every* chunk after the insertion.
- **Efficiency:** Significant storage savings for large log files, database dumps, and frequently edited documents.

### Integrity Rules
- Do **NOT** change the rolling hash logic or the `CUT_MASK` unless you intend to force a global re-upload of all data.
- Always calculate the whole-file SHA-256 hash *during* the chunking pass to save I/O.
