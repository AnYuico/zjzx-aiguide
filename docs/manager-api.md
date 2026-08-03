# 紫金甄选后台管理 API 文档（8501）

> 基于当前仓库 zjzx-manager 源码静态整理，更新时间：2026-07-21。
> 
> 覆盖范围：8501 服务提供给后台管理前端的全部 50 个 HTTP 接口；不包含商城前台、Feign 内部调用、定时任务及数据库管理接口。
> 
> 本文以源码实际行为为准，未连接外部数据库、Redis 或对象存储进行联调。

## 1. 接入约定

### 1.1 服务地址

8501 服务的 server.port 为 8501，当前网关配置中没有 zjzx-manager 的路由。因此，按现有配置，后台前端应直连：

    http://<manager-host>:8501

本文列出的 Path 均为绝对路径，例如：

    GET http://<manager-host>:8501/admin/system/index/generateValidateCode

若部署环境另有 Nginx、Ingress 或新增 Gateway 路由，应仅替换 Base URL，Path 保持不变。

### 1.2 请求格式

| 场景 | 格式 |
| --- | --- |
| 普通新增、修改、登录、授权 | Content-Type: application/json |
| 文件与 Excel 导入 | Content-Type: multipart/form-data |
| 文件下载 | 响应为二进制 Excel，不是 JSON Result |
| 身份凭证 | 请求头 token: 登录接口返回的 token |
| ID | Java Long；JavaScript 如可能超过安全整数范围，建议以字符串保存并传递 |
| 时间字段 | 返回格式为 yyyy-MM-dd HH:mm:ss |

服务已允许任意来源、任意方法和任意请求头的跨域请求。前端仍应按上述 Content-Type 发送请求。

注意：商品上下架和审核接口虽然会改变数据，但后端定义为 GET，前端必须按本文的 HTTP Method 调用。

### 1.3 鉴权

只有以下两个接口无需 token：

| Method | Path | 用途 |
| --- | --- | --- |
| GET | /admin/system/index/generateValidateCode | 获取图形验证码 |
| POST | /admin/system/index/login | 后台账号登录 |

其余 48 个接口均需要请求头：

    token: <token>

认证行为：

1. 登录成功时，服务生成随机 token。
2. 图形验证码有效期为 5 分钟；校验通过后立即删除。
3. 登录 token 写入 Redis 后有效期为 30 分钟；每次访问受保护接口后重置为 30 分钟无操作超时。缓存值仅包含 `userId`、`username` 和 `authVersion`。
4. token 缺失、过期或权限版本失效时，拦截器返回业务码 208。其 HTTP 状态通常仍为 200，Content-Type 为 `application/json;charset=UTF-8`；前端必须以响应体 code 为准。
5. 当前服务只校验“是否已登录”，未按菜单/角色做接口级授权。也就是说，任一有效后台 token 都可调用所有受保护接口；前端菜单隐藏不能替代后端权限控制。

### 1.4 统一响应

除分类导出接口外，正常业务响应统一为：

    {
      "code": 200,
      "message": "操作成功",
      "data": {}
    }

业务异常由全局异常处理器包装，通常也不会改变 HTTP 状态。前端应同时处理网络/HTTP 错误和 Result.code。

| code | message | 8501 中的触发场景 | 前端处理建议 |
| --- | --- | --- | --- |
| 200 | 操作成功 | 请求成功 | 使用 data |
| 201 | 用户名或者密码错误 | 登录用户名不存在或密码不匹配 | 提示账号或密码错误 |
| 202 | 验证码错误 | 验证码失效或不匹配 | 刷新验证码后重试 |
| 204 | 数据异常 | 分类 Excel 导入或导出失败 | 提示操作失败 |
| 208 | 用户未登录 | token 缺失、失效 | 清理本地 token 并跳转登录 |
| 209 | 用户名已经存在 | 新增后台用户时用户名重复 | 提示更换用户名 |
| 217 | 该节点下有子节点，不可以删除 | 删除仍有直接子菜单的菜单 | 先删除或迁移子菜单 |
| 223 | 上传文件不能为空 | 图片字段为空或零字节 | 提示重新选择图片 |
| 224 | 上传图片大小超过限制 | 图片超过 5 MiB 或部署配置上限 | 压缩图片后重试 |
| 225 | 仅支持 JPG、PNG、WebP 图片 | 文件头不是允许的图片类型 | 重新选择支持的图片 |
| 226 | 上传图片内容无效 | 图片损坏、尺寸异常或 WebP 结构不完整 | 重新导出图片后上传 |
| 227 | 图片存储失败，请稍后重试 | MinIO 写入失败 | 稍后重试并检查对象存储状态 |
| 228 | 商品至少需要一个SKU | 新增或修改商品时 SKU 列表为空 | 至少生成一个 SKU 后再提交 |
| 229 | 商品SKU数据无效 | 商品 SKU 列表包含无效项 | 检查 SKU 数据完整性 |
| 230 | 请求参数校验失败 | 商品 ID、SKU ID 或数值字段格式/范围不合法 | 按响应 message 修正对应字段 |
| 9999 | 您的网络有问题请稍后重试 | 未捕获异常或未知系统故障 | 提示稍后重试 |

登录会校验 `SysUser.status`；停用账号返回 216，已签发 token 也会在状态变更后统一撤销。

### 1.5 分页结构

所有分页接口的 data 为 PageInfo<T>。前端通常使用以下字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| pageNum | integer | 当前页，从 1 开始 |
| pageSize | integer | 每页大小 |
| size | integer | 当前页实际记录数 |
| total | long | 总记录数 |
| pages | integer | 总页数 |
| list | T[] | 当前页数据 |
| prePage / nextPage | integer | 上一页 / 下一页页码 |
| isFirstPage / isLastPage | boolean | 是否首页 / 尾页 |
| hasPreviousPage / hasNextPage | boolean | 是否可前翻 / 后翻 |
| navigatepageNums | integer[] | 导航页码 |

PageHelper 还会序列化其他辅助字段；前端不应依赖未在此表列出的字段。

## 2. 数据模型

### 2.1 BaseEntity

以下实体均继承 BaseEntity，返回时通常包含这些字段。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 主键 |
| createTime | string | 创建时间，yyyy-MM-dd HH:mm:ss |
| updateTime | string | 修改时间，yyyy-MM-dd HH:mm:ss |
| isDeleted | integer | 逻辑删除标记；创建和更新请求通常无需传递 |

新增时请省略 id、createTime、updateTime、isDeleted。商品新增和修改接口已使用独立 DTO 与 Bean Validation；其他部分接口仍可能因缺少字段校验而返回 9999。

### 2.2 系统管理实体

#### SysUser

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| userName | string | 登录用户名，对应数据库 username |
| password | string | 仅新增和专用改密接口接收明文；查询响应永不返回该字段 |
| name | string | 姓名或昵称 |
| phone | string | 手机号 |
| avatar | string | 头像 URL |
| description | string | 描述 |
| status | integer | 1 正常，0 停用 |

当前用户返回 `SysUserInfoVo`；用户分页返回 `SysUserListVo`。两者均不包含 `password` 和 `isDeleted`，列表额外包含 `createTime`。

#### SysRole

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| roleName | string | 角色名称 |
| roleCode | string | 角色编码 |
| description | string | 描述 |

#### SysMenu

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| parentId | long | 父节点 ID；根节点使用 0 |
| title | string | 菜单显示名称 |
| component | string | 前端组件名/路由名 |
| sortValue | integer | 同层排序值 |
| status | integer | 0 禁用，1 正常 |
| children | SysMenu[] | 菜单树子节点，仅树接口返回 |

#### SysMenuVo

用于“当前用户菜单”接口，不返回完整 SysMenu：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| title | string | 菜单标题 |
| name | string | 等同于 SysMenu.component |
| children | SysMenuVo[] | 递归子菜单；无子项时通常不设置 |

### 2.3 商品管理实体

#### Brand

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| name | string | 品牌名称 |
| logo | string | 品牌 Logo URL |

#### Category

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| name | string | 分类名称 |
| imageUrl | string | 分类图片 URL |
| parentId | long | 父分类 ID |
| status | integer | 0 不显示，1 显示 |
| orderNum | integer | 排序值 |
| hasChildren | boolean | 是否有下一层子分类，仅列表接口补充 |
| children | Category[] | 当前 8501 分类接口不构建此字段 |

#### CategoryBrand

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| brandId | long | 品牌 ID |
| categoryId | long | 分类 ID |
| categoryName | string | 分类名称，仅列表返回 |
| brandName | string | 品牌名称，仅列表返回 |
| logo | string | 品牌 Logo，仅列表返回 |

#### ProductUnit

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| name | string | 计量单位名称 |

#### ProductSpec

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| specName | string | 规格名称，例如颜色 |
| specValue | string | 规格值字符串；后端不解析其格式 |

#### ProductSku

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| skuCode | string | SKU 编码；新增商品时由服务端生成，格式为 商品ID_序号 |
| skuName | string | SKU 名称；新增商品时由服务端拼接 商品名称 + skuSpec |
| productId | long | 所属商品 ID；新增时由服务端写入 |
| thumbImg | string | 缩略图 URL |
| salePrice | number | 销售价，对应 BigDecimal |
| marketPrice | number | 市场价，对应 BigDecimal |
| costPrice | number | 成本价，对应 BigDecimal |
| stockNum | integer | 库存数 |
| saleNum | integer | 销量；新增时服务端置为 0 |
| skuSpec | string | SKU 规格 JSON 字符串，例如 {"颜色":"红色"} |
| weight | number | 重量，单位 kg，对应 BigDecimal，最多两位小数 |
| volume | number | 体积，单位 m³，对应 BigDecimal，最多两位小数 |
| status | integer | 线上状态：0 初始，1 上架，-1 下架 |

#### Product

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| name | string | 商品名称 |
| brandId | long | 品牌 ID |
| category1Id | long | 一级分类 ID |
| category2Id | long | 二级分类 ID |
| category3Id | long | 三级分类 ID |
| unitName | string | 计量单位名称 |
| sliderUrls | string | 轮播图 URL 字符串；服务端不拆分、不校验格式 |
| specValue | string | 商品规格值 JSON 字符串；服务端不解析 |
| status | integer | 0 初始，1 上架，-1 下架 |
| auditStatus | integer | 0 初始，1 审批通过，-1 审批不通过 |
| auditMessage | string | 审核说明 |
| brandName | string | 品牌名称，仅商品查询返回 |
| category1Name | string | 一级分类名称，仅商品查询返回 |
| category2Name | string | 二级分类名称，仅商品查询返回 |
| category3Name | string | 三级分类名称，仅商品查询返回 |
| productSkuList | ProductSku[] | 商品详情接口返回；新增/修改商品时也使用此字段 |
| detailsImageUrls | string | 商品详情图 URL 字符串；新增/修改/详情接口使用 |

### 2.4 订单统计对象

#### OrderStatisticsVo

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| dateList | string[] | 日期集合，格式 yyyy-MM-dd |
| amountList | number[] | 与 dateList 同下标对应的订单总金额 |

## 3. 接口总览

| 模块 | 数量 | 接口 |
| --- | ---: | --- |
| 登录与首页 | 5 | 验证码、登录、当前用户、用户菜单、退出 |
| 用户管理 | 6 | 分页、新增、资料修改、密码修改、删除、分配角色 |
| 角色管理 | 5 | 查询用户角色、分页、增、改、删 |
| 菜单与角色菜单 | 6 | 菜单树、增、改、删、查询授权、保存授权 |
| 品牌 | 5 | 分页、增、改、删、全部查询 |
| 分类 | 3 | 子分类、导出、导入 |
| 分类品牌 | 5 | 按分类品牌、分页、增、改、删 |
| 商品单位 | 1 | 全部查询 |
| 商品规格 | 5 | 分页、增、改、删、全部查询 |
| 商品 | 7 | 上下架、审核、删、改、详情、分页、新增 |
| 订单统计 | 1 | 趋势数据 |
| 文件 | 1 | 上传 |
| 合计 | 50 | 全部后台前端 HTTP 接口 |

## 4. 登录与首页

### 4.1 获取图形验证码

    GET /admin/system/index/generateValidateCode

无需登录，无参数。

data 类型：ValidateCodeVo。

    {
      "codeKey": "验证码唯一标识",
      "codeValue": "data:image/png;base64,..."
    }

将 codeValue 直接作为 img 的 src；登录时提交同一个 codeKey 和用户输入的 captcha。验证码有效 5 分钟，大小写不敏感。

### 4.2 登录

    POST /admin/system/index/login
    Content-Type: application/json

无需登录。请求体：

    {
      "userName": "admin",
      "password": "明文密码",
      "captcha": "a1b2",
      "codeKey": "4.1 接口返回的 codeKey"
    }

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| userName | string | 是 | 后台用户名 |
| password | string | 是 | 明文密码；服务端校验 BCrypt，旧 MD5 账号首次成功登录后自动升级 |
| captcha | string | 是 | 图形验证码输入值 |
| codeKey | string | 是 | 图形验证码标识 |

成功时 data 类型为 LoginVo：

    {
      "token": "f2d7...",
      "refresh_token": null
    }

当前没有 token 刷新接口，refresh_token 始终未赋值。失败时主要返回 201 或 202。

新建账号和修改密码均保存 BCrypt 哈希。旧的 32 位 MD5 数据仍可登录，成功校验后服务端使用带旧值条件的 SQL 自动替换为 BCrypt，前端无需参与迁移。

### 4.3 获取当前登录用户

    GET /admin/system/index/getUserInfo
    token: <token>

data 类型：`SysUserInfoVo`，包含 `id、userName、name、phone、avatar、description、status`，不包含密码及持久化审计字段；数据按当前登录用户 ID 从数据库实时查询。

### 4.4 获取当前用户菜单

    GET /admin/system/index/menus
    token: <token>

data 类型：SysMenuVo[]。服务根据当前用户的角色-菜单关联查询，再构造成 parentId 为 0 的树。

返回示例：

    [
      {
        "title": "系统管理",
        "name": "system",
        "children": [
          {
            "title": "用户管理",
            "name": "sysUser"
          }
        ]
      }
    ]

name 来自数据库 component 字段，不是菜单 ID。

### 4.5 退出登录

    GET /admin/system/index/logout
    token: <token>

无请求体。服务删除当前 token 的 Redis 登录记录，成功时 data 为 null。前端收到成功响应后也应清理本地 token 和菜单状态。

## 5. 用户管理

### 5.1 用户条件分页

    GET /admin/system/sysUser/findByPage/{pageNum}/{pageSize}
    token: <token>

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| pageNum | integer | 是 | 页码，从 1 开始 |
| pageSize | integer | 是 | 每页数量 |

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | string | 否 | username 模糊查询 |
| createTimeBegin | string | 否 | 创建时间下限，建议 yyyy-MM-dd HH:mm:ss |
| createTimeEnd | string | 否 | 创建时间上限，建议 yyyy-MM-dd HH:mm:ss |

示例：

    GET /admin/system/sysUser/findByPage/1/10?keyword=admin&createTimeBegin=2026-07-01%2000:00:00

data 类型：`PageInfo<SysUserListVo>`，按 id 倒序。列表元素包含 `id、userName、name、phone、avatar、description、status、createTime`，不包含 password。

### 5.2 新增用户

    POST /admin/system/sysUser/saveSysUser
    token: <token>
    Content-Type: application/json

请求体示例：

    {
      "userName": "operator01",
      "password": "plain-password",
      "name": "运营人员",
      "phone": "13800138000",
      "avatar": "https://...",
      "description": "商品运营"
    }

服务端会将 password 编码为 BCrypt，并强制将 status 设为 1；传入 status 不生效。用户名重复返回 209。成功时 data 为 null。

### 5.3 修改用户

    PUT /admin/system/sysUser/updateSysUser
    token: <token>
    Content-Type: application/json

请求体必须含 id；name、phone、avatar、description、status 等非空字段会被局部更新。

    {
      "id": 12,
      "name": "新的姓名",
      "status": 0
    }

该接口只接收 `id、userName、name、phone、avatar、description、status`，即使请求额外携带 password 也不会进入更新 DTO。修改 status 或 userName 会撤销该用户全部后台 token；修改头像等展示字段不会强制退出，下一次查询当前用户信息时读取数据库最新值。

### 5.4 修改密码

    PUT /admin/system/sysUser/updatePassword
    token: <token>
    Content-Type: application/json

请求体：

    {
      "id": 12,
      "newPassword": "new-plain-password"
    }

服务端将新密码编码为 BCrypt 后保存，并撤销该用户全部后台 token。该接口不通过用户资料更新对象传递密码。

### 5.5 删除用户

    DELETE /admin/system/sysUser/deleteById/{userId}
    token: <token>

逻辑删除用户，成功时 data 为 null。当前实现不同时删除用户-角色关联记录。

### 5.6 为用户分配角色

    POST /admin/system/sysUser/doAssign
    token: <token>
    Content-Type: application/json

请求体：

    {
      "userId": 12,
      "roleIdList": [1, 3]
    }

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| userId | long | 是 | 用户 ID |
| roleIdList | long[] | 是 | 目标角色 ID 集合 |

该操作会先删除该用户已有的所有角色，再按 roleIdList 重建。传 [] 可清空角色；不要传 null，否则当前实现可能返回 9999。

## 6. 角色管理

### 6.1 查询全部角色及用户已分配角色

    GET /admin/system/sysRole/findAllRoles/{userId}
    token: <token>

data：

    {
      "allRolesList": [SysRole],
      "sysUserRoles": [1, 3]
    }

allRolesList 为所有未逻辑删除角色；sysUserRoles 为指定用户已分配的角色 ID。

### 6.2 角色条件分页

    POST /admin/system/sysRole/findByPage/{current}/{limit}
    token: <token>
    Content-Type: application/json

即使不筛选也应提交空对象 {}。

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| current | integer | 是 | 页码，从 1 开始 |
| limit | integer | 是 | 每页数量 |

请求体：

    {
      "roleName": "管理员"
    }

roleName 可省略或为空，表示不按名称过滤。data 类型：PageInfo<SysRole>，按 id 倒序。

### 6.3 新增角色

    POST /admin/system/sysRole/saveSysRole
    token: <token>
    Content-Type: application/json

请求体：

    {
      "roleName": "商品审核员",
      "roleCode": "product_auditor",
      "description": "负责审核商品"
    }

成功时 data 为 null。

### 6.4 修改角色

    PUT /admin/system/sysRole/updateSysRole
    token: <token>
    Content-Type: application/json

请求体必须含 id，非空的 roleName、roleCode、description 会被更新。

    {
      "id": 3,
      "roleName": "高级商品审核员",
      "roleCode": "product_auditor",
      "description": "..."
    }

成功时 data 为 null。

### 6.5 删除角色

    DELETE /admin/system/sysRole/deleteById/{roleId}
    token: <token>

逻辑删除角色，成功时 data 为 null。当前实现不自动清理用户-角色和角色-菜单关联记录。

## 7. 菜单与角色菜单管理

### 7.1 查询菜单树

    GET /admin/system/sysMenu/findNodes
    token: <token>

data 类型：SysMenu[]。服务按 sortValue 排序后组装树，根节点为 parentId=0，children 递归返回。无菜单时 data 可能为 null。

### 7.2 新增菜单

    POST /admin/system/sysMenu/save
    token: <token>
    Content-Type: application/json

请求体：

    {
      "parentId": 0,
      "title": "商品管理",
      "component": "product",
      "sortValue": 10,
      "status": 1
    }

成功时 data 为 null。新增子菜单后，服务会将其已有父级角色菜单关联标记为半选状态。

### 7.3 修改菜单

    PUT /admin/system/sysMenu/update
    token: <token>
    Content-Type: application/json

请求体必须含 id；parentId、title、component、sortValue、status 等传入非空字段会被更新。

    {
      "id": 20,
      "title": "商品列表",
      "component": "productList",
      "sortValue": 1,
      "status": 1
    }

成功时 data 为 null。

### 7.4 删除菜单

    DELETE /admin/system/sysMenu/removeById/{id}
    token: <token>

id 在该接口中绑定为 integer。若该菜单仍有直接的未删除子菜单，返回 217；否则逻辑删除，data 为 null。

### 7.5 查询角色菜单授权数据

    GET /admin/system/sysRoleMenu/findSysRoleMenuByRoleId/{roleId}
    token: <token>

data：

    {
      "sysMenuList": [SysMenu],
      "roleMenuIds": [10, 12, 13]
    }

sysMenuList 是完整菜单树；roleMenuIds 只包含该角色已完整选中的菜单 ID，不包含数据库标记为半选的父级菜单。

### 7.6 保存角色菜单授权

    POST /admin/system/sysRoleMenu/doAssign
    token: <token>
    Content-Type: application/json

请求体：

    {
      "roleId": 3,
      "menuIdList": [
        {"id": 10, "isHalf": 1},
        {"id": 12, "isHalf": 0},
        {"id": 13, "isHalf": 0}
      ]
    }

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| roleId | long | 是 | 角色 ID |
| menuIdList | object[] | 否 | 菜单授权集合 |
| menuIdList[].id | long | 是 | 菜单 ID |
| menuIdList[].isHalf | integer | 是 | 0 完整选择，1 半选父节点 |

服务会先清空该角色已有菜单关联，再写入 menuIdList。传 [] 或省略 menuIdList 可清空该角色菜单。

## 8. 品牌管理

### 8.1 品牌分页

    GET /admin/product/brand/{page}/{limit}
    token: <token>

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| page | integer | 是 | 页码，从 1 开始 |
| limit | integer | 是 | 每页数量 |

无筛选 Query 参数。data 类型：PageInfo<Brand>，按 id 倒序。

### 8.2 新增品牌

    POST /admin/product/brand/save
    token: <token>
    Content-Type: application/json

请求体：

    {
      "name": "示例品牌",
      "logo": "https://..."
    }

成功时 data 为 null。

### 8.3 修改品牌

    PUT /admin/product/brand/updateById
    token: <token>
    Content-Type: application/json

请求体必须含 id；name、logo 非空时更新。

    {
      "id": 10,
      "name": "新品牌名",
      "logo": "https://..."
    }

### 8.4 删除品牌

    DELETE /admin/product/brand/deleteById/{id}
    token: <token>

逻辑删除，成功时 data 为 null。

### 8.5 查询全部品牌

    GET /admin/product/brand/findAll
    token: <token>

data 类型：Brand[]，返回所有未逻辑删除品牌，按 id 倒序。

## 9. 分类管理

当前 8501 仅提供分类逐层查询和 Excel 导入/导出，不提供分类新增、修改、删除接口。

### 9.1 查询某父分类的直接子分类

    GET /admin/product/category/findCategoryListById/{id}
    token: <token>

id 为父分类 ID，根层通常传 0。data 类型：Category[]，仅返回下一层数据；每条数据会包含 hasChildren，供级联/懒加载树组件使用。

### 9.2 导出分类 Excel

    GET /admin/product/category/exportData
    token: <token>

响应直接写出 xlsx 文件，Content-Type 为 application/vnd.ms-excel，并设置下载文件名 分类数据.xlsx；不返回 Result JSON。

Axios 示例处理方式：设置 responseType 为 blob，并保留 token 请求头。

### 9.3 导入分类 Excel

    POST /admin/product/category/importData
    token: <token>
    Content-Type: multipart/form-data

表单字段名必须为 file。上传 xlsx 的列顺序和表头要求如下：

| 列序号 | Excel 表头 | 字段 | 类型 |
| ---: | --- | --- | --- |
| 0 | id | id | long |
| 1 | 名称 | name | string |
| 2 | 图片url | imageUrl | string |
| 3 | 上级id | parentId | long |
| 4 | 状态 | status | integer |
| 5 | 排序 | orderNum | integer |

服务每 100 行批量插入；当前实现没有导入事务和去重校验，导入异常可能出现部分数据已写入。成功时 data 为 null，读取/写入失败通常返回 204。

## 10. 分类品牌关联

### 10.1 按分类查询关联品牌

    GET /admin/product/categoryBrand/findBrandByCategoryId/{categoryId}
    token: <token>

data 类型：Brand[]，按关联 id 倒序。

### 10.2 分类品牌条件分页

    GET /admin/product/categoryBrand/{page}/{limit}
    token: <token>

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| page | integer | 是 | 页码 |
| limit | integer | 是 | 每页数量 |

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| brandId | long | 否 | 精确筛选品牌 |
| categoryId | long | 否 | 精确筛选分类 |

data 类型：PageInfo<CategoryBrand>，列表项包含 categoryName、brandName、logo。

### 10.3 新增分类品牌关联

    POST /admin/product/categoryBrand/save
    token: <token>
    Content-Type: application/json

请求体：

    {
      "brandId": 10,
      "categoryId": 100
    }

成功时 data 为 null。

### 10.4 修改分类品牌关联

    PUT /admin/product/categoryBrand/updateById
    token: <token>
    Content-Type: application/json

请求体必须含 id；brandId、categoryId 非空时更新。

    {
      "id": 9,
      "brandId": 11,
      "categoryId": 100
    }

### 10.5 删除分类品牌关联

    DELETE /admin/product/categoryBrand/deleteById/{id}
    token: <token>

逻辑删除，成功时 data 为 null。

## 11. 商品单位与规格

### 11.1 查询全部商品单位

    GET /admin/product/productUnit/findAll
    token: <token>

data 类型：ProductUnit[]，返回所有未逻辑删除单位，按 id 正序。当前服务没有商品单位的新增、修改、删除接口。

### 11.2 商品规格分页

    GET /admin/product/productSpec/{page}/{limit}
    token: <token>

page 和 limit 均为必填整数。data 类型：PageInfo<ProductSpec>，按 id 倒序。

### 11.3 新增商品规格

    POST /admin/product/productSpec/save
    token: <token>
    Content-Type: application/json

请求体：

    {
      "specName": "颜色",
      "specValue": "[\"红色\",\"蓝色\"]"
    }

specValue 是普通字符串；若前端需要数组语义，可自行 JSON.stringify 后传递。成功时 data 为 null。

### 11.4 修改商品规格

    PUT /admin/product/productSpec/updateById
    token: <token>
    Content-Type: application/json

请求体必须含 id；specName、specValue 非空时更新。

    {
      "id": 8,
      "specName": "颜色",
      "specValue": "[\"红色\",\"蓝色\",\"黑色\"]"
    }

### 11.5 删除商品规格

    DELETE /admin/product/productSpec/deleteById/{id}
    token: <token>

逻辑删除，成功时 data 为 null。

### 11.6 查询全部商品规格

    GET /admin/product/productSpec/findAll
    token: <token>

data 类型：ProductSpec[]，按 id 倒序。

注意：该接口的 SQL 未过滤 is_deleted，因此它与 11.2 不一致，可能返回已逻辑删除的规格。前端如需展示可用规格，应使用分页接口或自行按 isDeleted 过滤。

## 12. 商品管理

### 12.1 商品条件分页

    GET /admin/product/product/{page}/{limit}
    token: <token>

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| page | integer | 是 | 页码 |
| limit | integer | 是 | 每页数量 |

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| brandId | long | 否 | 精确筛选品牌 |
| category1Id | long | 否 | 精确筛选一级分类 |
| category2Id | long | 否 | 精确筛选二级分类 |
| category3Id | long | 否 | 精确筛选三级分类 |

示例：

    GET /admin/product/product/1/20?brandId=10&category3Id=103

data 类型：PageInfo<Product>，列表项包含品牌与三级分类名称。当前服务没有按商品名称、状态或审核状态筛选的参数。

### 12.2 获取商品详情

    GET /admin/product/product/getById/{id}

实现注意：该接口查询 SKU 的 SQL 没有选出 saleNum，因此 ProductSku.saleNum 可能为 null。
    token: <token>

data 类型：Product。除商品基础字段外，服务还会查询并填充：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| productSkuList | ProductSku[] | 该商品未逻辑删除的 SKU，按 id 倒序 |
| detailsImageUrls | string | product_details.imageUrls |

该接口用于商品编辑回显。当前 SQL 存在实现缺陷：category3Name 实际取的是二级分类名称；前端若需要准确三级分类名称，应以 category3Id 查询分类数据，或修复后端。

### 12.3 新增商品

    POST /admin/product/product/save
    token: <token>
    Content-Type: application/json

请求体结构：

    {
      "name": "示例商品",
      "brandId": 10,
      "category1Id": 1,
      "category2Id": 12,
      "category3Id": 103,
      "unitName": "件",
      "sliderUrls": "[\"https://.../1.png\",\"https://.../2.png\"]",
      "specValue": "[{\"specName\":\"颜色\",\"specValue\":[\"红色\",\"蓝色\"]}]",
      "detailsImageUrls": "[\"https://.../detail-1.png\"]",
      "productSkuList": [
        {
          "thumbImg": "https://.../sku-red.png",
          "salePrice": 99.00,
          "marketPrice": 129.00,
          "costPrice": 60.00,
          "stockNum": 100,
          "skuSpec": "{\"颜色\":\"红色\"}",
          "weight": 0.50,
          "volume": 0.02
        }
      ]
    }

前端提交规则：

1. productSkuList 必须是非空数组；为空时返回 228，不会进入数据库写入。
2. 新增时不要传 skuCode、skuName、productId、saleNum、status、auditStatus、auditMessage。服务端会将商品 status、auditStatus 置为 0，将 SKU skuCode、skuName、productId、saleNum、status 自动写入。
3. sliderUrls、specValue、detailsImageUrls、skuSpec 均按字符串保存，服务端不校验或转换 JSON 格式。前端可统一使用 JSON.stringify。
4. salePrice、marketPrice、costPrice、weight、volume 必须为非负数且最多两位小数；stockNum 必须为非负整数。weight 固定使用 kg，volume 固定使用 m³，请求中不携带单位字符。
5. 成功时 data 为 null，不返回新商品 ID。

### 12.4 修改商品

    PUT /admin/product/product/updateById
    token: <token>
    Content-Type: application/json

请求体必须含 id，结构与新增类似：

    {
      "id": 101,
      "name": "修改后的商品名",
      "brandId": 10,
      "category1Id": 1,
      "category2Id": 12,
      "category3Id": 103,
      "unitName": "件",
      "sliderUrls": "[\"https://.../1.png\"]",
      "specValue": "[]",
      "detailsImageUrls": "[\"https://.../detail.png\"]",
      "productSkuList": [
        {
          "id": 10001,
          "thumbImg": "https://.../sku.png",
          "salePrice": 109.00,
          "marketPrice": 129.00,
          "costPrice": 60.00,
          "stockNum": 88,
          "skuSpec": "{\"颜色\":\"红色\"}",
          "weight": 0.50,
          "volume": 0.02
        }
      ]
    }

当前实现限制：

1. 商品基础字段为非空局部更新。
2. productSkuList 中每一项仅按 sku.id 更新；不能通过本接口新增 SKU，也不会删除未出现在数组中的旧 SKU。
3. SKU 更新项必须保留已有 id；商品 id 或任一 SKU id 缺失时返回 230。
4. 服务会查询现有 product_details 后更新它；若该商品没有详情记录，可能返回 9999。
5. 成功时 data 为 null。

### 12.5 删除商品

    DELETE /admin/product/product/deleteById/{id}
    token: <token>

服务会逻辑删除商品、其全部 SKU 和商品详情。成功时 data 为 null。

### 12.6 更新商品上下架状态

    GET /admin/product/product/updateStatus/{id}/{status}
    token: <token>

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 商品 ID |
| status | integer | 传 1 时上架；任何其他值都会写为 -1（下架） |

服务会在同一事务内同步更新商品及其全部未删除 SKU 的状态，避免商品已上架但 SKU 仍不可售。成功时 data 为 null。该写操作为 GET 是现有实现，前端不可改用 PUT。

### 12.7 更新商品审核状态

    GET /admin/product/product/updateAuditStatus/{id}/{auditStatus}
    token: <token>

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 商品 ID |
| auditStatus | integer | 传 1 时写入 1 和“审批通过”；任何其他值写入 -1 和“审批不通过” |

成功时 data 为 null。该接口不能将审核状态恢复为 0，也不能自定义审核文案。

## 13. 订单统计

### 13.1 查询订单金额趋势

    GET /admin/order/orderInfo/getOrderStatisticsData
    token: <token>

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| createTimeBegin | string | 否 | 统计日期下限，例如 2026-07-01 |
| createTimeEnd | string | 否 | 统计日期上限，例如 2026-07-31 |

示例：

    GET /admin/order/orderInfo/getOrderStatisticsData?createTimeBegin=2026-07-01&createTimeEnd=2026-07-31

data 类型：OrderStatisticsVo。

    {
      "dateList": ["2026-07-01", "2026-07-02"],
      "amountList": [199.00, 520.50]
    }

两个数组按相同下标对应。未传筛选条件时返回统计表中的全部数据；接口注释中的“前一天”不是当前实际查询限制。

## 14. 文件上传

### 14.1 上传图片

    POST /admin/system/fileUpload
    token: <token>
    Content-Type: multipart/form-data

表单字段名：file。

成功时 data 为对象存储访问 URL 字符串：

    {
      "code": 200,
      "message": "操作成功",
      "data": "http://<object-storage-host>/<bucket>/<yyyyMMdd>/<uuid>.<detected-extension>"
    }

安全约束：

1. 仅允许 JPEG、PNG、WebP，类型由文件头和图片结构识别，不信任原始文件名或客户端 Content-Type。
2. 默认最大 5 MiB，最大宽高 8192，总像素不超过 2500 万；可通过 `IMAGE_UPLOAD_*` 环境变量调整。
3. 对象名只使用日期、UUID 和检测得到的扩展名，不保存原始文件名。
4. MinIO 对象 Content-Type 使用检测结果设置。
5. 成功响应仍为 URL 字符串，现有头像、品牌和商品图片赋值逻辑无需调整。

## 15. 前端调用建议与已知实现差异

1. 后台接口不经当前 Gateway 配置转发，开发环境请直接使用 8501；不要误用商城前台的 8500 Base URL。
2. 全局响应拦截器必须依据 body.code 处理 208、223-227、9999 等业务错误，不能仅依赖 HTTP status。
3. token 应放入请求头 token，而不是 Authorization Bearer。
4. 用户响应已改为安全 VO；管理端页面不应再依赖或回填 password 字段。
5. 停用账号无法重新登录，且修改账号状态后该账号的全部后台 token 会立即失效。
6. 修改密码、用户名、角色或角色菜单后，受影响用户会收到 208 并需要重新登录。
7. 商品详情回显的 category3Name 当前错误地等于二级分类名称；请以 category3Id 为准。
8. 商品修改接口只会更新现有 SKU，不能新增或删除 SKU；新增/删除 SKU 需要后端扩展接口后再做完整编辑能力。
9. 商品规格的 findAll 会包含已逻辑删除记录；优先使用分页接口或前端过滤 isDeleted。
10. 商品上下架与审核是 GET 写操作，务必避免浏览器预取、缓存或链接探测触发这些 URL。
11. Excel 导入不具备事务和去重能力，正式导入前应先在前端校验文件列、空值和重复数据。
12. 图片控件建议设置 `accept=".jpg,.jpeg,.png,.webp"` 和 5 MiB 前端提示，但后端校验才是安全边界。
13. 当前 Knife4j 分组只匹配 /api/**，而本服务路径均为 /admin/**；不要将自动生成文档未显示管理端接口误解为接口不存在。

## 16. 源码依据

- 服务端口与免鉴权路径：zjzx-manager/src/main/resources/application.yml、application-dev.yml
- Controller：zjzx-manager/src/main/java/com/tzp/zjzx/manager/controller
- 登录拦截器：zjzx-manager/src/main/java/com/tzp/zjzx/manager/interceptor/LoginAuthInterceptor.java
- 业务实现：zjzx-manager/src/main/java/com/tzp/zjzx/manager/service/impl
- SQL 查询与写入语义：zjzx-manager/src/main/resources/mapper
- 统一响应与数据模型：zjzx-model/src/main/java/com/tzp/zjzx/model
- 全局异常处理：zjzx-common/common-service/src/main/java/com/tzp/zjzx/common/exception/GlobalExceptionHandler.java
