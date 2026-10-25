"""Emulator-only UI check: select Chinese, force-stop, then verify a real cold start."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--adb', required=True)
p.add_argument('--serial', required=True)
p.add_argument('--apk', required=True)
p.add_argument('--out', required=True)
args = p.parse_args()
out = Path(args.out)
out.mkdir(parents=True, exist_ok=True)


def adb(*parts):
    result = subprocess.run([args.adb, '-s', args.serial, *parts], check=True,
                            capture_output=True, text=True, encoding='utf-8', timeout=45)
    return result.stdout


if not args.serial.startswith('emulator-') or adb('shell', 'getprop', 'ro.kernel.qemu').strip() != '1':
    raise SystemExit('This probe is restricted to a disposable Android emulator.')


def hierarchy():
    adb('shell', 'uiautomator', 'dump', '--compressed', '/sdcard/aegis16-cold-ui.xml')
    return adb('shell', 'cat', '/sdcard/aegis16-cold-ui.xml')


def node(xml, attribute, values):
    return next((n for n in ET.fromstring(xml).iter('node') if n.get(attribute) in values), None)


def tap(n):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    if x2 <= x1 or y2 <= y1:
        raise AssertionError('Target has no visible bounds')
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))


adb('install', '-r', args.apk)
adb('shell', 'am', 'force-stop', 'com.aegisvault.mobile')
adb('shell', 'am', 'start', '-n', 'com.aegisvault.mobile/.MainActivity')
xml = hierarchy()
settings = node(xml, 'content-desc', {'Settings', '设置'})
if settings is None:
    raise AssertionError('Settings button not found in the observed hierarchy')
tap(settings)
for _ in range(6):
    xml = hierarchy()
    chinese = node(xml, 'text', {'简体中文'})
    if chinese is not None:
        tap(chinese)
        break
    root = ET.fromstring(xml).find('node')
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', root.get('bounds')))
    adb('shell', 'input', 'swipe', str((x1 + x2) // 2), str(y1 + (y2-y1)*4//5),
        str((x1+x2)//2), str(y1+(y2-y1)//3), '400')
else:
    raise AssertionError('Chinese language option was not found')

xml = hierarchy()
if '输入内容' not in xml and node(xml, 'text', {'简体中文'}) is not None:
    adb('shell', 'input', 'keyevent', '4')
    xml = hierarchy()
if '输入内容' not in xml:
    raise AssertionError('Chinese input resource is not displayed after selection')
(out / 'before-force-stop.xml').write_text(xml, encoding='utf-8')
adb('shell', 'am', 'force-stop', 'com.aegisvault.mobile')
adb('shell', 'am', 'start', '-n', 'com.aegisvault.mobile/.MainActivity')
deadline = time.monotonic() + 20
while time.monotonic() < deadline:
    xml = hierarchy()
    if '输入内容' in xml:
        break
    time.sleep(0.2)
else:
    raise AssertionError('Cold start did not restore the Chinese input resource')
(out / 'after-cold-start.xml').write_text(xml, encoding='utf-8')
receipt = {'result': 'PASS', 'serial': args.serial, 'kind': 'actual emulator UI + force-stop/cold-start',
           'selectedLanguage': 'zh-CN', 'verifiedResource': '输入内容', 'physicalPhone': False}
(out / 'receipt.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(receipt, ensure_ascii=False))
