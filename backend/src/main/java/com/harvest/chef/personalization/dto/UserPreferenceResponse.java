package com.harvest.chef.personalization.dto;

import com.harvest.chef.personalization.entity.UserPreference;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserPreferenceResponse {
    private Long id;
    private String category;
    private String value;
    private double confidence;
    private String source;

    public static UserPreferenceResponse from(UserPreference preference) {
        return UserPreferenceResponse.builder()
                .id(preference.getId())
                .category(preference.getCategory().name())
                .value(preference.getValue())
                .confidence(preference.getConfidence())
                .source(preference.getSource().name())
                .build();
    }
}
