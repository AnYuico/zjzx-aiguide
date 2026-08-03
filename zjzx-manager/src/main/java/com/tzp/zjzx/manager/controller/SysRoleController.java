package com.tzp.zjzx.manager.controller;

import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.common.log.annotation.Log;
import com.tzp.zjzx.manager.service.SysRoleService;
import com.tzp.zjzx.model.dto.system.SysRoleDto;
import com.tzp.zjzx.model.entity.system.SysRole;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(value = "/admin/system/sysRole")
public class SysRoleController {

    @Autowired
    private SysRoleService sysRoleService;


    /**
     * 查询所有角色
     * @return
     */
    @GetMapping("/findAllRoles/{userId}")
    public Result findAllRoles(@PathVariable("userId") Long userId) {
        Map<String, Object> map = sysRoleService.findAll(userId);
        return Result.build(map, ResultCodeEnum.SUCCESS);
    }


    /**
     * 分页查询 访问角色列表
     *
     * @param current    当前页
     * @param limit      每页显示数量
     * @param sysRoleDto 条件角色名称对象
     * @return
     */
    @PostMapping("/findByPage/{current}/{limit}")
    public Result findByPage(@PathVariable("current") Integer current,
                             @PathVariable("limit") Integer limit,
                             @RequestBody SysRoleDto sysRoleDto) {

        //利用pageHelper插件实现分页
        PageInfo<SysRole> pageInfo = sysRoleService.findByPage(sysRoleDto, current, limit);

        return Result.build(pageInfo, ResultCodeEnum.SUCCESS);

    }

    /**
     * 角色添加
     *
     * @param sysRole
     * @return
     */
    @Log(title = "角色管理:添加",businessType = 1)
    @PostMapping(value = "/saveSysRole")
    public Result saveSysRole(@RequestBody SysRole sysRole) {
        sysRoleService.saveSysRole(sysRole);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 角色修改
     *
     * @param sysRole
     * @return
     */
    @PutMapping(value = "/updateSysRole")
    public Result updateSysRole(@RequestBody SysRole sysRole) {
        sysRoleService.updateSysRole(sysRole);
        System.out.println("修改成功并返回200");
        return Result.build(null, ResultCodeEnum.SUCCESS);

    }

    /**
     * 角色删除
     *
     * @param roleId
     * @return
     */
    @DeleteMapping("/deleteById/{roleId}")
    public Result deleteById(@PathVariable("roleId") Long roleId) {
        sysRoleService.deleteById(roleId);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }


}
