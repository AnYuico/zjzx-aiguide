package com.tzp.zjzx.manager.utils;

import com.tzp.zjzx.model.entity.system.SysMenu;

import java.util.ArrayList;
import java.util.List;

//封装树形菜单数据
public class MenuHelper {


    //递归实现封装过程
    public static List<SysMenu> buildTree(List<SysMenu> sysMenuList) {
        //TODO 完成封装过程

        //sysMenuList 所有菜单的集合
        //创建一个list集合 用于封装最终的数据
        List<SysMenu> treeList = new ArrayList<>();
        //遍历所有菜单集合
        for (SysMenu sysMenu : sysMenuList) {
            //找到递归操作的入口 菜单的第一层菜单
            //条件：parent_id=0 第一层菜单
            if (sysMenu.getParentId().longValue() == 0) {
                //根据第一层，去找下层数据，使用递归完成
                //单独写一个方法实现下层数据查找，参数一：表示当前第一层菜单，参数二是所有菜单的集合
                treeList.add(findChildren(sysMenu, sysMenuList));
            }
        }
        return treeList;
    }

    //递归查找下层数据
    private static SysMenu findChildren(SysMenu sysMenu, List<SysMenu> sysMenuList) {
        // SysMenu有 private List<SysMenu> children; 用于封装子节点的数据
        //1.初始化子节点
        sysMenu.setChildren(new ArrayList<>());
        //2.递归查询
        //拿着sysMenu的id 去 sysMenuList中找 子节点(parent_id=id)
        for (SysMenu menu : sysMenuList) {
            //判断id 和 parent_id值是否相同
            if (sysMenu.getId().longValue() == menu.getParentId()) {
                // id == parent_id 表明 此menu就是sysMenu的子节点
                // 封装
                sysMenu.getChildren().add(findChildren(menu, sysMenuList));
            }

        }
        return sysMenu;
    }
}
