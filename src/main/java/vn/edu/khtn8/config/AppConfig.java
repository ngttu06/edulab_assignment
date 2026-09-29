package vn.edu.khtn8.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import vn.edu.khtn8.common.config.AppProperties;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig { }
