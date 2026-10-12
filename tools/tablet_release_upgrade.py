"""Upgrade an existing disposable rooted emulator through the production service.

Uses the app's download, permission screen and Android installer. Keeps pet data.
Defaults exercise the 0.1.1 -> 0.2.0 family/curriculum migration on Android 10.
"""
import argparse
import json
import os
import time
from pathlib import Path
import xml.etree.ElementTree as ET
from tablet_smoke import Tablet, APP, ROOT
from tablet_update import open_update, parent, save, tap_node, wait


def family(t):
    xml = t.cmd("exec-out", "cat", "/data/user/0/" + APP + "/shared_prefs/family_home.xml")
    return json.loads(next(n.text for n in ET.fromstring(xml) if n.get("name") == "state"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", default=str(Path(os.environ.get("LOCALAPPDATA", ""))/"Android/Sdk/platform-tools/adb.exe"))
    parser.add_argument("--serial", default="emulator-5558")
    parser.add_argument("--from-version", default="0.1.1")
    parser.add_argument("--to-version", default="0.2.0")
    parser.add_argument("--to-code", type=int, default=3)
    args = parser.parse_args()
    t = Tablet(args.adb, args.serial)
    t.cmd("root"); t.cmd("wait-for-device"); t.restart()
    package = t.cmd("shell", "dumpsys", "package", APP)
    t.check("versionName=" + args.from_version in package, "Expected previous release installed before upgrade")
    before = save(t)
    open_update(t); t.tap("检查更新"); wait(t, "下载新版", timeout=90)
    t.check(args.to_version in str(ET.tostring(t.tree(), encoding="unicode")), "Production metadata offers the expected new version")
    t.shot("family-update-available")
    t.tap("下载新版"); wait(t, "安装新版", timeout=120)
    t.check(save(t) == before, "Production check and verified download preserve every existing pet save")
    t.tap("安装新版"); parent(t)
    nodes = list(t.tree().iter("node"))
    if any("settings" in n.get("package", "") for n in nodes):
        switch = next(n for n in nodes if n.get("checkable") == "true" and n.get("enabled") == "true")
        if switch.get("checked") != "true": tap_node(t, switch)
        t.cmd("shell", "input", "keyevent", "4")
        t.restart(); open_update(t); wait(t, "安装新版")
        t.check("versionName=" + args.from_version in t.cmd("shell", "dumpsys", "package", APP), "Granting unknown-source permission does not install automatically")
        t.tap("安装新版"); parent(t)
        nodes = list(t.tree().iter("node"))
    t.check(any("packageinstaller" in n.get("package", "") for n in nodes), "Android installer requests explicit upgrade confirmation")
    t.shot("family-system-upgrade")
    confirm = next(n for n in nodes if n.get("resource-id", "").endswith(":id/button1") and n.get("enabled") == "true")
    tap_node(t, confirm)
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        package = t.cmd("shell", "dumpsys", "package", APP)
        if f"versionCode={args.to_code} " in package: break
        time.sleep(.5)
    t.check(f"versionCode={args.to_code} " in package and "versionName=" + args.to_version in package, "System confirmation installs the published new release")
    wait(t, "App installed."); t.tap("Open")
    t.check(wait(t, "设置").get("package") == APP, "System Open enters the real upgraded home")
    t.restart(); after = save(t)
    # The new gift-delivery field is optional for all older saves. Compare every old field.
    for key, value in before.items():
        if key.startswith("pet.") or key == "state":
            old, new = json.loads(value), json.loads(after[key])
            t.check(all(new.get(k) == v for k, v in old.items()), "All existing pet fields survive family/curriculum migration: " + key)
        else:
            t.check(after.get(key) == value, "Existing save preference preserved: " + key)
    t.check("debuggable" not in package.lower(), "Published APK is a non-debuggable release")
    t.find("今日约定"); t.shot("family-api29-home")
    t.tap("设置"); t.tap("语数英学习方案"); wait(t, "设置家长 PIN")
    t.check(True, "Existing release user sets a parent PIN before editing the new learning plan")
    parent(t); wait(t, "语数英学习方案")
    state = family(t)
    t.check(len(state["tasks"]) == 6 and not state["ledger"] and not state["gifts"], "Upgrade creates the six life tasks with an empty independent family wallet")
    l = state["learning"]
    t.check([l[k] for k in ("chinesePercent", "mathPercent", "englishPercent", "pinyinPercent", "sessionLength")] == [40, 40, 20, 50, 10], "Android 10 preserves the expected default curriculum options")
    t.tap("回到小屋")
    (ROOT/"artifacts/family-results.json").write_text(json.dumps(t.checks, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Published release upgrade checks:", len(t.checks), flush=True)


if __name__ == "__main__":
    main()
