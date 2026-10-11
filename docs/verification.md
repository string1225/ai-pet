# 验证记录

## 0.1.1 手动更新

验证日期：2026-10-11。与 star-collect 共用 139 的 Release 镜像，新仓库通过独立 systemd drop-in 加入白名单。健康接口正常，原 star-collect 的 latest 仍返回 v0.1.1。

- `assembleDebug`、`assembleRelease` 成功；33 个 JUnit 测试通过、0 失败。新增版本比较、正式发布筛选、固定仓库与 APK 资产、摘要、大小、重定向策略测试。
- debug / release lint 均为 0 错误、13 提示。
- API 36 真实 Android PackageManager 执行 21 项更新校验：缺少 APK、草稿、HTTP 故障、损坏摘要、错误签名、旧版和其他应用包均被拒绝；失败清理临时文件；正确签名与文件通过；安装前文件篡改被拒绝；只读 Provider 拒绝写入与其他路径。
- 原有玩法在更新测试构建完成 35 项 UI 冒烟，照顾、四学科、睡眠、重启存档均通过。
- 15 项附加回归通过：旋转后的答题锁定、改名、家长门槛、取消重置与确认重置均保持原有行为。
- API 29 上将最初的 0.1.0 覆盖升级为不可调试的正式 0.1.1，4 项检查通过。完整 `pet_home` 内容在升级前后相同，新版家长更新入口可以打开。
- API 36 完成 14 项手动更新 UI 检查：启动不联网、家长门槛、网络故障重试、缺失 APK、损坏摘要及不同签名拒绝、下载后重启继续、系统未知来源权限、返回后不自动安装、安卓显式确认。实际点击系统确认后从 code 1 升级到 code 2，正式版不可调试，完整宠物存档保持一致。

更新界面截图：[发现新版](../artifacts/update-available.png)、[下载完成](../artifacts/update-ready.png)、[系统安装确认](../artifacts/update-system-confirm.png)、[升级后的小屋](../artifacts/update-installed-home.png)。

复现工具与签名配置见 [手动更新说明](manual-updates.md)。更新测试只使用可丢弃模拟器，没有操作实体平板。

---

## 0.1.0

验证日期：2026-10-11。

## 构建和逻辑

执行 `tools/build.ps1`：`assembleDebug`、`testDebugUnitTest`、`lintDebug` 全部成功。

- 20 个 JUnit 测试通过，0 失败。
- 数学随机生成 5,000 题检查操作数、结果范围与答案；另外检查四学科选项不重复且包含唯一答案。
- 验证孵化、余下学科恢复、喂食扣库存、睡眠限制、陪玩奖励、回合奖励、离线休息、时间回拨和长时间离开保护。
- 使用另一种测试宠物验证物种注册与独立养成参数。
- lint：0 错误，12 个提示，主要是固定横屏建议、工具/SDK 新版提示、原生 View 的设计器构造函数、绘制分配和中文文案本地化建议。

安装包：`artifacts/yaya-home-v0.1.0.apk`，调试签名，可直接安装。

SHA-256：`b84a453f363dc3c6f10f8231f26f4dd9b48a40c1c05d9831a46b4400b3bcc512`。

## Android 16 平板 UI

API 36 模拟器，1280×800、160dpi 横屏；旋转后为 800×1280。

`tools/tablet_smoke.py` 完成 35 项实际 UI 检查：

- 首次启动为蛋；错误答案不改变星星或孵化进度。
- 每个正确答案恰好奖励一颗星；孵化中断、强制停止后能继续。
- 四学科共同完成孵化，并保存出生时间。
- 喂食减少库存并增加饱腹；喝水恢复精力。
- 连续三个球的位置可点击，拼音题完成后结算陪玩。
- 四学科各完成一个三题回合，回合奖励只结算一次。
- 家具入口、睡眠、睡眠时禁止喂食、重启后睡眠保存与叫醒正常。
- 星星、学科进度、养成状态和点心库存经过强制停止再启动保持一致。
- 完整流程没有应用崩溃。

`tools/tablet_regression.py` 完成 15 项附加检查，包括：答对后旋转保留禁用选项且不重复领奖、改名保存、改名后重启、错误家长答案禁止重置、取消确认保留存档，以及确认重置后恢复新蛋。

最终安装包额外验证了竖屏资料区、转屏中的已答对题目、设备无语音时的提示、返回关闭学习弹窗、睡眠和成长记录。

## Android 10 兼容性

API 29 模拟器，同样为 1280×800、160dpi：实际启动、错误题重试、孵化中断并重启恢复、完成四类孵化题、出生欢迎与抚摸均正常。更新安装包保留宠物存档，无应用崩溃。

## 截图

| 场景 | 截图 |
|---|---|
| 初始宠物蛋 | [egg-home.png](../artifacts/egg-home.png) |
| 小蛇的家 | [snake-home.png](../artifacts/snake-home.png) |
| 数学 | [math-lesson.png](../artifacts/math-lesson.png) |
| 拼音 | [pinyin-lesson.png](../artifacts/pinyin-lesson.png) |
| 认字 | [literacy-lesson.png](../artifacts/literacy-lesson.png) |
| 英语 | [english-lesson.png](../artifacts/english-lesson.png) |
| 睡眠 | [sleeping-home.png](../artifacts/sleeping-home.png) |
| 成长记录 | [growth-journal.png](../artifacts/growth-journal.png) |
| 竖屏小屋 | [portrait-home.png](../artifacts/portrait-home.png) |
| 竖屏答题 | [portrait-lesson.png](../artifacts/portrait-lesson.png) |
| Android 10 | [api29-home.png](../artifacts/api29-home.png) |

没有连接实体平板；设备的中文/英文 TTS 发音质量需要在装有相应系统语音数据的设备上确认。题目文字、答题和奖励不依赖 TTS。
