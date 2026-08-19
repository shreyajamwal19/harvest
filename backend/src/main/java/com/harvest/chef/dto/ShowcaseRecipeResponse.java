package com.harvest.chef.dto;

import lombok.Builder;
import lombok.Getter;

/** A single real dish (title/photo) surfaced on the public, unauthenticated homepage. */
@Getter
@Builder
public class ShowcaseRecipeResponse {
    private String title;
    private String description;
    private String imageUrl;
    private String source;
}
