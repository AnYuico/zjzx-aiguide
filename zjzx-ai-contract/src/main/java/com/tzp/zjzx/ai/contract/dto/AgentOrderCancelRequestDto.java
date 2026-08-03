package com.tzp.zjzx.ai.contract.dto;

public class AgentOrderCancelRequestDto {

    private String requestId;
    private String orderNo;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }
}
