package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.ai.contract.vo.AgentActionPreparationVo;
import com.tzp.zjzx.ai.contract.vo.AgentUserPrincipalVo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PersonalActionTools {

    private final AgentActionService actionService;
    private final AgentUserPrincipalVo principal;
    private final CopyOnWriteArrayList<AgentActionPreparationVo> preparedActions =
            new CopyOnWriteArrayList<>();

    public PersonalActionTools(
            AgentActionService actionService,
            AgentUserPrincipalVo principal) {
        if (principal == null || principal.getUserId() == null
                || principal.getUserId() <= 0) {
            throw new IllegalArgumentException(
                    "Authenticated principal is required"
            );
        }
        this.actionService = actionService;
        this.principal = principal;
    }

    @Tool(name = "prepareAddToCart", description = """
            Prepare an add-to-cart action for the authenticated mall user.
            This tool does not modify the cart. It returns a server-generated
            confirmation that the user must explicitly approve in the UI.
            Never claim that the item was added before confirmation succeeds.
            """)
    public AgentActionPreparationVo prepareAddToCart(
            @ToolParam(description = "SKU ID selected from a product catalog tool")
            Long skuId,
            @ToolParam(description = "Quantity to add, between 1 and 10")
            Integer quantity) {
        AgentActionPreparationVo preparation =
                actionService.prepareAddToCart(
                        principal.getUserId(),
                        skuId,
                        quantity
                );
        preparedActions.add(preparation);
        return preparation;
    }

    @Tool(name = "prepareCancelRecentOrder", description = """
            Prepare cancellation of one recent waiting-payment order for the
            authenticated mall user. Call listMyRecentOrders with
            WAITING_PAYMENT first, then pass only its recentPosition.
            This tool does not cancel an order. It returns a server-generated
            confirmation that the user must explicitly approve in the UI.
            Never ask for or expose an order number.
            """)
    public AgentActionPreparationVo prepareCancelRecentOrder(
            @ToolParam(description = """
                    Position from listMyRecentOrders(WAITING_PAYMENT), between
                    1 and 10
                    """)
            Integer recentPosition) {
        AgentActionPreparationVo preparation =
                actionService.prepareCancelRecentOrder(
                        principal.getUserId(),
                        recentPosition
                );
        preparedActions.add(preparation);
        return preparation;
    }

    public List<AgentActionPreparationVo> preparedActions() {
        return List.copyOf(preparedActions);
    }
}
