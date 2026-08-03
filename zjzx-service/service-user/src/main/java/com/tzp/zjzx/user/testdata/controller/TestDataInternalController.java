package com.tzp.zjzx.user.testdata.controller;

import com.tzp.zjzx.common.security.InternalApiAuth;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.user.testdata.config.TestDataProperties;
import com.tzp.zjzx.user.testdata.dto.TestDataBatchUserRequest;
import com.tzp.zjzx.user.testdata.dto.TestDataBatchUserResult;
import com.tzp.zjzx.user.testdata.service.TestDataUserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("test-data")
@ConditionalOnProperty(prefix = "zjzx.test-data", name = "enabled", havingValue = "true")
@RequestMapping("/api/user/internal/test-data")
public class TestDataInternalController {

    public static final String TEST_DATA_KEY_HEADER = "X-Test-Data-Key";

    private final TestDataUserService testDataUserService;
    private final TestDataProperties testDataProperties;
    private final String internalApiToken;

    public TestDataInternalController(TestDataUserService testDataUserService,
                                      TestDataProperties testDataProperties,
                                      @Value("${zjzx.internal-api.token}") String internalApiToken) {
        this.testDataUserService = testDataUserService;
        this.testDataProperties = testDataProperties;
        this.internalApiToken = internalApiToken;
    }

    @PostMapping("/users/batch")
    public Result<TestDataBatchUserResult> createUsers(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String internalToken,
            @RequestHeader(TEST_DATA_KEY_HEADER) String testDataKey,
            @Valid @RequestBody TestDataBatchUserRequest request) {
        InternalApiAuth.verify(internalApiToken, internalToken);
        InternalApiAuth.verify(testDataProperties.getApiKey(), testDataKey);
        return Result.build(
                testDataUserService.createOrResetUsers(request),
                ResultCodeEnum.SUCCESS
        );
    }
}
