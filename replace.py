import sys

with open('app/src/main/java/com/example/kaspawallet/ui/screens/SettingsTab.kt', 'r') as f:
    content = f.read()

start_str = "    if (showDeleteConfirm) {"
end_str = "        )\n    }\n}\n"

start_idx = content.find(start_str)
end_idx = content.find(end_str, start_idx) + len(end_str)

with open('update_settings.kt', 'r') as f:
    replacement = f.read()

new_content = content[:start_idx] + replacement + "}\n"

with open('app/src/main/java/com/example/kaspawallet/ui/screens/SettingsTab.kt', 'w') as f:
    f.write(new_content)
