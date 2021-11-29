# Musician-Auto-Check-Web

## 使用

访问 Auto-Musician 站点（例如[本项目的官方站](#nothing)），点击“二维码登录”，出现二维码后扫码登录，如显示“登录成功”字样，即可完成登录，之后交给 Auto-Musician 进行自动操作即可。

## 安装

> 注意：本章节仅适用于有自行搭建 Auto-Musician 的用户，如果没有这个想法，请不要照做。 环境要求：

- Java 11（或以上）
- MySQL（建议 MySQL 5 以上）

虽然可选，但强烈建议的：

- 拥有反向代理功能的 Web 服务端（Auto-Musician 并不支持 SSL，建议通过 Web 服务端做 SSL 访问）

安装过程：

1. 下载最新版的 Auto-Musician（[发布页面](https://github.com/LamGC/Auto-Musician/releases/latest)）
2. 解压到服务端，并使用 Java 启动一次，生成 `config.json` 配置文件
3. 在配置文件中配置好 MySQL 服务器连接信息, 如有需要可更改其他配置
4. 确保 MySQL 服务器正在运行，再次启动 Auto-Musician，如显示 `The automatic musician is awake! He's working now!`，则安装成功。
