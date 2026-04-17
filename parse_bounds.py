import xml.etree.ElementTree as ET
import re

def parse_path(path_data):
    nums = re.findall(r'[-+]?\d*\.\d+|\d+', path_data)
    nums = [float(n) for n in nums]
    return nums

def get_bounds(xml_file):
    try:
        tree = ET.parse(xml_file)
        root = tree.getroot()
        all_y = []
        all_x = []
        for path in root.findall('.//{http://schemas.android.com/apk/res/android}path'):
            data = path.get('{http://schemas.android.com/apk/res/android}pathData')
            if data:
                # Naive parsing, assumes M x,y L x,y C x,y x,y x,y ...
                tokens = re.finditer(r'([a-zA-Z])|([-+]?\d*\.\d+|\d+)', data)
                coords = []
                for t in tokens:
                    if t.group(2):
                        coords.append(float(t.group(2)))
                # Pair them up
                for i in range(0, len(coords)-1, 2):
                    all_x.append(coords[i])
                    all_y.append(coords[i+1])
        if not all_x: return None
        return min(all_x), max(all_x), min(all_y), max(all_y)
    except Exception as e:
        return str(e)

print("Hardware bounds: ", get_bounds('app/src/main/res/drawable/ic_tank_hardware.xml'))
print("Silhouette bounds: ", get_bounds('app/src/main/res/drawable/ic_tank_silhouette.xml'))

