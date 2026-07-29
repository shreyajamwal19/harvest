package com.harvest.chef.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** A single prior turn (role + content) fed into the reasoning stages as short-term memory. */
@Getter
@AllArgsConstructor
public class ConversationTurn {
    private final String role;
    private final String content;
}
