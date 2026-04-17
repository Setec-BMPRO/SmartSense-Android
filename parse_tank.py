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

paths = root.findall('.//path')
kept_paths = []

for path in paths:
    pd = path.get(f"{svg_ns}pathData", "")
    fc = path.get(f"{svg_ns}fillColor", "")
    b = get_bounds(pd)
    # Check if bounds hit the edges of 2048x2048
    if b[0] <= 1 or b[1] <= 1 or b[2] >= 2047 or b[3] >= 2047:
        pass # Background or edge
    else:
        kept_paths.append((path, b))

print(f"Total paths: {len(paths)}")
print(f"Kept paths: {len(kept_paths)}")

min_x = min(p[1][0] for p in kept_paths)
min_y = min(p[1][1] for p in kept_paths)
max_x = max(p[1][2] for p in kept_paths)
max_y = max(p[1][3] for p in kept_paths)

print(f"Kept Tank bounds: ({min_x}, {min_y}, {max_x}, {max_y})")
