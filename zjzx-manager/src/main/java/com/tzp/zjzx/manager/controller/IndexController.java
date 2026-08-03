package com.tzp.zjzx.manager.controller;

import com.tzp.zjzx.manager.service.SysMenuService;
import com.tzp.zjzx.manager.service.SysUserService;
import com.tzp.zjzx.manager.service.ValidateCodeService;
import com.tzp.zjzx.model.dto.system.LoginDto;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.system.LoginVo;
import com.tzp.zjzx.model.vo.system.SysMenuVo;
import com.tzp.zjzx.model.vo.system.ValidateCodeVo;
import com.tzp.zjzx.utils.AuthContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "User API")
@RestController
@RequestMapping(value = "/admin/system/index")
public class IndexController {

    private final SysUserService sysUserService;
    private final ValidateCodeService validateCodeService;
    private final SysMenuService sysMenuService;

    public IndexController(SysUserService sysUserService,
                           ValidateCodeService validateCodeService,
                           SysMenuService sysMenuService) {
        this.sysUserService = sysUserService;
        this.validateCodeService = validateCodeService;
        this.sysMenuService = sysMenuService;
    }

    @GetMapping("/menus")
    public Result<List<SysMenuVo>> menus() {
        return Result.build(sysMenuService.findMenuByUserId(), ResultCodeEnum.SUCCESS);
    }

    @GetMapping(value = "/logout")
    public Result<Object> logout(@RequestHeader("token") String token) {
        sysUserService.logout(token);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    @GetMapping(value = "/getUserInfo")
    public Result<Object> getUserInfo() {
        return Result.build(
                sysUserService.getCurrentUserInfo(AuthContextUtil.get().getId()),
                ResultCodeEnum.SUCCESS
        );
    }

    @Operation(summary = "Login")
    @PostMapping("login")
    public Result<LoginVo> login(@RequestBody LoginDto loginDto) {
        return Result.build(sysUserService.login(loginDto), ResultCodeEnum.SUCCESS);
    }

    @GetMapping(value = "/generateValidateCode")
    public Result<ValidateCodeVo> generateValidateCode() {
        return Result.build(validateCodeService.generateValidateCode(), ResultCodeEnum.SUCCESS);
    }
}
