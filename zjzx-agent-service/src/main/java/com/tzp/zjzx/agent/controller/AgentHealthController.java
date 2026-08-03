package com.tzp.zjzx.agent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentHealthController {

    @GetMapping("/health")
    public AgentHealthResponse health() {
        return new AgentHealthResponse("UP", "zjzx-agent-service");
    }

    public record AgentHealthResponse(String status, String service) {
    }
}
