package com.tzp.zjzx.manager.controller;

import com.tzp.zjzx.manager.service.SysMenuService;
import com.tzp.zjzx.model.entity.system.SysMenu;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("admin/system/sysMenu")
public class SysMenuController {

    @Autowired
    private SysMenuService sysMenuService;

    /**
     * 根据菜单id删除菜单
     * @param id
     * @return
     */
    @DeleteMapping("/removeById/{id}")
    public Result removeById(@PathVariable("id") Integer id){
        sysMenuService.removeById(id);
        return  Result.build(null,ResultCodeEnum.SUCCESS);
    }

    /**
     * 修改菜单
     * @param sysMenu
     * @return
     */
    @PutMapping("/update")
    public Result update(@RequestBody SysMenu sysMenu){
         sysMenuService.update(sysMenu);
         return Result.build(null,ResultCodeEnum.SUCCESS);
    }

    /**
     * 添加菜单
     * @param sysMenu
     * @return
     */
    @PostMapping("/save")
    public  Result save(@RequestBody SysMenu sysMenu){
        sysMenuService.save(sysMenu);
        return Result.build(null,ResultCodeEnum.SUCCESS);
    }

    /**
     * 菜单列表方法
     * @return
     */
    @GetMapping("/findNodes")
    public Result findNodes(){
        List<SysMenu> list = sysMenuService.findNodes();
        return Result.build(list, ResultCodeEnum.SUCCESS);
    }

}
