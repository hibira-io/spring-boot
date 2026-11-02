# Browser e2e

Drives [`samples/chat`](../samples/chat) in two browsers: both users see each
other online, a message from one reaches the other, a wave arrives through
`sendToUser`, and a channel the rules refuse answers 403.

```sh
npm install
# start the sample on port 8089 with your app's credentials, then
BASE=http://127.0.0.1:8089 npm test
```
