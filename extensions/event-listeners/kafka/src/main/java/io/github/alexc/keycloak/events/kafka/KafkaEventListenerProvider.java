package io.github.alexc.keycloak.events.kafka;

import io.github.alexc.keycloak.events.kafka.config.KafkaEventListenerConfig;
import io.github.alexc.keycloak.events.kafka.config.PartitionStrategy;
import io.github.alexc.keycloak.events.kafka.mapper.CloudEventsRecordMapper;
import io.github.alexc.keycloak.events.kafka.producer.KafkaProducerManager;
import io.github.alexc.keycloak.events.kafka.producer.TopicResolver;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

/**
 * Request-scoped Keycloak {@link EventListenerProvider} that processes user audit and administrative
 * events, formats them as CloudEvents 1.0 JSON payloads, and asynchronously dispatches them
 * to Apache Kafka topics with dynamic multi-tenant realm routing.
 */
public class KafkaEventListenerProvider implements EventListenerProvider {

    private static final Logger LOG = Logger.getLogger(KafkaEventListenerProvider.class);

    private final KeycloakSession session;
    private final KafkaProducerManager producerManager;
    private final KafkaEventListenerConfig config;

    public KafkaEventListenerProvider(KeycloakSession session,
                                      KafkaProducerManager producerManager,
                                      KafkaEventListenerConfig config) {
        this.session = session;
        this.producerManager = producerManager;
        this.config = config;
    }

    @Override
    public void onEvent(Event event) {
        if (event == null || !config.shouldDispatchUserEvent(event.getType() != null ? event.getType().name() : "")) {
            return;
        }

        try {
            String realmName = resolveRealmName(event.getRealmId());
            String topic = TopicResolver.resolve(config.eventsTopicPattern(), realmName != null ? realmName : event.getRealmId());
            String messageKey = resolveUserMessageKey(event);
            String payload = CloudEventsRecordMapper.toUserCloudEvent(event, realmName);

            producerManager.sendAsync(topic, messageKey, payload);
        } catch (Exception e) {
            LOG.errorv(e, "Unexpected error dispatching user event {0} to Kafka; transaction uninterrupted", event.getId());
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        if (event == null || !config.shouldDispatchAdminEvent()) {
            return;
        }

        try {
            String realmName = resolveRealmName(event.getRealmId());
            String topic = TopicResolver.resolve(config.adminEventsTopicPattern(), realmName != null ? realmName : event.getRealmId());
            String messageKey = resolveAdminMessageKey(event);
            String payload = CloudEventsRecordMapper.toAdminCloudEvent(event, realmName);

            producerManager.sendAsync(topic, messageKey, payload);
        } catch (Exception e) {
            LOG.errorv(e, "Unexpected error dispatching admin event {0} to Kafka; transaction uninterrupted", event.getId());
        }
    }

    private String resolveUserMessageKey(Event event) {
        PartitionStrategy strategy = config.partitionStrategy();
        if (strategy == PartitionStrategy.NONE) {
            return null;
        }
        if (strategy == PartitionStrategy.REALM) {
            return event.getRealmId();
        }
        // PartitionStrategy.USER
        if (event.getUserId() != null && !event.getUserId().isBlank()) {
            return event.getUserId();
        }
        if (event.getSessionId() != null && !event.getSessionId().isBlank()) {
            return event.getSessionId();
        }
        return event.getRealmId();
    }

    private String resolveAdminMessageKey(AdminEvent event) {
        PartitionStrategy strategy = config.partitionStrategy();
        if (strategy == PartitionStrategy.NONE) {
            return null;
        }
        if (strategy == PartitionStrategy.REALM) {
            return event.getRealmId();
        }
        // PartitionStrategy.USER
        if (event.getAuthDetails() != null && event.getAuthDetails().getUserId() != null) {
            return event.getAuthDetails().getUserId();
        }
        if (event.getResourcePath() != null && !event.getResourcePath().isBlank()) {
            return event.getResourcePath();
        }
        return event.getRealmId();
    }

    private String resolveRealmName(String realmId) {
        if (realmId == null || session == null) {
            return null;
        }
        try {
            RealmModel realm = session.getContext() != null ? session.getContext().getRealm() : null;
            if (realm != null && realmId.equals(realm.getId())) {
                return realm.getName();
            }
            if (session.realms() != null) {
                RealmModel found = session.realms().getRealm(realmId);
                if (found != null) {
                    return found.getName();
                }
            }
        } catch (Exception e) {
            LOG.debugv("Could not resolve realm name for realmId {0}: {1}", realmId, e.getMessage());
        }
        return realmId;
    }

    @Override
    public void close() {
        // Request-scoped resources cleanup (none allocated)
    }
}
