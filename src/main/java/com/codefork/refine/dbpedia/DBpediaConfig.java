package com.codefork.refine.dbpedia;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * DBpedia configuration
 */
@Configuration
@ConfigurationProperties(prefix = "datasource.dbpedia")
public class DBpediaConfig {

}
