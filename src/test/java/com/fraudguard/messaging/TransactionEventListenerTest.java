package com.fraudguard.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import tools.jackson.databind.json.JsonMapper;
import com.fraudguard.evaluation.EvaluationService;
import com.fraudguard.evaluation.TransactionRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransactionEventListenerTest {

    private final EvaluationService service = mock(EvaluationService.class);
    private final TransactionEventListener listener = new TransactionEventListener(
            JsonMapper.builder().build(),
            Validation.buildDefaultValidatorFactory().getValidator(), service);

    private static final String TX = """
            {"transactionId":"t1","customerId":"c1","amount":150.50,"channel":"PIX",
             "deviceId":"d1","location":"BR-SP","recipientId":"r1","occurredAt":"2026-01-15T15:00:00Z"}""";

    @Test
    void rawMessageIsEvaluatedAsAsync() {
        listener.onMessage(TX);
        var captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(service).evaluate(captor.capture(), eq("async"));
        assertThat(captor.getValue().transactionId()).isEqualTo("t1");
    }

    @Test
    void snsEnvelopeIsUnwrapped() throws Exception {
        String envelope = JsonMapper.builder().build().writeValueAsString(java.util.Map.of(
                "Type", "Notification", "TopicArn", "arn:aws:sns:us-east-1:000000000000:transaction-events",
                "Message", TX));
        listener.handle(envelope);
        verify(service).evaluate(any(), eq("async"));
    }

    @Test
    void invalidOrUnreadableEventsFailSoTheyReachTheDlq() {
        assertThatThrownBy(() -> listener.handle("não é json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listener.handle("{\"transactionId\":\"\",\"amount\":-1}"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(service, never()).evaluate(any(), any());
    }
}
