/* Controlador REST para gerenciar o ciclo de vida dos snapshots de backup, incluindo início, conclusão, falha, listagem e obtenção do manifesto. */
package com.keeply.backend.controller;

import com.keeply.backend.dto.SnapshotDtos;
import com.keeply.backend.service.ManifestReaderService;
import com.keeply.backend.service.SnapshotService;
import com.keeply.backend.util.CurrentUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import jakarta.servlet.http.HttpServletRequest;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/snapshots")
public class SnapshotController {
    private final SnapshotService snapshots;
    private final ManifestReaderService manifestReader;

    public SnapshotController(SnapshotService snapshots, ManifestReaderService manifestReader) {
        this.snapshots = snapshots;
        this.manifestReader = manifestReader;
    }

    @PostMapping("/start")
    public SnapshotDtos.SnapshotResponse start(@RequestBody SnapshotDtos.StartSnapshotRequest request) {
        return snapshots.start(CurrentUser.get().userId(), request);
    }

    @PostMapping(value = "/{snapshotId}/complete", consumes = "application/gzip")
    public SnapshotDtos.SnapshotResponse complete(
            @PathVariable UUID snapshotId,
            @RequestHeader("X-Keeply-Total-Files") long totalFiles,
            @RequestHeader("X-Keeply-Total-Original-Size") long totalOriginalSize,
            @RequestHeader("X-Keeply-Total-Compressed-Size") long totalCompressedSize,
            HttpServletRequest request
    ) throws java.io.IOException {
        return snapshots.complete(CurrentUser.get().userId(), snapshotId, request.getInputStream(),
                request.getContentLengthLong(), totalFiles, totalOriginalSize, totalCompressedSize);
    }

    @PostMapping("/{snapshotId}/fail")
    public SnapshotDtos.SnapshotResponse fail(
            @PathVariable UUID snapshotId,
            @RequestBody SnapshotDtos.FailSnapshotRequest request
    ) {
        return snapshots.fail(CurrentUser.get().userId(), snapshotId, request);
    }

    @GetMapping
    public List<SnapshotDtos.SnapshotResponse> list() {
        return snapshots.list(CurrentUser.get().userId());
    }

    @GetMapping("/{snapshotId}/files")
    public SnapshotDtos.SnapshotFileListResponse listFiles(
            @PathVariable UUID snapshotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search
    ) {
        // Limite de segurança para o tamanho da página
        int pageSize = Math.min(size, 200);
        return manifestReader.listFiles(CurrentUser.get().userId(), snapshotId, page, pageSize, search);
    }

    @GetMapping(value = "/{snapshotId}/manifest", produces = "application/json")
    public ResponseEntity<StreamingResponseBody> manifest(@PathVariable UUID snapshotId) {
        InputStream stream = snapshots.manifest(CurrentUser.get().userId(), snapshotId);
        StreamingResponseBody body = output -> {
            try (stream) {
                stream.transferTo(output);
            }
        };
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
