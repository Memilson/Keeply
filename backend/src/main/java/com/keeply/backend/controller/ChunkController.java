/* Controlador REST responsável pelas operações de chunks (blocos de dados), incluindo verificação de existência, upload e download. */
package com.keeply.backend.controller;

import com.keeply.backend.dto.ChunkDtos;
import com.keeply.backend.service.ChunkService;
import com.keeply.backend.util.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import jakarta.servlet.http.HttpServletRequest;

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

    @PutMapping(value = "/{hash}", consumes = "application/gzip")
    public ChunkDtos.ChunkUploadResponse upload(
            @PathVariable String hash,
            @RequestHeader("X-Keeply-Original-Size") long originalSize,
            HttpServletRequest request
    ) throws java.io.IOException {
        long compressedSize = request.getContentLengthLong();
        long maxCompressedSize = (4L * 1024 * 1024) + (64L * 1024);
        if (compressedSize <= 0 || compressedSize > maxCompressedSize) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Tamanho comprimido inválido");
        }
        return chunks.upload(CurrentUser.get().userId(), hash, originalSize, compressedSize, request.getInputStream());
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
