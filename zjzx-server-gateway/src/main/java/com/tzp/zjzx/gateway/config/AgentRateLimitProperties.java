package com.tzp.zjzx.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zjzx.agent.rate-limit")
public class AgentRateLimitProperties {

    private int userPerMinute = 10;
    private int ipPerMinute = 30;
    private int sessionPerMinute = 15;

    public int getUserPerMinute() {
        return userPerMinute;
    }

    public void setUserPerMinute(int userPerMinute) {
        this.userPerMinute = userPerMinute;
    }

    public int getIpPerMinute() {
        return ipPerMinute;
    }

    public void setIpPerMinute(int ipPerMinute) {
        this.ipPerMinute = ipPerMinute;
    }

    public int getSessionPerMinute() {
        return sessionPerMinute;
    }

    public void setSessionPerMinute(int sessionPerMinute) {
        this.sessionPerMinute = sessionPerMinute;
    }
}
