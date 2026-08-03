package com.tzp.zjzx.manager.mapper;

import com.tzp.zjzx.model.entity.system.SysOperLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysOperLogMapper {
    /**
     * 新增操作日志
     * @param sysOperLog
     */
    void insert(SysOperLog sysOperLog);

}
