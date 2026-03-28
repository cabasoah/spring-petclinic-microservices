package org.springframework.samples.petclinic.visits.web;

import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.samples.petclinic.visits.config.RabbitVisitConfig;
import org.springframework.samples.petclinic.visits.model.VisitRepository;
import org.springframework.stereotype.Component;

@Component
public class VisitCompensationListener {

    private final VisitRepository visitRepository;

    public VisitCompensationListener(VisitRepository visitRepository) {
        this.visitRepository = visitRepository;
    }

    @RabbitListener(queues = RabbitVisitConfig.VISIT_FAILED_QUEUE)
    public void handleVisitFailed(Map<String, Object> event) {
        Integer visitId = Integer.parseInt(event.get("visitId").toString());
        visitRepository.findById(visitId).ifPresent(visit -> {
            visit.setStatus("CANCELLED");
            visitRepository.save(visit);
        });
    }
}
