package com.unimove.domain.maps;

import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({OsrmProperties.class, PhotonProperties.class})
class MapsConfig {

    @Bean
    WebClient osrmWebClient(OsrmProperties props) {
        return buildClient(props.baseUrl());
    }

    @Bean
    WebClient photonWebClient(PhotonProperties props) {
        return buildClient(props.baseUrl());
    }

    private static WebClient buildClient(String baseUrl) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3_000)
                .responseTimeout(Duration.ofSeconds(5));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
