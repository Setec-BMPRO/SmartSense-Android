with open("app/src/main/java/com/smartsense/app/ui/views/TankLevelView.kt", "r") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "tankLabelPaint.color = 0x60FFFFFF" in line:
        lines[i] = """            val dark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            tankLabelPaint.color = if (dark) 0x90FFFFFF.toInt() else 0x70000000.toInt()
"""

with open("app/src/main/java/com/smartsense/app/ui/views/TankLevelView.kt", "w") as f:
    f.writelines(lines)
