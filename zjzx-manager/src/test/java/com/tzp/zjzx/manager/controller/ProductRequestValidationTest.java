package com.tzp.zjzx.manager.controller;

import com.tzp.zjzx.model.dto.product.ProductCreateDto;
import com.tzp.zjzx.model.dto.product.ProductSkuCreateDto;
import com.tzp.zjzx.model.dto.product.ProductSkuUpdateDto;
import com.tzp.zjzx.model.dto.product.ProductUpdateDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void rejectsNegativeWeightAndOverPrecisionVolume() {
        ProductSkuCreateDto sku = new ProductSkuCreateDto();
        sku.setWeight(new BigDecimal("-0.01"));
        sku.setVolume(new BigDecimal("0.001"));
        ProductCreateDto request = createRequest(sku);

        Set<ConstraintViolation<ProductCreateDto>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString().equals("productSkuList[0].weight")));
        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString().equals("productSkuList[0].volume")));
    }

    @Test
    void acceptsNumericWeightAndVolumeWithTwoDecimalPlaces() {
        ProductSkuCreateDto sku = new ProductSkuCreateDto();
        sku.setWeight(new BigDecimal("0.50"));
        sku.setVolume(new BigDecimal("0.02"));
        ProductCreateDto request = createRequest(sku);

        Set<ConstraintViolation<ProductCreateDto>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void updateRequiresProductAndSkuIds() {
        ProductUpdateDto request = new ProductUpdateDto();
        request.setName("Mac mini");
        request.setProductSkuList(Collections.singletonList(new ProductSkuUpdateDto()));

        Set<ConstraintViolation<ProductUpdateDto>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString().equals("id")));
        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString().equals("productSkuList[0].id")));
    }

    private ProductCreateDto createRequest(ProductSkuCreateDto sku) {
        ProductCreateDto request = new ProductCreateDto();
        request.setName("Mac mini");
        request.setProductSkuList(Collections.singletonList(sku));
        return request;
    }
}
