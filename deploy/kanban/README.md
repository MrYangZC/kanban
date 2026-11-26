# kanban 部署说明（192.168.2.17）

## 一、部署拓扑

| 组件 | 位置 | 端口 | 管理方式 |
|---|---|---|---|
| 前端静态文件 | `/home/ubuntu/sda/yushun/nginx/html/kanban/` | 80（经 yushun-nginx） | 直接放文件，无需重启容器（仅刷新浏览器） |
| 后端 kanban-server | `/home/ubuntu/sda/yushun/kanban/` | 9085（宿主）/ 容器内 9085 | `docker compose`（工程名 `kanban`） |
| nginx | `/home/ubuntu/sda/yushun/nginx/conf/nginx.conf` | 80 / 443 | 容器 `yushun-nginx`，ro 挂载整份 nginx.conf |
| 数据库 | 容器 `pgsql16-postgis`（库名 `kanban`） | 5433（宿主）/ 5432（容器） | 已存在，Flyway 自动迁移到最新版本 |
| 附件存储 | `/home/ubuntu/sda/yushun/kanban/uploads/` → 容器 `/app/uploads` | - | docker volume 绑定目录 |

访问入口：**http://192.168.2.17/kanban/**

## 二、目录结构

```
/home/ubuntu/sda/yushun/kanban/
├── docker-compose.yml      # 后端容器定义（独立工程 kanban，接入 yushun_bridge 外部网络）
├── Dockerfile              # 运行镜像（FROM eclipse-temurin:17-jdk，本机无外网拉不到 jre）
├── kanban-server.jar       # 由本机 Maven 构建后上传，容器只运行不编译
├── uploads/                # 附件/头像物理目录（挂到容器 /app/uploads）
└── README.md
```

## 三、发布 / 更新流程

### 3.1 后端更新

```bash
# 1) 本机（Windows，仓库根目录）构建
mvn -B -DskipTests package          # 产出 server/target/kanban-server.jar

# 2) 上传
scp server/target/kanban-server.jar ubuntu@192.168.2.17:/home/ubuntu/sda/yushun/kanban/

# 3) 目标机重建并启动（--build 才会把新 jar 拷进镜像层）
cd /home/ubuntu/sda/yushun/kanban && docker compose up -d --build

# 4) 看日志确认 Started PmServerApplication
docker logs -f --tail 50 kanban-server
```

### 3.2 前端更新

```bash
# 1) 本机构建（注意 --base=/kanban/，否则资源路径会指向站点根）
cd web && npm run build:prod
#   等价于：vue-tsc -b && vite build --base=/kanban/

# 2) 打包上传解压
tar -czf dist.tgz -C web/dist .
scp dist.tgz ubuntu@192.168.2.17:/tmp/
ssh ubuntu@192.168.2.17 "rm -rf /home/ubuntu/sda/yushun/nginx/html/kanban/* && \
  tar -xzf /tmp/dist.tgz -C /home/ubuntu/sda/yushun/nginx/html/kanban/"
```

前端是纯静态文件，改完刷新浏览器即可；nginx 不需要重启。

### 3.3 nginx 配置更新

`nginx.conf` 是整份 ro 挂载进容器的，改完必须让 nginx 重新加载：

```bash
docker exec yushun-nginx nginx -t          # 先验语法，必须通过
docker restart yushun-nginx                # 或用 docker exec yushun-nginx nginx -s reload（不断连接）
```

改动前的备份在 `/home/ubuntu/sda/yushun/nginx/conf/nginx.conf.bak-<时间戳>`。

## 四、路由分工（最容易踩错的地方）

| 请求路径 | 去处 | 说明 |
|---|---|---|
| `/kanban/...` | `/usr/share/nginx/html/kanban/` | 前端静态资源 + history 回退到 `/kanban/index.html` |
| `/kanban` | 301 → `/kanban/` | 避免相对路径解析错位 |
| `/kanban-api/...` | `http://172.17.0.1:9085/api/...` | 后端接口：nginx 摘掉 `/kanban-api` 前缀再转给后端 context-path `/api` |

站点根的 `/api` **不占用**，留给其他项目（本机已有一堆 `/xxx-api` 前缀的项目）。

**前缀对齐靠后端两个环境变量**（在 `docker-compose.yml` 里，缺一个就会出怪问题）：

| 变量 | 值 | 说明 |
|---|---|---|
| `KANBAN_UPLOAD_PUBLIC_PREFIX` | `/kanban-api` | 决定两处：后端返回的头像/附件地址前缀；以及附件读取用的 `KB_FILE` Cookie 的 `Path`（= `<前缀>/uploads`）。不配的话后端会按 context-path 给出 `/api/uploads/...` → 头像、附件全 404/401 |
| `KANBAN_JWT_SECRET` | `.env` 里的随机串（≥32 字节） | JWT 签名密钥。**不配服务直接起不来**（compose 用 `:?` 强制、JwtService 也拒绝内置开发密钥上线） |

鉴权是 Spring Security + JWT（`Authorization: Bearer`），**不再有 HttpSession/JSESSIONID**。
唯一的例外是 `KB_FILE` Cookie：浏览器渲染 `<img src>` 时无法附带自定义请求头，所以登录接口会额外下发一个
`HttpOnly`、`Path=/kanban-api/uploads`、`SameSite=Lax` 的 Cookie，只对静态文件路径生效；
业务接口即使拿到它也不会被当作登录态，不扩大 CSRF 面。

也就是说：nginx 侧 `proxy_pass` 把 `/kanban-api/` 映射到后端的 `/api/`，
而后端**对外宣称**的地址（上传文件 URL、`KB_FILE` Cookie Path）由 `KANBAN_UPLOAD_PUBLIC_PREFIX` 统一成 `/kanban-api`，
三者一致才不会出现「能登录但头像全裂」「能登录但下一步 401」这类问题。

改前缀时三处要一起改：`web/src/api/request.ts` 的 `API_BASE`、`web/src/api/issue.ts` 的导出 URL、nginx + 上面两个环境变量。

## 五、其他说明

- **运行镜像是 JDK 而不是 JRE**：目标机无外网（`registry-1.docker.io` 不可达），只能用本机已有的
  `eclipse-temurin:17-jdk`。将来能拉镜像时把 `Dockerfile` 的 `FROM` 换成 `17-jre` 即可。
- **JVM 内存**：`JAVA_OPTS=-Xms256m -Xmx768m`，写死在 compose 的 environment 里。
- **数据库连接**：走 `yushun_bridge` 网络内的 `pgsql16-postgis:5432`，不依赖宿主机 5433 端口映射。
- **重命名接口前缀时别忘了库里的存量数据**：`sys_user.avatar_url`、`attachment.url` 存的是完整对外地址。
  本次从 `/api/uploads/` 切到 `/kanban-api/uploads/` 时执行过：

  ```sql
  update sys_user  set avatar_url = replace(avatar_url, '/api/uploads/', '/kanban-api/uploads/') where avatar_url like '/api/uploads/%';
  update attachment set url        = replace(url,        '/api/uploads/', '/kanban-api/uploads/') where url        like '/api/uploads/%';
  ```

- **密钥放 `.env`，不进仓库**：`/home/ubuntu/sda/yushun/kanban/.env`（`chmod 600`）里放
  `KANBAN_JWT_SECRET=<64 位随机 hex>`。compose 用 `${KANBAN_JWT_SECRET:?...}` 读取，缺失即报错启动失败。
  换密钥会让所有已签发 token 失效（用户需重新登录），这是无状态方案的代价。
- **重启策略**：`restart: always`，宿主重启后容器自动拉起。
- **日志**：`docker logs kanban-server`（json-file，50m × 5 轮转）。应用本身不落文件日志。
- **nginx 上游用 172.17.0.1 而不是容器名**：这是本文件既有约定；用容器名做 upstream 时，后端没启动会让
  nginx 直接启动失败。

## 六、验证清单

```bash
curl -o /dev/null -w '%{http_code}\n' http://127.0.0.1/kanban/                # 200
curl -o /dev/null -w '%{http_code}\n' http://127.0.0.1/kanban/logo.svg        # 200 image/svg+xml
curl -o /dev/null -w '%{http_code}\n' http://127.0.0.1/kanban/projects/1      # 200（history 回退）
curl -o /dev/null -w '%{http_code}\n' http://127.0.0.1/kanban-api/v1/projects # 401（未登录）
curl -o /dev/null -w '%{http_code}\n' http://127.0.0.1/api/v1/projects        # 404（根 /api 不归 kanban）

# 登录：应同时拿到 token（响应体）和 KB_FILE Cookie（响应头，Path=/kanban-api/uploads）
RESP=$(curl -s -c /tmp/ck -X POST -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"admin123"}' \
     http://127.0.0.1/kanban-api/v1/users/login)
TOKEN=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

curl -s -H "Authorization: Bearer $TOKEN" -o /dev/null -w '%{http_code}\n' \
     http://127.0.0.1/kanban-api/v1/projects                                  # 200
```

上传 + `<img>` 链路（返回地址必须是 `/kanban-api/uploads/...`，且**只带 Cookie** 也能取到）：

```bash
URL=$(curl -s -H "Authorization: Bearer $TOKEN" -F "file=@/tmp/t.png;type=image/png" \
      http://127.0.0.1/kanban-api/v1/upload/image | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['url'])")
curl -s -b /tmp/ck -o /dev/null -w '%{http_code}\n' "http://127.0.0.1$URL"    # 200（jar 按 Path 自动带上 KB_FILE）
curl -s -o /dev/null -w '%{http_code}\n' "http://127.0.0.1$URL"               # 401（无凭证）
```

## 七、回滚

```bash
cd /home/ubuntu/sda/yushun/kanban && docker compose down
# nginx：把 nginx.conf.bak-<时间戳> 覆盖回 nginx.conf，docker exec yushun-nginx nginx -t && docker restart yushun-nginx
# 前端：rm -rf html/kanban（或换回上一版 dist）
```
