# AI 客服管理平台前端

基于 Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router、Axios 构建的管理端前端工程。

## 功能页面

- 登录页：账号密码登录，包含滑块验证码交互，登录时提交 `sliderToken` 给后端。
- 注册页：管理员账号注册表单。
- 后台布局：侧边栏导航、顶部用户信息、退出登录入口。
- 用户管理：用户分页查询、新增、编辑、删除、状态展示。
- 角色管理：角色分页查询、新增、编辑、删除。
- 资源树管理：菜单、按钮、接口资源树维护，包含同步资源按钮。
- 用户角色授权：选择用户并分配角色。
- 角色资源授权：选择角色并分配资源树权限。

## 目录结构

```text
frontend
├── src
│   ├── api          # Axios 接口封装
│   ├── components   # 通用组件，例如滑块验证码
│   ├── layouts      # 后台布局
│   ├── router       # 路由与登录守卫
│   ├── stores       # Pinia 状态
│   ├── styles       # 全局样式
│   ├── types        # TypeScript 类型
│   ├── utils        # 请求工具
│   └── views        # 页面
├── index.html
├── package.json
├── vite.config.ts
└── tsconfig.json
```

## 本地运行

> 本次创建工程未运行任何需要网络安装依赖的命令。首次运行前请在 `frontend` 目录安装依赖。

```bash
cd frontend
npm install
npm run dev
```

构建命令：

```bash
npm run build
```

## 环境变量

复制 `.env.example` 为 `.env.local` 后按需调整：

```bash
VITE_API_BASE_URL=/api
VITE_API_PROXY_TARGET=http://localhost:18080
```

- `VITE_API_BASE_URL`：前端 Axios 请求基础路径。
- `VITE_API_PROXY_TARGET`：Vite 开发代理目标后端地址。

## 后端接口约定

当前前端按以下 REST 风格接口封装，若后端实际路径不同，可集中调整 `src/api/auth.ts` 和 `src/api/system.ts`。

### 认证

- `POST /api/auth/login`：登录，参数 `{ username, password, sliderToken }`，返回 `{ token, user }`。
- `POST /api/auth/register`：注册。
- `GET /api/auth/me`：获取当前用户。
- `POST /api/auth/logout`：退出登录。

### 用户与角色

- `GET /api/users`：用户分页查询。
- `POST /api/users`：新增用户。
- `PUT /api/users/{id}`：更新用户。
- `DELETE /api/users/{id}`：删除用户。
- `GET /api/roles`：角色分页查询。
- `POST /api/roles`：新增角色。
- `PUT /api/roles/{id}`：更新角色。
- `DELETE /api/roles/{id}`：删除角色。

### 资源与授权

- `GET /api/resources/tree`：资源树。
- `POST /api/resources`：新增资源。
- `PUT /api/resources/{id}`：更新资源。
- `DELETE /api/resources/{id}`：删除资源。
- `POST /api/resources/sync`：同步后端资源。
- `GET /api/users/{userId}/roles`：查询用户角色 ID。
- `PUT /api/users/{userId}/roles`：保存用户角色。
- `GET /api/roles/{roleId}/resources`：查询角色资源 ID。
- `PUT /api/roles/{roleId}/resources`：保存角色资源。

## 响应格式

Axios 响应拦截器兼容两类返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

或直接返回业务数据。分页数据默认格式：

```json
{
  "records": [],
  "total": 0
}
```
