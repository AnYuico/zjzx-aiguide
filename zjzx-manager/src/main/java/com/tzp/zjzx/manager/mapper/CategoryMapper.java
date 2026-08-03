package com.tzp.zjzx.manager.mapper;

import com.tzp.zjzx.model.entity.product.Category;
import com.tzp.zjzx.model.vo.product.CategoryExcelVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CategoryMapper {

    /**
     * 1.查询此id类别的下一层子类
     * @param id
     * @return
     */
    List<Category> selectCategoryByParentId(Long id);

    /**
     * 2.查询此id类别的子类数量
     * @param id
     * @return
     */
    int selectCountByParentId(Long id);

    /**
     * 3.查询所有的分类
     * @return
     */
    List<Category> findAll();

    /**
     * 4.批量添加数据
     * @param categoryList
     */
    void batchInsert(List<CategoryExcelVo> categoryList);
}
