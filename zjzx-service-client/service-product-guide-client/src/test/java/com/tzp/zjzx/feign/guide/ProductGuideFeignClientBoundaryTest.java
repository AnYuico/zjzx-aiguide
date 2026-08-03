package com.tzp.zjzx.feign.guide;

import com.tzp.zjzx.ai.contract.dto.ProductGuideQueryDto;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductGuideFeignClientBoundaryTest {

    @Test
    void clientMethodsExposeOnlyProductGuideArguments() throws NoSuchMethodException {
        Method search = ProductGuideFeignClient.class.getMethod(
                "search",
                ProductGuideQueryDto.class
        );
        Method getBySkuId = ProductGuideFeignClient.class.getMethod("getBySkuId", Long.class);

        assertEquals(1, search.getParameterCount());
        assertEquals(1, getBySkuId.getParameterCount());
    }

    @Test
    void internalTokenIsInjectedByConfiguration() {
        ProductGuideFeignConfiguration configuration = new ProductGuideFeignConfiguration();
        RequestInterceptor interceptor = configuration
                .productGuideInternalTokenInterceptor("internal-secret");
        RequestTemplate requestTemplate = new RequestTemplate();

        interceptor.apply(requestTemplate);

        Collection<String> values = requestTemplate.headers()
                .get(ProductGuideFeignConfiguration.INTERNAL_TOKEN_HEADER);
        assertEquals(1, values.size());
        assertEquals("internal-secret", values.iterator().next());
        assertThrows(
                IllegalStateException.class,
                () -> configuration.productGuideInternalTokenInterceptor(" ")
        );
    }
}
