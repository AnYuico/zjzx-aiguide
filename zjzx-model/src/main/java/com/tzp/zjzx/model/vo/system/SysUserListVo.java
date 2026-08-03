package com.tzp.zjzx.model.vo.system;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class SysUserListVo {

    private Long id;
    private String userName;
    private String name;
    private String phone;
    private String avatar;
    private String description;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
