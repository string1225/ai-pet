# 手动更新与发布

## 家长操作

「设置 → 检查应用更新」通过家长算术门槛后，点击「检查更新」。发现新版时显示版本说明，点击「下载新版」，校验通过后点击「安装新版」。安装需要家长再次确认，最后由安卓系统显示覆盖安装确认。

第一次安装可能需要开启系统的「允许来自此来源的应用」。返回小屋后再次点击安装，不会自动安装。取消安装可以保留下载，下次打开更新窗口继续。关闭窗口可继续下载；杀进程中断的下载需要重新发起。下载完成的版本和校验信息保存在本机，重启后可以继续安装。

0.1.0 没有更新入口，需要先手动覆盖安装一次 0.1.1；之后可在应用内更新。不要卸载旧版：卸载会清空安卓本机存档。

Android 10 测试模拟器在首次增加联网权限的覆盖安装后，曾返回系统 `socket failed: EPERM`，设备重启后公网检查恢复，存档保留。旧版首次升级遇到这一错误时，请重启平板再检查更新。

## 服务

与 `star-collect` 共用 139.224.12.141 上的 `github-release.service`：

- 版本：`https://home.sunny-string.cn/github-release/string1225/ai-pet/latest`
- APK：`https://home.sunny-string.cn/github-release/string1225/ai-pet/download/v0.1.1/ai-pet.apk`
- 回退版本：`https://api.github.com/repos/string1225/ai-pet/releases/latest`
- 回退下载：该 Release 在 GitHub 上的固定 `ai-pet.apk` 地址。

仅接受稳定标签 `vX.Y.Z`，拒绝 draft、prerelease、非本仓库资产及缺失的 SHA-256。下载大小上限 100 MiB，连接和读取分别超时 10 秒、20 秒；校验声明大小、SHA-256、包名、递增的 versionCode、与标签一致的 versionName、最低 Android 版本和当前签名。重定向限定 HTTPS 与镜像/GitHub 下载域名。安装前重新校验完整文件，系统安装器只能临时只读访问专用更新 APK。

应用启动、照顾和学习不会访问更新服务。新增联网和安装权限仅用于家长手动更新，宠物存档仍在 `pet_home`，更新信息单独在 `app-update`。

已有共享服务时，上传 `tools/enable_update_service.py` 到服务器并用 sudo 执行。脚本读取当前仓库白名单，添加本仓库，写入 `github-release.service.d/40-ai-pet.conf`，重启并检查健康；失败会恢复之前的配置。保留其他仓库、nginx、代理与缓存。无需再次部署整套 star-collect 服务。

## 签名和构建

0.1.1 沿用 0.1.0 的证书，SHA-256 指纹：

`05893ba88f9c0d9a744e9867f11ab46b5c2389a981a1ec316bcd030212080667`

固定 keystore 保存在仓库外 `C:\Users\junte\.android\ai-pet-release.keystore`，来自最初的 `debug.keystore`，只需在原开发机复制一次。不要在新机器重新生成不同证书。备份并沿用这份密钥；新机器通过 `AI_PET_KEYSTORE`、`AI_PET_STORE_PASSWORD`、`AI_PET_KEY_ALIAS`、`AI_PET_KEY_PASSWORD` 配置原密钥。默认原证书 alias 为 `androiddebugkey`、密码为 `android`，发布构建不可调试。密钥文件不要进入 Git。

版本 code 必须递增，name 与标签一致。例如下次发布：

```powershell
.\tools\build.ps1 -VersionCode 3 -VersionName 0.1.2
```

其他平台使用 `./gradlew assembleRelease -PappVersionCode=3 -PappVersionName=0.1.2`。

先完成测试并提交推送，再构建最终 APK，然后执行：

```powershell
python tools\publish_release.py --tag v0.1.2 --apk artifacts\ai-pet-v0.1.2.apk --notes docs\release-v0.1.2.md
```

脚本使用 GitHub CLI 和已有的 GCM 凭据，或进程中的 `GH_TOKEN`，不会打印或保存 token。发布前要求工作区干净且远程 main 与本地 HEAD 一致；先创建草稿并上传 `ai-pet.apk`、`SHA256SUMS`，再正式发布为 latest。镜像元数据缓存最长 60 秒。

## 模拟器回归

只在可丢弃的模拟器上执行。`tablet_smoke.py` 会清空所选模拟器中本应用的数据；升级脚本不会清空存档，使用 adb root 检查正式包覆盖安装后的本机记录。

构建测试旧版（仅 debug 可启用本地 fixture；正式版始终无测试 URL）：

```powershell
$env:AI_PET_UPDATE_TEST_URL='http://127.0.0.1:8765/latest'
.\gradlew.bat assembleDebug assembleDebugAndroidTest '-PappVersionCode=1' '-PappVersionName=0.1.0'
# 安装两个 debug APK，adb reverse tcp:8765 tcp:8765
```

`update_fixture.py` 参数：`--apk` 正确签名新版、`--bad-apk` 不同证书新版、`--old-apk` 旧版、`--foreign-apk` 其他应用 APK。启动该本地服务后，运行 `adb shell am instrument -w com.string.aipet.test/com.string.aipet.UpdateInstrumentation` 验证实际 APK 拒绝路径、篡改和 Provider 权限。再执行 `tablet_smoke.py`、`tablet_regression.py`，最后 `tablet_update.py` 验证实际界面、安装权限和系统确认升级。正式发布后 `tablet_update.py --public` 验证公网镜像与最新版本界面。

debug 只允许 `127.0.0.1`、`10.0.2.2` 的测试 HTTP，release 始终只允许 HTTPS。

公网回归前确认模拟器已经连上 Wi-Fi。API 36 的默认开放网络可用 `adb shell cmd wifi connect-network AndroidWifi open` 连接；`adb shell cmd wifi status` 确认已连接后再运行 `--public`。
