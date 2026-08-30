# Deploy Flash 1.111 white logo circle

```bash
cd ~/Messenger-repo-1.103 || exit 1

unzip -o \
"$HOME/storage/downloads/flash-native-source-v1.111-white-circle.zip" \
-d ~/Messenger-repo-1.103

git add -A
git commit -m "Use white logo circle in Flash"
git push origin main
```
