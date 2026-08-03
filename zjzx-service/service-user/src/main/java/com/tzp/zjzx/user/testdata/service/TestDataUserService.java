package com.tzp.zjzx.user.testdata.service;

import com.tzp.zjzx.user.testdata.dto.TestDataBatchUserRequest;
import com.tzp.zjzx.user.testdata.dto.TestDataBatchUserResult;

public interface TestDataUserService {

    TestDataBatchUserResult createOrResetUsers(TestDataBatchUserRequest request);
}
