# Android Client Benchmark - Sony XQ-DQ72

采集日期：2026-06-08

## 测试环境

| 项目 | 值 |
| --- | --- |
| 设备 | Sony XQ-DQ72 |
| Android | 15 / API 35 |
| 分辨率 | 1096 x 2560 |
| 屏幕刷新率 | 120 Hz |
| 构建 | release，未混淆，debug 签名 |
| App 版本 | 1.0 |
| 动画倍率 | 1x / 1x / 1x |
| 电源 | USB 供电，电量 59% |
| 电池温度 | 测试前 32.0 C，完整滚动后 35.0 C |
| Feed 数据 | 本地服务器 `/api/feed`，共 50 条 |

安装 release APK 时使用 `adb install -r`，保留了设备上已有的登录、服务器地址和 App 数据。测试期间屏幕保持开启。

## 测试方法

### 1. 准备设备和构建

测试前先确认 ADB 连接、设备型号、系统、分辨率、刷新率、电池状态和系统动画倍率：

```powershell
adb devices -l
adb -s [Device Serial] shell getprop ro.product.model
adb -s [Device Serial] shell getprop ro.build.version.release
adb -s [Device Serial] shell wm size
adb -s [Device Serial] shell wm density
adb -s [Device Serial] shell dumpsys display
adb -s [Device Serial] shell dumpsys battery
adb -s [Device Serial] shell settings get global animator_duration_scale
adb -s [Device Serial] shell settings get global transition_animation_scale
adb -s [Device Serial] shell settings get global window_animation_scale
```

随后构建并覆盖安装 release APK：

```powershell
.\gradlew.bat :app:assembleRelease
adb -s [Device Serial] install -r app\build\outputs\apk\release\app-release.apk
```

这里使用 `-r` 保留 App 数据，以复用真机上已配置的服务器地址和登录状态。安装后通过 `dumpsys package` 确认安装包不再带 `DEBUGGABLE` 或 `TEST_ONLY` 标记。

### 2. 验证 Feed 数据

服务器接口先单独请求一次，确认 `/api/feed?limit=50` 确实返回 50 条数据。App 启动后使用 UI hierarchy 检查首页已进入真实 Feed 页面，而不是 onboarding、错误页或本地 fallback 页面。

客户端分页大小为 20，因此完整遍历会依次触发约 `20 + 20 + 10` 条数据加载。列表距底部 3 项时调用 `loadMore()`。滚动到底后继续执行相同手势，末尾内容不再变化，用于确认列表已经到达第 50 条。

### 3. 启动测试

冷启动流程重复 20 次：

```powershell
adb -s [Device Serial] shell am force-stop com.ico.nekofeed
adb -s [Device Serial] shell am start -W -n com.ico.nekofeed/.MainActivity
```

每轮读取输出中的 `TotalTime`。`force-stop` 会结束 App 进程，因此下一次启动需要重新创建进程、Application 和 Activity。

热启动流程先用 Back 结束 Activity，但保留缓存进程，再重新执行 `am start -W`。同样采集 20 次 `TotalTime`。

P50/P95 使用 nearest-rank 算法：样本升序排列后，P50 取第 `ceil(N * 0.50)` 个值，P95 取第 `ceil(N * 0.95)` 个值。20 个样本中，P50 是第 10 个，P95 是第 19 个。

### 4. Feed 滚动和帧测试

每轮先重新启动 App，等待首页和首屏内容稳定 5 秒，然后重置帧统计：

```powershell
adb -s [Device Serial] shell dumpsys gfxinfo com.ico.nekofeed reset
```

使用固定坐标、距离和持续时间的手势向上滚动：

```powershell
adb -s [Device Serial] shell input swipe 548 1950 548 650 350
```

首次遍历和缓存后遍历各执行 24 次手势。为了确认 50 条 Feed 全部加载，另执行 60 次较短间隔手势的完整遍历。结束后读取：

```powershell
adb -s [Device Serial] shell dumpsys gfxinfo com.ico.nekofeed
```

报告使用 Android 输出的 `Janky frames`、`Number Frame deadline missed` 以及帧耗时 P50/P90/P95/P99。相比简单地用 16.67 ms 判断卡顿，`Frame deadline missed` 更适合本次 120 Hz 设备，因为系统会结合实际 VSync 和帧 deadline 判断。

### 5. 内存测试

滚动前、滚动过程中以及滚动结束后周期执行：

```powershell
adb -s [Device Serial] shell dumpsys meminfo com.ico.nekofeed
```

从结果中提取 `TOTAL PSS` 和 `TOTAL RSS`，再计算本轮起始值、峰值和结束值：

- PSS：按共享内存比例分摊后的进程物理内存，更适合比较 App 自身内存压力。
- RSS：当前映射到进程的全部驻留物理页，包含共享页，通常高于 PSS。
- 峰值：所有周期采样中的最大值，不保证捕获两个采样点之间的瞬时尖峰，因此它是观测到的下限。

### 6. 测试顺序

本次按以下顺序执行：设备检查、release 安装、页面验证、启动采样、首次滚动、缓存后滚动、完整 50 条滚动。结束时电池温度由 32.0 C 上升到 35.0 C，没有观察到明显过热，但后执行的测试仍可能受到轻微温升影响。

## 启动耗时

启动耗时来自 `adb shell am start -W` 的 `TotalTime`，每种场景采样 20 次。

| 场景 | Min | P50 | P95 | Max | Mean |
| --- | ---: | ---: | ---: | ---: | ---: |
| 冷启动 | 93 ms | 104 ms | 217 ms | 239 ms | 139.4 ms |
| 热启动 | 67 ms | 77 ms | 85 ms | 101 ms | 77.4 ms |

冷启动每次先执行 `am force-stop`；热启动通过 Back 结束 Activity 后，在保留进程的情况下重新启动。

冷启动原始数据（ms）：

```text
146,103,101,101,98,96,99,217,106,110,101,239,217,215,210,99,212,104,93,122
```

热启动原始数据（ms）：

```text
101,82,83,85,83,74,80,84,79,73,79,73,67,67,75,67,78,70,71,77
```

### 启动数据分析

- 冷启动 P50 为 104 ms，表示一半样本可在约 0.1 秒内完成 Activity 首帧；P95 为 217 ms，说明绝大多数启动仍在 0.22 秒内完成。
- 冷启动样本呈现两组数据：约 93-122 ms 和约 210-239 ms。这种双峰可能来自进程创建、系统调度、磁盘页缓存或后台任务时序差异。仅凭 `am start -W` 无法确定具体原因，需要 Perfetto 或 Macrobenchmark trace 进一步拆分。
- 热启动 P50 为 77 ms，P95 为 85 ms，分布明显更集中。原因是进程、类加载结果和部分资源仍保留在内存中，不需要完成完整的进程初始化。
- 冷启动和热启动差值不大，说明当前首帧路径本身较轻。`MainActivity` 在读取 DataStore 启动配置后才调用 `setContent`，但此次设备缓存和本地配置读取没有形成明显启动瓶颈。
- 这些数字只覆盖“首帧出现”。首屏图片、后续分页和 AI 内容可能仍在首帧之后异步加载，因此不能把 104 ms 解释成“50 条 Feed 全部可用耗时”。

## 50 条 Feed 滚动

使用固定坐标和时长的 ADB swipe 从列表顶部持续滚动到底部。帧数据来自 `dumpsys gfxinfo`，内存在滚动过程中周期采样 `dumpsys meminfo`。

| 指标 | 首次遍历 | 缓存后遍历 | 完整滚动到底 |
| --- | ---: | ---: | ---: |
| 渲染帧数 | 2815 | 2810 | 5106 |
| Janky frames | 3 (0.11%) | 1 (0.04%) | 3 (0.06%) |
| Frame deadline missed | 3 | 1 | 3 |
| 帧耗时 P50 | 6 ms | 6 ms | 6 ms |
| 帧耗时 P90 | 17 ms | 7 ms | 7 ms |
| 帧耗时 P95 | 17 ms | 8 ms | 8 ms |
| 帧耗时 P99 | 19 ms | 12 ms | 11 ms |
| PSS 峰值 | 359.7 MiB | 371.3 MiB | 448.8 MiB |
| RSS 峰值 | 485.1 MiB | 497.0 MiB | 574.8 MiB |

完整滚动到底时：

- 起始 PSS：169.5 MiB
- 峰值 PSS：448.8 MiB
- 结束 PSS：290.3 MiB
- 峰值后 PSS 能回落，但结束值仍比起始值高约 120.8 MiB

