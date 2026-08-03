package com.tzp.zjzx.manager.test;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;

import java.util.ArrayList;
import java.util.List;

public class ExcelListener<T> extends AnalysisEventListener<T> {

    private List<T> data = new ArrayList<>();

    //读取excel内容
    //从第二行开始读取，把每行数据封装到t对象中
    @Override
    public void invoke(T t, AnalysisContext analysisContext) {
        data.add(t);
    }

    public List<T> getData(){
        return data;
    }

    //所有读操作结束后执行
    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }
}
