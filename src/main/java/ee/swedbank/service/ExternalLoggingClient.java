package ee.swedbank.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalLoggingClient {

    private final RestClient restClient;

    @Value("${external.logging.url}")
    private String loggingUrl;

    public void logWithdrawal() {
        try {
            restClient.get()
                    .uri(loggingUrl)
                    .retrieve()
                    .toBodilessEntity();
            log.info("External logging call succeeded for URL: {}", loggingUrl);
        } catch (Exception ex) {
            log.error("External logging call failed for URL: {}", loggingUrl, ex);
            throw new ExternalLoggingFailedException("External logging call failed", ex);
        }
    }
}
