package com.tzp.zjzx.product.service.impl;

import com.alibaba.fastjson.JSON;
import com.tzp.zjzx.model.entity.product.Category;
import com.tzp.zjzx.model.vo.product.CategoryVo;
import com.tzp.zjzx.product.mapper.CategoryMapper;
import com.tzp.zjzx.product.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl implements CategoryService {


    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;



    /**
     * 查询所有一级分类
     *
     * @return
     */
    @Override
    public List<CategoryVo> selectOneCategory() {
        //1 查询redis 是否有一级分类
        String categoryOneJson = redisTemplate.opsForValue().get("category:one");

        //2 如果redis包含所有一级分类 直接返回
        if (StringUtils.hasText(categoryOneJson)) {
            //将json字符串转换为list集合 返回
            return JSON.parseArray(categoryOneJson, Category.class).stream()
                    .map(this::toCategoryVo)
                    .collect(Collectors.toList());
        }
        //3 如果redis中没有，那么查询mysql，将数据库内容返回 并写入redis
        List<Category> categoryList = categoryMapper.selectOneCategory();
        redisTemplate.opsForValue()
                .set("category:one",
                JSON.toJSONString(categoryList),
                7, TimeUnit.DAYS);

        return categoryList.stream().map(this::toCategoryVo).collect(Collectors.toList());
    }

    /**
     * 查询所有分类 树形封装
     *
     * @return
     */
    @Cacheable(value = "category", key = "'all:v2'")
    @Override
    public List<CategoryVo> findCategoryTree() {
        //1 查询所有分类 返回list集合
        List<Category> allCategoryList = categoryMapper.findAll();

        //2 遍历所有分类list集合 通过条件parentId=0得到所有一级分类
        List<Category> oneCategoryList =
                allCategoryList.stream()
                        .filter(category -> category.getParentId() == 0)
                        .collect(Collectors.toList());

        //3 遍历所有一级分类list集合 通过条件parentId=一级分类id 得到所有二级分类
        oneCategoryList.forEach(oneCategory -> {
            List<Category> twoCategoryList =
                    allCategoryList.stream()
                            .filter(item -> Objects.equals(item.getParentId(), oneCategory.getId()))
                            .collect(Collectors.toList());
            //把二级分类封装到一级分类中
            oneCategory.setChildren(twoCategoryList);

            //4 遍历所有二级分类 通过条件parentId=二级分类id 得到所有三级分类
            twoCategoryList.forEach(twoCategory -> {
                List<Category> threeCategoryList =
                        allCategoryList.stream()
                                .filter(item -> Objects.equals(item.getParentId(), twoCategory.getId()))
                                .collect(Collectors.toList());
                //把三级分类封装到二级分类中
                twoCategory.setChildren(threeCategoryList);
            });
        });

        return oneCategoryList.stream().map(this::toCategoryVo).collect(Collectors.toList());
    }

    private CategoryVo toCategoryVo(Category category) {
        CategoryVo result = new CategoryVo();
        BeanUtils.copyProperties(category, result, "children");
        if (category.getChildren() != null) {
            result.setChildren(category.getChildren().stream()
                    .map(this::toCategoryVo)
                    .collect(Collectors.toList()));
        }
        return result;
    }
}
