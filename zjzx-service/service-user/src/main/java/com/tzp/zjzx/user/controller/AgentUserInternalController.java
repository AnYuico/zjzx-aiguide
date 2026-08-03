package com.tzp.zjzx.user.controller;

import com.tzp.zjzx.ai.contract.vo.AgentUserPrincipalVo;
import com.tzp.zjzx.common.security.InternalApiAuth;
import com.tzp.zjzx.user.service.UserInfoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/userInfo/internal/agent")
public class AgentUserInternalController {

    private final UserInfoService userInfoService;

    @Value("${zjzx.internal-api.token}")
    private String internalApiToken;

    public AgentUserInternalController(UserInfoService userInfoService) {
        this.userInfoService = userInfoService;
    }

    @GetMapping("/current")
    public ResponseEntity<AgentUserPrincipalVo> resolveCurrentUser(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String internalToken,
            @RequestHeader("token") String mallToken) {
        InternalApiAuth.verify(internalApiToken, internalToken);
        AgentUserPrincipalVo principal =
                userInfoService.resolveAgentPrincipal(mallToken);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(principal);
    }
}
