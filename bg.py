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

bg_colors = set()

for path in root.findall('.//path'):
    pd = path.get(f"{svg_ns}pathData", "")
    fc = path.get(f"{svg_ns}fillColor", "")
    b = get_bounds(pd)
    if b[0] <= 1 or b[1] <= 1 or b[2] >= 2047 or b[3] >= 2047:
        if fc: bg_colors.add(fc)

print(bg_colors)
