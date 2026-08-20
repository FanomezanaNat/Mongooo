package com.bank.dbs.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Notifies the consuming application's callbackUrl once a chunked upload completes
 * (Appendix B: "Call-back application back-end to notify that document has been
 * loaded by user"). Runs @Async so the chunk-completion HTTP response to the
 * browser is never held up waiting on a downstream callback, and retries with
 * backoff since the receiving application may be transiently unavailable.
 */
@Service
public class CallbackService {

    private static final Logger log = LoggerFactory.getLogger(CallbackService.class);

    private final RestClient restClient;

    public CallbackService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Async
    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public void notifyUploadComplete(String callbackUrl, UUID docId, String status) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return;
        }

        HttpStatus responseStatus = (HttpStatus) restClient.post()
                .uri(callbackUrl)
                .body(new CallbackPayload(docId, status))
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();

        log.info("Callback to {} for docId={} completed with status {}", callbackUrl, docId, responseStatus);
    }

    public record CallbackPayload(UUID docId, String status) {}
}
