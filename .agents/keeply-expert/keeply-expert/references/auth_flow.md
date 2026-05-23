# Authentication Reference: Device JWT Rotation

Keeply implements a secure, automated authentication flow for both the UI and background Daemon.

## Identity Persistence
- **Device Identity:** Stored in `device-auth.json`. This identity is unique per installation and is used to register the device in the backend.
- **Tokens:** The agent stores an `accessToken` (short-lived) and a `refreshToken` (long-lived).

## BackendClient Interceptor
The `BackendClient.java` contains logic to handle token expiration transparently:
1. An HTTP request is sent with the current `accessToken`.
2. If the backend returns `401 Unauthorized`, the client intercepts this.
3. It calls `/api/auth/refresh` using the `refreshToken`.
4. If the refresh succeeds, it updates the local tokens and retries the original request.
5. If the refresh fails (session revoked/expired), it throws an error requiring a new UI login.

## Concurrency
- Parallel uploads in `BackupEngine` can trigger simultaneous 401s.
- The `BackendClient` uses `synchronized` on `refreshSession()` and `volatile` on the session field to prevent redundant/conflicting refresh attempts.
