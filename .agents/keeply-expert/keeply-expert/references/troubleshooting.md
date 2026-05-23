# Troubleshooting Reference: Common Issues

## Error: "Chunk not found on backend"
- **Cause:** Local SQLite `file_cache` or `known_chunks` is out of sync with the MinIO storage. Usually happens after a server reset without clearing the agent's data.
- **Fix:** 
    1. Run `./debug/reset_env.sh` (clears both sides).
    2. Surgical fix: Call `db.clearCacheForPath(path)` in the agent to force a full re-scan and re-upload.

## Error: "IllegalStateException: Backup falhou"
- **Investigation:** Check `~/keeply/daemon.log`.
- **Common culprit:** IO permissions on a specific file or transient network failure.
- **Memory Check:** Ensure the `Streaming Pipeline` mandates are being followed. If memory spikes, look for `List<ChunkPayload>` or `List<Path>` accumulation.

## Error: UI "Desconectado" after Daemon crash
- **Cause:** Daemon PID lock (`daemon.pid`) might still exist.
- **Fix:** UI attempts to restart it via `DaemonProcessManager`. Manual fix: `pkill -f KeeplyAgentDaemonApp && rm ~/keeply/daemon.pid`.
