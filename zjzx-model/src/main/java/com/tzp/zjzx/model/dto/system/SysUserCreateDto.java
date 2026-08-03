package com.tzp.zjzx.model.dto.system;

import lombok.Data;

@Data
public class SysUserCreateDto {

    private String userName;
    private String password;
    private String name;
    private String phone;
    private String avatar;
    private String description;
}
