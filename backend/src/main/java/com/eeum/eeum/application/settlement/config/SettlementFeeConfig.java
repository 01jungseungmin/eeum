package com.eeum.eeum.application.settlement.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SettlementFeeProperties.class)
public class SettlementFeeConfig {
}
