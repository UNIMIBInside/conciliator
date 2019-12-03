package com.codefork.refine.wikidata;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wikidata configuration
 */
@Configuration
@ConfigurationProperties(prefix = "datasource.wikidata")
public class WikidataConfig {

}
