package com.tzp.zjzx.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.ai.model.chat=openai",
                "spring.ai.openai.api-key=test-key-not-a-secret"
        }
)
class DeepSeekConfigurationTest {

    @Autowired
    private OpenAiChatModel chatModel;

    @Test
    void configuresDeepSeekV4FlashInNonThinkingMode() {
        OpenAiChatOptions options = (OpenAiChatOptions) chatModel.getDefaultOptions();

        assertEquals("deepseek-v4-flash", options.getModel());
        assertNotNull(options.getExtraBody());
        Object thinkingValue = options.getExtraBody().get("thinking");
        @SuppressWarnings("unchecked")
        Map<String, Object> thinking = (Map<String, Object>) thinkingValue;
        assertEquals("disabled", thinking.get("type"));
    }
}
