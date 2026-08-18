package io.aegis.scim.outbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/**
 * The outbound-provisioning consumer: an {@code identity.user.created} event queues a provisioning
 * task; everything else and anything malformed is ignored; a redelivery for a user that already has
 * a task is a no-op (idempotent over Kafka's at-least-once delivery).
 */
class IdentityUserConsumerTest {

    private final OutboundProvisioningTaskRepository tasks = mock(OutboundProvisioningTaskRepository.class);
    private final IdentityUserConsumer consumer = new IdentityUserConsumer(tasks);

    @Test
    void a_user_created_event_queues_an_outbound_task() {
        when(tasks.existsByAegisUserId("u-1")).thenReturn(false);

        consumer.onUserEvent("{\"eventType\":\"identity.user.created\",\"tenantId\":\"acme\","
                + "\"userId\":\"u-1\",\"username\":\"alice\",\"email\":\"alice@acme.test\"}");

        verify(tasks).save(any(OutboundProvisioningTask.class));
    }

    @Test
    void a_redelivered_event_for_an_existing_user_creates_no_duplicate() {
        when(tasks.existsByAegisUserId("u-1")).thenReturn(true);

        consumer.onUserEvent("{\"eventType\":\"identity.user.created\",\"tenantId\":\"acme\","
                + "\"userId\":\"u-1\",\"username\":\"alice\"}");

        verify(tasks, never()).save(any());
    }

    @Test
    void a_non_creation_event_queues_nothing() {
        consumer.onUserEvent("{\"eventType\":\"identity.user.deactivated\",\"userId\":\"u-1\","
                + "\"tenantId\":\"acme\"}");

        verify(tasks, never()).save(any());
    }

    @Test
    void an_event_missing_ids_queues_nothing() {
        consumer.onUserEvent("{\"eventType\":\"identity.user.created\"}");

        verify(tasks, never()).save(any());
    }

    @Test
    void a_malformed_message_is_dropped_not_thrown() {
        consumer.onUserEvent("{not json");

        verify(tasks, never()).save(any());
    }
}
