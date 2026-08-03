package com.tzp.zjzx.common.exception;

import cn.hutool.core.lang.Range;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import lombok.Data;

@Data
public class MyException extends RuntimeException {
    private Integer code;
    private String message;
    private ResultCodeEnum resultCodeEnum;

    public MyException(ResultCodeEnum resultCodeEnum){
        this.resultCodeEnum = resultCodeEnum;
        this.code = resultCodeEnum.getCode();
        this.message = resultCodeEnum.getMessage();
    }
}
