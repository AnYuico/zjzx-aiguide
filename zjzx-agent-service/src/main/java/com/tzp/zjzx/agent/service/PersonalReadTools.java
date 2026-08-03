package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.agent.client.PersonalDataClient;
import com.tzp.zjzx.ai.contract.vo.AgentCartItemVo;
import com.tzp.zjzx.ai.contract.vo.AgentOrderSummaryVo;
import com.tzp.zjzx.ai.contract.vo.AgentUserPrincipalVo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PersonalReadTools {

    private static final Set<String> ALLOWED_ORDER_STATUSES = Set.of(
            "ALL",
            "CANCELLED",
            "WAITING_PAYMENT",
            "WAITING_DELIVERY",
            "DELIVERED",
            "COMPLETED"
    );

    private final PersonalDataClient personalDataClient;
    private final AgentUserPrincipalVo principal;
    private final int maximumOrderLimit;
    private final Duration timeout;

    public PersonalReadTools(PersonalDataClient personalDataClient,
                             AgentUserPrincipalVo principal,
                             int maximumOrderLimit,
                             Duration timeout) {
        if (principal == null || principal.getUserId() == null
                || principal.getUserId() <= 0) {
            throw new IllegalArgumentException("Authenticated principal is required");
        }
        this.personalDataClient = personalDataClient;
        this.principal = principal;
        this.maximumOrderLimit = maximumOrderLimit;
        this.timeout = timeout;
    }

    @Tool(name = "getMyCart", description = """
            Read the authenticated mall user's current shopping cart.
            This tool is read-only. Prices are cart snapshots, not final checkout quotes.
            It accepts no identity or token arguments.
            """)
    public List<AgentCartItemVo> getMyCart() {
        List<AgentCartItemVo> items = personalDataClient
                .getCart(principal.getUserId())
                .block(timeout);
        return items == null ? List.of() : items;
    }

    @Tool(name = "listMyRecentOrders", description = """
            Read sanitized recent order summaries for the authenticated mall user.
            This tool is read-only and never returns order numbers, addresses or phone numbers.
            It accepts only an optional status and result limit.
            """)
    public List<AgentOrderSummaryVo> listMyRecentOrders(
            @ToolParam(description = """
                    Optional order status: ALL, CANCELLED, WAITING_PAYMENT,
                    WAITING_DELIVERY, DELIVERED or COMPLETED
                    """, required = false)
            String status,
            @ToolParam(description = "Maximum summaries to return, between 1 and 10",
                    required = false)
            Integer limit) {
        String effectiveStatus = normalizeStatus(status);
        int effectiveLimit = normalizeLimit(limit);
        List<AgentOrderSummaryVo> orders = personalDataClient
                .listRecentOrders(
                        principal.getUserId(),
                        effectiveStatus,
                        effectiveLimit
                )
                .block(timeout);
        return orders == null ? List.of() : orders;
    }

    private String normalizeStatus(String status) {
        String normalized = StringUtils.hasText(status)
                ? status.trim().toUpperCase(Locale.ROOT)
                : "ALL";
        if (!ALLOWED_ORDER_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported order status");
        }
        return normalized;
    }

    private int normalizeLimit(Integer requestedLimit) {
        int limit = requestedLimit == null ? Math.min(5, maximumOrderLimit) : requestedLimit;
        if (limit < 1 || limit > maximumOrderLimit) {
            throw new IllegalArgumentException(
                    "Order result limit must be between 1 and " + maximumOrderLimit
            );
        }
        return limit;
    }
}
