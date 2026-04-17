import xml.etree.ElementTree as ET

svg_ns = "{http://schemas.android.com/apk/res/android}"

tree = ET.parse("app/src/main/res/drawable/ic_tank_3_7.xml")
root = tree.getroot()

bg_colors = {'#D0D1D0', '#CECECE', '#E1E1E1', '#CECFCE', '#D0D0D0', '#CFCFCF', '#FDFDFD', '#CFD0CF', '#EBEBEA', '#E6E6E6', '#E4E4E4', '#E5E5E5', '#EAEAEA', '#CFCFCE', '#EFEFEF', '#D9D9D9', '#D0CFCF', '#FDFEFD', '#E7E7E7', '#E3E3E3', '#E9E9E9', '#FDFDFC', '#D9D9D8', '#CECFCE'}

paths = root.findall('.//path')
kept_paths = []
removed_paths = []
for p in paths:
    fc = p.get(f"{svg_ns}fillColor", "")
    if fc in bg_colors:
        removed_paths.append(p)
    else:
        kept_paths.append(p)

import re
def get_bounds(paths):
    xmin = ymin = float('inf')
    xmax = ymax = float('-inf')
    for p in paths:
        coords = re.findall(r'[-+]?\d*\.\d+|[-+]?\d+', p.get(f"{svg_ns}pathData", ""))
        coords = [float(c) for c in coords]
        if coords:
            xs = coords[0::2]
            ys = coords[1::2]
            xmin = min(xmin, min(xs))
            ymin = min(ymin, min(ys))
            xmax = max(xmax, max(xs))
            ymax = max(ymax, max(ys))
    return xmin, ymin, xmax, ymax

print(f"Kept paths: {len(kept_paths)}")
print(f"Kept bounds: {get_bounds(kept_paths)}")
