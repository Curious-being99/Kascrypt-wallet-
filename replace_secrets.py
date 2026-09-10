import sys

with open('.github/workflows/release.yml', 'r') as f:
    content = f.read()

content = content.replace(
    "${{ secrets.KEYSTORE_BASE64 }}", 
    "${{ secrets.RELEASE_KEYSTORE_BASE64 }}"
)
content = content.replace(
    "${{ secrets.KEYSTORE_ALIAS }}", 
    "${{ secrets.RELEASE_KEY_ALIAS }}"
)

with open('.github/workflows/release.yml', 'w') as f:
    f.write(content)
