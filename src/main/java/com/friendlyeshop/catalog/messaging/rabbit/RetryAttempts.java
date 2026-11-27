package com.friendlyeshop.catalog.messaging.rabbit;

import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Message;

public final class RetryAttempts {

    public static final String RETRY_QUEUE = "stock.reserve.retry";

    private RetryAttempts() {
    }

    public static int of(Message message) {
        List<Map<String, ?>> xDeath = message.getMessageProperties().getXDeathHeader();
        long retries = 0;
        if (xDeath != null) {
            for (Map<String, ?> death : xDeath) {
                if (RETRY_QUEUE.equals(death.get("queue"))) {
                    retries += deathCount(death);
                }
            }
        }
        return (int) Math.min(retries + 1, Integer.MAX_VALUE);
    }

    private static long deathCount(Map<String, ?> death) {
        Object count = death.get("count");
        return count instanceof Number number ? number.longValue() : 1L;
    }
}
