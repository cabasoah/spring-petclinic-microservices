package org.springframework.samples.petclinic.api.boundary.web;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = AsyncVisitCommandController.class)
class AsyncVisitCommandControllerTest {

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private WebTestClient client;

    @Test
    void addVisit_returnsAcceptedAndPublishesEvent() {
        client.post()
            .uri("/api/visit/owners/1/pets/2/visits")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "date": "2025-01-01",
                  "description": "test visit"
                }
                """)
            .exchange()
            .expectStatus().isAccepted();

        Mockito.verify(rabbitTemplate).convertAndSend(
            Mockito.eq("petclinic.events"),
            Mockito.eq("VisitCreatedEvent"),
            Mockito.<Object>argThat(payload ->
                payload instanceof java.util.Map<?, ?> event
                    && event.get("ownerId").equals(1)
                    && event.get("petId").equals(2)
                    && event.get("visitDate").equals("2025-01-01")
                    && event.get("description").equals("test visit")
            )
        );
    }
}
