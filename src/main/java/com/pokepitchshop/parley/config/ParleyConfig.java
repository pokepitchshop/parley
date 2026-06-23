package com.pokepitchshop.parley.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ ParleyProperties.class, TwilioProperties.class })
public class ParleyConfig {
}
