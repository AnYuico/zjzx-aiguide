package com.tzp.zjzx.manager.service;

import com.tzp.zjzx.model.entity.product.Category;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CategoryService {
    /**
     * 分类列表 每次查询一层数据
     * @param id
     * @return
     */
    List<Category> findCategoryListById(Long id);

    /**
     * 数据导出
     * @param response
     */
    void exportData(HttpServletResponse response);

    /**
     * 数据导入
     * @param file
     */
    void importData(MultipartFile file);
}
