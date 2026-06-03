# AI学术写作优化与降重辅助平台 MVP

## 项目结构

- `backend/`：Spring Boot 3 + MyBatis-Plus 后端服务，端口 `8080`
- `frontend/`：Vue3 + Vite + Element Plus 前端，端口 `5173`

## 在线部署

已提供 Docker Compose 部署配置，详见 [DEPLOY.md](DEPLOY.md)。

## 数据库

MySQL 默认连接配置在 `backend/src/main/resources/application.yml`：

```yaml
url: jdbc:mysql://localhost:3306/drop_ai
username: root
password: root
```

初始化数据库和表：

```sql
source backend/src/main/resources/schema.sql;
```

如本地 MySQL 账号密码不同，请先修改 `application.yml`。

## 启动后端

先设置豆包 API Key：

```powershell
$env:DOUBAO_API_KEY="你的豆包API Key"
$env:DOUBAO_MODEL="doubao-seed-1-8-251228"
```

```bash
cd backend
mvn spring-boot:run
```

接口统一前缀：`http://localhost:8080/api/rewrite`

如果未设置 `DOUBAO_API_KEY`，系统会自动使用 Mock 改写服务，便于本地开发。

## 启动前端

```bash
cd frontend
npm install --cache .npm-cache
npm run dev
```

访问：`http://localhost:5173`

## 已实现功能

- 文本优化提交：`POST /api/rewrite/submit`
- AI痕迹风险分析：`POST /api/rewrite/analyze`
- 历史记录列表：`GET /api/rewrite/list`
- 历史记录详情：`GET /api/rewrite/{id}`
- 删除历史记录：`DELETE /api/rewrite/{id}`
- 上传整篇 Word 文档异步处理：`POST /api/document/rewrite/upload`
- 查询文档处理任务：`GET /api/document/rewrite/job/{jobId}`
- 下载优化后的 Word 文档：`GET /api/document/rewrite/download/{jobId}`

当前 AI 改写服务已接入豆包 Ark OpenAI-compatible 接口：`DoubaoAiRewriteService`。
密钥从环境变量 `DOUBAO_API_KEY` 读取，未配置或调用失败时回退到 `MockAiRewriteService`。

## 整篇文档处理说明

当前 MVP 仅支持 `.docx` 文件。系统会按段落分析 AI 痕迹风险，只改写风险较高的正文段落，并生成新的 `.docx` 文件下载。

第一版限制：

- 暂不处理 PDF、扫描件、图片文字。
- 主要处理 Word 普通正文段落，复杂文本框、页眉页脚、批注等不作为第一版重点。
- 任务状态暂存在服务内存中，重启后任务记录会丢失；生成文件位于 `backend/storage/outputs`。
