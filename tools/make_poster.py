import base64
from pathlib import Path

BASE = Path(r"C:\Users\demon\Desktop\click")


def b64(path: Path) -> str:
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode()


logo = b64(BASE / "icon-512.png")
home = b64(BASE / "screenshots" / "01-首页任务列表.png")
record = b64(BASE / "screenshots" / "05-录制合并点位.png")
pause = b64(BASE / "screenshots" / "06-执行与暂停.png")

TEMPLATE = r"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    width: 1200px; height: 1600px; overflow: hidden; background: #05080E;
    font-family: "Microsoft YaHei", "微软雅黑", sans-serif;
    -webkit-font-smoothing: antialiased;
  }
  .canvas {
    position: relative; width: 1200px; height: 1600px; overflow: hidden;
    background:
      radial-gradient(760px 560px at 88% -8%, rgba(124,92,255,0.40), rgba(124,92,255,0) 68%),
      radial-gradient(680px 520px at -6% 4%, rgba(62,216,255,0.24), rgba(62,216,255,0) 68%),
      radial-gradient(900px 700px at 50% 112%, rgba(62,150,255,0.18), rgba(62,150,255,0) 70%),
      linear-gradient(168deg, #070C15 0%, #0B1221 46%, #070B13 100%);
  }
  .grid {
    position: absolute; left: 0; top: 0; width: 1200px; height: 560px; opacity: .45;
    background-image:
      linear-gradient(rgba(120,200,255,.14) 1px, transparent 1px),
      linear-gradient(90deg, rgba(120,200,255,.14) 1px, transparent 1px);
    background-size: 60px 60px;
    -webkit-mask-image: linear-gradient(to bottom, rgba(0,0,0,.9), rgba(0,0,0,0));
            mask-image: linear-gradient(to bottom, rgba(0,0,0,.9), rgba(0,0,0,0));
  }

  .header { position: absolute; left: 72px; top: 72px; display: flex; align-items: center; }
  .logo { width: 92px; height: 92px; border-radius: 23px;
          box-shadow: 0 16px 40px rgba(62,180,255,.40), 0 0 0 1px rgba(255,255,255,.10) inset; }
  .htext { margin-left: 24px; }
  .hname { font-size: 50px; font-weight: 800; letter-spacing: .5px; line-height: 1.05;
           background: linear-gradient(96deg, #FFFFFF 12%, #9FF1FF 52%, #A9B4FF 92%);
           -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
  .hsub { margin-top: 9px; font-size: 18px; color: #8FA6CC; letter-spacing: .6px; }

  .chips { position: absolute; left: 72px; top: 194px; display: flex; gap: 11px; }
  .chip { padding: 8px 17px; border-radius: 999px; font-size: 13.5px; color: #C5E8FF;
          border: 1px solid rgba(120,200,255,.32); background: rgba(62,216,255,.07); }

  .divider { position: absolute; left: 72px; top: 250px; width: 1056px; height: 1px;
             background: linear-gradient(90deg, rgba(62,216,255,.55), rgba(124,92,255,.32), rgba(0,0,0,0)); }

  .features { position: absolute; left: 72px; top: 282px; width: 1056px;
              display: grid; grid-template-columns: 520px 520px; gap: 15px; }
  .card { height: 118px; border-radius: 18px; padding: 17px 20px; display: flex; align-items: flex-start;
          background: linear-gradient(160deg, rgba(255,255,255,.055), rgba(255,255,255,.02));
          border: 1px solid rgba(110,190,255,.15);
          box-shadow: 0 10px 24px rgba(0,0,0,.26); }
  .num { flex: 0 0 36px; height: 36px; border-radius: 11px; display: flex; align-items: center;
         justify-content: center; font-size: 14.5px; font-weight: 800; color: #06101A;
         background: linear-gradient(135deg, #3ED8FF, #7C5CFF); }
  .ctext { margin-left: 15px; margin-top: 1px; }
  .ctitle { font-size: 17px; font-weight: 700; color: #EAF3FF; letter-spacing: .2px; }
  .cdesc { margin-top: 7px; font-size: 13.5px; line-height: 1.5; color: #92A5C7; }

  .sectitle { position: absolute; left: 72px; top: 736px; font-size: 14px; color: #6F7E9E; letter-spacing: 2px; }
  .sectitle b { color: #9FE6FF; font-weight: 700; }

  .shots { position: absolute; left: 72px; top: 772px; width: 1056px;
           display: flex; justify-content: space-between; }
  .shot { width: 337px; }
  .phone { width: 337px; height: 600px; border-radius: 26px; overflow: hidden;
           border: 1px solid rgba(120,200,255,.28);
           box-shadow: 0 24px 56px rgba(0,0,0,.55), 0 0 0 6px rgba(255,255,255,.035);
           background: #0A0F1A; }
  .phone img { width: 100%; height: 100%; display: block; }
  .cap { margin-top: 15px; text-align: center; font-size: 12.5px; color: #7E8CA8; letter-spacing: .5px; }

  .footer { position: absolute; left: 0; bottom: 96px; width: 1200px; text-align: center;
            font-size: 14.5px; color: #6D7A97; letter-spacing: 1px; }
  .footer .dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%;
                 background: linear-gradient(135deg, #3ED8FF, #7C5CFF); margin: 0 12px 3px; }
</style>
</head>
<body>
<div class="canvas">
  <div class="grid"></div>

  <div class="header">
    <img class="logo" src="__LOGO__">
    <div class="htext">
      <div class="hname">Click</div>
      <div class="hsub">免 ROOT 的安卓连点器 · 录制轨迹，一键回放</div>
    </div>
  </div>

  <div class="chips">
    <div class="chip">无障碍手势</div>
    <div class="chip">无需调试模式</div>
    <div class="chip">轨迹可视化</div>
    <div class="chip">多任务保存</div>
  </div>

  <div class="divider"></div>

  <div class="features">
    <div class="card">
      <div class="num">1</div>
      <div class="ctext">
        <div class="ctitle">录制即回放</div>
        <div class="cdesc">在目标界面按顺序点一遍，坐标与节奏（毫秒）自动记录。</div>
      </div>
    </div>
    <div class="card">
      <div class="num">2</div>
      <div class="ctext">
        <div class="ctitle">轨迹可编辑</div>
        <div class="cdesc">拖动圆点改位置、空白处新增、长按删除，轨迹实时预览。</div>
      </div>
    </div>
    <div class="card">
      <div class="num">3</div>
      <div class="ctext">
        <div class="ctitle">工具条常驻</div>
        <div class="cdesc">单击收缩、双击回首页、按住可拖动，任务名下拉切任务。</div>
      </div>
    </div>
    <div class="card">
      <div class="num">4</div>
      <div class="ctext">
        <div class="ctitle">轮次可选</div>
        <div class="cdesc">点「开始」先选 1~5 次或手动停止，选完立刻执行。</div>
      </div>
    </div>
    <div class="card">
      <div class="num">5</div>
      <div class="ctext">
        <div class="ctitle">圆点避让 + 随时暂停</div>
        <div class="cdesc">执行时收缩成小圆点并避开点击区域，点它即暂停。</div>
      </div>
    </div>
    <div class="card">
      <div class="num">6</div>
      <div class="ctext">
        <div class="ctitle">免 ROOT · 免调试</div>
        <div class="cdesc">走系统无障碍派发手势，不 root、不开调试模式，装上即用。</div>
      </div>
    </div>
  </div>

  <div class="sectitle">实拍界面 · <b>REAL SCREENSHOTS</b></div>
  <div class="shots">
    <div class="shot">
      <div class="phone"><img src="__HOME__"></div>
      <div class="cap">首页 · 已录制任务列表</div>
    </div>
    <div class="shot">
      <div class="phone"><img src="__RECORD__"></div>
      <div class="cap">录制 · 轨迹与节奏</div>
    </div>
    <div class="shot">
      <div class="phone"><img src="__PAUSE__"></div>
      <div class="cap">执行 · 圆点即暂停</div>
    </div>
  </div>

  <div class="footer">产品设计：FDY<span class="dot"></span>v1.3.0<span class="dot"></span>Android 7.0+</div>
</div>
</body>
</html>
"""

html = (TEMPLATE
        .replace("__LOGO__", logo)
        .replace("__HOME__", home)
        .replace("__RECORD__", record)
        .replace("__PAUSE__", pause))

out = BASE / "tools" / "poster.html"
out.write_text(html, encoding="utf-8")
print("written:", out, len(html.encode("utf-8")))
