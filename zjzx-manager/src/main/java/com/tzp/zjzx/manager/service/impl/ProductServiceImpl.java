package com.tzp.zjzx.manager.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.manager.mapper.ProductDetailsMapper;
import com.tzp.zjzx.manager.mapper.ProductMapper;
import com.tzp.zjzx.manager.mapper.ProductSkuMapper;
import com.tzp.zjzx.manager.service.ProductKnowledgeOutboxService;
import com.tzp.zjzx.manager.service.ProductService;
import com.tzp.zjzx.model.dto.product.ProductCreateDto;
import com.tzp.zjzx.model.dto.product.ProductDto;
import com.tzp.zjzx.model.dto.product.ProductSkuWriteDto;
import com.tzp.zjzx.model.dto.product.ProductUpdateDto;
import com.tzp.zjzx.model.dto.product.ProductWriteDto;
import com.tzp.zjzx.model.entity.product.Product;
import com.tzp.zjzx.model.entity.product.ProductDetails;
import com.tzp.zjzx.model.entity.product.ProductSku;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;

    private final ProductSkuMapper productSkuMapper;

    private final ProductDetailsMapper productDetailsMapper;

    private final ProductKnowledgeOutboxService productKnowledgeOutboxService;

    public ProductServiceImpl(ProductMapper productMapper,
                              ProductSkuMapper productSkuMapper,
                              ProductDetailsMapper productDetailsMapper,
                              ProductKnowledgeOutboxService productKnowledgeOutboxService) {
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.productDetailsMapper = productDetailsMapper;
        this.productKnowledgeOutboxService = productKnowledgeOutboxService;
    }

    /**
     * 列表条件查询接口
     *
     * @param page
     * @param limit
     * @param productDto
     * @return
     */
    @Override
    public PageInfo<Product> findByPage(Integer page, Integer limit, ProductDto productDto) {
        PageHelper.startPage(page, limit);
        List<Product> list = productMapper.findByPage(productDto);
        return new PageInfo<>(list);
    }

    /**
     * 添加商品
     *
     * @param productCreateDto
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(ProductCreateDto productCreateDto) {
        Product product = toProduct(productCreateDto, productCreateDto.getProductSkuList());
        List<ProductSku> productSkuList = requireProductSkuList(product);
        //1.保存商品的基本信息到product表
        product.setStatus(0);
        product.setAuditStatus(0);
        productMapper.save(product);

        //2.获取商品sku列表集合 保存sku信息 product_sku表
        for (int i = 0; i < productSkuList.size(); i++) {
            ProductSku productSku = productSkuList.get(i);

            //商品编号
            productSku.setSkuCode(product.getId() + "_" + i);
            //商品id
            productSku.setProductId(product.getId());
            //skuName
            productSku.setSkuName(product.getName() + productSku.getSkuSpec());
            productSku.setSaleNum(0);
            productSku.setStatus(0);

            productSkuMapper.save(productSku);
        }

        //3.保存商品详情数据 product_details表
        ProductDetails productDetails = new ProductDetails();
        productDetails.setProductId(product.getId());
        productDetails.setImageUrls(product.getDetailsImageUrls());

        productDetailsMapper.save(productDetails);
        productKnowledgeOutboxService.enqueue(
                product.getId(),
                ProductKnowledgeOutboxService.CREATED
        );
    }

    /**
     * 根据商品id查询商品信息
     *
     * @param id
     * @return
     */
    @Override
    public Product getById(Long id) {
        //1.根据商品id查询商品基本信息 product表
        Product product = productMapper.findProductById(id);
        //2.根据商品id查询商品sku信息 product_sku表
        List<ProductSku> productSkuList = productSkuMapper.findByProductSkuById(id);

        //3.根据商品id查询商品详情信息 product_details表
        ProductDetails productDetails = productDetailsMapper.findProductDetailsById(id);
        String imageUrls = productDetails.getImageUrls();

        //4.封装
        product.setProductSkuList(productSkuList);
        product.setDetailsImageUrls(imageUrls);

        return product;
    }

    /**
     * 删除商品
     *
     * @param id
     */
    @Override
    @Transactional
    public void deleteById(Long id) {
        //1 删除product表
        productMapper.deleteById(id);

        //2 删除product_sku表
        productSkuMapper.deleteByProductId(id);

        //3 删除product_details表
        productDetailsMapper.deleteByProductId(id);
        productKnowledgeOutboxService.enqueue(
                id,
                ProductKnowledgeOutboxService.DELETED
        );
    }

    /**
     * 修改商品信息
     *
     * @param productUpdateDto
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateById(ProductUpdateDto productUpdateDto) {
        Product product = toProduct(productUpdateDto, productUpdateDto.getProductSkuList());
        List<ProductSku> productSkuList = requireProductSkuList(product);
        //1 修改product表
        productMapper.updateById(product);

        //2 修改product_sku表
        productSkuList.forEach(productSku -> {
            productSkuMapper.updateById(productSku);
        });

        //3 修改product_details表
        String detailsImageUrls = product.getDetailsImageUrls();
        ProductDetails productDetails = productDetailsMapper.findProductDetailsById(product.getId());
        productDetails.setImageUrls(detailsImageUrls);
        productDetailsMapper.updateById(productDetails);
        productKnowledgeOutboxService.enqueue(
                product.getId(),
                ProductKnowledgeOutboxService.UPDATED
        );
    }

    /**
     * 商品上下架
     *
     * @param id
     * @param status
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        int targetStatus = Integer.valueOf(1).equals(status) ? 1 : -1;
        Product product = new Product();
        product.setId(id);
        product.setStatus(targetStatus);
        productMapper.updateById(product);
        productSkuMapper.updateStatusByProductId(id, targetStatus);
        productKnowledgeOutboxService.enqueue(
                id,
                ProductKnowledgeOutboxService.STATUS_CHANGED
        );
    }

    /**
     * 商品审核状态更新
     *
     * @param id
     * @param auditStatus
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAuditStatus(Long id, Integer auditStatus) {
        Product product = new Product();
        product.setId(id);
        if (auditStatus == 1) {
            product.setAuditStatus(1);
            product.setAuditMessage("审批通过");
        } else {
            product.setAuditStatus(-1);
            product.setAuditMessage("审批不通过");
        }
        productMapper.updateById(product);
        productKnowledgeOutboxService.enqueue(
                id,
                ProductKnowledgeOutboxService.AUDIT_CHANGED
        );
    }

    private List<ProductSku> requireProductSkuList(Product product) {
        if (product == null || CollectionUtils.isEmpty(product.getProductSkuList())) {
            throw new MyException(ResultCodeEnum.PRODUCT_SKU_REQUIRED);
        }
        if (product.getProductSkuList().stream().anyMatch(java.util.Objects::isNull)) {
            throw new MyException(ResultCodeEnum.PRODUCT_SKU_INVALID);
        }
        return product.getProductSkuList();
    }

    private Product toProduct(ProductWriteDto productDto,
                              List<? extends ProductSkuWriteDto> skuDtoList) {
        Product product = new Product();
        BeanUtils.copyProperties(productDto, product);
        if (skuDtoList == null) {
            return product;
        }
        List<ProductSku> productSkuList = skuDtoList.stream()
                .map(productSkuDto -> productSkuDto == null ? null : toProductSku(productSkuDto))
                .collect(Collectors.toList());
        product.setProductSkuList(productSkuList);
        return product;
    }

    private ProductSku toProductSku(ProductSkuWriteDto productSkuDto) {
        ProductSku productSku = new ProductSku();
        BeanUtils.copyProperties(productSkuDto, productSku);
        return productSku;
    }
}
