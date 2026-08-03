package com.tzp.zjzx.common.log.aspect;

import com.tzp.zjzx.common.log.annotation.Log;
import com.tzp.zjzx.common.log.service.OperLogService;
import com.tzp.zjzx.common.log.utils.LogUtil;
import com.tzp.zjzx.model.entity.system.SysOperLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LogAspect {

    @Autowired
    private OperLogService operLogService;

    //环绕通知
    @Around("@annotation(sysLog)")
    public Object doAroundAdvice(ProceedingJoinPoint joinPoint, Log sysLog) {
/*
        String title = sysLog.title();
        int i = sysLog.businessType();
        System.out.println("title = " + title + " businessType = " + i);*/

        //业务方法调用之前 封装数据
        SysOperLog sysOperLog = new SysOperLog();
        LogUtil.beforeHandleLog(sysLog, joinPoint, sysOperLog);


        //业务方法
        Object proceed = null;
        try {
            proceed = joinPoint.proceed();
//            System.out.println("在业务方法之后执行....");

            //业务方法调用之后 封装数据
            LogUtil.afterHandlLog(sysLog, proceed, sysOperLog, 0, null);
        } catch (Throwable e) {

            e.printStackTrace();
            LogUtil.afterHandlLog(sysLog, proceed, sysOperLog, 1, e.getMessage());
            throw new RuntimeException(e);

        }finally {
            //调用service保存日志
            operLogService.saveSysOperLog(sysOperLog);
        }


        return proceed;
    }
}
