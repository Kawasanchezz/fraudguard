package com.fraudguard.messaging;

import com.fraudguard.evaluation.EvaluationService;
import com.fraudguard.evaluation.TransactionRequest;
import io.awspring.cloud.sqs.annotation.SqsListener;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consome transações do fan-out SNS -> SQS (fraud-queue). Lança exceção em caso de falha:
 * a mensagem não é confirmada, volta para a fila e, após N tentativas, vai para a DLQ.
 * Aceita tanto o envelope SNS ("Message") quanto entrega raw.
 */
@Component
@ConditionalOnProperty(name = "fraudguard.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class TransactionEventListener {

    private final JsonMapper mapper;
    private final Validator validator;
    private final EvaluationService service;

    public TransactionEventListener(JsonMapper mapper, Validator validator, EvaluationService service) {
        this.mapper = mapper;
        this.validator = validator;
        this.service = service;
    }

    @SqsListener("${fraudguard.messaging.queue}")
    public void onMessage(String body) {
        handle(body);
    }

    void handle(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root.hasNonNull("Message") && root.has("TopicArn")) {
                root = mapper.readTree(root.get("Message").asString());
            }
            TransactionRequest tx = mapper.treeToValue(root, TransactionRequest.class);
            Set<ConstraintViolation<TransactionRequest>> errors = validator.validate(tx);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException("Evento de transação inválido: " + errors.size() + " violação(ões)");
            }
            service.evaluate(tx, "async");
        } catch (JacksonException e) {
            // Sem o corpo na mensagem: pode conter dado pessoal.
            throw new IllegalArgumentException("Evento de transação ilegível", e);
        }
    }
}
