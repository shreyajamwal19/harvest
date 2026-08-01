package com.harvest.chef.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Set;

/** Output of the Context Assembly stage. Everything downstream reads from this. */
@Getter
@Builder
public class ConversationContext {
    private Long sessionId;
    private Long userId;
    private String currentMessage;
    private List<ConversationTurn> recentTurns;
    /** The search query behind the session's last recipe request - reused on "more" turns. */
    private String lastSearchQuery;
    /** The ingredients behind the session's last recipe request - reused on "more" turns. */
    private List<String> lastMentionedIngredients;
    /** Normalized titles already shown to the user this session, so "more" doesn't repeat them. */
    private Set<String> shownRecipeTitles;
}
