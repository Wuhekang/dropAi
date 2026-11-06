# 智能画图部署

1. 执行 `sql/diagram_project_v1.6.sql`。
2. 安装 Python 3.10+，执行 `python -m pip install -r diagram-worker/requirements-web.txt`。Linux 还需系统 Cairo 与 `Noto Sans CJK SC`；Windows 若使用 PNG，需安装 Cairo DLL 并加入 `PATH`。
3. 默认使用 `python` 和项目根目录的 `diagram-worker/web_engine.py`。后端从仓库根目录或 `backend` 目录启动都能自动定位；也可用 `DIAGRAM_PYTHON`、`DIAGRAM_WORKER` 覆盖。
4. 前端执行 `npm ci && npm run build`，将 `frontend/dist` 按现有方式发布。

VSDX 是可选能力；当前服务器未配置 Windows Visio Worker 时，页面会明确提示不可用，不影响 SVG、JSON 和已正确安装 Cairo 后的 PNG。

回滚时回退本功能提交并删除 `diagram_project` 表即可；删除表会永久删除用户画图项目，操作前必须备份。
