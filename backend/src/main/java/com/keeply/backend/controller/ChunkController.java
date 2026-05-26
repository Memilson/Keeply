/* Controlador REST responsável pelas operações de chunks (blocos de dados), incluindo verificação de existência, upload e download. */
package com.keeply.backend.controller;

import com.keeply.backend.dto.ChunkDtos;
import com.keeply.backend.service.ChunkService;
import com.keeply.backend.util.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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
}
