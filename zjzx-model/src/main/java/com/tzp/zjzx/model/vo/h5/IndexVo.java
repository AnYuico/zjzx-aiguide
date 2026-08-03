package com.tzp.zjzx.model.vo.h5;

import com.tzp.zjzx.model.vo.product.CategoryVo;
import com.tzp.zjzx.model.vo.product.ProductSkuVo;
import lombok.Data;

import java.util.List;

@Data
public class IndexVo {

    private List<CategoryVo> categoryList;
    private List<ProductSkuVo> productSkuList;
}
