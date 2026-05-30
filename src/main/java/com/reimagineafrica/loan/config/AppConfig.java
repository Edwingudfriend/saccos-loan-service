package com.reimagineafrica.loan.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfig {

    // ── WebClient: Fineract ──────────────────────────────────────────

    @Bean
    public WebClient fineractWebClient(
            @Value("${fineract.base-url}") String baseUrl,
            @Value("${fineract.username}") String username,
            @Value("${fineract.password}") String password,
            @Value("${fineract.tenant-id:default}") String tenantId) {

        String credentials = java.util.Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes());

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Basic " + credentials)
                .defaultHeader("Fineract-Platform-TenantId", tenantId)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // ── WebClient: Member Service ────────────────────────────────────

    @Bean
    public WebClient memberWebClient(
            @Value("${services.member-service.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // ── RabbitMQ ─────────────────────────────────────────────────────

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    public TopicExchange loanEventsExchange() {
        return new TopicExchange("saccos.loan.events", true, false);
    }

    // Notification service listens to these
    @Bean
    public Queue loanNotificationQueue() {
        return QueueBuilder.durable("saccos.loan.notifications").build();
    }

    @Bean
    public Binding loanNotificationBinding() {
        return BindingBuilder.bind(loanNotificationQueue())
                .to(loanEventsExchange())
                .with("loan.#");
    }
}
