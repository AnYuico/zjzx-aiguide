package com.tzp.zjzx.common.log.service;

import com.tzp.zjzx.model.entity.system.SysOperLog;

public interface OperLogService {                  // 保存日志数据
    public abstract void saveSysOperLog(SysOperLog sysOperLog) ;
}
