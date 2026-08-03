package com.tzp.zjzx.model.dto.internal;

import lombok.Data;

@Data
public class UserAddressInternalDto {

    private Long id;
    private String name;
    private String phone;
    private String tagName;
    private String provinceCode;
    private String cityCode;
    private String districtCode;
    private String fullAddress;
}
