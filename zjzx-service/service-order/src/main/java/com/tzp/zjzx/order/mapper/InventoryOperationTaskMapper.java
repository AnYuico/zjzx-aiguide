package com.tzp.zjzx.order.mapper;

import com.tzp.zjzx.model.entity.order.InventoryOperationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface InventoryOperationTaskMapper {

    int insertIgnore(@Param("orderNo") String orderNo,
                     @Param("operationType") Integer operationType,
                     @Param("nextRetryTime") Date nextRetryTime);

    List<InventoryOperationTask> findPending(@Param("limit") int limit);

    int markSuccess(@Param("orderNo") String orderNo,
                    @Param("operationType") Integer operationType);

    int markRetry(@Param("id") Long id,
                  @Param("status") Integer status,
                  @Param("retryCount") Integer retryCount,
                  @Param("nextRetryTime") Date nextRetryTime,
                  @Param("lastError") String lastError);
}
