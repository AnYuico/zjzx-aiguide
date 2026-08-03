package com.tzp.zjzx.manager.controller;

import com.tzp.zjzx.manager.service.SysRoleMenuService;
import com.tzp.zjzx.model.dto.system.AssignMenuDto;
import com.tzp.zjzx.model.entity.system.SysMenu;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/system/sysRoleMenu")
public class SysRoleMenuController {

    @Autowired
    private SysRoleMenuService sysRoleMenuService;

    /**
     * 查询所有菜单 和 该roleId分配过的菜单Id
     *
     * @param roleId
     * @return
     */
    @GetMapping("/findSysRoleMenuByRoleId/{roleId}")
    public Result findSysRoleMenuByRoleId(@PathVariable("roleId") Long roleId) {
        Map<String, Object> map = sysRoleMenuService.findSysRoleMenuByRoleId(roleId);
        return Result.build(map, ResultCodeEnum.SUCCESS);
    }

    /**
     * 保存角色分配菜单数据
     *
     * @param assignMenuDto
     * @return
     */
    @PostMapping("/doAssign")
    public Result doAssign(@RequestBody AssignMenuDto assignMenuDto) {
        sysRoleMenuService.doAssign(assignMenuDto);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }


}
