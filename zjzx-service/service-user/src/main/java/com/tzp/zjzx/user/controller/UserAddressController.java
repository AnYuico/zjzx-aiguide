package com.tzp.zjzx.user.controller;

import com.tzp.zjzx.common.security.InternalApiAuth;
import com.tzp.zjzx.model.dto.h5.UserAddressRequestDto;
import com.tzp.zjzx.model.dto.internal.UserAddressInternalDto;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.h5.UserAddressVo;
import com.tzp.zjzx.user.service.UserAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Tag(name = "用户地址接口")
@RestController
@RequestMapping(value="/api/user/userAddress")
@SuppressWarnings({"unchecked", "rawtypes"})
public class UserAddressController {


    @Autowired
    private UserAddressService userAddressService;

    @Value("${zjzx.internal-api.token}")
    private String internalApiToken;

    @Operation(summary = "获取用户地址列表")
    @GetMapping("auth/findUserAddressList")
    public Result<List<UserAddressVo>> findUserAddressList() {
        List<UserAddressVo> list = userAddressService.findUserAddressList();
        return Result.build(list , ResultCodeEnum.SUCCESS) ;
    }

    @Operation(summary = "新增当前用户收货地址")
    @PostMapping("auth")
    public Result<UserAddressVo> save(@Valid @RequestBody UserAddressRequestDto requestDto) {
        return Result.build(userAddressService.save(requestDto), ResultCodeEnum.SUCCESS);
    }

    @Operation(summary = "修改当前用户收货地址")
    @PutMapping("auth/{id}")
    public Result<UserAddressVo> update(@PathVariable Long id,
                                        @Valid @RequestBody UserAddressRequestDto requestDto) {
        return Result.build(userAddressService.update(id, requestDto), ResultCodeEnum.SUCCESS);
    }

    @Operation(summary = "删除当前用户收货地址")
    @DeleteMapping("auth/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userAddressService.delete(id);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    @GetMapping("internal/getUserAddress/{userId}/{id}")
    public UserAddressInternalDto getUserAddress(@RequestHeader(InternalApiAuth.HEADER_NAME) String token,
                                                 @PathVariable("userId") Long userId,
                                                 @PathVariable("id") Long id) {
        InternalApiAuth.verify(internalApiToken, token);
        return userAddressService.getUserAddress(id, userId);
    }
}
