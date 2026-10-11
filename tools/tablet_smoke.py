"""Exercise the installed debug app through actual Android UI, with inspectable screenshots.

Uses only Python stdlib and adb. The test explicitly clears this app's emulator data.
python tools/tablet_smoke.py --serial emulator-5556 --apk app/build/outputs/apk/debug/app-debug.apk
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = "com.string.aipet"
WORDS = {"苹果": "apple", "香蕉": "banana", "猫": "cat", "狗": "dog", "鸟": "bird", "鱼": "fish",
         "太阳": "sun", "月亮": "moon", "书": "book", "球": "ball", "红色": "red", "蓝色": "blue",
         "绿色": "green", "黄色": "yellow", "蛇": "snake", "水": "water"}
CHARS = {"rì": "日", "yuè": "月", "shān": "山", "shuǐ": "水", "huǒ": "火", "mù": "木", "rén": "人",
         "kǒu": "口", "shǒu": "手", "tián": "田", "niú": "牛", "mǐ": "米", "dà": "大", "xiǎo": "小", "shàng": "上", "xià": "下"}


class Tablet:
    def __init__(self, adb: str, serial: str):
        self.prefix = [adb, "-s", serial]
        self.checks: list[str] = []
        self.out = ROOT / "artifacts"
        self.out.mkdir(exist_ok=True)

    def cmd(self, *args: str, binary: bool = False):
        result = subprocess.run(self.prefix + list(args), check=True, capture_output=True, timeout=45)
        return result.stdout if binary else result.stdout.decode("utf-8", errors="replace")

    def tree(self):
        for _ in range(4):
            self.cmd("shell", "rm", "-f", "/sdcard/ai-pet-window.xml")
            dumped = self.cmd("shell", "uiautomator", "dump", "/sdcard/ai-pet-window.xml")
            if "dumped to" not in dumped:
                time.sleep(.5)
                continue
            data = self.cmd("exec-out", "cat", "/sdcard/ai-pet-window.xml")
            try:
                return ET.fromstring(data)
            except ET.ParseError:
                time.sleep(.3)
        raise AssertionError("Could not read Android UI tree")

    def find(self, label: str, prefix: bool = False, clickable: bool = False):
        for n in self.tree().iter("node"):
            values = (n.get("text", ""), n.get("content-desc", ""))
            if any(v.startswith(label) if prefix else v == label for v in values):
                if n.get("enabled") == "true" and (not clickable or n.get("clickable") == "true"):
                    return n
        raise AssertionError(f"Missing UI control: {label}")

    def tap(self, label: str, prefix: bool = False):
        n = self.find(label, prefix, clickable=True)
        bounds = [int(s) for s in re.findall(r"\d+", n.get("bounds", ""))]
        assert len(bounds) == 4 and bounds[2] > bounds[0] + 8, (label, n.attrib)
        self.cmd("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
        time.sleep(.7)

    def state(self):
        xml = self.cmd("exec-out", "run-as", APP, "cat", "shared_prefs/pet_home.xml")
        for n in ET.fromstring(xml):
            if n.get("name") == "pet.little_snake":
                return json.loads(n.text)
        raise AssertionError("No snake save found")

    def shot(self, name: str):
        (self.out / f"{name}.png").write_bytes(self.cmd("exec-out", "screencap", "-p", binary=True))

    def check(self, condition: bool, message: str):
        assert condition, message
        self.checks.append(message)
        print("PASS", message, flush=True)

    def answer(self, wrong_first: bool = False, screenshot: str | None = None, advance: bool = True):
        nodes = list(self.tree().iter("node"))
        texts = [n.get("text", "") for n in nodes]
        expected = None
        for value in texts:
            math = re.fullmatch(r"(\d+) ([+−]) (\d+) = \?", value)
            if math:
                a, op, b = math.groups()
                expected = str(int(a) + int(b) if op == "+" else int(a) - int(b))
                break
            if value.endswith(" → ?"):
                expected = value[:-4].replace(" + ", "")
                break
            if value in CHARS:
                expected = CHARS[value]
                break
            if value in WORDS:
                expected = WORDS[value]
                break
        assert expected is not None, texts
        buttons = [n for n in nodes if n.get("class") == "android.widget.Button" and n.get("text") not in {"返回", "听一听", "下一题", "完成啦"}]
        assert expected in [n.get("text") for n in buttons], (expected, texts)
        before = self.state()
        if wrong_first:
            wrong = next(n.get("text") for n in buttons if n.get("text") != expected)
            self.tap(wrong)
            after_wrong = self.state()
            self.check(after_wrong["stars"] == before["stars"] and after_wrong["warmth"] == before["warmth"], "Wrong answer preserves stars and incubation progress")
            self.find("再试一次。", prefix=True)
        if screenshot:
            self.shot(screenshot)
        self.tap(expected)
        after = self.state()
        self.check(after["stars"] == before["stars"] + 1, "Correct answer awards exactly one star")
        self.find("答对啦！", prefix=True)
        if advance:
            labels = [n.get("text") for n in self.tree().iter("node")]
            self.tap("完成啦" if "完成啦" in labels else "下一题")

    def restart(self):
        self.cmd("shell", "am", "force-stop", APP)
        self.cmd("shell", "am", "start", "-W", "-f", "0x10008000", "-n", f"{APP}/.MainActivity")
        # The launch transition can expose a UI tree before InputDispatcher accepts touches.
        time.sleep(1.6)
        # NEW_TASK | CLEAR_TASK clears a system installer left in the app's task.
        # Require an actual app control; the installer also displays the app name.
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            try:
                settings = self.find("设置", clickable=True)
                if settings.get("package") == APP:
                    return
            except AssertionError:
                time.sleep(.3)
        raise AssertionError("App home did not become interactive after restart")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--adb", default=str(Path(os.environ.get("LOCALAPPDATA", "")) / "Android/Sdk/platform-tools/adb.exe"))
    p.add_argument("--serial", default="emulator-5556")
    p.add_argument("--apk", default="app/build/outputs/apk/debug/app-debug.apk")
    args = p.parse_args()
    t = Tablet(args.adb, args.serial)
    t.cmd("install", "-r", str((ROOT / args.apk).resolve()))
    t.cmd("shell", "pm", "clear", APP)
    t.cmd("logcat", "-c")
    t.restart()
    t.shot("egg-home")
    t.check(t.state()["warmth"] == 0, "First launch starts with an unhatched egg")
    # Prevent narration toasts obscuring screenshot/test controls; verifies persisted preference.
    t.tap("设置"); t.tap("关闭朗读"); t.tap("返回")
    t.tap("一起孵化", prefix=True)
    t.answer(wrong_first=True, screenshot="math-lesson")
    t.tap("返回")
    t.check(t.state()["warmth"] == 25, "Incubation can be canceled after one correct answer")
    t.restart()
    t.check(t.state()["warmth"] == 25 and not t.state()["sound"], "Relaunch preserves egg warmth and sound setting")
    t.tap("一起孵化", prefix=True)
    t.answer(screenshot="pinyin-lesson")
    t.answer(screenshot="literacy-lesson")
    t.answer(screenshot="english-lesson")
    t.find("小蛇出生啦！")
    t.check(t.state()["warmth"] == 100 and t.state()["adoptedAt"] > 0, "All four subjects complete incubation and record adoption")
    t.tap("带我回家")
    t.shot("snake-home")
    before = t.state()
    t.tap("喂食", prefix=True); t.tap("浆果点心")
    t.check(t.state()["snacks"] == before["snacks"] - 1 and t.state()["fullness"] > before["fullness"], "Feeding consumes a snack and increases fullness")
    t.tap("喂食", prefix=True); t.tap("喝点清凉水")
    t.check(t.state()["energy"] == min(100, before["energy"] + 3), "Water increases energy without consuming a snack")
    t.tap("陪玩")
    for i in range(3):
        t.tap(f"闪闪的小球，第{i + 1}回合")
    before = t.state()
    t.answer()
    t.check(t.state()["energy"] == before["energy"] - 6 and t.state()["affection"] > before["affection"], "Three ball rounds and a pinyin question reward play")
    for title in ["小小数学家", "拼音小火车", "汉字花园", "英语小世界"]:
        t.tap("学习"); t.tap(title)
        before = t.state()
        for _ in range(3):
            t.answer()
        t.find("做得真棒！")
        t.check(t.state()["snacks"] == before["snacks"] + 2 and t.state()["lessons"] == before["lessons"] + 1, f"{title}: full lesson awards two snacks once")
        t.tap("回到小屋")
    t.tap("软软小床，休息")
    t.check(t.state()["sleeping"], "Bed interaction puts snake to sleep")
    t.shot("sleeping-home")
    before = t.state()
    t.tap("喂食", prefix=True)
    t.check(t.state()["snacks"] == before["snacks"], "Sleeping pet cannot consume food")
    t.restart()
    t.check(t.state()["sleeping"], "Sleep state survives process restart")
    t.tap("软软小床，休息")
    t.check(not t.state()["sleeping"], "Bed interaction wakes snake")
    t.tap("摸摸芽芽")
    t.tap("绘本书架，学习"); t.find("一起发现新知识"); t.tap("返回")
    t.tap("成长记录"); t.find("我们的成长记录"); t.shot("growth-journal"); t.tap("返回")
    saved = t.state()
    t.restart()
    t.check(t.state() == saved, "All care, learning, and rewards survive force-stop/relaunch")
    logs = t.cmd("logcat", "-d", "-b", "crash")
    t.check("FATAL EXCEPTION" not in logs, "No Android crash during full tablet interaction run")
    t.shot("snake-home")
    (t.out / "smoke-results.json").write_text(json.dumps({"serial": args.serial, "checks": t.checks, "state": t.state()}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Completed {len(t.checks)} checks; screenshots in {t.out}", flush=True)


if __name__ == "__main__":
    main()
