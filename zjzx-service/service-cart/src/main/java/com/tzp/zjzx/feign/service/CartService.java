package com.tzp.zjzx.feign.service;

import com.tzp.zjzx.ai.contract.vo.AgentCartItemVo;
import com.tzp.zjzx.model.dto.internal.CartItemInternalDto;
import com.tzp.zjzx.model.vo.h5.CartItemVo;

import java.util.List;

public interface CartService {
    /**
     * 添加商品到购物车
     * @param skuId
     * @param skuNum
     */
    void addToCart(Long skuId, Integer skuNum);

    /**
     * 查询购物车
     * @return
     */
    List<CartItemVo> getCartList();

    /**
     * 删除购物车中的商品
     * @param skuId
     */
    void deleteCart(Long skuId);

    /**
     * 修改购物车中商品的状态
     * @param skuId
     * @param isChecked
     */
    void checkCart(Long skuId, Integer isChecked);

    /**
     * 全选 全不选
     * @param isChecked
     */
    void allCheckCart(Integer isChecked);

    /**
     * 清空购物车
     */
    void clearCart();

    /**
     * 获取购物车中选中的商品
     * @return
     */
    List<CartItemInternalDto> getAllChecked(Long userId);

    List<AgentCartItemVo> getAgentCart(Long userId);

    /**
     * 删除选中的商品
     */
    void deleteChecked();

}
