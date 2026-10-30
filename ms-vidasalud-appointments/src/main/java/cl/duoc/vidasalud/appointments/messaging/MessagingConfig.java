package cl.duoc.vidasalud.appointments.messaging;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppointmentsMessagingProperties.class)
public class MessagingConfig {

    @Bean
    Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    DirectExchange directExchange(AppointmentsMessagingProperties properties) {
        return new DirectExchange(properties.directExchange(), true, false);
    }

    @Bean
    TopicExchange topicExchange(AppointmentsMessagingProperties properties) {
        return new TopicExchange(properties.topicExchange(), true, false);
    }
}
