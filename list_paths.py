import xml.etree.ElementTree as ET
import re

svg_ns = "{http://schemas.android.com/apk/res/android}"
tree = ET.parse("app/src/main/res/drawable/ic_tank_3_7.xml")
root = tree.getroot()

def get_bounds(path_data):
    coords = re.findall(r'[-+]?\d*\.\d+|[-+]?\d+', path_data)
    coords = [float(c) for c in coords]
    if not coords: return (0,0,0,0)
    xs = coords[0::2]
    ys = coords[1::2]
    return (min(xs), min(ys), max(xs), max(ys))

def get_area(b):
    return (b[2]-b[0]) * (b[3]-b[1])

paths = []
for i, path in enumerate(root.findall('.//path')):
    pd = path.get(f"{svg_ns}pathData", "")
    fc = path.get(f"{svg_ns}fillColor", "")
    b = get_bounds(pd)
    a = get_area(b)
    # Ignore background rectangles
    if b[0] <= 1 or b[1] <= 1 or b[2] >= 2047 or b[3] >= 2047:
        continue
    paths.append((i, fc, b, a, pd))

paths.sort(key=lambda x: x[3], reverse=True)

for p in paths[:30]:
    print(f"Path {p[0]}: bounds={p[2]}, bg={p[1]}, area={p[3]}")
