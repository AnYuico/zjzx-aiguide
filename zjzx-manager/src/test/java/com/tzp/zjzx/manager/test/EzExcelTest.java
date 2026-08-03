package com.tzp.zjzx.manager.test;

import com.alibaba.excel.EasyExcel;
import com.tzp.zjzx.model.vo.product.CategoryExcelVo;

import java.util.ArrayList;
import java.util.List;

public class EzExcelTest {
    public static void main(String[] args) {
        //read();

        write();
    }

    //读操作
    public static void read() {
        //1 定义待读取的excel文件位置
        String fileName = "D:\\CloudStage_Download\\BaiduNetdiskDownload\\紫金甄选\\资料\\01.xlsx";
        //2. 调用方法
        ExcelListener<CategoryExcelVo> excelListener = new ExcelListener<>();
        EasyExcel.read(fileName, CategoryExcelVo.class, excelListener)
                .sheet().doRead();
        List<CategoryExcelVo> data = excelListener.getData();
        System.out.println(data);
    }

    //写操作
    public static void write() {
        List<CategoryExcelVo> list = new ArrayList<>();
        list.add(new CategoryExcelVo(1L , "数码办公" , "",0L, 1, 1)) ;
        list.add(new CategoryExcelVo(11L , "华为手机" , "",1L, 1, 2)) ;
        EasyExcel.write("D:\\CloudStage_Download\\BaiduNetdiskDownload\\紫金甄选\\资料\\02.xlsx", CategoryExcelVo.class)
                .sheet("分类数据").doWrite(list);
    }
}
