# 领克助手 (Lynk Assistant)

吉利 / 领克 Flyme Auto（OneOS）车机的小工具，**只用普通应用权限**做两件事：

1. **进入工程模式** —— 一键拉起 `com.desaysv.engmode`；
2. **把任意应用写进桌面「我的应用」** —— 通用实现，不写死包名。

外加只读的**设备信息**（VIN / 序列号 / 版本）和系统设置入口。

> 适用于用户自己的车机，用于安装自己开发/侧载的应用。本项目与吉利、领克、
> 德赛西威、魅族官方无关。

---

## 功能

| 功能 | 说明 |
| --- | --- |
| ① 进入工程模式 | 多策略：显式组件 → `getLaunchIntentForPackage` → `MAIN/LAUNCHER` 解析，全过程打印结果 |
| ② 扫描工程模式页面 | 遍历 40+ 个已知 Activity，用 `getActivityInfo()` 读出 `exported`，能直接进的给按钮 |
| ③ 桌面应用列表管理 | 列出所有已安装应用，选中即写入桌面「我的应用」；可批量写入；可按 `_id` 单条删除 |
| ④ 设备信息 | `Build` + 车机系统属性（VIN、序列号、版本、ADB 开关…），可一键复制 |
| ⑤ 打开系统设置 | 快捷入口 |

**写入后需要重启车机生效**，应用会弹窗提示。车机可以用硬件按键强制重启
（不同车型组合不同，常见为长按电源键或音量键组合）。

---

## 原理

### 进入工程模式

工程模式宿主 `com.desaysv.engmode` 的 `MainActivity` 声明为
`android:exported="true"` 且带 `MAIN/LAUNCHER`，所以任何普通应用都能拉起：

```java
startActivity(new Intent().setComponent(new ComponentName(
        "com.desaysv.engmode", "com.desaysv.engmode.MainActivity"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
```

内部页面（版本信息、屏幕测试、煲机…）大多 `exported=false`，普通应用无法直达，
只能从工程模式菜单进入 —— 功能②会现场告诉你哪些可以。

### 写入桌面应用列表

车机桌面（`com.android.launcher3`）的「我的应用」是白名单驱动的：

```
显示 = 已安装 ∩ (assets 白名单 ∪ 商店 provider 记录 ∪ shortcut)
```

assets 白名单在系统分区只读，所以只能走可写的商店 provider。对着
`OneOS-AppStore.apk` 逆向后确认：

```
permission com.geely.appstore.APPSTORE_PROVIDER   protectionLevel = normal  ← 声明即授予
provider   authorities="com.geely.appstore.AppstoreProvider"  exported=true
table      t_app_info(_id, package_name, apk_name, apk_icon, apk_size,
                      apk_version_name, apk_version_code, apk_type,
                      apk_type_name, display_screen)
insert()   ContentValues 直接进 SQLiteDatabase.insert —— 不校验包名、除 _id 外全部可选
delete()   /appInfo 走 delete(table, null, null) —— 无 _id 会清空整表
```

因此本工具**不写死包名**：权限是 normal 级，对所有包名一视同仁。实现细节：

- `apk_icon` 故意不写（TEXT 列），桌面回落到已安装应用的图标；
- 分类名含「游戏」时桌面归为 `itemType=3`，其余 `itemType=2`；
- 删除只走 `content://…/appInfo/<_id>`，**绝不触发清空整表**；
- 顺带只读查一下桌面的 `com.android.launcher3.interactive/shortcutEntity` 做交叉校验。

---

## 安装

```sh
adb install -r lynk-assistant.apk
```

也可以把 APK 拷到 U 盘，用文件管理器安装。

装好后出现在「我的应用」里，如果没有，用应用内的功能③把**领克助手自己**也写进去。

## 构建

不需要 Gradle、不需要联网，只用 Android SDK build-tools + 一个 JDK：

```sh
ANDROID_HOME=/path/to/Android/sdk ./build.sh
```

可选环境变量：`BT_VERSION`（默认 `36.0.0`）、`PLATFORM`（默认 `android-37.0`）、
`LYNK_KEYSTORE`（默认 `~/.android/debug.keystore`，不存在会自动用 `keytool` 生成）。

重新生成图标（需要 Pillow）：

```sh
python3 make_icons.py
```

目录结构：

```
AndroidManifest.xml
build.sh
make_icons.py
res/values/strings.xml
res/mipmap-*/ic_launcher.png
java/io/github/machetehot/lynkassistant/
    MainActivity.java        入口 + 工程模式 + 页面扫描
    AppListActivity.java     桌面应用列表管理（含重启提示）
    AppStoreBridge.java      商店 provider 读写封装
    DeviceInfoActivity.java  设备信息（只读）
```

## 已知限制

- 写入 / 删除后**需要重启车机**才在桌面生效，普通应用没权限重启桌面；
- 应用商店同步/刷新可能清理 `t_app_info` 记录，届时重新写入即可；
- 工程模式由车机厂商提供，本工具只负责拉起；不同车型的工程模式包名可能不同；
- 不同车机固件的系统属性、桌面实现可能不同，功能③依赖 Geely/OneOS 的商店 provider。

## 免责声明

仅供在**你自己拥有或获授权**的车机上使用，用于安装自己开发或合法获取的应用。
请自行确认车机保修条款与当地法规。作者不对任何误用或由此造成的损失负责。

## License

MIT
