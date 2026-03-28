package org.springframework.samples.petclinic.visits.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitVisitConfig {

    public static final String PETCLINIC_EVENTS_EXCHANGE = "petclinic.events";
    public static final String VISIT_CREATED_QUEUE = "visit.created.queue";
    public static final String VISIT_CONFIRMED_QUEUE = "visit.confirmed.queue";
    public static final String VISIT_FAILED_QUEUE = "visit.failed.queue";

    @Bean
    TopicExchange petClinicEventsExchange() {
        return new TopicExchange(PETCLINIC_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    Queue visitCreatedQueue() {
        return new Queue(VISIT_CREATED_QUEUE, true);
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
    Declarables visitBindings(Queue visitCreatedQueue,
                              Queue visitConfirmedQueue,
                              Queue visitFailedQueue,
                              TopicExchange petClinicEventsExchange) {
        Binding createdBinding = BindingBuilder.bind(visitCreatedQueue)
            .to(petClinicEventsExchange)
            .with("VisitCreatedEvent");
        Binding confirmedBinding = BindingBuilder.bind(visitConfirmedQueue)
            .to(petClinicEventsExchange)
            .with("VisitConfirmedEvent");
        Binding failedBinding = BindingBuilder.bind(visitFailedQueue)
            .to(petClinicEventsExchange)
            .with("VisitFailedEvent");
        return new Declarables(createdBinding, confirmedBinding, failedBinding);
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
