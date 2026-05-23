package com.keeply.backend.controller;

import com.keeply.backend.dto.ChunkDtos;
import com.keeply.backend.service.ChunkService;
import com.keeply.backend.util.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chunks")
public class ChunkController {
    private final ChunkService chunks;

    public ChunkController(ChunkService chunks) {
        this.chunks = chunks;
    }

    @PostMapping("/check")
    public ChunkDtos.CheckChunksResponse check(@RequestBody ChunkDtos.CheckChunksRequest request) {
        return chunks.check(CurrentUser.get().userId(), request.hashes());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChunkDtos.ChunkUploadResponse upload(
            @RequestParam String hash,
            @RequestParam long originalSize,
            @RequestParam long compressedSize,
            @RequestPart("file") MultipartFile file
    ) throws Exception {
        boolean stored = chunks.upload(
                CurrentUser.get().userId(),
                hash,
                originalSize,
                compressedSize,
                file.getBytes()
        );
        return new ChunkDtos.ChunkUploadResponse(hash, stored);
    }

    @GetMapping(value = "/{hash}/download", produces = "application/gzip")
    public byte[] download(@PathVariable String hash) {
        return chunks.download(CurrentUser.get().userId(), hash);
    }
}
