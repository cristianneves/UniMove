package com.unimove.domain.verification;

import com.unimove.domain.verification.channel.LogOnlyChannel;
import com.unimove.domain.verification.channel.PhoneVerificationChannel;
import com.unimove.domain.verification.channel.WhatsAppLinkChannel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seleciona o canal por configuracao — ligar o WhatsApp real e trocar
 * variavel de ambiente, sem tocar em codigo.
 */
@Configuration
@EnableConfigurationProperties({PhoneVerificationProperties.class, WhatsAppProperties.class})
class VerificationConfig {

    @Bean
    @ConditionalOnProperty(name = "app.phone-verification.channel", havingValue = "WHATSAPP")
    PhoneVerificationChannel whatsAppLinkChannel(WhatsAppProperties props) {
        return new WhatsAppLinkChannel(props);
    }

    @Bean
    @ConditionalOnProperty(name = "app.phone-verification.channel", havingValue = "LOG", matchIfMissing = true)
    PhoneVerificationChannel logOnlyChannel() {
        return new LogOnlyChannel();
    }
}
