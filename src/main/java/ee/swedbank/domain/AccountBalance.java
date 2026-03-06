package ee.swedbank.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "account_balances",
        uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "currency"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
public class AccountBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private SupportedCurrency currency;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AccountBalance(Account account, SupportedCurrency currency, BigDecimal amount) {
        this.account = account;
        this.currency = currency;
        this.amount = amount;
    }

    /** The only field that changes after creation. */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
