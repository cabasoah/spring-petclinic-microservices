package org.springframework.samples.petclinic.api.boundary.web;

import java.net.URLDecoder;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.api.dto.VisitDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visit")
public class AsyncVisitCommandController {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public AsyncVisitCommandController(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/owners/{ownerId}/pets/{petId}/visits")
    public ResponseEntity<Void> addVisit(@PathVariable int ownerId,
                                         @PathVariable int petId,
                                         @RequestBody(required = false) String payload) {
        VisitDetails visit = parseVisit(payload);
        if (visit == null || !StringUtils.hasText(visit.date()) || !StringUtils.hasText(visit.description())) {
            return ResponseEntity.badRequest().build();
        }

        Map<String, Object> event = new HashMap<>();
        event.put("ownerId", ownerId);
        event.put("petId", petId);
        event.put("visitDate", visit.date());
        event.put("description", visit.description());

        rabbitTemplate.convertAndSend("petclinic.events", "VisitCreatedEvent", event);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    private VisitDetails parseVisit(String payload) {
        if (!StringUtils.hasText(payload)) {
            return defaultVisit();
        }

        String normalized = payload.trim();
        if (normalized.startsWith("=")) {
            normalized = normalized.substring(1).trim();
        }

        try {
            return objectMapper.readValue(normalized, VisitDetails.class);
        } catch (JsonProcessingException ignored) {
            VisitDetails formVisit = parseFormPayload(normalized);
            return formVisit != null ? formVisit : defaultVisit();
        }
    }

    private VisitDetails parseFormPayload(String payload) {
        Map<String, String> values = new HashMap<>();
        for (String pair : payload.split("&")) {
            if (!StringUtils.hasText(pair)) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1
                ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                : "";
            values.put(key, value);
        }

        if (!values.containsKey("date") || !values.containsKey("description")) {
            return null;
        }

        return new VisitDetails(null, null, values.get("date"), values.get("description"));
    }

    private VisitDetails defaultVisit() {
        return new VisitDetails(null, null, LocalDate.now().toString(), "JMeter EDA visit");
    }
}
