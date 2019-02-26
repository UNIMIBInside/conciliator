package com.codefork.refine.lau;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LAU configuration
 */
@Configuration
@ConfigurationProperties(prefix = "datasource.lau")
public class LAUConfig {

}
