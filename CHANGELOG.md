# Changelog

## 0.3.1

- Fix: requests whose path holds an encoded character (a Chat API conversation or user ID with a space or `/`, for example) are now signed over the decoded path, as the server checks them. Before, such calls answered 401.

## 0.3.0

- Chat API on `Hibira`: `putConversation`, `getConversation`, `deleteConversation`, `addMembers`, `removeMember`, `sendMessage`, `listMessages`, `markRead`, `systemMessage`, `userConversations`, and `chat(method, path, query, body)` for any other call.
- Live Chat: `io.hibira.LiveChat.userHash` / `identity` for verified visitors; the starter adds a `HibiraLiveChat` bean (`hibira.live-chat.workspace`, `hibira.live-chat.identity-secret`) whose `scriptTag(...)` renders the widget.

## 0.2.0

- Waiting rooms. `io.hibira.WaitingRoom` checks the signed pass from the
  Hibira queue (`verifyPass`) and decides what to do with a request
  (`decide`). The starter adds a servlet filter and a WebFlux filter for the
  path prefixes in `hibira.waiting-room.rooms`: visitors without a pass go to
  the hosted queue page, and the pass is kept in an HttpOnly cookie and
  exposed as the `hibira.pass` request attribute.

## 0.1.0

- `io.hibira:hibira-java`: HTTP API client (`trigger`, `triggerBatch`,
  `sendToUser`, channels, presence users, `terminateUserConnections`),
  private, presence and user sign-in signatures, webhook verification
  (including the previous secret during a rotation), approvals
  (`requestApproval`, `getApproval`, `awaitApproval`), the notification inbox
  (`sendInbox`, `listInbox`, `markInbox`, `deleteInbox`, user tokens) and push
  notifications (`publishToUsers`, `publishToInterests`, `deletePushUser`,
  push tokens).
- `io.hibira:hibira-spring-boot-starter`: auto-configured `Hibira` client from
  `hibira.*`; `@HibiraChannel` channel authorization; endpoints for channel
  auth, `pusher.signin()`, webhooks, inbox tokens and push tokens on Spring MVC
  and WebFlux; webhooks published as `WebhookEvent` application events;
  `@HibiraBroadcast` and `HibiraBroadcastable` application events sent after
  the transaction commits. Spring Boot 3.x and 4.x, Java 17+.
