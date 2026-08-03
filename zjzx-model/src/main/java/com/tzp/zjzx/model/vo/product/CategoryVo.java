package com.tzp.zjzx.model.vo.product;

import lombok.Data;

import java.util.List;

@Data
public class CategoryVo {

    private Long id;
    private String name;
    private String imageUrl;
    private Long parentId;
    private Integer status;
    private Integer orderNum;
    private Boolean hasChildren;
    private List<CategoryVo> children;
}
