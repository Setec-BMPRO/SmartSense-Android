import re

def shift_y(path_str, dy):
    # This regex pulls out all commands and coords
    # Command letters: M, C, L, Z, m, c, l, z
    # Since these are simple paths, we can just split by letters and spaces
    
    # Actually, let's just carefully parse it
    # We find all (x,y) pairs. Since it's all absolute (M, L, C):
    commands = re.findall(r'[A-Za-z]|[-+]?\d*\.\d+|[-+]?\d+', path_str)
    
    out = []
    i = 0
    while i < len(commands):
        tok = commands[i]
        if tok.isalpha():
            out.append(tok)
            i += 1
        else:
            # The next two tokens are x and y
            x_tok = commands[i]
            y_tok = commands[i+1]
            x = float(x_tok)
            y = float(y_tok)
            y_new = y + dy
            # Format nicely
            out.append(f"{x},{y_new}")
            i += 2
    
    # We need to preserve the formatting slightly.
    # We'll just join with spaces, except 'Z'
    res = []
    for t in out:
        if t.isalpha():
            res.append(t)
        else:
            res.append(t + " ")
    return "".join(res).replace(" Z", "Z").strip()

# Path 1: Shift down by 55
p1 = "M138.4,1.9 C132.1,5.7 131.4,14.7 137.1,19.4 C139.6,21.5 141.2,22.0 145.6,22.0 L151.0,22.0 L151.0,47.9 L151.0,73.7 L143.9,74.9 C132.7,76.7 123.1,81.7 115.2,89.7 C102.7,102.4 100.0,110.4 100.0,135.2 L100.0,152.0 L223.5,152.0 L347.0,152.0 L347.0,135.2 C347.0,115.6 345.8,109.8 340.0,99.9 C332.3,86.9 318.3,77.4 303.1,74.9 L296.0,73.7 L296.0,47.9 L296.0,22.0 L301.4,22.0 C305.8,22.0 307.4,21.5 309.9,19.4 C315.6,14.7 314.9,5.7 308.6,1.9 C305.9,0.3 303.4,0.0 289.8,0.0 L274.0,0.0 L274.0,6.0 L274.0,12.0 L223.5,12.0 L173.0,12.0 L173.0,6.0 L173.0,0.0 L157.3,0.0 C143.6,0.0 141.1,0.3 138.4,1.9 Z M274.0,51.0 L274.0,74.0 L253.0,74.0 L232.0,74.0 L232.0,64.5 L232.0,55.0 L236.5,55.0 L241.0,55.0 L241.0,49.0 L241.0,43.0 L223.5,43.0 L206.0,43.0 L206.0,49.0 L206.0,55.0 L210.5,55.0 L215.0,55.0 L215.0,64.5 L215.0,74.0 L194.0,74.0 L173.0,74.0 L173.0,51.0 L173.0,28.0 L223.5,28.0 L274.0,28.0 L274.0,51.0 Z"
new_p1 = shift_y(p1, 55)

# Path 3: Shift up by 55
p3 = "M100.0,351.8 C100.0,369.2 101.3,375.5 107.0,385.0 C113.6,396.1 125.2,405.3 136.1,408.1 C138.8,408.8 141.0,409.7 141.0,410.0 C141.0,410.3 139.7,411.9 138.0,413.5 C128.6,422.5 130.8,439.4 142.2,444.9 C146.6,447.0 147.2,447.0 223.5,447.0 C299.8,447.0 300.4,447.0 304.8,444.9 C316.2,439.4 318.4,422.5 309.0,413.5 C307.3,411.9 306.0,410.3 306.0,410.0 C306.0,409.7 308.2,408.8 310.9,408.1 C321.8,405.3 333.4,396.1 340.0,385.0 C345.7,375.5 347.0,369.2 347.0,351.8 L347.0,337.0 L223.5,337.0 L100.0,337.0 L100.0,351.8 Z"
new_p3 = shift_y(p3, -55)

print("NEW PATH 1:")
print(new_p1)
print("\nNEW PATH 3:")
print(new_p3)
