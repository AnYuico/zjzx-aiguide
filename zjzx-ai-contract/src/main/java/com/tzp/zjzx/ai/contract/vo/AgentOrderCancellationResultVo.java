package com.tzp.zjzx.ai.contract.vo;

public class AgentOrderCancellationResultVo {

    private Boolean applied;
    private Boolean replayed;

    public Boolean getApplied() {
        return applied;
    }

    public void setApplied(Boolean applied) {
        this.applied = applied;
    }

    public Boolean getReplayed() {
        return replayed;
    }

    public void setReplayed(Boolean replayed) {
        this.replayed = replayed;
    }
}
