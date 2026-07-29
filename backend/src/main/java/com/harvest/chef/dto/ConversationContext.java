package com.harvest.chef.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Output of the Context Assembly stage. Everything downstream reads from this. */
@Getter
@Builder
public class ConversationContext {
    private Long sessionId;
    private Long userId;
    private String currentMessage;
    private List<ConversationTurn> recentTurns;
}
