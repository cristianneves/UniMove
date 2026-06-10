package com.unimove.domain.user;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(LoginLockoutProperties.class)
class UserConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
