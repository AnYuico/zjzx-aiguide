package com.tzp.zjzx.user.testdata.service.impl;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.common.security.LoginSessionService;
import com.tzp.zjzx.common.security.PasswordService;
import com.tzp.zjzx.model.entity.user.UserInfo;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.user.mapper.UserInfoMapper;
import com.tzp.zjzx.user.testdata.config.TestDataProperties;
import com.tzp.zjzx.user.testdata.dto.TestDataBatchUserRequest;
import com.tzp.zjzx.user.testdata.dto.TestDataBatchUserResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestDataUserServiceImplTest {

    @Mock
    private UserInfoMapper userInfoMapper;

    @Mock
    private PasswordService passwordService;

    @Mock
    private LoginSessionService loginSessionService;

    private TestDataProperties properties;
    private TestDataUserServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new TestDataProperties();
        properties.setMaxBatchSize(100);
        service = new TestDataUserServiceImpl(
                userInfoMapper,
                passwordService,
                loginSessionService,
                properties,
                "http://example.test/default.png"
        );
    }

    @Test
    void createsMarkedUsersWithoutReturningSecrets() {
        TestDataBatchUserRequest request = request(2);
        when(passwordService.encode("LoadTest@123456")).thenReturn("bcrypt-hash");

        TestDataBatchUserResult result = service.createOrResetUsers(request);

        assertEquals(2, result.getCreatedCount());
        assertEquals(0, result.getResetCount());
        assertEquals(List.of("19910000000", "19910000001"),
                result.getAccounts().stream()
                        .map(account -> account.getUsername())
                        .collect(Collectors.toList()));
        assertFalse(result.getAccounts().toString().contains("LoadTest@123456"));
        assertFalse(result.getAccounts().toString().contains("bcrypt-hash"));

        ArgumentCaptor<UserInfo> captor = ArgumentCaptor.forClass(UserInfo.class);
        verify(userInfoMapper, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals("load-test:jmeter-load-test", captor.getAllValues().get(0).getMemo());
        assertEquals("bcrypt-hash", captor.getAllValues().get(0).getPassword());
        verifyNoInteractions(loginSessionService);
    }

    @Test
    void resetsOnlyAccountWithMatchingTestMarkerAndRevokesOldSessions() {
        TestDataBatchUserRequest request = request(1);
        UserInfo existing = new UserInfo();
        existing.setId(33L);
        existing.setMemo("load-test:jmeter-load-test");
        when(userInfoMapper.selectByUserName("19910000000")).thenReturn(existing);
        when(passwordService.encode("LoadTest@123456")).thenReturn("new-bcrypt-hash");
        when(userInfoMapper.resetTestUser(
                33L,
                "load-test:jmeter-load-test",
                "new-bcrypt-hash",
                "压测用户0001",
                "19910000000",
                "http://example.test/default.png"
        )).thenReturn(1);

        TestDataBatchUserResult result = service.createOrResetUsers(request);

        assertEquals(0, result.getCreatedCount());
        assertEquals(1, result.getResetCount());
        verify(loginSessionService).revokeUserSessionsAfterCommit(33L);
        verify(userInfoMapper, never()).save(any(UserInfo.class));
    }

    @Test
    void rejectsCollisionWithOrdinaryUser() {
        TestDataBatchUserRequest request = request(1);
        UserInfo existing = new UserInfo();
        existing.setId(33L);
        existing.setMemo("ordinary-user");
        when(userInfoMapper.selectByUserName("19910000000")).thenReturn(existing);
        when(passwordService.encode("LoadTest@123456")).thenReturn("bcrypt-hash");

        MyException exception = assertThrows(
                MyException.class,
                () -> service.createOrResetUsers(request)
        );

        assertEquals(ResultCodeEnum.TEST_DATA_USER_CONFLICT, exception.getResultCodeEnum());
        verify(userInfoMapper, never()).resetTestUser(
                any(), any(), any(), any(), any(), any());
        verifyNoInteractions(loginSessionService);
    }

    @Test
    void rejectsBatchLargerThanConfiguredLimit() {
        properties.setMaxBatchSize(20);
        TestDataBatchUserRequest request = request(21);

        MyException exception = assertThrows(
                MyException.class,
                () -> service.createOrResetUsers(request)
        );

        assertEquals(ResultCodeEnum.TEST_DATA_BATCH_INVALID, exception.getResultCodeEnum());
        verifyNoInteractions(userInfoMapper, passwordService, loginSessionService);
    }

    @Test
    void rejectsPhoneSequenceOverflow() {
        TestDataBatchUserRequest request = request(2);
        request.setSequenceStart(99999999);

        MyException exception = assertThrows(
                MyException.class,
                () -> service.createOrResetUsers(request)
        );

        assertEquals(ResultCodeEnum.TEST_DATA_BATCH_INVALID, exception.getResultCodeEnum());
        verifyNoInteractions(userInfoMapper, passwordService, loginSessionService);
    }

    private TestDataBatchUserRequest request(int count) {
        TestDataBatchUserRequest request = new TestDataBatchUserRequest();
        request.setCount(count);
        request.setDefaultPassword("LoadTest@123456");
        return request;
    }
}
