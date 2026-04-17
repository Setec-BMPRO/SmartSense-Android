import parse_tank
res = []
for p, b in parse_tank.kept_paths:
    if b[0] < 100:
        fc = p.get(parse_tank.svg_ns+"fillColor")
        res.append((fc, b))
print("Paths crossing x < 100:", res)
