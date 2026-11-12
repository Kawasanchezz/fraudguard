package com.fraudguard.profile;

import com.fraudguard.evaluation.TransactionRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Atualiza o perfil fora do caminho crítico (assíncrono) para não pesar na latência. */
@Component
public class ProfileUpdater {

    private final CustomerProfileRepository repository;

    public ProfileUpdater(CustomerProfileRepository repository) {
        this.repository = repository;
    }

    @Async("profileExecutor")
    @Transactional
    public void learn(TransactionRequest tx) {
        CustomerProfile profile = repository.findById(tx.customerId())
                .orElseGet(() -> new CustomerProfile(tx.customerId(), null, null));
        profile.learn(tx.amount());
        repository.save(profile);
    }
}
