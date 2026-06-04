package com.keeply.backend.service;

import com.keeply.backend.dto.TransferSessionDtos;
import com.keeply.backend.exception.ForbiddenException;
import com.keeply.backend.model.TransferSession;
import com.keeply.backend.model.TransferSessionStatus;
import com.keeply.backend.model.TransferSessionType;
import com.keeply.backend.repository.TransferSessionRepository;
import com.keeply.backend.security.JwtPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class TransferCredentialBroker {
    private static final Logger log = LoggerFactory.getLogger(TransferCredentialBroker.class);

    private final TransferSessionRepository sessions;
    private final TemporaryCredentialIssuer issuer;
    private final ObjectStorageService storage;
    private final String bucket;
    private final String endpoint;
    private final Duration credentialLifetime;
    private final Duration renewWindow;

    public TransferCredentialBroker(
            TransferSessionRepository sessions,
            TemporaryCredentialIssuer issuer,
            ObjectStorageService storage,
            @Value("${keeply.minio.bucket}") String bucket,
            @Value("${keeply.minio.public-endpoint:${keeply.minio.endpoint}}") String endpoint,
            @Value("${keeply.transfer.credentials.lifetime-minutes:10}") long credentialLifetimeMinutes,
            @Value("${keeply.transfer.credentials.renew-window-minutes:3}") long renewWindowMinutes
    ) {
        this.sessions = sessions;
        this.issuer = issuer;
        this.storage = storage;
        this.bucket = bucket;
        this.endpoint = endpoint;
        this.credentialLifetime = Duration.ofMinutes(credentialLifetimeMinutes);
        this.renewWindow = Duration.ofMinutes(renewWindowMinutes);
    }

    @Transactional
    public TransferSessionDtos.Credentials openBackup(JwtPrincipal principal, UUID deviceId, UUID snapshotId) {
        requireDevice(principal, deviceId);
        TransferSession session = newSessionUnsaved(principal.userId(), deviceId, snapshotId, TransferSessionType.BACKUP_UPLOAD);
        return issue(session);
    }

    @Transactional
    public TransferSessionDtos.Credentials openRestore(JwtPrincipal principal, UUID deviceId, UUID snapshotId) {
        requireDevice(principal, deviceId);
        TransferSession session = newSessionUnsaved(principal.userId(), deviceId, snapshotId, TransferSessionType.RESTORE_READ);
        return issue(session);
    }

    @Transactional
    public TransferSessionDtos.Credentials renew(JwtPrincipal principal, UUID id) {
        TransferSession session = ownedOpen(principal, id);
        revokeCurrent(session);
        return issue(session);
    }

    @Transactional
    public TransferSession processing(JwtPrincipal principal, UUID id, UUID snapshotId) {
        TransferSession session = ownedOpen(principal, id);
        if (session.type != TransferSessionType.BACKUP_UPLOAD || !session.snapshotId.equals(snapshotId)) {
            throw new ForbiddenException("Sessão não pertence ao snapshot de upload");
        }
        revokeCurrent(session);
        session.status = TransferSessionStatus.PROCESSING;
        session.closedReason = "Uploads concluídos; conclusão do snapshot iniciada";
        return sessions.save(session);
    }

    @Transactional
    public TransferSessionDtos.FinishResponse finish(JwtPrincipal principal, UUID id) {
        TransferSession session = ownedOpen(principal, id);
        if (session.type != TransferSessionType.RESTORE_READ) {
            throw new IllegalStateException("Somente sessões de restore podem ser finalizadas");
        }
        close(session, TransferSessionStatus.COMPLETED, "Restore finalizado");
        return new TransferSessionDtos.FinishResponse(id, session.status.name());
    }

    @Transactional
    public TransferSessionDtos.FinishResponse cancel(JwtPrincipal principal, UUID id) {
        TransferSession session = ownedOpen(principal, id);
        close(session, TransferSessionStatus.CANCELLED, "Cancelado pelo agente");
        if (session.stagingPrefix != null) {
            storage.deletePrefix(session.stagingPrefix);
        }
        return new TransferSessionDtos.FinishResponse(id, session.status.name());
    }

    @Transactional
    public void completeProcessing(UUID sessionId, boolean succeeded, String reason) {
        TransferSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sessão não encontrada"));
        close(session, succeeded ? TransferSessionStatus.COMPLETED : TransferSessionStatus.FAILED, reason);
    }

    @Scheduled(fixedDelayString = "${keeply.transfer.expiry-scan-ms:60000}")
    @Transactional
    public void expireOpenSessions() {
        for (TransferSession session : sessions.findByStatusAndExpiresAtBefore(TransferSessionStatus.OPEN, Instant.now())) {
            close(session, TransferSessionStatus.EXPIRED, "Credencial expirada");
            if (session.stagingPrefix != null) {
                storage.deletePrefix(session.stagingPrefix);
            }
            log.info("event=transfer_session.expired session_id={} snapshot_id={}", session.id, session.snapshotId);
        }
    }

    private TransferSession newSessionUnsaved(UUID userId, UUID deviceId, UUID snapshotId, TransferSessionType type) {
        TransferSession session = new TransferSession();
        session.id = UUID.randomUUID();
        session.type = type;
        session.status = TransferSessionStatus.OPEN;
        session.userId = userId;
        session.deviceId = deviceId;
        session.snapshotId = snapshotId;
        return session;
    }

    private TransferSessionDtos.Credentials issue(TransferSession session) {
        Instant now = Instant.now();
        session.expiresAt = now.plus(credentialLifetime);
        session.lastRenewedAt = now;
        TemporaryCredentialIssuer.IssuedCredential credential =
                issuer.issue(policy(session), session.expiresAt);
        session.minioAccessKey = credential.accessKey();
        sessions.save(session);
        log.info("event=transfer_session.issued type={} session_id={} snapshot_id={} expires_at={}",
                session.type, session.id, session.snapshotId, session.expiresAt);
        return new TransferSessionDtos.Credentials(
                session.id, session.type, bucket, endpoint, credential.accessKey(), credential.secretKey(),
                credential.sessionToken(), session.expiresAt, session.expiresAt.minus(renewWindow), session.stagingPrefix
        );
    }

    private String policy(TransferSession session) {
        String bucketArn = "arn:aws:s3:::" + bucket;
        String bucketLocation = "{\"Effect\":\"Allow\",\"Action\":[\"s3:GetBucketLocation\"],\"Resource\":[\"" + bucketArn + "\"]}";
        if (session.type == TransferSessionType.BACKUP_UPLOAD) {
            String manifest = "%s/users/%s/manifests/%s.json.zst".formatted(bucketArn, session.userId, session.snapshotId);
            String chunks = "%s/users/%s/chunks/*".formatted(bucketArn, session.userId);
            return "{\"Version\":\"2012-10-17\",\"Statement\":[" + bucketLocation
                    + ",{\"Effect\":\"Allow\",\"Action\":[\"s3:PutObject\",\"s3:AbortMultipartUpload\",\"s3:ListMultipartUploadParts\"],\"Resource\":[\""
                    + manifest + "\",\"" + chunks + "\"]}]}";
        }
        String manifest = "%s/users/%s/manifests/%s.json.zst".formatted(bucketArn, session.userId, session.snapshotId);
        String chunks = "%s/users/%s/chunks/*".formatted(bucketArn, session.userId);
        return "{\"Version\":\"2012-10-17\",\"Statement\":[" + bucketLocation
                + ",{\"Effect\":\"Allow\",\"Action\":[\"s3:GetObject\"],\"Resource\":[\"" + manifest + "\",\"" + chunks + "\"]}]}";
    }

    private TransferSession ownedOpen(JwtPrincipal principal, UUID id) {
        if (principal.deviceId() == null) {
            throw new ForbiddenException("Token de dispositivo obrigatório");
        }
        TransferSession session = sessions.findByIdAndUserIdAndDeviceId(id, principal.userId(), principal.deviceId())
                .orElseThrow(() -> new ForbiddenException("Sessão não pertence ao dispositivo autenticado"));
        if (session.status != TransferSessionStatus.OPEN || session.expiresAt.isBefore(Instant.now())) {
            throw new IllegalStateException("Sessão de transferência não está aberta");
        }
        return session;
    }

    private void requireDevice(JwtPrincipal principal, UUID deviceId) {
        if (principal.deviceId() == null || !principal.deviceId().equals(deviceId)) {
            throw new ForbiddenException("Token não pertence ao dispositivo solicitado");
        }
    }

    private void revokeCurrent(TransferSession session) {
        if (session.minioAccessKey == null || session.minioAccessKey.isBlank()) {
            return;
        }
        issuer.revoke(session.minioAccessKey);
        session.minioAccessKey = null;
    }

    private void close(TransferSession session, TransferSessionStatus status, String reason) {
        revokeCurrent(session);
        session.status = status;
        session.closedReason = reason;
        sessions.save(session);
    }
}
