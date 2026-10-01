package com.taskworkflow.messaging;

import org.slf4j.MDC;

/**
 * Chave usada tanto no header AMQP quanto no MDC do logger para permitir
 * rastrear o fluxo completo de uma Task através de todos os workers (ver README 2.5).
 */
public final class CorrelationIdSupport {

    public static final String HEADER = "correlationId";
    public static final String MDC_KEY = "correlationId";

    private CorrelationIdSupport() {
    }

    public static void put(String correlationId) {
        MDC.put(MDC_KEY, correlationId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
