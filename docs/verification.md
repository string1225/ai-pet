# 验证记录

## 0.2.1 Android 10 更新兼容

验证日期：2026-10-12。正式版继续包含 0.2.0 的全部生活约定、礼物和课程选项。

- Android 16 完成 0.1.1 → 0.2.0 的 11 项实际公网升级检查：应用内检查、139 下载、系统显式确认、打开新版、宠物全部旧字段不变、首次 PIN、六项生活任务与默认学习方案。
- Android 10 初次公网下载遇到 TLS `BAD_DECRYPT`，表现与 [Google 记录的模拟器 TCP 丢字节问题](https://issuetracker.google.com/issues/150758736)相符；测试改用本机 CONNECT 转发控制读取速率，仍由应用完成端到端 TLS 与摘要验证。转发未更改生产服务器或 APK。
- 下载和摘要通过后查出独立的 Android 10 签名查询差异：已安装包用新标志能取得证书，但待安装档案只传 `GET_SIGNING_CERTIFICATES` 时为 0，组合 `GET_SIGNATURES` 后为 1。0.2.1 已修复这一标志组合。
- Android 10 从原正式 0.1.1 直接覆盖安装 0.2.1，全部旧宠物字段保留；实际设置 PIN、打开课程选项、六项任务与默认 40 / 40 / 20 比例均正常。
- 在修复后的 Android 10 正式包执行原生兼容测试，用更高 code、相同证书的未发布 APK 验证真实更新器；签名、版本和完整 SHA-256 校验通过。测试输出同时记录三种系统查询标志的差异。
- 修复后重新构建正式包，70 个单测通过、0 失败。Android 10 的 0.1.1 / 0.2.0 需先直接覆盖安装一次修复版，旧客户端无法通过自身有缺陷的签名读取安装修复。
- 最终 0.2.1 公网服务验证通过：139 latest 返回 v0.2.1，下载与本地正式包逐字节相同，原 star-collect 仍返回 v0.1.1。Android 10 正式包完成 2 项公网检查，查询最新版本不改变宠物存档。
- Android 16 另完成 0.2.0 → 0.2.1 的 11 项真实应用内升级检查：139 下载、系统确认、打开新版与不可调试状态均正常；全部旧宠物字段、家长 PIN、生活任务、成长账本、礼物、用量和学习方案均保留。

最终正式 APK 727,418 字节，SHA-256：`7e8bc660a5fc6040f1ddd43ba0f2ac01137b1675906c7a21a5a811bef2ab7743`。签名与最初 0.1.0 相同。

截图：[Android 10 小屋](../artifacts/family-api29-home.png)、[Android 16 小屋](../artifacts/family-api36-home.png)、[发现新版](../artifacts/family-update-available.png)、[系统确认](../artifacts/family-system-upgrade.png)、[正式版本检查](../artifacts/update-current.png)。

---

## 0.2.0 生活约定与学习方案

验证日期：2026-10-12。行为规范沿用 star-collect 的任务、审批、账本与 App 用量规则，并通过成长星星兑换礼物连接养宠物。知识星星保留原有学习记录。

- debug / release 构建成功；70 个 JUnit 测试通过，0 失败，lint 0 错误、17 提示。
- API 36 原生界面集成完成 27 项检查：提交去重、任务奖励快照、批准与撤销、礼物消费与送达去重、余额不足、PIN 设置与错误输入、退出和超时锁定、比例校验、课程保存与题目配额、停用科目、重复答题不重复领奖，以及导出内容不包含 PIN 凭据。
- 另完成 11 项实际用量检查：系统设置权限入口、已安装 App 选择、计费规则保存、真实前台停留约 64 秒、按完整分钟扣星、重复刷新不重复扣费、知识星星与点心不变、离开应用锁定家长权限、停用后保留历史。
- 35 项原有玩法冒烟与 15 项旋转、改名、PIN 和重新领养回归通过。孵化、喂食、四类学习、休息及重启保存均保持正常，无应用崩溃。
- 比例单测覆盖 5 / 10 / 20 题配额、0% 停用、1% 小比例在多轮中出现、语文内部比例，以及小词库的 20 题回合不会卡住。数学操作数与结果按难度限制为 5 / 10 / 20 以内。
- 横屏与竖屏界面检查通过；家长页、学习难度和比例页可滚动操作。

界面截图：[今日约定](../artifacts/daily-agreements.png)、[等待确认](../artifacts/daily-pending.png)、[家长空间](../artifacts/family-parent.png)、[学习难度](../artifacts/learning-options.png)、[学习比例](../artifacts/learning-ratios.png)、[竖屏小屋](../artifacts/family-portrait.png)。

复现用量与课程集成测试见 [README](../README.md)。公开服务升级复现脚本为 `tools/tablet_release_upgrade.py`，只用于可丢弃的已安装旧版模拟器，保留宠物存档并通过系统确认安装。

---

## 0.1.1 手动更新

验证日期：2026-10-11 至 12 日。与 star-collect 共用 139 的 Release 镜像，新仓库通过独立 systemd drop-in 加入白名单。健康接口正常，原 star-collect 的 latest 仍返回 v0.1.1。

- `assembleDebug`、`assembleRelease` 成功；33 个 JUnit 测试通过、0 失败。新增版本比较、正式发布筛选、固定仓库与 APK 资产、摘要、大小、重定向策略测试。
- debug / release lint 均为 0 错误、13 提示。
- API 36 真实 Android PackageManager 执行 21 项更新校验：缺少 APK、草稿、HTTP 故障、损坏摘要、错误签名、旧版和其他应用包均被拒绝；失败清理临时文件；正确签名与文件通过；安装前文件篡改被拒绝；只读 Provider 拒绝写入与其他路径。
- 原有玩法在更新测试构建完成 35 项 UI 冒烟，照顾、四学科、睡眠、重启存档均通过。
- 15 项附加回归通过：旋转后的答题锁定、改名、家长门槛、取消重置与确认重置均保持原有行为。
- API 29 上将最初的 0.1.0 覆盖升级为不可调试的正式 0.1.1，4 项检查通过。完整 `pet_home` 内容在升级前后相同，新版家长更新入口可以打开。
- API 36 完成 15 项手动更新 UI 检查：启动不联网、家长门槛、网络故障重试、缺失 APK、损坏摘要及不同签名拒绝、下载后重启继续、系统未知来源权限、返回后不自动安装、安卓显式确认。实际点击系统确认后从 code 1 升级到 code 2，并点击系统「Open」进入真实新版首页，正式版不可调试，完整宠物存档保持一致。
- 测试脚本清理旧的安装器任务，并通过本应用的「设置」按钮确认实际首页；仅 app 名字会匹配系统安装器，不能用作启动成功依据。安装权限重置会让系统结束应用进程，因此测试准备在启动应用前完成。
- API 29 首次从无 INTERNET 权限的 0.1.0 覆盖安装后，PackageManager 已授权且进程包含 internet gid，但 socket 返回 EPERM；设备重启后公网更新检查通过，存档保留。此首次升级兼容性现象记录在更新说明中。
- Android 10、16 的最终正式包均完成 2 项公网 UI 检查：查询返回当前最新版本，完整宠物存档不变。API 36 的模拟器需要先连接 AndroidWifi；无网络时应用显示可重试的查询错误。

正式发布 `v0.1.1`，镜像 latest 返回正确稳定版本，139 下载与本地正式包逐字节一致，原 star-collect 发布仍可访问。APK 大小 694,418 字节，SHA-256：`2b6c2be72c5332ec0c312d5d05a39d230847789fa4287a60c9b0b7752bcb97c3`。镜像 APK 的签名也与原 0.1.0 相同。

更新界面截图：[发现新版](../artifacts/update-available.png)、[下载完成](../artifacts/update-ready.png)、[系统安装确认](../artifacts/update-system-confirm.png)、[升级后的小屋](../artifacts/update-installed-home.png)、[正式服务版本检查](../artifacts/update-current.png)。

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
