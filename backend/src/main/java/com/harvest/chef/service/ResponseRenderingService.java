package com.harvest.chef.service;

import com.harvest.chef.dto.ChatResponse;
import com.harvest.chef.dto.ChefResponse;
import org.springframework.stereotype.Service;

/** Stage 7 - Response Rendering. Converts the internal result into the public API DTO. */
@Service
public class ResponseRenderingService {

    public ChatResponse render(Long sessionId, ChefResponse chefResponse) {
        return ChatResponse.builder()
                .sessionId(sessionId)
                .responseType(chefResponse.getType())
                .message(chefResponse.getMessage())
                .recipes(chefResponse.getRecipes())
                .build();
    }
}
