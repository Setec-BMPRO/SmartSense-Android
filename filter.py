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

kept_paths = []

for i,path in enumerate(root.findall('.//path')):
    # Skip path 0 and 1
    if i in (0, 1): continue
    
    pd = path.get(f"{svg_ns}pathData", "")
    fc = path.get(f"{svg_ns}fillColor", "")
    b = get_bounds(pd)
    w = b[2] - b[0]
    h = b[3] - b[1]
    
    # Check if width and height are close to multiples of 51
    is_bg = False
    
    # Many background parts are exactly rectangular checkerboard tiles.
    if pd.count('L') >= 3 and pd.count('C') == 0:
        is_bg = True
    elif (w > 49 and h > 49):
        # Checkerboard tiles have these approximate widths
        w_rem = w % 51
        h_rem = h % 51
        if (w_rem < 2 or w_rem > 49) and (h_rem < 2 or h_rem > 49):
            is_bg = True
            
    # Also if the color is pure white or close to it, and it's large
    if not is_bg:
        kept_paths.append(path)

xmin = min((get_bounds(p.get(f"{svg_ns}pathData"))[0] for p in kept_paths), default=0)
xmax = max((get_bounds(p.get(f"{svg_ns}pathData"))[2] for p in kept_paths), default=0)

print(f"Kept {len(kept_paths)} paths")
print(f"X bounds: {xmin} to {xmax}")

