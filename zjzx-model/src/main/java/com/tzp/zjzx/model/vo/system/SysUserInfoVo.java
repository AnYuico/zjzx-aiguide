package com.tzp.zjzx.model.vo.system;

import lombok.Data;

@Data
public class SysUserInfoVo {

    private Long id;
    private String userName;
    private String name;
    private String phone;
    private String avatar;
    private String description;
    private Integer status;
}
