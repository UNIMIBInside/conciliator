package com.codefork.refine.keywords;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * semantickeywordcategorymatcher configuration
 */
@Configuration
@ConfigurationProperties(prefix = "datasource.semantickeywordcategorymatcher")

public class SemanticKeywordCategoryMatcherConfig {
    private String host;
    private int port;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

}
