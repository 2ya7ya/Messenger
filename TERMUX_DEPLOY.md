# Deploy Flash 1.108

```bash
cd ~/Messenger-repo-1.103 || exit 1

unzip -o \
"$HOME/storage/downloads/messenger-native-source.zip" \
-d ~/Messenger-repo-1.103

git add -A
git commit -m "Rename Messenger app to Flash"
git push origin main
```
