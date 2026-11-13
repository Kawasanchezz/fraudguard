package com.fraudguard.feedback;

/** Desfecho real de uma transação, informado depois da decisão. */
public enum OutcomeType {
    /** Cliente contestou e o adquirente estornou: fraude confirmada. */
    CHARGEBACK(true),
    /** Fraude confirmada por outro meio (análise, boletim, contato com o cliente). */
    FRAUDE_CONFIRMADA(true),
    /** Contestação improcedente ou cliente confirmou que foi ele: transação legítima. */
    LEGITIMA_CONFIRMADA(false);

    private final boolean fraude;

    OutcomeType(boolean fraude) {
        this.fraude = fraude;
    }

    public boolean isFraude() {
        return fraude;
    }
}
