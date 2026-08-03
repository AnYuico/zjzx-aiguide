package com.tzp.zjzx.agent.mcp;

import com.tzp.zjzx.agent.client.ProductGuideCatalogClient;
import com.tzp.zjzx.agent.security.McpApiKeyWebFilter;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.ai.mcp.server.enabled=true",
                "zjzx.agent.mcp.enabled=true",
                "zjzx.agent.mcp.api-key=test-mcp-key-with-at-least-32-chars",
                "spring.rabbitmq.listener.simple.auto-startup=false"
        }
)
class McpServerProtocolTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ProductGuideCatalogClient productGuideCatalogClient;

    @Test
    void rejectsProtocolRequestWithoutApiKey() {
        webTestClient.post()
                .uri("/mcp")
                .header("Content-Type", "application/json")
                .bodyValue(toolsListRequest())
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void discoversExactlyThreeReadOnlyTools() {
        webTestClient.post()
                .uri("/mcp")
                .header(
                        McpApiKeyWebFilter.API_KEY_HEADER,
                        "test-mcp-key-with-at-least-32-chars"
                )
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .bodyValue(toolsListRequest())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.result.tools.length()")
                .isEqualTo(3)
                .jsonPath("$.result.tools[?(@.name == 'searchProducts')]")
                .exists()
                .jsonPath("$.result.tools[?(@.name == 'getProductSnapshot')]")
                .exists()
                .jsonPath(
                        "$.result.tools[?(@.name == 'retrieveProductKnowledge')]"
                )
                .exists();
    }

    @Test
    void callsSearchToolThroughJsonRpc() {
        ProductGuideVo product = new ProductGuideVo();
        product.setSkuId(14L);
        product.setProductName("Mac mini");
        product.setSkuName("16G");
        product.setSalePrice(new BigDecimal("1999.00"));
        product.setInStock(true);
        when(productGuideCatalogClient.search(any()))
                .thenReturn(Mono.just(List.of(product)));

        webTestClient.post()
                .uri("/mcp")
                .header(
                        McpApiKeyWebFilter.API_KEY_HEADER,
                        "test-mcp-key-with-at-least-32-chars"
                )
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .bodyValue("""
                        {
                          "jsonrpc": "2.0",
                          "id": "search-test",
                          "method": "tools/call",
                          "params": {
                            "name": "searchProducts",
                            "arguments": {
                              "keyword": "Mac",
                              "limit": 5
                            }
                          }
                        }
                        """)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.result.isError")
                .isEqualTo(false)
                .jsonPath("$.result.content[0].text")
                .value(value -> {
                    String text = String.valueOf(value);
                    org.junit.jupiter.api.Assertions.assertTrue(
                            text.contains("Mac mini")
                                    && text.contains("\"skuId\":14")
                    );
                });
    }

    @ParameterizedTest
    @MethodSource("toolCallsWithUnknownArguments")
    void rejectsUnknownToolArguments(String requestBody) {
        webTestClient.post()
                .uri("/mcp")
                .header(
                        McpApiKeyWebFilter.API_KEY_HEADER,
                        "test-mcp-key-with-at-least-32-chars"
                )
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .bodyValue(requestBody)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.result.isError")
                .isEqualTo(true)
                .jsonPath("$.result.content[0].text")
                .value(value -> org.junit.jupiter.api.Assertions.assertTrue(
                        String.valueOf(value).contains(
                                "Unknown arguments are not allowed"
                        )
                ));
    }

    @ParameterizedTest
    @MethodSource("unsupportedCapabilityMethods")
    void returnsControlledErrorForUnsupportedCapabilities(String method) {
        webTestClient.post()
                .uri("/mcp")
                .header(
                        McpApiKeyWebFilter.API_KEY_HEADER,
                        "test-mcp-key-with-at-least-32-chars"
                )
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .bodyValue("""
                        {
                          "jsonrpc": "2.0",
                          "id": "unsupported-capability",
                          "method": "%s",
                          "params": {}
                        }
                        """.formatted(method))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo("unsupported-capability")
                .jsonPath("$.error.code")
                .isEqualTo(-32601)
                .jsonPath("$.error.message")
                .isEqualTo("Method not found");
    }

    private static Stream<Arguments> toolCallsWithUnknownArguments() {
        return Stream.of(
                Arguments.of("""
                        {
                          "jsonrpc": "2.0",
                          "id": "unknown-user-id",
                          "method": "tools/call",
                          "params": {
                            "name": "searchProducts",
                            "arguments": {
                              "keyword": "Mac",
                              "limit": 5,
                              "userId": 10001
                            }
                          }
                        }
                        """),
                Arguments.of("""
                        {
                          "jsonrpc": "2.0",
                          "id": "unknown-transaction-fields",
                          "method": "tools/call",
                          "params": {
                            "name": "getProductSnapshot",
                            "arguments": {
                              "skuId": 14,
                              "orderNo": "SECURITY-CANARY",
                              "paymentId": 123,
                              "addressId": 456
                            }
                          }
                        }
                        """),
                Arguments.of("""
                        {
                          "jsonrpc": "2.0",
                          "id": "unknown-token",
                          "method": "tools/call",
                          "params": {
                            "name": "retrieveProductKnowledge",
                            "arguments": {
                              "query": "mac mini",
                              "limit": 5,
                              "token": "SECRET-CANARY"
                            }
                          }
                        }
                        """)
        );
    }

    private static Stream<String> unsupportedCapabilityMethods() {
        return Stream.of(
                "resources/list",
                "prompts/list",
                "completion/complete"
        );
    }

    private String toolsListRequest() {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": "tools-list-test",
                  "method": "tools/list",
                  "params": {}
                }
                """;
    }
}
