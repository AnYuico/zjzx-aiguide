package com.tzp.zjzx.mq.consume;

import org.springframework.jdbc.core.JdbcTemplate;

public class MqConsumeLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public MqConsumeLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryClaim(String consumerName, String eventId, String eventType) {
        int inserted = jdbcTemplate.update(
                "insert ignore into mq_consume_log(consumer_name, event_id, event_type) " +
                        "values (?, ?, ?)",
                consumerName, eventId, eventType);
        return inserted == 1;
    }
}
