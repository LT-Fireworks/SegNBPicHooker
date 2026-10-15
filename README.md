# SegNBPicHooker

九号出行（`cn.ninebot.ninebot`）的 LSPosed 模块：把 App 首页的**车辆图片换成你自己的图**。 随时可以一键恢复默认。

## 声明
**本项目不是九号官方产品。作者不对用户的任何行为负责。**

## 功能

-   **桌面图标**：安装后桌面出现 SegNBPicHooker 图标，点开即模块界面。
-   **自带界面**：当前图片预览 + 状态信息，两个按钮：
    -   **选择车辆图片**：调系统文件选择器（`ACTION_GET_CONTENT`），支持 png / jpg / webp / gif / bmp；
    -   **恢复默认**：删除自定义图片，回到九号出行原本的车辆图。
-   **首页车辆图替换**：只改首页那张车图，不碰其它图片。
-   **权限干净**：不申请网络、存储等任何权限，不依赖 root 直接读写文件； 图片通过模块自己的只读 ContentProvider 下发给九号出行，只需要 LSPosed 框架本身。

## 环境要求

| 项 | 要求 |
| --- | --- |
| 系统 | Android 8.0+（`minSdk 26`，`targetSdk 34`） |
| 框架 | 已 root + LSPosed，模块作用域勾选「九号出行」（`cn.ninebot.ninebot`） |
| 宿主 | 九号出行 App；hook 点是它的 Glide 图片加载入口，详见「已知限制」 |

## 安装与使用

1.  安装 APK。
2.  在 LSPosed 里启用模块，作用域勾「九号出行」，然后重启九号出行。
3.  打开桌面上的 **SegNBPicHooker**，点 **选择车辆图片** 挑一张图。
4.  回到九号出行（杀掉后台重开最稳）就能看到首页车辆图变成你选的那张。
5.  想还原就点 **恢复默认**。

## 工作原理

```
模块界面进程                                   九号出行进程（被 hook）
─────────────                                  ──────────────────────
选图 → filesDir/vehicle.<ext>                   Glide RequestBuilder#load(String url)
     → prefs: enabled=true, rev+1               └─ 是车辆图 URL 且替换已开启？
                                                    ├─ 是 → 用 content:// 取图，
ContentProvider (authority:                         │      落副本到宿主外部私有目录/
com.LT.SegNBPicHooker.vehimg)                       │      segvehimg-<rev>.<ext>，
  · call("state") → enabled/rev/ext/…               │      把参数换成 file://<副本>
  · openFile("image/<rev>") → 图片字节              └─ 否 → 不动，保持宿主原图
```

## 已知限制

-   **只覆盖 Glide 这条链路**。若九号出行某版本改用别的加载器，替换不会生效； LSPosed 日志里的 `img ...` 诊断行会显示实际调用的是哪条链路，可据此补 hook 点。
-   首页车辆图以外的图片一律不动；模块也不做任何联网行为。