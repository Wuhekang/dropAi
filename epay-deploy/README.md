# DropAI 独立易支付部署包

目标：把 `lopinx/epay` 彩虹易支付作为独立服务部署到 Windows Server，默认服务器目录为：

```text
C:\epay
  app
  package
  runtime
  scripts
  logs
  run
```

它不放进 DropAI 目录，也不修改 DropAI 文件。两者只通过 Nginx 和 DropAI 的易支付环境变量对接。

## 1. 本地准备

在本机打开 PowerShell 或 CMD，进入本目录：

```bat
cd C:\Users\Administrator\Documents\dropAi\epay-deploy
scripts\local\download_epay.bat
scripts\local\inspect_epay.bat
scripts\local\package_epay.bat
```

完成后会生成：

```text
epay-deploy\dist\epay-package.zip
```

把这个 zip 上传到 Windows Server，例如上传到：

```text
C:\epay\package\epay-package.zip
```

## 2. 服务器部署

在服务器上新建目录：

```bat
mkdir C:\epay
mkdir C:\epay\package
```

把 `epay-package.zip` 放到 `C:\epay\package\` 后，解压：

```powershell
Expand-Archive -Force C:\epay\package\epay-package.zip C:\epay\package\expanded
```

复制应用和脚本：

```powershell
Copy-Item -Recurse -Force C:\epay\package\expanded\app C:\epay\app
Copy-Item -Recurse -Force C:\epay\package\expanded\server-scripts C:\epay\scripts
Copy-Item -Recurse -Force C:\epay\package\expanded\nginx C:\epay\nginx
```

安装 PHP 7.4 运行环境：

```bat
C:\epay\scripts\install_php74.bat
```

启动 PHP-CGI：

```bat
C:\epay\scripts\start_epay_php.bat
```

安装易支付计划任务，每分钟执行一次 `cron.php`：

```bat
C:\epay\scripts\install_epay_cron_task.bat
```

## 3. MySQL 数据库

登录 MySQL 后执行：

```sql
CREATE DATABASE epay DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER 'epay_user'@'localhost' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';
GRANT ALL PRIVILEGES ON epay.* TO 'epay_user'@'localhost';
FLUSH PRIVILEGES;
```

然后浏览器访问：

```text
http://dropai-demo.cn/install
```

安装页面里填写：

```text
数据库地址：127.0.0.1
数据库名：epay
数据库用户：epay_user
数据库密码：CHANGE_ME_STRONG_PASSWORD
```

安装成功后，进入后台：

```text
http://dropai-demo.cn/admin
```

如果服务器能直接执行 `mysql.exe`，也可以先复制模板：

```bat
copy C:\epay\scripts\create_epay_db.sql.template C:\epay\scripts\create_epay_db.sql
notepad C:\epay\scripts\create_epay_db.sql
C:\epay\scripts\create_epay_db.bat
```

只需要把模板里的数据库密码改成你的强密码。

## 4. Nginx 配置

不要覆盖原 Nginx 配置。把下面模板复制为你 Nginx 的一个新站点配置，或追加到 `http { ... }` 内：

```text
C:\epay\nginx\epay-dropai-demo.cn.conf
```

测试配置：

```bat
cd /d C:\nginx
nginx.exe -t
```

重载：

```bat
nginx.exe -s reload
```

如果你的 Nginx 不在 `C:\nginx`，改成实际目录。

## 5. HTTPS 最低成本方案

最低成本建议：

1. 先让 `dropai-demo.cn` A 记录解析到 `43.136.167.53`。
2. 先用 HTTP 完成安装和联调。
3. HTTPS 用 Cloudflare 代理加免费证书，或用 win-acme 给 Windows Nginx 申请 Let's Encrypt 证书。

如果走 Cloudflare，回源可以先 HTTP，生产建议改成 Full strict 并配置源站证书。

## 6. DropAI 对接参数

DropAI 环境变量建议：

```text
EPAY_GATEWAY=http://dropai-demo.cn/
EPAY_PID=易支付后台商户ID
EPAY_KEY=易支付后台商户密钥
APP_BASE_URL=https://dropai-demo.com
EPAY_NOTIFY_URL=https://dropai-demo.com/api/recharge/notify
EPAY_RETURN_URL=https://dropai-demo.com/recharge/result
```

易支付后台需要配置：

```text
网站地址：https://dropai-demo.com
异步通知地址：https://dropai-demo.com/api/recharge/notify
同步返回地址：https://dropai-demo.com/recharge/result
商户PID：后台创建商户后获得
KEY：后台商户密钥中获得
```

## 7. 检查

服务器上执行：

```bat
C:\epay\scripts\check_epay.bat
```

它会检查：

- PHP-CGI 进程
- 9074 端口
- 本机 FastCGI 监听
- Nginx 代理访问
- DropAI 回调接口可访问性

## 8. 常用命令

```bat
C:\epay\scripts\start_epay_php.bat
C:\epay\scripts\stop_epay_php.bat
C:\epay\scripts\restart_epay_php.bat
C:\epay\scripts\check_epay.bat
C:\epay\scripts\run_epay_cron.bat
C:\epay\scripts\install_epay_cron_task.bat
```
