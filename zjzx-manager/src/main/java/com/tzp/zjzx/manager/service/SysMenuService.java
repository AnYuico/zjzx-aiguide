package com.tzp.zjzx.manager.service;

import com.tzp.zjzx.model.entity.system.SysMenu;
import com.tzp.zjzx.model.vo.system.SysMenuVo;

import java.util.List;

public interface SysMenuService {

    /**
     * 菜单列表
     * @return
     */
    List<SysMenu> findNodes();

    /**
     * 添加菜单
     * @param sysMenu
     */
    void save(SysMenu sysMenu);

    /**
     * 修改菜单
     * @param sysMenu
     */
    void update(SysMenu sysMenu);

    /**
     * 菜单删除
     * @param id
     */
    void removeById(Integer id);

    /**
     * 查询用户可以操作的菜单
     * @return
     */
    List<SysMenuVo> findMenuByUserId();
}
