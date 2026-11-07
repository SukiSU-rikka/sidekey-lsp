# 快捷键增强 (LSPosed)

一加 Ace 6「快捷键」(扫描码 735) 单击 / 双击 / 长按自定义，可拦截系统原功能。

## 编译
推到 GitHub，Actions 自动编译，在 Actions 页面下载 `SideKeyLSP` 产物里的 apk。
签名用仓库里固定的 `app/sidekey.jks`（仅自用），之后更新可直接覆盖安装。

## 启用
1. 安装 apk，LSPosed 里启用模块，作用域勾「系统框架」，重启
2. 打开 App 设置动作，点保存（不用再重启）
3. 记得禁用之前的 root 版 sidekey 模块，不然会触发两次
4. Shell 动作需要在 SukiSU 里给本 App 授予 root

## 排错
LSPosed 日志里搜 `[SideKey]`：开机应有 `hooked 2 methods`。
