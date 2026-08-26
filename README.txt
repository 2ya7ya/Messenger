Messenger latest three-fix patch

This patch is for the CURRENT Messenger source repo and refuses to patch an
unknown/older MainActivity.java.

It changes only:
1. Keyboard/message composer safety.
2. Theme names/list to match the original v146 website.
3. Facebook direct-message handoff so the target conversation opens without
   rendering Chats first.

Apply from the Messenger repo root:

  python apply_latest_three_fixes.py

Then:

  git add -A
  git commit -m "Fix keyboard composer themes and direct Facebook conversation"
  git push origin main

If Actions does not auto-run:

  gh workflow run "Build Messenger APK" --ref main
