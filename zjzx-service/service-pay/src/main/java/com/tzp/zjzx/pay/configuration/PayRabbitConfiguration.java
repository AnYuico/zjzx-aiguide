package com.tzp.zjzx.pay.configuration;

import com.tzp.zjzx.mq.RabbitMqConstants;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PayRabbitConfiguration {

    @Bean
    public TopicExchange orderEventExchange() {
        return new TopicExchange(RabbitMqConstants.ORDER_EVENT_EXCHANGE, true, false);
    }
}
