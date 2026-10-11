# 芽芽小屋

面向安卓平板的一年级学习电子宠物。第一位伙伴是 2D 小蛇「芽芽」，从一枚蛋开始，在自己的小屋里长大。

![小蛇的家](artifacts/snake-home.png)

## 当前版本

- **孵化**：数学、拼音、汉字、英语各一道题，每答对一题加温 25%；中途退出保留进度。
- **喂食**：浆果点心、小虫饼干和清凉水；饱腹、心情、精力各自变化。
- **陪玩**：在小屋追到三次闪闪的小球，再拼读一个音节，获得心情和亲密度。
- **休息与抚摸**：点小床睡觉或叫醒，休息恢复精力；点小蛇能抚摸，出现爱心。
- **可互动家具**：绘本书架、软软小床、点心碗、小球和窗边绿植。
- **四类学习**：10 以内加减法、声母韵母拼读、常用汉字识读、基础英文词汇。每轮三题，每题一颗星，每轮奖励两份点心。
- **成长记录**：按学科统计星星，记录学习轮数、相伴天数、亲密度和等级。
- **本机存档**：孵化、名字、养成数值、星星、点心、睡眠和朗读开关重启后保留。
- **家长设置**：改名、朗读开关、带成人算术门槛和二次确认的重新领养。

答错可以重试，不扣分。小蛇不会死亡。没有广告、内购、账号或联网权限。中文和英文朗读调用系统 TTS，设备需要已有相应语音数据；没有声音时仍能完成所有玩法。

## 构建和安装

原生 Java + Android Canvas，运行时无需第三方库。Android 8.0（API 26）以上，编译与目标版本 API 36。界面优先横屏平板，支持屏幕尺寸变化；Android 16 大屏设备可以自由旋转。

需要 Android SDK 36、JDK 17 或以上。Windows 可直接使用 Android Studio 自带的 JBR：

```powershell
.\tools\build.ps1
# 构建、测试、lint，并安装到已连接的平板或模拟器
.\tools\build.ps1 -Install -Serial emulator-5556
```

安装包输出：`artifacts/yaya-home-v0.1.0.apk`，是可直接安装的开发调试包。

其他平台可配置 `JAVA_HOME` 和 `local.properties`，再执行：

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 扩展框架

宠物配置、养成逻辑、学习内容、绘制和存档独立。新增宠物无需改动题库或小屋互动规则，图鉴会自动列出已注册宠物，每种宠物有独立存档。

| 模块 | 职责 |
|---|---|
| `model/PetDefinition`、`PetRegistry` | 宠物种类、默认名字、描述、喂食和陪玩数值 |
| `model/PetState`、`PetEngine` | 孵化、养成、休息、亲密度、成长奖励 |
| `model/PetStore` | 带版本号的 JSON 本机存档和宠物切换 |
| `learning/Subject`、`QuestionBank` | 四学科题目、干扰项、提示和朗读内容 |
| `ui/PetRenderer`、`RendererRegistry` | 注册不同宠物的 2D 绘制器 |
| `ui/RoomView` | 小屋场景、触摸、家具、追球及无障碍按钮 |
| `MainActivity` | 平板布局、互动弹窗和学习回合 |

详细说明见 [扩展指南](docs/architecture.md)。

## 验证

```powershell
.\tools\build.ps1
python tools\tablet_smoke.py --serial emulator-5556
# 在同一台 Android 16 测试模拟器上验证旋转、改名、家长重置
python tools\tablet_regression.py --serial emulator-5556
```

UI 冒烟脚本会清空**所选模拟器中本应用**的数据，实际操作所有孵化与学习题、喂食、追球、休息和重启，并生成截图及 `artifacts/smoke-results.json`。勿对有真实存档的设备运行此脚本。

验证记录见 [测试记录](docs/verification.md)。
