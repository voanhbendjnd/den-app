package com.denhub.service.realtime;

import com.denhub.service.dto.MessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Both REST and STOMP publish this event inside the message's database transaction. */
@Component
public class ChannelMessageDelivery {
    private static final Logger log = LoggerFactory.getLogger(ChannelMessageDelivery.class);
    private final SimpMessagingTemplate messaging;

    public ChannelMessageDelivery(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(Created event) {
        MessageResponse message = event.getMessage();
        try {
            messaging.convertAndSend("/topic/channels/" + message.channelId(), message);
        } catch (RuntimeException ex) {
            // The database has committed. Do not turn a delivery failure into a failed send/retry.
            log.warn("Could not broadcast committed channel message, messageId={}", message.id());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliverUpdate(Updated event) {
        try {
            messaging.convertAndSend("/topic/channels/" + event.message().channelId(), event.message());
        } catch (RuntimeException ex) {
            log.warn("Could not broadcast committed channel update, messageId={}", event.message().id());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliverDelete(Deleted event) {
        try {
            messaging.convertAndSend("/topic/channels/" + event.channelId(),
                    java.util.Map.of("eventType", "DELETED", "id", event.id(), "channelId", event.channelId()));
        } catch (RuntimeException ex) {
            log.warn("Could not broadcast committed channel deletion, messageId={}", event.id());
        }
    }

    public record Updated(MessageResponse message) {}
    public record Deleted(Long id, Long channelId) {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliverFeature(com.denhub.service.ChannelFeaturesService.FeatureChanged event) {
        java.util.Map<String,Object> payload = new java.util.LinkedHashMap<>();
        payload.put("eventType",event.eventType());
        payload.put("channelId",event.channelId());
        try {
            if (event.userId() == null) messaging.convertAndSend("/topic/channels/"+event.channelId(),payload);
            else {
                messaging.convertAndSendToUser(event.userId().toString(),"/queue/channel-features",payload);
                messaging.convertAndSendToUser(event.email(),"/queue/channel-features",payload);
            }
        } catch (RuntimeException ex) {
            log.warn("Could not broadcast committed channel feature change, type={}",event.eventType());
        }
    }

    public static final class Created {
        private final MessageResponse message;

        public Created(MessageResponse message) {
            this.message = message;
        }

        public MessageResponse getMessage() {
            return message;
        }
    }
}
