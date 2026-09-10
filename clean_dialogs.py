import re

file_path = "app/src/main/java/com/example/kaspawallet/ui/dialogs/WalletDialogs.kt"

with open(file_path, "r") as f:
    content = f.read()

# 1. Replace OutlinedTextField border colors with Color.Transparent
content = content.replace("focusedBorderColor = KaspaPrimary,", "focusedBorderColor = Color.Transparent,")
content = content.replace("unfocusedBorderColor = KaspaCardBorder,", "unfocusedBorderColor = Color.Transparent,")

# 2. Remove .border(1.dp, KaspaCardBorder, ...) and similar border modifiers
content = re.sub(r'\s*\.border\(\s*\d+\.dp\s*,\s*KaspaCardBorder\s*,\s*RoundedCornerShape\(\s*\d+\.dp\s*\)\s*\)', '', content)

# 3. Clean up Card border properties
# Specifically target card border assignments which look like:
# border = CardDefaults.outlinedCardBorder().copy(...)
# Let's clean up single-line ones first
content = re.sub(r'border\s*=\s*CardDefaults\.outlinedCardBorder\(\)\.copy\(brush\s*=\s*SolidColor\(KaspaCardBorder\)\),?', '', content)
content = re.sub(r'border\s*=\s*CardDefaults\.outlinedCardBorder\(\)\.copy\(brush\s*=\s*SolidColor\(KaspaCardBorder\)\)', '', content)
content = re.sub(r'border\s*=\s*CardDefaults\.outlinedCardBorder\(\)\.copy\(brush\s*=\s*SolidColor\(KaspaWarning\.copy\(alpha\s*=\s*0\.4f\)\)\),?', '', content)

# Now target multi-line card borders like:
# border = CardDefaults.outlinedCardBorder().copy(
#     brush = SolidColor(...)
# )
# Or similar. We will parse line by line or do a multiline regex.
pattern_multiline_border = r'border\s*=\s*CardDefaults\.outlinedCardBorder\(\)\.copy\([\s\S]*?\)(?=,?\n\s*(?:shape|colors|content|\)))'
# Let's inspect what's left after we do this.

with open(file_path, "w") as f:
    f.write(content)

print("Pre-cleaning completed.")
