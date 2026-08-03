package com.tzp.zjzx.model.vo.h5;

import lombok.Data;

@Data
public class RegionVo {

    private String code;
    private String parentCode;
    private String name;
    private Integer level;
    private Boolean hasChildren;
}
