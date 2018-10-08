package com.codefork.refine.products;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Products configuration
 */
@Configuration
@ConfigurationProperties(prefix = "datasource.products")
public class ProductsConfig {
    private String sparqlEndpoint;

    public String getSparqlEndpoint() {
        return sparqlEndpoint;
    }

    public void setSparqlEndpoint(String sparqlEndpoint) {
        this.sparqlEndpoint = sparqlEndpoint;
    }
}
