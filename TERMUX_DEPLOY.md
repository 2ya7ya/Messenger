# Deploy Flash 1.110 minimal logo

```bash
cd ~/Messenger-repo-1.103 || exit 1

unzip -o \
"$HOME/storage/downloads/flash-native-source-v1.110-minimal-logo.zip" \
-d ~/Messenger-repo-1.103

git add -A
git commit -m "Use minimal Flash app logo"
git push origin main
```
