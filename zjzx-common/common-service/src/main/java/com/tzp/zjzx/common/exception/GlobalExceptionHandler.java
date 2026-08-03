package com.tzp.zjzx.common.exception;

import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    //全局异常处理
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Result error(Exception e){
        e.printStackTrace();
        return Result.build(null, ResultCodeEnum.SYSTEM_ERROR);
    }

    //自定义异常处理
    @ExceptionHandler(MyException.class)
    @ResponseBody
    public Result error(MyException e){
        return Result.build(null, e.getResultCodeEnum());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseBody
    public Result uploadTooLarge(MaxUploadSizeExceededException e) {
        return Result.build(null, ResultCodeEnum.UPLOAD_FILE_TOO_LARGE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public Result validationError(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return Result.build(null, ResultCodeEnum.REQUEST_PARAM_INVALID.getCode(), message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseBody
    public Result requestBodyFormatError(HttpMessageNotReadableException e) {
        return Result.build(
                null,
                ResultCodeEnum.REQUEST_PARAM_INVALID.getCode(),
                "请求参数格式错误，请检查日期、数值及字段类型"
        );
    }


}
