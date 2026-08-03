package com.tzp.zjzx.agent.controller;

import com.tzp.zjzx.agent.exception.AgentActionConflictException;
import com.tzp.zjzx.agent.exception.AgentActionExpiredException;
import com.tzp.zjzx.agent.exception.AgentActionNotFoundException;
import com.tzp.zjzx.agent.exception.AgentActionUnavailableException;
import com.tzp.zjzx.agent.exception.AgentAuthenticationException;
import com.tzp.zjzx.agent.exception.PersonalDataUnavailableException;
import com.tzp.zjzx.agent.exception.ProductCatalogUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
public class AgentExceptionHandler {

    @ExceptionHandler(AgentActionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public AgentErrorResponse handleActionNotFound() {
        return new AgentErrorResponse(
                "ACTION_NOT_FOUND",
                "待确认操作不存在。"
        );
    }

    @ExceptionHandler(AgentActionExpiredException.class)
    @ResponseStatus(HttpStatus.GONE)
    public AgentErrorResponse handleActionExpired() {
        return new AgentErrorResponse(
                "ACTION_EXPIRED",
                "待确认操作已过期，请重新发起。"
        );
    }

    @ExceptionHandler(AgentActionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public AgentErrorResponse handleActionConflict(
            AgentActionConflictException exception) {
        return new AgentErrorResponse(
                "ACTION_CONFLICT",
                exception.getMessage()
        );
    }

    @ExceptionHandler(AgentActionUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public AgentErrorResponse handleActionUnavailable() {
        return new AgentErrorResponse(
                "ACTION_UNAVAILABLE",
                "确认操作暂时不可用，请稍后重试。"
        );
    }

    @ExceptionHandler(AgentAuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public AgentErrorResponse handleAuthenticationRequired() {
        return new AgentErrorResponse(
                "AUTHENTICATION_REQUIRED",
                "登录状态无效，请重新登录。"
        );
    }

    @ExceptionHandler(PersonalDataUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public AgentErrorResponse handlePersonalDataUnavailable() {
        return new AgentErrorResponse(
                "PERSONAL_DATA_UNAVAILABLE",
                "个人数据暂时不可用，请稍后重试。"
        );
    }

    @ExceptionHandler(ProductCatalogUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public AgentErrorResponse handleCatalogUnavailable() {
        return new AgentErrorResponse(
                "PRODUCT_CATALOG_UNAVAILABLE",
                "商品目录暂时不可用，请稍后重试。"
        );
    }

    @ExceptionHandler({WebExchangeBindException.class, ServerWebInputException.class,
            IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public AgentErrorResponse handleBadRequest(Exception exception) {
        String message = exception instanceof IllegalArgumentException
                ? exception.getMessage()
                : "请求参数格式错误";
        return new AgentErrorResponse("INVALID_REQUEST", message);
    }

    public record AgentErrorResponse(String code, String message) {
    }
}
