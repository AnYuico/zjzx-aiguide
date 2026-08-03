package com.tzp.zjzx.model.vo.h5;

import lombok.Data;

@Data
public class UserAddressVo {

    private Long id;
    private String name;
    private String phone;
    private String tagName;
    private String provinceCode;
    private String cityCode;
    private String districtCode;
    private String address;
    private String fullAddress;
    private Integer isDefault;
}
