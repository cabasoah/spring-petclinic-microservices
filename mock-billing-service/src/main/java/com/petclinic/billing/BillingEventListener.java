package com.petclinic.billing;

import java.util.HashMap;
import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class BillingEventListener {

    private final RabbitTemplate rabbitTemplate;

    public BillingEventListener(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitBillingConfig.VISIT_CONFIRMED_QUEUE)
    public void handleVisitConfirmed(Map<String, Object> event) {
        Long visitId = Long.parseLong(event.get("visitId").toString());

        if (Math.random() < 0.5) {
            System.out.println("Billing succeeded for visit: " + visitId);
            return;
        }

        System.out.println("Billing FAILED for visit: " + visitId + " - emitting VisitFailedEvent");
        Map<String, Object> failedEvent = new HashMap<>();
        failedEvent.put("visitId", visitId);
        rabbitTemplate.convertAndSend(
            RabbitBillingConfig.PETCLINIC_EVENTS_EXCHANGE,
            "VisitFailedEvent",
            failedEvent
        );
    }
}
