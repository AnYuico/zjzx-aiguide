package com.tzp.zjzx.feign.user;

import com.tzp.zjzx.model.dto.internal.UserAddressInternalDto;
import com.tzp.zjzx.model.dto.internal.UserProfileInternalDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient("service-user")
public interface UserFeignClient {

    @GetMapping("/api/user/userInfo/internal/getUserInfo/{userId}")
    UserProfileInternalDto getUserInfo(@RequestHeader("X-Internal-Token") String internalToken,
                                       @PathVariable("userId") Long userId);

    @GetMapping("/api/user/userAddress/internal/getUserAddress/{userId}/{id}")
    UserAddressInternalDto getUserAddress(@RequestHeader("X-Internal-Token") String internalToken,
                                          @PathVariable("userId") Long userId,
                                          @PathVariable("id") Long id);
}
