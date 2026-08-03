package com.tzp.zjzx.manager.service.impl;

import com.tzp.zjzx.common.log.service.OperLogService;
import com.tzp.zjzx.manager.mapper.SysOperLogMapper;
import com.tzp.zjzx.model.entity.system.SysOperLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OperLogServiceImpl implements OperLogService {


    @Autowired
    private SysOperLogMapper sysOperLogMapper;

    /**
     * 保存日志的数据
     * @param sysOperLog
     */
    @Override
    public void saveSysOperLog(SysOperLog sysOperLog) {
        sysOperLogMapper.insert(sysOperLog);
    }
}
