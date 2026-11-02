# Chat sample

A Spring Boot 4 chat on Hibira: Spring Security form login (ann / password,
bob / password), a presence list of who is online, messages sent with
`@HibiraBroadcast`, and a wave button that uses `hibira.sendToUser`.

```sh
# from the repository root
mvn install -DskipTests
cd samples/chat
HIBIRA_APP_ID=... HIBIRA_KEY=... HIBIRA_SECRET=... mvn spring-boot:run
```

Open http://localhost:8080 in two browsers and sign in as each user. On the
Free plan, add `http://localhost:8080` to the app's allowed origins.

## 中文摘要

Spring Boot 4 聊天範例：Spring Security 登入（ann / bob，密碼 password）、
presence 線上名單、以 `@HibiraBroadcast` 送出訊息、以 `hibira.sendToUser`
打招呼。設定 `HIBIRA_APP_ID`、`HIBIRA_KEY`、`HIBIRA_SECRET` 後執行即可。
