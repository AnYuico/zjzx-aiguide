package com.tzp.zjzx.agent.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProductIndexConsumeLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductIndexConsumeLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean exists(String consumerName, String eventId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists(" +
                        "select 1 from agent_mq_consume_log " +
                        "where consumer_name = ? and event_id = ?" +
                        ")",
                Boolean.class,
                consumerName,
                eventId
        );
        return Boolean.TRUE.equals(exists);
    }

    public boolean markConsumed(String consumerName,
                                String eventId,
                                String eventType) {
        int inserted = jdbcTemplate.update(
                "insert into agent_mq_consume_log(" +
                        "consumer_name, event_id, event_type" +
                        ") values (?, ?, ?) on conflict do nothing",
                consumerName,
                eventId,
                eventType
        );
        return inserted == 1;
    }
}
