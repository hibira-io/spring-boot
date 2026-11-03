# Changelog

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
