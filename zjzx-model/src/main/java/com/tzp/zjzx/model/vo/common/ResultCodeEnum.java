package com.tzp.zjzx.model.vo.common;

import lombok.Getter;

@Getter // 提供获取属性值的getter方法
public enum ResultCodeEnum {

    SUCCESS(200 , "操作成功") ,
    LOGIN_ERROR(201 , "用户名或者密码错误"),
    VALIDATECODE_ERROR(202 , "验证码错误") ,
    LOGIN_AUTH(208 , "用户未登录"),
    USER_NAME_IS_EXISTS(209 , "用户名已经存在"),
    SYSTEM_ERROR(9999 , "您的网络有问题请稍后重试"),
    NODE_ERROR( 217, "该节点下有子节点，不可以删除"),
    DATA_ERROR(204, "数据异常"),
    ACCOUNT_STOP( 216, "账号已停用"),

    STOCK_LESS( 219, "库存不足"),
    STOCK_REQUEST_INVALID(220, "库存请求参数错误"),
    STOCK_RESERVATION_STATE_ERROR(221, "库存预占状态异常"),
    ORDER_SUBMIT_REQUEST_INVALID(222, "订单提交标识不能为空"),

    UPLOAD_FILE_EMPTY(223, "上传文件不能为空"),
    UPLOAD_FILE_TOO_LARGE(224, "上传图片大小超过限制"),
    UPLOAD_FILE_TYPE_NOT_ALLOWED(225, "仅支持 JPG、PNG、WebP 图片"),
    UPLOAD_FILE_CONTENT_INVALID(226, "上传图片内容无效"),
    UPLOAD_STORAGE_ERROR(227, "图片存储失败，请稍后重试"),

    PRODUCT_SKU_REQUIRED(228, "商品至少需要一个SKU"),
    PRODUCT_SKU_INVALID(229, "商品SKU数据无效"),
    REQUEST_PARAM_INVALID(230, "请求参数校验失败"),
    ORDER_NOT_FOUND(231, "订单不存在"),
    ORDER_CANNOT_CANCEL(232, "当前订单状态不允许取消"),
    ORDER_CANNOT_DELETE(233, "当前订单状态不允许删除"),
    SECKILL_ACTIVITY_NOT_FOUND(234, "秒杀活动不存在"),
    SECKILL_ACTIVITY_STATE_ERROR(235, "秒杀活动状态异常"),
    SECKILL_ACTIVITY_NOT_ACTIVE(236, "秒杀活动尚未开始或已经结束"),
    SECKILL_SOLD_OUT(237, "秒杀商品已售罄"),
    SECKILL_DUPLICATE_REQUEST(238, "秒杀请求已受理"),
    SECKILL_USER_LIMIT(239, "每位用户限购一件"),
    SECKILL_REQUEST_FAILED(240, "秒杀请求处理失败"),
    SECKILL_RATE_LIMITED(241, "请求过于频繁，请稍后重试"),
    ORDER_CANNOT_PAY(243, "当前订单状态不允许支付"),
    USER_ADDRESS_NOT_FOUND(244, "收货地址不存在"),
    USER_ADDRESS_REGION_INVALID(245, "省市区信息无效"),
    TEST_DATA_BATCH_INVALID(246, "测试数据批次参数无效"),
    TEST_DATA_USER_CONFLICT(247, "测试账号与现有用户冲突"),

    ;

    private Integer code ;      // 业务状态码
    private String message ;    // 响应消息

    private ResultCodeEnum(Integer code , String message) {
        this.code = code ;
        this.message = message ;
    }

}
