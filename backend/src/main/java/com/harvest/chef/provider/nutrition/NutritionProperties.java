package com.harvest.chef.provider.nutrition;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "harvest.providers.nutrition")
@Getter
@Setter
public class NutritionProperties {
    private String apiKey;
    private String baseUrl = "https://api.nal.usda.gov/fdc/v1";
}
