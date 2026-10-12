"""Actual manual-update UI and Android installer on a disposable rooted emulator.

Run fixture mode after tablet_smoke.py with the version 1 fixture build installed.
Public mode checks the production HTTPS service after installing the latest release.
No pet data is cleared. Fixture mode resets only update preferences and cached APKs.
Emulator adb root permits verifying non-debuggable release saves.
"""
import argparse
import json
import os
import re
import time
from pathlib import Path
from urllib.request import urlopen
import xml.etree.ElementTree as ET
from tablet_smoke import Tablet, ROOT, APP
from tablet_regression import enter


def control(scenario):
    with urlopen("http://127.0.0.1:8765/control?scenario=" + scenario, timeout=5) as r:
        return json.load(r)


def stats():
    with urlopen("http://127.0.0.1:8765/stats", timeout=5) as r:
        return json.load(r)


def wait(t, label, timeout=45):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            return t.find(label, prefix=True)
        except AssertionError:
            time.sleep(.4)
    raise AssertionError("Timed out waiting for: " + label)


def tap_node(t, node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    t.cmd("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(.8)


def parent(t):
    deadline = time.monotonic() + 15
    while True:
        nodes = list(t.tree().iter("node"))
        if any(n.get("class") == "android.widget.EditText" for n in nodes): break
        if any("settings" in n.get("package", "") or "packageinstaller" in n.get("package", "") for n in nodes): return
        if time.monotonic() >= deadline:
            if any(n.get("text", "").startswith("应用更新") for n in nodes): return
            raise AssertionError("Parent confirmation did not open")
        time.sleep(.2)
    if any(n.get("content-desc") == "家长 PIN" for n in nodes):
        fill(t, "家长 PIN", "1357")
        if any(n.get("content-desc") == "再次输入 PIN" for n in nodes):
            fill(t, "再次输入 PIN", "1357"); t.tap("保存 PIN")
        else:
            t.tap("进入家长空间")
    else:
        wait(t, "请家长帮忙"); enter(t, "25"); t.tap("继续")


def fill(t, label, value):
    node = next(n for n in t.tree().iter("node") if n.get("class") == "android.widget.EditText" and n.get("content-desc") == label)
    tap_node(t, node)
    t.cmd("shell", "input", "keyevent", "123")
    for _ in range(8): t.cmd("shell", "input", "keyevent", "67")
    t.cmd("shell", "input", "text", value)
    t.cmd("shell", "input", "keyevent", "4")


def open_update(t):
    t.tap("设置")
    t.tap("检查应用更新")
    parent(t)
    wait(t, "应用更新")


def save(t):
    data = t.cmd("exec-out", "cat", "/data/user/0/" + APP + "/shared_prefs/pet_home.xml")
    values = {n.get("name"): n.text for n in ET.fromstring(data)}
    return values


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--adb", default=str(Path(os.environ.get("LOCALAPPDATA", ""))/"Android/Sdk/platform-tools/adb.exe"))
    p.add_argument("--serial", default="emulator-5556")
    p.add_argument("--public", action="store_true")
    p.add_argument("--legacy-apk", type=Path, help="Cover-install this APK over the original 0.1.0 on a separate emulator")
    args = p.parse_args()
    t = Tablet(args.adb, args.serial)
    t.cmd("root")
    t.cmd("wait-for-device")
    t.cmd("reverse", "tcp:8765", "tcp:8765")
    if not args.public and not args.legacy_apk:
        t.cmd("shell", "am", "force-stop", APP)
        # Revoking install permission kills the app on Android; prepare before launch.
        t.cmd("shell", "appops", "set", APP, "REQUEST_INSTALL_PACKAGES", "default")
        for name in ("shared_prefs/app-update.xml", "shared_prefs/app-update.xml.bak",
                     "files/updates/update.apk", "files/updates/download.part.apk"):
            t.cmd("shell", "rm", "-f", "/data/user/0/" + APP + "/" + name)
    t.restart()
    before = save(t)
    if args.legacy_apk:
        t.check("versionName=0.1.0" in t.cmd("shell", "dumpsys", "package", APP), "Original 0.1.0 is installed before the first upgrade")
        t.cmd("install", "-r", str(args.legacy_apk.resolve()))
        t.restart()
        t.check("versionCode=2 " in t.cmd("shell", "dumpsys", "package", APP), "Original 0.1.0 accepts the fixed-signature release upgrade")
        t.check(save(t) == before, "Original pet save survives the first cover-install on Android 10")
        open_update(t); wait(t, "检查更新")
        t.check(True, "Original users gain the parent manual-update entry after their first upgrade")
        t.tap("关闭"); t.shot("api29-upgraded-home")
    elif args.public:
        t.restart()
        open_update(t)
        t.tap("检查更新")
        wait(t, "当前已经是最新版本。", timeout=80)
        t.check(True, "Production update check returns the installed latest release")
        t.shot("update-current")
        t.tap("关闭")
        t.check(save(t) == before, "Checking the production release leaves all pet saves unchanged")
    else:
        calls = stats()["requests"]
        t.restart()
        time.sleep(2)
        t.check(stats()["requests"] == calls, "Startup never checks for updates automatically")
        t.tap("设置"); t.tap("检查应用更新")
        enter(t, "24"); t.tap("继续")
        t.find("请家长帮忙")
        t.check(stats()["requests"] == calls, "Incorrect parent gate answer cannot reach update service")
        parent(t)
        control("unavailable"); t.tap("检查更新"); wait(t, "检查更新失败")
        t.check(True, "Offline service shows a retryable error")
        control("missing_apk"); t.tap("检查更新"); wait(t, "检查更新失败")
        t.check(True, "Missing APK metadata cannot expose a download action")
        control("bad_digest"); t.tap("检查更新"); wait(t, "下载新版")
        t.tap("下载新版"); wait(t, "下载失败")
        t.check(True, "Checksum failure is displayed and remains retryable")
        control("bad_signer"); t.tap("重新检查"); wait(t, "下载新版")
        t.tap("下载新版"); wait(t, "下载失败")
        t.check(True, "Different signing certificate is refused in the UI")
        control("good"); t.tap("重新检查"); wait(t, "下载新版")
        t.shot("update-available")
        t.tap("下载新版"); wait(t, "安装新版")
        t.shot("update-ready")
        t.check(save(t) == before, "Checks and downloads leave every pet save unchanged")
        t.tap("关闭")
        t.restart(); open_update(t); t.find("安装新版")
        t.check(True, "Verified download and install action survive process restart")
        t.tap("安装新版"); parent(t)
        nodes = list(t.tree().iter("node"))
        t.check(any("settings" in n.get("package", "") for n in nodes), "First install opens Android unknown-source settings")
        switch = next(n for n in nodes if n.get("checkable") == "true" and n.get("enabled") == "true")
        if switch.get("checked") != "true":
            tap_node(t, switch)
        t.cmd("shell", "input", "keyevent", "4")
        wait(t, "安装新版")
        t.check("versionCode=1 " in t.cmd("shell", "dumpsys", "package", APP), "Returning from permission settings never auto-installs")
        t.tap("安装新版"); parent(t)
        nodes = list(t.tree().iter("node"))
        t.check(any("packageinstaller" in n.get("package", "") for n in nodes), "Android installer asks for explicit update confirmation")
        t.shot("update-system-confirm")
        confirm = next(n for n in nodes if n.get("resource-id", "").endswith(":id/button1") and n.get("enabled") == "true")
        tap_node(t, confirm)
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline and "versionCode=2 " not in t.cmd("shell", "dumpsys", "package", APP):
            time.sleep(.5)
        t.check("versionCode=2 " in t.cmd("shell", "dumpsys", "package", APP), "System confirmation actually upgrades code 1 to code 2")
        wait(t, "App installed.")
        t.tap("Open")
        t.check(wait(t, "设置").get("package") == APP, "System Open launches the actual upgraded pet home")
        t.restart()
        t.check(save(t) == before, "System upgrade preserves pet names, incubation, care, stars and all subjects")
        t.check("debuggable" not in t.cmd("shell", "dumpsys", "package", APP).lower(), "Installed update is a non-debuggable release build")
        t.shot("update-installed-home")
    path = ROOT/"artifacts/update-results.json"
    previous = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    previous["legacy" if args.legacy_apk else "public" if args.public else "fixture"] = t.checks
    path.write_text(json.dumps(previous, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Update UI checks:", len(t.checks), flush=True)


if __name__ == "__main__":
    main()
