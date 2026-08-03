package com.tzp.zjzx.agent;

import com.tzp.zjzx.agent.config.AgentAiProperties;
import com.tzp.zjzx.agent.config.AgentMcpProperties;
import com.tzp.zjzx.agent.config.ProductGuideProperties;
import com.tzp.zjzx.agent.config.ProductKnowledgeMqProperties;
import com.tzp.zjzx.agent.config.ProductRetrievalProperties;
import com.tzp.zjzx.agent.config.PersonalToolsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        ProductGuideProperties.class,
        AgentAiProperties.class,
        AgentMcpProperties.class,
        ProductRetrievalProperties.class,
        ProductKnowledgeMqProperties.class,
        PersonalToolsProperties.class
})
public class GuideAgentApplication8520 {

    public static void main(String[] args) {
        SpringApplication.run(GuideAgentApplication8520.class, args);
    }
}
