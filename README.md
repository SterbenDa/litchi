## 欢迎使用litchi! 

这是一款轻量级的java游戏服务器框架。
它具备高性能、可伸缩、分布式、多线程等特点。并且上手简单、易学。
让开发者更多的关注游戏业务，高效完成功能实现。
litchi核心功能主要由两个“中心组件”实现。即：“消息中心”、“路由中心”。通过这两个“中心”有序的管理好所有消息和逻辑调用。通过RPC实现跨进程协作。
当然框架还整合了一些常用的基础库，比如配置管理、数据同步、DB访问等。同时开发者也可以基于业务需要自定义实现自己的组件。

## 讨论与交流
QQ群:**193673074**

## 特性

### 一款高性能、实时通信、多进程的游戏解决方案

* 适用于手游、h5游戏等各类高性能游戏服务器的开发

### 功能特点

* 基于Disruptor消息队列设计的无锁并发模式
* 分布式(多进程)架构，几行代码实现一个功能服务器的搭建
* 多线程设计，注解方式配置，轻松管理所有消息流
* 强大的RPC功能，调用远程RPC近似于调用本地函数，无需手工定义内部协议
* 支持插件功能，轻松实现功能插件
* 框架基于netty设计，轻松定义外部协议
* 简单的策划配置管理，可实现多条件查询配置


### 线程模型
![avatar](https://gitee.com/phantacix/litchi/raw/5f62b7a688daf193d10802883463a10515a84f00/docs/images/thread_mode.png)

[点击看图](https://gitee.com/phantacix/litchi/raw/5f62b7a688daf193d10802883463a10515a84f00/docs/images/thread_mode.png)


## 构建环境
* java 8 +
* gradle 4.0+

## 第三方引用
*  `Netty` 宇宙最强的java网络库，可定义各种网络通信方式，本框架中RPC、http、websocket等都基于netty的封装。
*  `Disruptor` 高性能线程间消息传递库，通过它来实现“消息中心”，跨线程消息传递so easy！
*  `HikariCP` 稳定、高性能的JDBC连接池。github star破11k!
*  `logback` 快速、灵活的日志库，log4j作者的续作。
*  `fastjson` 马爸爸家的json库，看名字就知道快！
*  `okhttp3` http client库，真的很ok~
*  `reflectasm` 反射库，据说性能高...


## 功能与特性
[传送门](https://github.com/phantacix/litchi/wiki/%E5%8A%9F%E8%83%BD%E4%B8%8E%E7%89%B9%E6%80%A7)

## 新手引导
待完成
