package com.unimove.domain.ride;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RideExpirationProperties.class)
class RideConfig {
}
