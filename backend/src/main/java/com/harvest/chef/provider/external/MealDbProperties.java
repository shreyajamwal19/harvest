package com.harvest.chef.provider.external;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "harvest.providers.mealdb")
@Getter
@Setter
public class MealDbProperties {
    private String baseUrl = "https://www.themealdb.com/api/json/v1/1";
    private boolean enabled = true;
}
