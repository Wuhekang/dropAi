# 在线部署说明

## 免费在线试用版：Render

如果只是想先得到一个公网链接在线试用，推荐用 Render Free Web Service。

这个方案会把前端和后端打进同一个 Docker 服务，使用内置 H2 文件库，不需要单独购买 MySQL。适合演示和试用，不适合正式商用长期保存数据。

### Render 部署步骤

1. 把项目推到 GitHub 仓库。
2. 打开 [Render](https://render.com/) 并用 GitHub 登录。
3. 选择 `New` -> `Blueprint`。
4. 选择你的 GitHub 仓库。
5. Render 会读取项目根目录的 `render.yaml`。
6. 在环境变量里填写：

```text
DOUBAO_API_KEY=你的豆包Ark Key
```

7. 点击部署。

部署完成后 Render 会给你一个公网地址，例如：

```text
https://dropai-demo.onrender.com
```

诊断地址：

```text
https://你的Render域名/api/rewrite/ai/status
```

### Render 免费版限制

- 免费实例冷启动较慢，长时间没人访问会休眠。
- 文件和 H2 数据库不适合作为正式生产持久存储。
- 文档并发过高可能受免费实例 CPU/内存限制，必要时把 `application-demo.yml` 里的 `document-concurrency` 降低。

## 适用方式

推荐用一台云服务器直接 Docker Compose 部署。部署后访问服务器公网 IP 或绑定域名即可在线试用。

## 服务器要求

- 2 核 4G 起步，文档并发高时建议 4 核 8G。
- 已安装 Docker 和 Docker Compose。
- 开放服务器安全组/防火墙 `80` 端口。

## 首次部署

进入项目根目录：

```bash
cd dropAi
cp .env.example .env
```

编辑 `.env`：

```bash
MYSQL_ROOT_PASSWORD=你的root密码
MYSQL_DATABASE=drop_ai
MYSQL_USER=dropai
MYSQL_PASSWORD=你的数据库密码

DOUBAO_API_KEY=你的豆包Ark Key
DOUBAO_MODEL=doubao-seed-1-8-251228
DOUBAO_ENDPOINT=https://ark.cn-beijing.volces.com/api/v3/chat/completions
```

启动：

```bash
docker compose up -d --build
```

访问：

```text
http://服务器公网IP
```

后端诊断：

```text
http://服务器公网IP/api/rewrite/ai/status
```

## 更新代码后重新部署

```bash
docker compose up -d --build
```

## 查看日志

```bash
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f mysql
```

## 数据与文件

- MySQL 数据保存在 Docker volume：`mysql-data`
- 上传文件和处理结果保存在 Docker volume：`backend-storage`
- 不要把 `.env` 上传到公开仓库。

## 常见问题

如果页面能打开但接口失败：

```bash
docker compose ps
docker compose logs -f backend
```

如果豆包调用失败，先打开：

```text
http://服务器公网IP/api/rewrite/ai/status
```

如果文档上传失败，检查 Nginx 和 Spring 上传限制。当前已配置：

- Nginx：`client_max_body_size 60m`
- Spring：`max-file-size: 50MB`，`max-request-size: 60MB`

## 域名和 HTTPS

如果有域名，可以先把域名 A 记录解析到服务器公网 IP。HTTPS 可以后续加 Caddy 或 Certbot/Nginx 证书配置。
