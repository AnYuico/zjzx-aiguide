package com.tzp.zjzx.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.dto.seckill.SeckillSubmitDto;
import com.tzp.zjzx.model.event.seckill.SeckillOrderRequestedEvent;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.seckill.SeckillResultVo;
import com.tzp.zjzx.model.vo.seckill.SeckillSubmitVo;
import com.tzp.zjzx.utils.SeckillRedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SeckillSubmissionService {

    private final SeckillRedisService redisService;
    private final SeckillMqPublisher mqPublisher;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SeckillSubmissionService(SeckillRedisService redisService,
                                    SeckillMqPublisher mqPublisher,
                                    StringRedisTemplate redisTemplate,
                                    ObjectMapper objectMapper) {
        this.redisService = redisService;
        this.mqPublisher = mqPublisher;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public SeckillSubmitVo submit(Long activityId, Long skuId, Long userId,
                                  SeckillSubmitDto dto) {
        if (dto == null) {
            throw new MyException(ResultCodeEnum.REQUEST_PARAM_INVALID);
        }
        SeckillOrderRequestedEvent event = redisService.reserve(
                activityId, skuId, userId, dto.getUserAddressId(), dto.getRequestId());
        Double pendingScore = redisTemplate.opsForZSet().score(
                SeckillRedisKeys.pending(activityId, skuId), dto.getRequestId());
        if (pendingScore != null) {
            mqPublisher.publish(event);
        }
        return new SeckillSubmitVo(event.getRequestId(), event.getOrderNo(),
                0, "QUEUED");
    }

    public SeckillResultVo getResult(Long activityId, Long skuId,
                                     Long userId, String requestId) {
        Object raw = redisTemplate.opsForHash().get(
                SeckillRedisKeys.results(activityId, skuId), requestId);
        if (raw == null) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_FOUND);
        }
        try {
            JsonNode node = objectMapper.readTree(raw.toString());
            if (node.path("userId").asLong() != userId) {
                throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_FOUND);
            }
            SeckillResultVo result = new SeckillResultVo();
            result.setRequestId(node.path("requestId").asText());
            result.setOrderNo(node.path("orderNo").asText(null));
            result.setStatus(node.path("status").asInt());
            if (node.hasNonNull("orderId")) {
                result.setOrderId(node.get("orderId").asLong());
            }
            result.setMessage(node.path("message").asText());
            return result;
        } catch (MyException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid seckill result cache", ex);
        }
    }
}

