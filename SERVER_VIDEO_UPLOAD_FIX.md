# Video upload proxy fix

The production host currently rejects request bodies above nginx's default
`1m` limit before the Messenger API receives them. Add this directive inside
the existing `server {}` block for `messengerextra.duckdns.org` (or inside the
specific `/api/messaging/` location):

```nginx
client_max_body_size 50m;
```

Then validate and reload nginx:

```sh
sudo nginx -t
sudo systemctl reload nginx
```

The Android client also keeps exported videos below the current proxy ceiling
so short videos work before the server configuration is deployed.
