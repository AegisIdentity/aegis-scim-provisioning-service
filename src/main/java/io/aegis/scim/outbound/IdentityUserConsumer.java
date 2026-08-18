package io.aegis.scim.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes identity user-lifecycle events and turns {@code identity.user.created} into an outbound
 * provisioning task — the flagship business-event choreography (identity directory change drives
 * downstream provisioning), decoupled from the identity service via Kafka.
 *
 * <p><b>Idempotent</b> (Kafka is at-least-once): a task is created only if one does not already exist
 * for the user; a redelivery is a no-op, and a race is caught on the unique constraint. Tolerant JSON
 * parse so a newer producer schema does not break the consumer.
 *
 * <p>Off unless {@code aegis.identity.consumer.enabled=true} + a broker is configured, so the service
 * runs standalone (and unit tests boot) without Kafka.
 */
@Component
public class IdentityUserConsumer {

    private static final Logger log = LoggerFactory.getLogger(IdentityUserConsumer.class);

    private final OutboundProvisioningTaskRepository tasks;
    private final ObjectMapper mapper = new ObjectMapper();

    public IdentityUserConsumer(OutboundProvisioningTaskRepository tasks) {
        this.tasks = tasks;
    }

    @KafkaListener(
            topics = "${aegis.identity.user-topic:aegis.identity.user}",
            groupId = "${aegis.identity.consumer-group:aegis-scim-outbound}",
            autoStartup = "${aegis.identity.consumer.enabled:false}")
    @Transactional
    public void onUserEvent(String json) {
        JsonNode node;
        try {
            node = mapper.readTree(json);
        } catch (Exception e) {
            log.warn("dropping unparseable identity user event");
            return;
        }
        if (!"identity.user.created".equals(text(node, "eventType"))) {
            return; // only creation drives outbound provisioning here
        }
        String userId = text(node, "userId");
        String tenantId = text(node, "tenantId");
        if (userId == null || tenantId == null) {
            return;
        }
        if (tasks.existsByAegisUserId(userId)) {
            return; // already have a task for this user — idempotent
        }
        try {
            tasks.save(new OutboundProvisioningTask(
                    tenantId, userId, text(node, "username"), text(node, "email")));
            log.info("queued outbound provisioning for tenant={} user={}", tenantId, userId);
        } catch (DataIntegrityViolationException race) {
            // Another consumer/redelivery created it first — success, not an error.
            log.debug("outbound task for user {} already exists", userId);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }
}
