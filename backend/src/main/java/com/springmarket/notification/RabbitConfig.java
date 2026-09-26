package com.springmarket.notification;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String NOTIFICATIONS_EXCHANGE = "notifications";
    public static final String ORDER_CONFIRMATION_QUEUE = "order.confirmation";
    public static final String ORDER_CONFIRMATION_ROUTING_KEY = "order.confirmation";
    public static final String DEAD_LETTER_EXCHANGE = "notifications.dlx";
    public static final String ORDER_CONFIRMATION_DEAD_LETTER_QUEUE = "order.confirmation.dlq";
    public static final String ORDER_CONFIRMATION_DEAD_LETTER_ROUTING_KEY =
            "order.confirmation.dlq";

    @Bean
    DirectExchange notificationsExchange() {
        return new DirectExchange(NOTIFICATIONS_EXCHANGE, true, false);
    }

    @Bean
    Queue orderConfirmationQueue() {
        return QueueBuilder.durable(ORDER_CONFIRMATION_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(ORDER_CONFIRMATION_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding orderConfirmationBinding(
            Queue orderConfirmationQueue,
            DirectExchange notificationsExchange) {
        return BindingBuilder.bind(orderConfirmationQueue)
                .to(notificationsExchange)
                .with(ORDER_CONFIRMATION_ROUTING_KEY);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue orderConfirmationDeadLetterQueue() {
        return QueueBuilder.durable(ORDER_CONFIRMATION_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding orderConfirmationDeadLetterBinding(
            Queue orderConfirmationDeadLetterQueue,
            DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(orderConfirmationDeadLetterQueue)
                .to(deadLetterExchange)
                .with(ORDER_CONFIRMATION_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
