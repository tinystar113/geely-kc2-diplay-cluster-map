# H52 Cluster Map

在吉利 H52（KC2 / Neusoft·ECARX 车机，Android 4.3）上，把 **CarPlay 的仪表盘地图** 显示到车机仪表屏的实验性实现。

原车高德可以通过 CarPlay 在仪表盘显示地图；本项目让第三方 CarPlay 中转方案获得同样能力。

## 原理

1. 中转 App（DiPlay）向手机申请第二路 CarPlay 显示（stream type `111`，`maps:/car/instrumentcluster/map`）；
2. 解码输出交给 `SurfaceTexture`，由新增的 `GeelyClusterMap` 类接管；
3. 一个小的 JNI 库通过车机自带的 `/system/lib/libexternalsurfacehelper.so` 拿到仪表 surface，用 EGL/GLES2 把帧画上去；
4. 车机的 `screencap_neu` 只在有帧交换时推流，所以 `GeelyClusterMap` 在路线激活期间以固定帧率重绘最新帧，路线结束后停止，仪表约 1 秒内回落到原状态栏；
5. `CarPlayHostActivity` 里三处 smali hook 把原有的（BYD 专用）仪表通路改指向 `GeelyClusterMap`，`H52NavigationBridge` 中一处 hook 负责上报路线开始 / 结束。

## 目录

| 路径 | 说明 |
| --- | --- |
| `h52-cluster-map/java/` | `GeelyClusterMap` 及配套 Java 源码 |
| `h52-cluster-map/native/` | JNI 渲染层（C，NDK r21e，`armv7a-linux-androideabi18`） |
| `h52-cluster-map/checkpoints/` | 各阶段可回退的代码快照与就地运行的脚本 |
| `h52-cluster-map/tests/` | 巡航 / 路线结束策略的单元测试 |
| `h52-cluster-map/*.md` | 构建说明（R7）与巡航模式设计 |
| `hud-probe/` | 仪表 surface 探测工具与抓取到的运行日志 |
| `docs/` | 实施计划与背景记录 |

## 构建

见 [`h52-cluster-map/R7_QUICKSTART.md`](h52-cluster-map/R7_QUICKSTART.md) 与 [`BUILD_R7.md`](h52-cluster-map/BUILD_R7.md)。工具链为 C（NDK r21e）、Java 7、smali/baksmali 3.0.9、Android build-tools 34，构建脚本为 Python 3 + `adb`。

> 仓库**不包含**：厂商固件包、任何 APK 成品、反编译出的完整类树、Root 工具。这些需自行从设备或官方渠道获取。

## 免责声明

本项目仅用于**自己车辆**上的个人研究与学习。修改车机系统可能影响保修、稳定性与行车安全，所有操作风险自负。请遵守当地法律法规，不要分发他人受版权保护的固件或软件。
