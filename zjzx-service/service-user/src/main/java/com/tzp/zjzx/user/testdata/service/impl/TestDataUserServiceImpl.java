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
import com.tzp.zjzx.user.testdata.dto.TestDataUserAccountVo;
import com.tzp.zjzx.user.testdata.service.TestDataUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Profile("test-data")
@ConditionalOnProperty(prefix = "zjzx.test-data", name = "enabled", havingValue = "true")
public class TestDataUserServiceImpl implements TestDataUserService {

    private static final String TEST_MARKER_PREFIX = "load-test:";

    private final UserInfoMapper userInfoMapper;
    private final PasswordService passwordService;
    private final LoginSessionService loginSessionService;
    private final TestDataProperties testDataProperties;
    private final String defaultAvatarUrl;

    public TestDataUserServiceImpl(UserInfoMapper userInfoMapper,
                                   PasswordService passwordService,
                                   LoginSessionService loginSessionService,
                                   TestDataProperties testDataProperties,
                                   @Value("${zjzx.user.default-avatar-url}") String defaultAvatarUrl) {
        this.userInfoMapper = userInfoMapper;
        this.passwordService = passwordService;
        this.loginSessionService = loginSessionService;
        this.testDataProperties = testDataProperties;
        this.defaultAvatarUrl = defaultAvatarUrl;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized TestDataBatchUserResult createOrResetUsers(TestDataBatchUserRequest request) {
        validateBatch(request);
        String marker = TEST_MARKER_PREFIX + request.getTag();
        int createdCount = 0;
        int resetCount = 0;
        List<TestDataUserAccountVo> accounts = new ArrayList<>(request.getCount());

        for (int index = 0; index < request.getCount(); index++) {
            String phone = buildPhone(request, index);
            String nickName = request.getNickNamePrefix() + String.format("%04d", index + 1);
            UserInfo existing = userInfoMapper.selectByUserName(phone);
            String encodedPassword = passwordService.encode(request.getDefaultPassword());

            if (existing == null) {
                userInfoMapper.save(buildUser(phone, nickName, marker, encodedPassword));
                createdCount++;
            } else {
                resetExistingTestUser(existing, phone, nickName, marker, encodedPassword);
                resetCount++;
            }
            accounts.add(new TestDataUserAccountVo(phone, phone, nickName));
        }
        return new TestDataBatchUserResult(createdCount, resetCount, accounts);
    }

    private void validateBatch(TestDataBatchUserRequest request) {
        if (request == null
                || request.getCount() == null
                || request.getCount() < 1
                || request.getCount() > testDataProperties.getMaxBatchSize()
                || request.getPhonePrefix() == null
                || !request.getPhonePrefix().matches("^1[3-9]\\d$")
                || request.getSequenceStart() == null
                || request.getSequenceStart() < 0
                || request.getDefaultPassword() == null
                || !request.getDefaultPassword().matches("^[\\x21-\\x7E]{8,72}$")
                || request.getNickNamePrefix() == null
                || request.getNickNamePrefix().trim().isEmpty()
                || request.getNickNamePrefix().length() > 40
                || request.getTag() == null
                || !request.getTag().matches("^[A-Za-z0-9._-]{1,40}$")) {
            throw new MyException(ResultCodeEnum.TEST_DATA_BATCH_INVALID);
        }

        long sequenceEnd = (long) request.getSequenceStart() + request.getCount() - 1L;
        if (sequenceEnd > 99999999L) {
            throw new MyException(ResultCodeEnum.TEST_DATA_BATCH_INVALID);
        }
    }

    private String buildPhone(TestDataBatchUserRequest request, int index) {
        int sequence = request.getSequenceStart() + index;
        return request.getPhonePrefix() + String.format("%08d", sequence);
    }

    private UserInfo buildUser(String phone,
                               String nickName,
                               String marker,
                               String encodedPassword) {
        UserInfo userInfo = new UserInfo();
        userInfo.setUsername(phone);
        userInfo.setPassword(encodedPassword);
        userInfo.setNickName(nickName);
        userInfo.setPhone(phone);
        userInfo.setAvatar(defaultAvatarUrl);
        userInfo.setSex(0);
        userInfo.setStatus(1);
        userInfo.setMemo(marker);
        return userInfo;
    }

    private void resetExistingTestUser(UserInfo existing,
                                       String phone,
                                       String nickName,
                                       String marker,
                                       String encodedPassword) {
        if (!Objects.equals(existing.getMemo(), marker) || existing.getId() == null) {
            throw new MyException(ResultCodeEnum.TEST_DATA_USER_CONFLICT);
        }
        int affectedRows = userInfoMapper.resetTestUser(
                existing.getId(),
                marker,
                encodedPassword,
                nickName,
                phone,
                defaultAvatarUrl
        );
        if (affectedRows != 1) {
            throw new MyException(ResultCodeEnum.TEST_DATA_USER_CONFLICT);
        }
        loginSessionService.revokeUserSessionsAfterCommit(existing.getId());
    }
}
