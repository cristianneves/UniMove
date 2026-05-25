package com.unimove.domain.chat;

import com.unimove.domain.chat.dto.ChatMessageResponse;
import com.unimove.domain.chat.dto.SendChatMessageRequest;
import com.unimove.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/chat/rides/{id}")
@PreAuthorize("hasAnyRole('PASSAGEIRO','MOTORISTA')")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/messages")
    public List<ChatMessageResponse> history(@AuthenticationPrincipal AuthenticatedUser user,
                                             @PathVariable("id") UUID rideId) {
        return chatService.history(user, rideId);
    }

    @PostMapping("/messages")
    public ChatMessageResponse send(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable("id") UUID rideId,
                                    @Valid @RequestBody SendChatMessageRequest req) {
        return chatService.send(user, rideId, req);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthenticatedUser user,
                             @PathVariable("id") UUID rideId,
                             @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId) {
        return chatService.subscribe(user, rideId, lastEventId);
    }
}
