package com.tzp.zjzx.ai.contract.mq;

public final class ProductKnowledgeMqConstants {

    public static final String EVENT_EXCHANGE = "zjzx.product.knowledge.events";
    public static final String DEAD_EXCHANGE = "zjzx.product.knowledge.dlx";

    public static final String CHANGED_ROUTING_KEY = "product.knowledge.changed";
    public static final String CHANGED_DEAD_ROUTING_KEY =
            "product.knowledge.changed.dead";

    public static final String AGENT_CHANGED_QUEUE =
            "zjzx.agent.product-knowledge-changed";
    public static final String AGENT_CHANGED_DEAD_QUEUE =
            AGENT_CHANGED_QUEUE + ".dlq";

    public static final String CHANGED_EVENT_TYPE =
            "PRODUCT_KNOWLEDGE_CHANGED";
    public static final String AGENT_CHANGED_CONSUMER =
            "zjzx-agent-service:product-knowledge-changed";

    private ProductKnowledgeMqConstants() {
    }
}
