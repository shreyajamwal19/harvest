package com.harvest.chef.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRequest {

    /** Null starts a new conversation session. */
    private Long sessionId;

    @NotBlank(message = "Message must not be blank")
    @Size(max = 2000, message = "Message must be 2000 characters or fewer")
    private String message;
}
