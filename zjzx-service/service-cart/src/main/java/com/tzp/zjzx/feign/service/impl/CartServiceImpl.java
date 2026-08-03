package com.tzp.zjzx.feign.service.impl;

import com.alibaba.fastjson2.JSON;
import com.tzp.zjzx.ai.contract.vo.AgentCartItemVo;
import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.feign.service.CartService;
import com.tzp.zjzx.feign.product.ProductFeignClient;
import com.tzp.zjzx.model.dto.internal.CartItemInternalDto;
import com.tzp.zjzx.model.dto.internal.ProductSkuInternalDto;
import com.tzp.zjzx.model.entity.h5.CartInfo;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.h5.CartItemVo;
import com.tzp.zjzx.utils.AuthContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Value("${zjzx.internal-api.token}")
    private String internalApiToken;


    private String getCartKey(Long userId) {
        return "user:cart:" + userId;
    }

    /**
     * 添加商品到购物车
     *
     * @param skuId
     * @param skuNum
     */
    @Override
    public void addToCart(Long skuId, Integer skuNum) {
        //1 必须登录状态 获取当前登录用户id
        //从threadLocal中获取
        Long userId = AuthContextUtil.getUserInfo().getId();
        //构建hash中的key
        String cartKey = this.getCartKey(userId);

        //2 因为是购物车，所以需要判断是否已经存在该商品
        //2.1 从redis中获取购物车数据 根据用户id+skuId获取
        Object cartInfoObj = redisTemplate.opsForHash().get(cartKey, String.valueOf(skuId));

        CartInfo cartInfo = null;
        if (cartInfoObj != null) {
            //3 如果redis中存在，数量相加
            cartInfo = JSON.parseObject(cartInfoObj.toString(), CartInfo.class);
            //数量相加
            cartInfo.setSkuNum(cartInfo.getSkuNum() + skuNum);
            //设置属性 表示购物车中的物品为选中状态
            cartInfo.setIsChecked(1);
            cartInfo.setUpdateTime(new Date());
        } else {
            //4 如果redis中不存在，直接添加
            cartInfo = new CartInfo();

            //远程调用: 根据skuId获取sku信息
            Result<ProductSkuInternalDto> productResult =
                    productFeignClient.getBySkuId(internalApiToken, skuId);
            if (productResult == null
                    || !ResultCodeEnum.SUCCESS.getCode().equals(productResult.getCode())
                    || productResult.getData() == null) {
                throw new MyException(ResultCodeEnum.DATA_ERROR);
            }
            ProductSkuInternalDto productSku = productResult.getData();
            //设置相应的数据
            cartInfo.setCartPrice(productSku.getSalePrice());
            cartInfo.setSkuNum(skuNum);
            cartInfo.setSkuId(skuId);
            cartInfo.setUserId(userId);
            cartInfo.setImgUrl(productSku.getThumbImg());
            cartInfo.setSkuName(productSku.getSkuName());
            cartInfo.setIsChecked(1);
            cartInfo.setCreateTime(new Date());
            cartInfo.setUpdateTime(new Date());
        }

        //5 将购物车数据存入redis中
        redisTemplate.opsForHash().put(cartKey, String.valueOf(skuId), JSON.toJSONString(cartInfo));

    }

    /**
     * 查询购物车
     *
     * @return
     */
    @Override
    public List<CartItemVo> getCartList() {

        //1 构建查询的redis里面的key值 根据当前userId
        Long userId = AuthContextUtil.getUserInfo().getId();
        String cartKey = this.getCartKey(userId);

        //2 根据key从redis里面hash取值
        List<Object> valueList = redisTemplate.opsForHash().values(cartKey);

        //List<Obj> --> List<CartInfo>
        if (!CollectionUtils.isEmpty(valueList)) {
            List<CartInfo> cartInfoList = valueList.stream().map(cartInfoObj ->
                            JSON.parseObject(cartInfoObj.toString(), CartInfo.class))
                    .sorted((o1, o2) -> o2.getCreateTime().compareTo(o1.getCreateTime()))
                    .collect(Collectors.toList());

            return cartInfoList.stream().map(this::toCartItemVo).collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    /**
     * 删除购物车中的商品
     *
     * @param skuId
     */
    @Override
    public void deleteCart(Long skuId) {

        // 获取当前登录的用户数据
        Long userId = AuthContextUtil.getUserInfo().getId();
        String cartKey = getCartKey(userId);

        //获取缓存对象
        redisTemplate.opsForHash().delete(cartKey, String.valueOf(skuId));
    }

    /**
     * 修改购物车中商品的状态
     *
     * @param skuId
     * @param isChecked
     */
    @Override
    public void checkCart(Long skuId, Integer isChecked) {

        //1 获取当前登录的用户数据
        Long userId = AuthContextUtil.getUserInfo().getId();
        String cartKey = this.getCartKey(userId);

        //2 判断key中是否包含filed
        Boolean hasKey = redisTemplate.opsForHash().hasKey(cartKey, String.valueOf(skuId));
        if (hasKey) {
            //3 根据key + filed获取value
            String cartInfoJSON = redisTemplate.opsForHash().get(cartKey, String.valueOf(skuId)).toString();
            CartInfo cartInfo = JSON.parseObject(cartInfoJSON, CartInfo.class);
            //4 更新value中的选中状态
            cartInfo.setIsChecked(isChecked);
            //5.更新缓存
            redisTemplate.opsForHash().put(cartKey, String.valueOf(skuId), JSON.toJSONString(cartInfo));
        }
    }

    /**
     * 全选 或 全不选
     *
     * @param isChecked
     */
    @Override
    public void allCheckCart(Integer isChecked) {

        // 获取当前登录的用户数据
        Long userId = AuthContextUtil.getUserInfo().getId();
        String cartKey = getCartKey(userId);

        // 获取所有的购物项数据
        List<Object> objectList = redisTemplate.opsForHash().values(cartKey);
        if (!CollectionUtils.isEmpty(objectList)) {
            objectList.stream().map(cartInfoJSON -> {
                CartInfo cartInfo = JSON.parseObject(cartInfoJSON.toString(), CartInfo.class);
                cartInfo.setIsChecked(isChecked);
                return cartInfo;
            }).forEach(cartInfo -> redisTemplate.opsForHash().put(cartKey, String.valueOf(cartInfo.getSkuId()), JSON.toJSONString(cartInfo)));

        }
    }

    /**
     * 清空购物车
     */
    @Override
    public void clearCart() {
        Long userId = AuthContextUtil.getUserInfo().getId();
        String cartKey = getCartKey(userId);
        redisTemplate.delete(cartKey);
    }

    /**
     * 获取选中的商品
     *
     * @return
     */
    @Override
    public List<CartItemInternalDto> getAllChecked(Long userId) {
        String cartKey = getCartKey(userId);

        //2 获取所有的购物项数据
        List<Object> objectList = redisTemplate.opsForHash().values(cartKey);
        if (!CollectionUtils.isEmpty(objectList)) {
            List<CartInfo> cartInfoList = objectList.stream().map(cartInfoJSON -> JSON.parseObject(cartInfoJSON.toString(), CartInfo.class))
                    .filter(cartInfo -> cartInfo.getIsChecked() == 1)
                    .collect(Collectors.toList());
            return cartInfoList.stream().map(this::toInternalDto).collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    @Override
    public List<AgentCartItemVo> getAgentCart(Long userId) {
        if (userId == null || userId <= 0) {
            throw new MyException(ResultCodeEnum.REQUEST_PARAM_INVALID);
        }
        List<Object> values = redisTemplate.opsForHash()
                .values(getCartKey(userId));
        if (CollectionUtils.isEmpty(values)) {
            return List.of();
        }
        return values.stream()
                .map(value -> JSON.parseObject(
                        value.toString(),
                        CartInfo.class
                ))
                .sorted((left, right) -> right.getCreateTime()
                        .compareTo(left.getCreateTime()))
                .map(this::toAgentCartItem)
                .collect(Collectors.toList());
    }


    /**
     * 删除选中的商品
     */
    @Override
    public void deleteChecked() {
        deleteCheckedForUser(AuthContextUtil.getUserInfo().getId());
    }

    private void deleteCheckedForUser(Long userId) {
        String cartKey = getCartKey(userId);

        //根据key获取redis所有的value值
        List<Object> objectList = redisTemplate.opsForHash().values(cartKey);

        //删除选中的商品
        if (!CollectionUtils.isEmpty(objectList)) {
            objectList.stream().map(object -> JSON.parseObject(object.toString(), CartInfo.class))
                    .filter(cartInfo -> cartInfo.getIsChecked() == 1)
                    .forEach(cartInfo -> redisTemplate.opsForHash()
                            .delete(cartKey, String.valueOf(cartInfo.getSkuId())));
        }
    }

    private CartItemVo toCartItemVo(CartInfo cartInfo) {
        CartItemVo result = new CartItemVo();
        BeanUtils.copyProperties(cartInfo, result);
        return result;
    }

    private CartItemInternalDto toInternalDto(CartInfo cartInfo) {
        CartItemInternalDto result = new CartItemInternalDto();
        BeanUtils.copyProperties(cartInfo, result);
        return result;
    }

    private AgentCartItemVo toAgentCartItem(CartInfo cartInfo) {
        AgentCartItemVo result = new AgentCartItemVo();
        result.setSkuId(cartInfo.getSkuId());
        result.setSkuName(cartInfo.getSkuName());
        result.setImageUrl(cartInfo.getImgUrl());
        result.setCartPrice(cartInfo.getCartPrice());
        result.setQuantity(cartInfo.getSkuNum());
        result.setSelected(Integer.valueOf(1).equals(cartInfo.getIsChecked()));
        return result;
    }
}
