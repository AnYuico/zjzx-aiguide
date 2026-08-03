package com.tzp.zjzx.model.vo.order;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
public class OrderDetailVo {

    private Long id;
    private String orderNo;
    private Integer orderSource;
    private BigDecimal totalAmount;
    private BigDecimal couponAmount;
    private BigDecimal originalTotalAmount;
    private BigDecimal feightFee;
    private Integer payType;
    private Integer orderStatus;
    private String receiverName;
    private String receiverPhone;
    private String receiverTagName;
    private String receiverProvince;
    private String receiverCity;
    private String receiverDistrict;
    private String receiverAddress;
    private String remark;
    private String cancelReason;
    private List<OrderItemVo> orderItemList;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date paymentTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date deliveryTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date receiveTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date cancelTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
