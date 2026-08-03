package com.tzp.zjzx.manager.service;

import com.tzp.zjzx.model.vo.system.ValidateCodeVo;

public interface ValidateCodeService {
    /**
     * 生成验证码
     * @return
     */
    ValidateCodeVo generateValidateCode();

}
