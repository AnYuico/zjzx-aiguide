package com.tzp.zjzx.mq.outbox;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

public class MqOutboxRepository {

    private static final int PENDING = 0;
    private static final int SENT = 1;
    private static final int DEAD = 2;

    private final JdbcTemplate jdbcTemplate;

    public MqOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insert(MqOutboxRecord record) {
        return jdbcTemplate.update(
                "insert ignore into mq_outbox(" +
                        "event_id, producer, event_type, exchange_name, routing_key, payload, " +
                        "status, retry_count, next_retry_time, deliver_at" +
                        ") values (?, ?, ?, ?, ?, ?, 0, 0, now(), ?)",
                record.getEventId(), record.getProducer(), record.getEventType(),
                record.getExchangeName(), record.getRoutingKey(), record.getPayload(),
                toTimestamp(record.getDeliverAt()));
    }

    public List<MqOutboxRecord> findPending(String producer, int limit) {
        return jdbcTemplate.query(
                "select id, event_id, producer, event_type, exchange_name, routing_key, " +
                        "payload, retry_count, deliver_at " +
                        "from mq_outbox where producer = ? and status = 0 " +
                        "and next_retry_time <= now() order by id limit ?",
                (resultSet, rowNum) -> {
                    MqOutboxRecord record = new MqOutboxRecord();
                    record.setId(resultSet.getLong("id"));
                    record.setEventId(resultSet.getString("event_id"));
                    record.setProducer(resultSet.getString("producer"));
                    record.setEventType(resultSet.getString("event_type"));
                    record.setExchangeName(resultSet.getString("exchange_name"));
                    record.setRoutingKey(resultSet.getString("routing_key"));
                    record.setPayload(resultSet.getString("payload"));
                    record.setRetryCount(resultSet.getInt("retry_count"));
                    record.setDeliverAt(resultSet.getTimestamp("deliver_at"));
                    return record;
                }, producer, limit);
    }

    public int markSent(Long id) {
        return jdbcTemplate.update(
                "update mq_outbox set status = ?, sent_time = now(), update_time = now() " +
                        "where id = ? and status = ?",
                SENT, id, PENDING);
    }

    public int markRetry(Long id, int retryCount, Date nextRetryTime,
                         String error, boolean dead) {
        return jdbcTemplate.update(
                "update mq_outbox set status = ?, retry_count = ?, next_retry_time = ?, " +
                        "last_error = ?, update_time = now() where id = ? and status = ?",
                dead ? DEAD : PENDING, retryCount, toTimestamp(nextRetryTime),
                error, id, PENDING);
    }

    private Timestamp toTimestamp(Date value) {
        return value == null ? null : new Timestamp(value.getTime());
    }
}
