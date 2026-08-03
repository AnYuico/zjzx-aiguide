package com.tzp.zjzx.manager.service;

import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.model.dto.product.ProductCreateDto;
import com.tzp.zjzx.model.dto.product.ProductDto;
import com.tzp.zjzx.model.dto.product.ProductUpdateDto;
import com.tzp.zjzx.model.entity.product.Product;

public interface ProductService {
    /**
     * 列表条件查询接口
     * @param page
     * @param limit
     * @param productDto
     * @return
     */
    PageInfo<Product> findByPage(Integer page, Integer limit, ProductDto productDto);

    /**
     * 商品添加接口
     * @param productCreateDto
     */
    void save(ProductCreateDto productCreateDto);

    /**
     * 根据商品id查询商品信息
     * @param id
     * @return
     */
    Product getById(Long id);

    /**
     * 根据商品id删除商品信息
     * @param id
     */
    void deleteById(Long id);

    /**
     * 根据商品id修改商品信息
     * @param productUpdateDto
     */
    void updateById(ProductUpdateDto productUpdateDto);

    /**
     * 审核状态更新
     * @param id
     * @param auditStatus
     */
    void updateAuditStatus(Long id, Integer auditStatus);

    /**
     * 上下架状态更新
     * @param id
     * @param status
     */
    void updateStatus(Long id, Integer status);
}
