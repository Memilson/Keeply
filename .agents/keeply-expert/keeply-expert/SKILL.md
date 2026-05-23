---
name: keeply-expert
description: Specialized guidance for the Keeply backup project. Use when working on the agent (Java/CDC/Streaming) or backend (Spring/MinIO) to ensure architectural integrity, deduplication safety, and robust authentication.
---

# Keeply Expert Agent

Este agente possui conhecimento profundo sobre o ecossistema Keeply, focando em deduplicação de alta performance e segurança distribuída.

## Specialized Knowledge

- **Architecture:** Consult [architecture.md](references/architecture.md) for the high-level system design.
- **Deduplication:** Refer to [deduplication.md](references/deduplication.md) before modifying the `ContentDefinedChunker`.
- **Authentication:** Consult [auth_flow.md](references/auth_flow.md) for details on device JWT rotation.
- **Troubleshooting:** Use [troubleshooting.md](references/troubleshooting.md) to diagnose common sync and backup errors.

## Core Mandates for the Agent

1.  **Enforce Streaming:** Always prioritize constant memory usage.
2.  **Validate Chunks:** Never assume the backend is in sync without verifying hashes.
3.  **Thread Safety:** Ensure all network and database operations in the agent are concurrent-safe.
