package com.tzp.zjzx.manager.service.impl;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.manager.mapper.SysMenuMapper;
import com.tzp.zjzx.manager.mapper.SysRoleMenuMapper;
import com.tzp.zjzx.manager.service.SysMenuService;
import com.tzp.zjzx.manager.utils.MenuHelper;
import com.tzp.zjzx.model.entity.system.SysMenu;
import com.tzp.zjzx.model.entity.system.SysUser;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.system.SysMenuVo;
import com.tzp.zjzx.utils.AuthContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.LinkedList;
import java.util.List;

@Service
public class SysMenuServiceImpl implements SysMenuService {


    @Autowired
    private SysMenuMapper sysMenuMapper;

    @Autowired
    private SysRoleMenuMapper sysRoleMenuMapper;

    /**
     * 菜单列表方法
     *
     * @return
     */
    @Override
    public List<SysMenu> findNodes() {
        //1 先查询所有的菜单，返回一个list集合
        List<SysMenu> sysMenuList = sysMenuMapper.findAll();
        if (CollectionUtils.isEmpty(sysMenuList)) {
            return null;
        }

        //2 调用工具类，把返回的list集合封装成要求的数据格式 后返回
        return MenuHelper.buildTree(sysMenuList);
    }

    /**
     * 添加菜单
     *
     * @param sysMenu
     */
    @Override
    public void save(SysMenu sysMenu) {
        sysMenuMapper.save(sysMenu);

        //当新添加一个子菜单，把父菜单isHalf设置为半开状态 1
        updateSysRoleMenu(sysMenu);

    }

    //新加子菜单后，将父菜单的isHalf设置为半开状态 1
    private void updateSysRoleMenu(SysMenu sysMenu) {
        //获取当前添加菜单的父菜单
        SysMenu parentMenu = sysMenuMapper.selectParentMenu(sysMenu.getParentId());
        if (parentMenu != null) {
            //将父菜单的isHalf值 改为 1
            sysRoleMenuMapper.updateSysRoleMenuIsHalf(parentMenu.getId());

            //递归调用
            updateSysRoleMenu(parentMenu);
        }

    }

    /**
     * 修改菜单
     *
     * @param sysMenu
     */
    @Override
    public void update(SysMenu sysMenu) {
        sysMenuMapper.update(sysMenu);
    }

    /**
     * 根据id删除菜单
     *
     * @param id
     */
    @Override
    public void removeById(Integer id) {
        //1.判断当前id菜单是否有子菜单
        int count = sysMenuMapper.selectCountById(id);

        //判断,count > 0 有子节点
        if (count > 0) {
            //抛出一个 子节点错误
            throw new MyException(ResultCodeEnum.NODE_ERROR);
        }

        //count = 0 没有子节点 直接删除
        sysMenuMapper.delete(id);
    }

    /**
     * 查询用户可以操作的菜单
     *
     * @return
     */
    @Override
    public List<SysMenuVo> findMenuByUserId() {

        //1.获取当前用户id
        SysUser sysUser = AuthContextUtil.get();
        Long userId = sysUser.getId();

        //2.根据userId查询可以操作的菜单
        List<SysMenu> sysMenuList = sysMenuMapper.findMenusByUserId(userId);

        //3.封装后返回
        List<SysMenu> sysMenus = MenuHelper.buildTree(sysMenuList);
        List<SysMenuVo> sysMenuVoList = this.buildMenus(sysMenus);

        return sysMenuVoList;
    }

    /**
     * 将List<SysMenu>对象 转换成 List<SysMenuVo>对象
     *
     * @param menus
     * @return
     */
    private List<SysMenuVo> buildMenus(List<SysMenu> menus) {
        List<SysMenuVo> sysMenuVoList = new LinkedList<SysMenuVo>();
        for (SysMenu menu : menus) {
            SysMenuVo sysMenuVo = new SysMenuVo();
            sysMenuVo.setTitle(menu.getTitle());
            sysMenuVo.setName(menu.getComponent());
            List<SysMenu> children = menu.getChildren();
            if (!CollectionUtils.isEmpty(children)) {
                sysMenuVo.setChildren(buildMenus(children));
            }
            sysMenuVoList.add(sysMenuVo);
        }
        return sysMenuVoList;
    }

}

