/* Controlador REST para gerenciar o ciclo de vida dos snapshots de backup, incluindo início, conclusão, falha, listagem e obtenção do manifesto. */
package com.keeply.backend.controller;

import com.keeply.backend.dto.SnapshotDtos;
import com.keeply.backend.service.SnapshotService;
import com.keeply.backend.util.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/snapshots")
public class SnapshotController {
    private final SnapshotService snapshots;

    public SnapshotController(SnapshotService snapshots) {
        this.snapshots = snapshots;
    }

    @PostMapping("/start")
    public SnapshotDtos.SnapshotResponse start(@RequestBody SnapshotDtos.StartSnapshotRequest request) {
        return snapshots.start(CurrentUser.get().userId(), request);
    }

    @PostMapping("/{snapshotId}/complete")
    public SnapshotDtos.SnapshotResponse complete(
            @PathVariable UUID snapshotId,
            @RequestBody SnapshotDtos.CompleteSnapshotRequest request
    ) {
        return snapshots.complete(CurrentUser.get().userId(), snapshotId, request);
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

    @GetMapping(value = "/{snapshotId}/manifest", produces = "application/json")
    public String manifest(@PathVariable UUID snapshotId) {
        return snapshots.manifest(CurrentUser.get().userId(), snapshotId);
    }
}
