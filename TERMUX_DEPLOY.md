# Deploy Flash 1.109 login and new logo

```bash
cd ~/Messenger-repo-1.103 || exit 1

unzip -o \
"$HOME/storage/downloads/flash-native-source-v1.109-login-logo.zip" \
-d ~/Messenger-repo-1.103

git add -A
git commit -m "Match FaceTok login design and add Flash logo"
git push origin main
```
