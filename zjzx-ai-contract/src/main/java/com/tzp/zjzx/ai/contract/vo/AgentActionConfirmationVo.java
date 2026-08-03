package com.tzp.zjzx.ai.contract.vo;

public class AgentActionConfirmationVo {

    private String confirmationId;
    private String status;
    private String summary;
    private String message;
    private Boolean replayed;

    public String getConfirmationId() {
        return confirmationId;
    }

    public void setConfirmationId(String confirmationId) {
        this.confirmationId = confirmationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getReplayed() {
        return replayed;
    }

    public void setReplayed(Boolean replayed) {
        this.replayed = replayed;
    }
}
