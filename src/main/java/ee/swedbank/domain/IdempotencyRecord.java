package ee.swedbank.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "idempotency_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"idempotency_key", "operation_type"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "operation_type", nullable = false, length = 50)
    private String operationType;

    @Lob
    @Column(name = "response_body", nullable = false)
    private String responseBody;

    /**
     * Username of the actor who initiated the operation (USER or ADMIN).
     */
    @Column(name = "initiated_by", nullable = false, length = 100)
    private String initiatedBy;

    /**
     * Optional free-text note added by an admin (e.g. reason for manual adjustment).
     */
    @Column(name = "note", length = 500)
    private String note;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public IdempotencyRecord(String idempotencyKey, String operationType,
                             String responseBody, String initiatedBy, String note) {
        this.idempotencyKey = idempotencyKey;
        this.operationType = operationType;
        this.responseBody = responseBody;
        this.initiatedBy = initiatedBy;
        this.note = note;
    }
}
