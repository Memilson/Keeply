/* Controlador REST responsável pelas operações de chunks (blocos de dados), incluindo verificação de existência, upload e download. */
package com.keeply.backend.controller;

import com.keeply.backend.dto.ChunkDtos;
import com.keeply.backend.service.ChunkService;
import com.keeply.backend.util.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;

@RestController
@RequestMapping("/api/chunks")
public class ChunkController {
    private final ChunkService chunks;

    public ChunkController(ChunkService chunks) {
        this.chunks = chunks;
    }

    @PostMapping("/check")
    public ChunkDtos.CheckChunksResponse check(@Valid @RequestBody ChunkDtos.CheckChunksRequest request) {
        return chunks.check(CurrentUser.get().userId(), request.hashes());
    }

    @PostMapping("/upload-batch")
    public ChunkDtos.ChunkUploadBatchResponse uploadBatch(@Valid @RequestBody ChunkDtos.ChunkUploadBatchRequest request) {
        return chunks.uploadBatch(CurrentUser.get().userId(), request.items());
    }

    @GetMapping(value = "/{hash}/download", produces = "application/gzip")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String hash) {
        InputStream stream = chunks.downloadStream(CurrentUser.get().userId(), hash);
        StreamingResponseBody responseBody = outputStream -> {
            try (stream) {
                stream.transferTo(outputStream);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/gzip"))
                .body(responseBody);
    }
}
