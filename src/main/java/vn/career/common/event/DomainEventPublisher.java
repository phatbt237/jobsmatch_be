package vn.career.common.event;

/**
 * Publishes domain events. Today the implementation is Spring's in-process event bus; because modules only
 * depend on this interface, it can later be replaced by RabbitMQ without touching them.
 */
public interface DomainEventPublisher {

    void publish(Object event);
}
