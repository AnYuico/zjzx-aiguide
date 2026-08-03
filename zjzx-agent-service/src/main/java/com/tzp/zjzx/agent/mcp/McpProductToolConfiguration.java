package com.tzp.zjzx.agent.mcp;

import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.provider.tool.AsyncStatelessMcpToolProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "zjzx.agent.mcp",
        name = "enabled",
        havingValue = "true"
)
public class McpProductToolConfiguration {

    private static final Map<String, Set<String>> ALLOWED_ARGUMENTS = Map.of(
            "searchProducts", Set.of("keyword", "limit"),
            "getProductSnapshot", Set.of("skuId"),
            "retrieveProductKnowledge", Set.of("query", "limit")
    );

    @Bean
    List<McpStatelessServerFeatures.AsyncToolSpecification>
    productGuideMcpToolSpecifications(ProductCatalogMcpTools tools) {
        return new AsyncStatelessMcpToolProvider(
                List.of(tools)
        ).getToolSpecifications()
                .stream()
                .map(this::withStrictArguments)
                .toList();
    }

    private McpStatelessServerFeatures.AsyncToolSpecification
    withStrictArguments(
            McpStatelessServerFeatures.AsyncToolSpecification specification) {
        String toolName = specification.tool().name();
        Set<String> allowedArguments = ALLOWED_ARGUMENTS.get(toolName);
        if (allowedArguments == null) {
            throw new IllegalStateException(
                    "MCP tool has no argument whitelist: " + toolName
            );
        }

        return new McpStatelessServerFeatures.AsyncToolSpecification(
                specification.tool(),
                (context, request) -> {
                    Map<String, Object> arguments = request.arguments() == null
                            ? Map.of()
                            : request.arguments();
                    List<String> unknownArguments = arguments.keySet()
                            .stream()
                            .filter(argument ->
                                    !allowedArguments.contains(argument)
                            )
                            .sorted()
                            .toList();
                    if (!unknownArguments.isEmpty()) {
                        return Mono.just(
                                new McpSchema.CallToolResult(
                                        "Unknown arguments are not allowed: "
                                                + String.join(
                                                ", ",
                                                unknownArguments
                                        ),
                                        true
                                )
                        );
                    }
                    return specification.callHandler()
                            .apply(context, request);
                }
        );
    }
}
