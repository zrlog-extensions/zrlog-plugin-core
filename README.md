# zrlog-plugin-core

ZrLog 插件运行时服务。负责插件启动、运行状态、功能注册、定时任务、通知渠道路由和运行时管理页面。

## 功能

- 启动和管理本地插件进程
- 展示插件运行状态、调用日志和通知投递记录
- 管理插件定时任务和外部触发入口
- 为插件提供运行时调用、通知渠道和静态页面代理

## 构建

```shell
export JAVA_HOME=${HOME}/dev/graalvm-jdk-latest
export PATH=${JAVA_HOME}/bin:$PATH
```


更多插件开发说明见 [ZrLog 插件开发文档](https://blog.zrlog.com/zrlog-plugin-dev.html)。
