package com.tzp.zjzx.common.security;

import com.tzp.zjzx.model.vo.h5.CartItemVo;
import com.tzp.zjzx.model.vo.h5.UserAddressVo;
import com.tzp.zjzx.model.vo.order.OrderDetailVo;
import com.tzp.zjzx.model.vo.product.ProductSkuVo;
import com.tzp.zjzx.model.vo.system.SysUserInfoVo;
import com.tzp.zjzx.model.vo.system.SysUserListVo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicVoBoundaryTest {

    @Test
    void publicVosDoNotExposeSensitivePersistenceFields() {
        assertNoGetter(SysUserInfoVo.class, "getPassword");
        assertNoGetter(SysUserListVo.class, "getPassword");
        assertNoGetter(ProductSkuVo.class, "getCostPrice");
        assertNoGetter(OrderDetailVo.class, "getUserId");
        assertNoGetter(OrderDetailVo.class, "getRequestId");
        assertNoGetter(UserAddressVo.class, "getUserId");
        assertNoGetter(CartItemVo.class, "getUserId");
    }

    private void assertNoGetter(Class<?> type, String getterName) {
        assertThrows(NoSuchMethodException.class, () -> type.getMethod(getterName));
    }
}
