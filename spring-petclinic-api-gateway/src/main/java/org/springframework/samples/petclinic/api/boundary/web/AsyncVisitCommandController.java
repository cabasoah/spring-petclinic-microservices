package org.springframework.samples.petclinic.api.boundary.web;

import java.util.HashMap;
import java.util.Map;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.api.dto.VisitDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visit")
public class AsyncVisitCommandController {

    private final RabbitTemplate rabbitTemplate;

    public AsyncVisitCommandController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping("/owners/{ownerId}/pets/{petId}/visits")
    public ResponseEntity<Void> addVisit(@PathVariable int ownerId,
                                         @PathVariable int petId,
                                         @RequestBody VisitDetails visit) {
        Map<String, Object> event = new HashMap<>();
        event.put("ownerId", ownerId);
        event.put("petId", petId);
        event.put("visitDate", visit.date());
        event.put("description", visit.description());

        rabbitTemplate.convertAndSend("petclinic.events", "VisitCreatedEvent", event);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
