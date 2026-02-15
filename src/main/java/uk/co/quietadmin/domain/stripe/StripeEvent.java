package uk.co.quietadmin.domain.stripe;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "stripe_event", indexes = {
        @Index(name = "ix_stripe_event_event_id", columnList = "event_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StripeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 255)
    private String eventId;

    @Column(name = "type", nullable = false, length = 255)
    private String type;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;
}