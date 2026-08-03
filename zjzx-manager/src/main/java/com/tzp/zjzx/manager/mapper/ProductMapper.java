package com.tzp.zjzx.manager.mapper;

import com.tzp.zjzx.model.dto.product.ProductDto;
import com.tzp.zjzx.model.entity.product.Product;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductMapper {
    /**
     * 条件分页查询
     * @param productDto
     * @return
     */
    List<Product> findByPage(ProductDto productDto);

    /**
     * 添加商品信息
     * @param product
     */
    void save(Product product);

    /**
     * 根据id查询商品信息
     * @param id
     * @return
     */
    Product findProductById(Long id);

    /**
     * 根据id删除商品信息
     * @param id
     */
    void deleteById(Long id);

    /**
     * 根据id修改商品信息
     * @param product
     */
    void updateById(Product product);
}
