package com.petclinic.billing;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitBillingConfig {

    public static final String PETCLINIC_EVENTS_EXCHANGE = "petclinic.events";
    public static final String VISIT_CONFIRMED_QUEUE = "visit.confirmed.queue";
    public static final String VISIT_FAILED_QUEUE = "visit.failed.queue";

    @Bean
    TopicExchange petClinicEventsExchange() {
        return new TopicExchange(PETCLINIC_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    Queue visitConfirmedQueue() {
        return new Queue(VISIT_CONFIRMED_QUEUE, true);
    }

    @Bean
    Queue visitFailedQueue() {
        return new Queue(VISIT_FAILED_QUEUE, true);
    }

    @Bean
    Binding visitConfirmedBinding(Queue visitConfirmedQueue, TopicExchange petClinicEventsExchange) {
        return BindingBuilder.bind(visitConfirmedQueue)
            .to(petClinicEventsExchange)
            .with("VisitConfirmedEvent");
    }

    @Bean
    Binding visitFailedBinding(Queue visitFailedQueue, TopicExchange petClinicEventsExchange) {
        return BindingBuilder.bind(visitFailedQueue)
            .to(petClinicEventsExchange)
            .with("VisitFailedEvent");
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }
}
