package com.tzp.zjzx.manager.service.impl;

import com.alibaba.excel.EasyExcel;
import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.manager.listener.ExcelListener;
import com.tzp.zjzx.manager.mapper.CategoryMapper;
import com.tzp.zjzx.manager.service.CategoryService;
import com.tzp.zjzx.model.entity.product.Category;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.product.CategoryExcelVo;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    /**
     * 分类列表 每次查询一层数据
     * @param id
     * @return
     */
    @Override
    public List<Category> findCategoryListById(Long id) {
        //1.根据id值进行条件查询 返回list集合
        List<Category> categoryList = categoryMapper.selectCategoryByParentId(id);

        //2.遍历返回的list集合
        // 判断每个类别是否有子类，如果有 设置hasChildren=true
        if (!CollectionUtils.isEmpty(categoryList)) { //对list集合进行非空判断
            categoryList.forEach(category -> {
                //判断是否有子类
                int count = categoryMapper.selectCountByParentId(category.getId());
                if (count > 0) {    //有下一层分类
                    category.setHasChildren(true);
                } else {             //没有下一层分类
                    category.setHasChildren(false);
                }
            });
        }
        return categoryList;
    }

    /**
     * 数据导出
     * @param response
     */
    @Override
    public void exportData(HttpServletResponse response) {

        try {
            //1.设置响应头信息和其他信息
            response.setContentType("application/vnd.ms-excel");
            response.setCharacterEncoding("utf-8");
            //设置文件名 使用URLEncoder.encode可以防止中文乱码
            String fileName = URLEncoder.encode("分类数据", "UTF-8");

            //设置响应头信息 Content-disposition 表示文件以下载方式打开
            response.setHeader("Content-disposition","attachment;filename=" + fileName + ".xlsx");

            //2.调用mapper方法查询所有分类 返回list集合
            List<Category> categoryList = categoryMapper.findAll();

            //2.5类型转换 将list集合中的Category类型转换为CategoryExcelVo类型
            List<CategoryExcelVo> categoryExcelVoList = new ArrayList<>();
            for (Category category : categoryList) {
                CategoryExcelVo categoryExcelVo = new CategoryExcelVo();
                //把category对象中的属性值赋值给categoryExcelVo对象
                //BeanUtils.copyProperties spring提供的类型转换方法
                BeanUtils.copyProperties(category,categoryExcelVo);
                categoryExcelVoList.add(categoryExcelVo);
            }

            //3.调用EasyExcel的write方法 完成写操作
            EasyExcel.write(response.getOutputStream(), CategoryExcelVo.class)
                    .sheet("分类数据").doWrite(categoryExcelVoList);

        }catch (Exception e){
            e.printStackTrace();
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
    }

    /**
     * 数据导入
     * @param file
     */
    @Override
    public void importData(MultipartFile file) {

        //监听器
        ExcelListener<CategoryExcelVo> excelListener = new ExcelListener(categoryMapper);

        try {
            EasyExcel.read(file.getInputStream(), CategoryExcelVo.class,excelListener)
                    .sheet().doRead();

        } catch (IOException e) {
            e.printStackTrace();
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
    }
}
