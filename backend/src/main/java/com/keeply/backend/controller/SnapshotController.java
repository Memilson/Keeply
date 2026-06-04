/* Controlador REST para gerenciar o ciclo de vida dos snapshots de backup, incluindo início, conclusão, falha, listagem e obtenção do manifesto. */
package com.keeply.backend.controller;

import com.keeply.backend.dto.SnapshotDtos;
import com.keeply.backend.service.FileDownloadService;
import com.keeply.backend.service.ManifestReaderService;
import com.keeply.backend.service.RateLimitService;
import com.keeply.backend.service.SnapshotService;
import com.keeply.backend.service.TransferCredentialBroker;
import com.keeply.backend.util.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/snapshots")
public class SnapshotController {
    private final SnapshotService snapshots;
    private final ManifestReaderService manifestReader;
    private final TransferCredentialBroker transferBroker;
    private final FileDownloadService fileDownload;
    private final RateLimitService rateLimit;

    public SnapshotController(
            SnapshotService snapshots,
            ManifestReaderService manifestReader,
            TransferCredentialBroker transferBroker,
            FileDownloadService fileDownload,
            RateLimitService rateLimit) {
        this.snapshots = snapshots;
        this.manifestReader = manifestReader;
        this.transferBroker = transferBroker;
        this.fileDownload = fileDownload;
        this.rateLimit = rateLimit;
    }

    @PostMapping("/start")
    public SnapshotDtos.StartSnapshotResponse start(@Valid @RequestBody SnapshotDtos.StartSnapshotRequest request) {
        return snapshots.start(CurrentUser.get(), request);
    }

    @PostMapping("/{snapshotId}/complete")
    public SnapshotDtos.SnapshotResponse complete(
            @PathVariable UUID snapshotId,
            @Valid @RequestBody SnapshotDtos.CompleteSnapshotRequest request
    ) {
        return snapshots.complete(CurrentUser.get(), snapshotId, request);
    }

    @PostMapping("/{snapshotId}/restore-sessions")
    public com.keeply.backend.dto.TransferSessionDtos.Credentials restoreSession(@PathVariable UUID snapshotId) {
        var principal = CurrentUser.get();
        if (principal.deviceId() == null) {
            throw new IllegalStateException("Token de dispositivo obrigatório para restore");
        }
        snapshots.assertRestorable(principal.userId(), snapshotId);
        return transferBroker.openRestore(principal, principal.deviceId(), snapshotId);
    }

    @PostMapping("/{snapshotId}/fail")
    public SnapshotDtos.SnapshotResponse fail(
            @PathVariable UUID snapshotId,
            @Valid @RequestBody SnapshotDtos.FailSnapshotRequest request
    ) {
        return snapshots.fail(CurrentUser.get().userId(), snapshotId, request);
    }

    @GetMapping
    public SnapshotDtos.PagedSnapshotResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return snapshots.list(CurrentUser.get().userId(), page, Math.min(size, 200));
    }

    @GetMapping("/{snapshotId}")
    public SnapshotDtos.SnapshotResponse get(@PathVariable UUID snapshotId) {
        return snapshots.get(CurrentUser.get().userId(), snapshotId);
    }

    @DeleteMapping("/{snapshotId}")
    public void delete(@PathVariable UUID snapshotId) {
        snapshots.delete(CurrentUser.get().userId(), snapshotId);
    }

    @GetMapping("/{snapshotId}/files/download")
    public void downloadFile(
            @PathVariable UUID snapshotId,
            @RequestParam String path,
            jakarta.servlet.http.HttpServletResponse response
    ) throws java.io.IOException {
        var principal = CurrentUser.get();
        rateLimit.checkAndRecordFileDownloadAttempt(principal.userId().toString());
        fileDownload.streamFile(principal.userId(), snapshotId, path, response);
    }

    @PostMapping("/{snapshotId}/archive-selected")
    public void downloadSelectedArchive(
            @PathVariable UUID snapshotId,
            @Valid @RequestBody SnapshotDtos.SelectedArchiveRequest request,
            jakarta.servlet.http.HttpServletResponse response
    ) throws java.io.IOException {
        var principal = CurrentUser.get();
        rateLimit.checkAndRecordArchiveDownloadAttempt(principal.userId().toString());
        fileDownload.streamSelectedArchive(principal.userId(), snapshotId, request.paths(), response);
    }

    @GetMapping("/{snapshotId}/files")
    public SnapshotDtos.SnapshotFileListResponse listFiles(
            @PathVariable UUID snapshotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String prefix
    ) {
        // Limite de segurança para o tamanho da página
        int pageSize = Math.min(size, 200);
        return manifestReader.listFiles(CurrentUser.get().userId(), snapshotId, page, pageSize, search, prefix);
    }

    @GetMapping("/{snapshotId}/nodes")
    public SnapshotDtos.SnapshotNodeListResponse listNodes(
            @PathVariable UUID snapshotId,
            @RequestParam(required = false) String prefix
    ) {
        return manifestReader.listNodes(CurrentUser.get().userId(), snapshotId, prefix);
    }

}
