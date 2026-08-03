package com.tzp.zjzx.manager.controller;

import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.manager.service.SysUserService;
import com.tzp.zjzx.model.dto.system.AssignRoleDto;
import com.tzp.zjzx.model.dto.system.SysUserCreateDto;
import com.tzp.zjzx.model.dto.system.SysUserDto;
import com.tzp.zjzx.model.dto.system.SysUserPasswordDto;
import com.tzp.zjzx.model.dto.system.SysUserUpdateDto;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.system.SysUserListVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/admin/system/sysUser")
public class SysUserController {

    @Autowired
    private SysUserService sysUserService;

    //1.列表接口 用户的条件分页查询接口
    @GetMapping("/findByPage/{pageNum}/{pageSize}")
    public Result findByPage(@PathVariable("pageNum") Integer pageNum,
                             @PathVariable("pageSize") Integer pageSize,
                             SysUserDto sysUserDto) {
        //利用pageHelper插件实现分页
        PageInfo<SysUserListVo> pageInfo = sysUserService.findByPage(sysUserDto, pageNum, pageSize);
        return Result.build(pageInfo, ResultCodeEnum.SUCCESS);
    }

    //2.用户添加接口
    @PostMapping(value = "/saveSysUser")
    public Result saveSysUser(@RequestBody SysUserCreateDto sysUser){
        sysUserService.saveSysUser(sysUser);
        return Result.build(null,ResultCodeEnum.SUCCESS);
    }

    //3.用户修改接口
    @PutMapping(value = "/updateSysUser")
    public Result updateSysUser(@RequestBody SysUserUpdateDto sysUser){
        sysUserService.updateSysUser(sysUser);
        return Result.build(null,ResultCodeEnum.SUCCESS);
    }

    @PutMapping(value = "/updatePassword")
    public Result updatePassword(@RequestBody SysUserPasswordDto passwordDto) {
        sysUserService.updatePassword(passwordDto);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    //4.用户的删除接口
    @DeleteMapping(value = "/deleteById/{userId}")
    public Result deleteById(@PathVariable("userId") Long userId){
        sysUserService.deleteById(userId);
        return Result.build(null,ResultCodeEnum.SUCCESS);
    }

    //5.用户分配角色
    //保存分配数据
    @PostMapping("/doAssign")
    public Result doAssign(@RequestBody AssignRoleDto assignRoleDto){
        sysUserService.doAssign(assignRoleDto);
        return Result.build(null,ResultCodeEnum.SUCCESS);
    }
}
