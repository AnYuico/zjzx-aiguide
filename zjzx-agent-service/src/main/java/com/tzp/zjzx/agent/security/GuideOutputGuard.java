package com.tzp.zjzx.agent.security;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class GuideOutputGuard {

    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
            Pattern.compile(
                    "(?i)(?:api[_ -]?key|password|secret)\\s*[:=]\\s*\\S+"
            ),
            Pattern.compile("(?i)sk-[a-z0-9]{16,}"),
            Pattern.compile("(?i)-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)"),
            Pattern.compile("(?i)(?<![a-f0-9])[a-f0-9]{32}(?![a-f0-9])"),
            Pattern.compile(
                    "已(?:经)?(?:为你|为您|帮你|帮您)?"
                            + "(?:下单|支付|取消订单|修改库存|扣减库存|修改价格)"
            ),
            Pattern.compile(
                    "(?:下单|支付|退款|取消订单|修改库存|扣减库存|修改价格)"
                            + "(?:成功|完成)"
            )
    );

    public boolean isSafe(String answer) {
        if (!StringUtils.hasText(answer)) {
            return false;
        }
        return FORBIDDEN_PATTERNS.stream()
                .noneMatch(pattern -> pattern.matcher(answer).find());
    }
}
