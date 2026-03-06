package ee.swedbank.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.swedbank.api.dto.BalanceResponse;
import ee.swedbank.domain.IdempotencyRecord;
import ee.swedbank.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * If a cached response exists for the key+type, returns it.
     * Otherwise executes {@code operation}, persists the result with audit info, and returns it.
     * When {@code idempotencyKey} is null or blank the operation is always executed (no caching).
     *
     * @param initiatedBy username of the actor who triggered the operation
     * @param note        optional admin comment; ignored for user-initiated operations
     */
    public BalanceResponse executeIdempotent(String idempotencyKey, OperationType operationType,
                                             String initiatedBy, String note,
                                             Supplier<BalanceResponse> operation) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return operation.get();
        }
        return findExisting(idempotencyKey, operationType)
                .orElseGet(() -> {
                    BalanceResponse response = operation.get();
                    save(idempotencyKey, operationType, response, initiatedBy, note);
                    return response;
                });
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private Optional<BalanceResponse> findExisting(String idempotencyKey, OperationType operationType) {
        return repository.findByIdempotencyKeyAndOperationType(idempotencyKey, operationType.name())
                .map(record -> {
                    try {
                        return objectMapper.readValue(record.getResponseBody(), BalanceResponse.class);
                    } catch (JsonProcessingException e) {
                        throw new IdempotencySerializationException(
                                "Failed to deserialize cached response for key '" + idempotencyKey + "'", e);
                    }
                });
    }

    private void save(String idempotencyKey, OperationType operationType,
                      BalanceResponse response, String initiatedBy, String note) {
        try {
            String json = objectMapper.writeValueAsString(response);
            repository.save(new IdempotencyRecord(
                    idempotencyKey, operationType.name(), json, initiatedBy, note));
        } catch (JsonProcessingException e) {
            throw new IdempotencySerializationException(
                    "Failed to serialize response for idempotency key '" + idempotencyKey + "'", e);
        }
    }
}
