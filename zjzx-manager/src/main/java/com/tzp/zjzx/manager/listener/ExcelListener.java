package com.tzp.zjzx.manager.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.util.ListUtils;
import com.tzp.zjzx.manager.mapper.CategoryMapper;
import com.tzp.zjzx.model.vo.product.CategoryExcelVo;
import org.apache.poi.ss.formula.functions.T;

import java.util.List;

//监听器
public class ExcelListener<T> implements ReadListener<T> {

    /**
     * 每隔5条存储数据库，实际使用中可以100条，然后清理list ，方便内存回收
     */
    private static final int BATCH_COUNT = 100;
    /**
     * 缓存的数据
     */
    private List<T> cacheDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);


    //通过构造传递mapper 操作数据库
    private CategoryMapper categoryMapper;
    public ExcelListener(CategoryMapper categoryMapper){
        this.categoryMapper = categoryMapper;
    }

    //从第二行开始读取，把每行内容封装到对象中
    @Override
    public void invoke(T t, AnalysisContext analysisContext) {
        //把每行数据的对象t放到缓存cacheDataList中
        cacheDataList.add(t);
        if (cacheDataList.size() >= BATCH_COUNT) {
            //调用方法 一次性存入数据库
            saveData();
            //清空缓存
            cacheDataList.clear();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        //保存数据 为防止数据丢失  手动调用一次
        saveData();
    }

    /**
     * 保存数据到数据库
     */
    private void saveData() {
        categoryMapper.batchInsert((List<CategoryExcelVo>) cacheDataList);
    }
}
