package com.tzp.zjzx.model.entity.product;

import com.tzp.zjzx.model.entity.base.BaseEntity;
import lombok.Data;

@Data
public class ProductDetails extends BaseEntity {

	private Long productId;
	private String imageUrls;

}