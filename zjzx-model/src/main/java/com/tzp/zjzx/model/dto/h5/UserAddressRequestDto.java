package com.tzp.zjzx.model.dto.h5;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserAddressRequestDto {

    @NotBlank(message = "收货人姓名不能为空")
    @Size(max = 20, message = "收货人姓名不能超过20个字符")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 20, message = "地址标签不能超过20个字符")
    private String tagName;

    @NotBlank(message = "省级地区编码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "省级地区编码格式不正确")
    private String provinceCode;

    @NotBlank(message = "市级地区编码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "市级地区编码格式不正确")
    private String cityCode;

    @NotBlank(message = "区县地区编码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "区县地区编码格式不正确")
    private String districtCode;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 100, message = "详细地址不能超过100个字符")
    private String address;

    @Min(value = 0, message = "默认地址标记只能为0或1")
    @Max(value = 1, message = "默认地址标记只能为0或1")
    private Integer isDefault;
}
