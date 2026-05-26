package com.keeply.backend.controller;

import com.keeply.backend.dto.TransferSessionDtos;
import com.keeply.backend.service.TransferCredentialBroker;
import com.keeply.backend.util.CurrentUser;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/transfer-sessions")
public class TransferSessionController {
    private final TransferCredentialBroker broker;

    public TransferSessionController(TransferCredentialBroker broker) {
        this.broker = broker;
    }

    @PostMapping("/{id}/renew")
    public TransferSessionDtos.Credentials renew(@PathVariable UUID id) {
        return broker.renew(CurrentUser.get(), id);
    }

    @PostMapping("/{id}/cancel")
    public TransferSessionDtos.FinishResponse cancel(@PathVariable UUID id) {
        return broker.cancel(CurrentUser.get(), id);
    }

    @PostMapping("/{id}/finish")
    public TransferSessionDtos.FinishResponse finish(@PathVariable UUID id) {
        return broker.finish(CurrentUser.get(), id);
    }
}
