package org.springframework.samples.petclinic.visits.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.samples.petclinic.visits.config.RabbitVisitConfig;
import org.springframework.samples.petclinic.visits.model.Visit;
import org.springframework.samples.petclinic.visits.model.VisitRepository;
import org.springframework.stereotype.Component;

@Component
public class VisitEventListener {

    private final VisitRepository visitRepository;
    private final RabbitTemplate rabbitTemplate;

    public VisitEventListener(VisitRepository visitRepository, RabbitTemplate rabbitTemplate) {
        this.visitRepository = visitRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitVisitConfig.VISIT_CREATED_QUEUE)
    public void handleVisitCreated(Map<String, Object> event) {
        Object petIdValue = event.get("petId");
        Object visitDateValue = event.getOrDefault("visitDate", event.get("date"));
        Object descriptionValue = event.get("description");

        if (petIdValue == null || visitDateValue == null || descriptionValue == null) {
            return;
        }

        int petId = Integer.parseInt(petIdValue.toString());
        LocalDate visitDate = LocalDate.parse(visitDateValue.toString());
        String description = descriptionValue.toString();

        Visit visit = new Visit();
        visit.setPetId(petId);
        visit.setDate(Date.from(visitDate.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        visit.setDescription(description);
        visit.setStatus("ACTIVE");

        Visit savedVisit = visitRepository.save(visit);

        Map<String, Object> confirmedEvent = new HashMap<>();
        confirmedEvent.put("visitId", savedVisit.getId());
        confirmedEvent.put("createdAt", Instant.now().toString());
        rabbitTemplate.convertAndSend(
            RabbitVisitConfig.PETCLINIC_EVENTS_EXCHANGE,
            "VisitConfirmedEvent",
            confirmedEvent
        );
    }
}
