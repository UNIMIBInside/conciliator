package com.codefork.refine.solr;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AnotherSolr configuration
 */
@Configuration
@ConfigurationProperties(prefix = "datasource.anothersolr")
public class AnotherSolrConfig extends SolrConfig {

}
