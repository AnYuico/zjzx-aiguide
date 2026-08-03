package com.tzp.zjzx.user.testdata.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TestDataBatchUserResult {

    private int createdCount;
    private int resetCount;
    private List<TestDataUserAccountVo> accounts;
}
