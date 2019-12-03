package com.codefork.refine.wikifier;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wikifier configuration
 */
@Configuration
@ConfigurationProperties(prefix = "datasource.wikifier")
public class WikifierConfig {
    private String apiToken;

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }
}
