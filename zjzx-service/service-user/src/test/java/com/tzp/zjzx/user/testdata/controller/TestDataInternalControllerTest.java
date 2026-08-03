package com.tzp.zjzx.user.testdata.controller;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.user.testdata.config.TestDataProperties;
import com.tzp.zjzx.user.testdata.dto.TestDataBatchUserRequest;
import com.tzp.zjzx.user.testdata.dto.TestDataBatchUserResult;
import com.tzp.zjzx.user.testdata.service.TestDataUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TestDataInternalControllerTest {

    private TestDataUserService service;
    private TestDataInternalController controller;
    private TestDataBatchUserRequest request;

    @BeforeEach
    void setUp() {
        service = mock(TestDataUserService.class);
        TestDataProperties properties = new TestDataProperties();
        properties.setApiKey("test-data-secret");
        controller = new TestDataInternalController(service, properties, "internal-secret");
        request = new TestDataBatchUserRequest();
        request.setCount(1);
        request.setDefaultPassword("LoadTest@123456");
    }

    @Test
    void acceptsBothValidKeys() {
        TestDataBatchUserResult expected = new TestDataBatchUserResult(1, 0, List.of());
        when(service.createOrResetUsers(request)).thenReturn(expected);

        Result<TestDataBatchUserResult> result =
                controller.createUsers("internal-secret", "test-data-secret", request);

        assertEquals(ResultCodeEnum.SUCCESS.getCode(), result.getCode());
        assertEquals(expected, result.getData());
        verify(service).createOrResetUsers(request);
    }

    @Test
    void rejectsWrongInternalTokenBeforeCallingService() {
        MyException exception = assertThrows(
                MyException.class,
                () -> controller.createUsers("wrong", "test-data-secret", request)
        );

        assertEquals(ResultCodeEnum.LOGIN_AUTH, exception.getResultCodeEnum());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsWrongTestDataKeyBeforeCallingService() {
        MyException exception = assertThrows(
                MyException.class,
                () -> controller.createUsers("internal-secret", "wrong", request)
        );

        assertEquals(ResultCodeEnum.LOGIN_AUTH, exception.getResultCodeEnum());
        verifyNoInteractions(service);
    }

    @Test
    void endpointRequiresDedicatedProfileAndExplicitEnableFlag() {
        Profile profile = TestDataInternalController.class.getAnnotation(Profile.class);
        ConditionalOnProperty condition =
                TestDataInternalController.class.getAnnotation(ConditionalOnProperty.class);

        assertNotNull(profile);
        assertArrayEquals(new String[]{"test-data"}, profile.value());
        assertNotNull(condition);
        assertEquals("zjzx.test-data", condition.prefix());
        assertArrayEquals(new String[]{"enabled"}, condition.name());
        assertEquals("true", condition.havingValue());
    }
}
