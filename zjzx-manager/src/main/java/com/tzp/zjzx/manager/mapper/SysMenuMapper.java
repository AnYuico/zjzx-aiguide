package com.tzp.zjzx.manager.mapper;

import com.tzp.zjzx.model.entity.system.SysMenu;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysMenuMapper {

    /**
     * 1.查询所有的菜单
     * @return 返回一个list集合
     */
    List<SysMenu> findAll();

    /**
     * 2.添加菜单
     * @param sysMenu
     */
    void save(SysMenu sysMenu);

    /**
     * 3.修改菜单
     * @param sysMenu
     */
    void update(SysMenu sysMenu);

    /**
     * 4.查询id菜单的子菜单数量
     * @param id
     * @return
     */
    int selectCountById(Integer id);

    /**
     * 5.根据id删除菜单
     * @param id
     */
    void delete(Integer id);

    /**
     * 6.根据用户id查询可操作的菜单
     * @param userId
     * @return
     */
    List<SysMenu> findMenusByUserId(Long userId);

    /**
     * 7.获取当前添加菜单的父菜单
     * @param parentId
     * @return
     */
    SysMenu selectParentMenu(Long parentId);
}
