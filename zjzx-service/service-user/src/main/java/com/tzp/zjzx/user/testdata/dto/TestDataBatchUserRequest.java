package com.tzp.zjzx.user.testdata.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TestDataBatchUserRequest {

    @NotNull(message = "测试用户数量不能为空")
    @Min(value = 1, message = "测试用户数量不能小于1")
    @Max(value = 200, message = "测试用户数量不能超过200")
    private Integer count;

    @NotBlank(message = "手机号号段不能为空")
    @Pattern(regexp = "^1[3-9]\\d$", message = "手机号号段必须是合法的三位号段")
    private String phonePrefix = "199";

    @NotNull(message = "手机号序列起点不能为空")
    @Min(value = 0, message = "手机号序列起点不能小于0")
    @Max(value = 99999999, message = "手机号序列起点不能超过8位")
    private Integer sequenceStart = 10000000;

    @NotBlank(message = "测试密码不能为空")
    @Pattern(
            regexp = "^[\\x21-\\x7E]{8,72}$",
            message = "测试密码必须是8至72位非空格ASCII字符"
    )
    private String defaultPassword;

    @NotBlank(message = "昵称前缀不能为空")
    @Size(max = 40, message = "昵称前缀不能超过40个字符")
    private String nickNamePrefix = "压测用户";

    @NotBlank(message = "测试批次标记不能为空")
    @Pattern(
            regexp = "^[A-Za-z0-9._-]{1,40}$",
            message = "测试批次标记只能包含字母、数字、点、下划线和短横线"
    )
    private String tag = "jmeter-load-test";
}
