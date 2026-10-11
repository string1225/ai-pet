"""Extra API 36 regressions: rotation, naming, adult reset gate, and final rendered assets.
Run after tablet_smoke.py on the same disposable emulator. Resets the test pet through UI.
"""
import json
import argparse
import os
from pathlib import Path
import re
import time
from tablet_smoke import Tablet, ROOT


def enter(t, value):
    node = next(n for n in t.tree().iter("node") if n.get("class") == "android.widget.EditText")
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    t.cmd("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    t.cmd("shell", "input", "keyevent", "123")
    for _ in range(8):
        t.cmd("shell", "input", "keyevent", "67")
    t.cmd("shell", "input", "text", value)
    t.cmd("shell", "input", "keyevent", "4")  # Hide keyboard.
    time.sleep(.5)


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--adb",default=str(Path(os.environ.get("LOCALAPPDATA",""))/"Android/Sdk/platform-tools/adb.exe"))
    parser.add_argument("--serial",default="emulator-5556")
    args=parser.parse_args()
    t=Tablet(args.adb,args.serial)
    t.restart();t.shot("snake-home")
    t.tap("学习");t.tap("小小数学家");t.answer(advance=False)
    correct=t.state()
    t.cmd("shell", "settings", "put", "system", "accelerometer_rotation", "0")
    t.cmd("shell", "settings", "put", "system", "user_rotation", "1")
    time.sleep(2)
    t.find("答对啦！",prefix=True)
    t.check(t.state()["stars"]==correct["stars"],"Rotation keeps correct answer locked and cannot award another star")
    t.shot("portrait-lesson")
    t.cmd("shell", "settings", "put", "system", "user_rotation", "0")
    time.sleep(1)
    t.tap("下一题");t.answer();t.answer();t.tap("回到小屋")
    t.tap("设置");t.tap("给小蛇取名字");enter(t,"Yaya");t.tap("保存名字")
    t.check(t.state()["name"]=="Yaya","Rename saves the entered pet name")
    t.restart();t.find("Yaya的家")
    t.check(t.state()["name"]=="Yaya","Renamed pet survives process restart")
    t.tap("设置");t.tap("家长：重新领养");enter(t,"24");t.tap("继续")
    t.check(t.state()["warmth"]==100,"Incorrect adult gate answer cannot reset pet")
    enter(t,"25");t.tap("继续");t.tap("保留小伙伴")
    t.check(t.state()["name"]=="Yaya","Canceling reset confirmation preserves the pet")
    t.tap("继续");t.tap("重新领养")
    t.check(t.state()["warmth"]==0 and t.state()["stars"]==0 and t.state()["name"]=="芽芽","Confirmed parent reset restores a fresh egg")
    t.find("等待小生命");t.shot("egg-home")
    t.tap("一起孵化",prefix=True)
    for _ in range(4):t.answer()
    t.tap("带我回家");t.find("芽芽的家");time.sleep(2);t.shot("snake-home")
    t.tap("点心碗，喂食");t.tap("小虫饼干")
    t.tap("陪玩")
    for i in range(3):t.tap(f"闪闪的小球，第{i+1}回合")
    t.answer()
    t.tap("休息");time.sleep(1);t.shot("sleeping-home");t.tap("软软小床，休息")
    t.tap("成长记录");t.shot("growth-journal");t.tap("返回")
    t.cmd("shell", "settings", "put", "system", "user_rotation", "1");time.sleep(2)
    t.find("芽芽的家");t.shot("portrait-home")
    t.cmd("shell", "settings", "put", "system", "user_rotation", "0");time.sleep(1)
    t.shot("snake-home")
    t.check("FATAL EXCEPTION" not in t.cmd("logcat","-d","-b","crash"),"Updated renderer and lifecycle pass without an Android crash")
    (ROOT/"artifacts/regression-results.json").write_text(json.dumps(t.checks,ensure_ascii=False,indent=2),encoding="utf8")
    print("Regression checks:",len(t.checks),flush=True)


if __name__=="__main__":main()
