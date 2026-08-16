# Messenger V146 Native Android

This branch is the start of a true native Android rebuild of the existing v146 Messenger UI. It does not load the Facebook website in a WebView.

Current native implementation:
- one-time native login against `https://facebook-extra.onrender.com/api/login`
- native Chats screen matching the v146 web layout dimensions/colors
- immediate SQLite cached inbox render, then silent network refresh
- native New message / contact picker
- native conversation screen
- cached conversation history
- native text sending
- authenticated avatar disk/memory cache
- live Messenger WebSocket updates

Next parity work: media/photo/video/file messages, voice recording/playback, reactions, reply/edit/delete/forward, group controls, themes, message info/read receipts, shared posts/reels, full emoji picker, media preview and the remaining v146 details.
