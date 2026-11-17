# Referral / Agent API — Frontend Guide

How a user becomes a referral **agent**, shares an **invite code**, and tracks the people they refer plus what they earn.

- **Base URL:** `{{baseUrl}}` (defaults to `http://localhost:8080`)
- **All paths are prefixed** with `/api/v1/refer`
- **Auth:** every endpoint needs `Authorization: Bearer <jwt>` **except** `GET /code-lookup` (public). Use the JWT you already get from `POST /api/v1/auth/login`.

## Response envelope

Every endpoint returns the standard envelope:

```json
{
  "success": true,
  "message": "Human readable message",
  "timestamp": "2026-08-28T10:15:30.123",
  "data": { }
}
```

- `success` — `true` for 2xx, `false` for errors.
- `message` — safe to surface to the user.
- `data` — the payload described per-endpoint below (object, array, or `null`).

Read the HTTP status **and** `success`. On any `4xx/5xx`, show `message`.

---

## Concepts

- **Agent** — a user who opted into the referral programme (`POST /be-an-agent`). Only agents have an invite code, a balance, and a referral list.
- **Invite code** — a short, shareable code that points at the agent. Auto-generated from the agent's name (e.g. `DAV012MATI`) and customisable.
  - **4–20 characters, letters and digits only** (`[A-Za-z0-9]`).
  - **Case-insensitive.** Stored and returned in **UPPERCASE**; the user can type it in any case when applying or looking it up.
- **Referral / invite** — a user who signed up with an agent's code. Tracked so the agent can see who they invited.
- **Balance & accruals** — the agent's referral earnings, read live from MyWealth. `balance` is the current total; `accruals` are the daily earning lines (last 30).

---

## Endpoints

| # | Method | Path | Auth | Purpose |
|---|--------|------|------|---------|
| 1 | `POST` | `/be-an-agent` | ✅ | Enrol the current user as an agent; returns their invite code |
| 2 | `GET` | `/dashboard` | ✅ | Everything for the referral screen in one call |
| 3 | `GET` | `/retrieve-invite-code` | ✅ | Just the current user's invite code |
| 4 | `GET` | `/invite-code/available?code=` | ✅ | Check if a custom code is free |
| 5 | `PATCH` | `/invite-code/customize` | ✅ | Set a custom invite code |
| 6 | `GET` | `/code-lookup?code=` | 🌐 public | "You were invited by …" — validate a code before signup |
| 7 | `GET` | `/balance` | ✅ | Current referral balance |
| 8 | `GET` | `/balance-accruals` | ✅ | Daily earning lines (last 30) |
| 9 | `GET` | `/my-referrals` | ✅ | List of users this agent referred |

A common error across the authenticated endpoints: if the user is **not an agent yet**, you get `404` with `"You are not a referral agent yet"`. Send them through `POST /be-an-agent` first (or gate these screens on agent status).

---

### 1. Become an agent

`POST /api/v1/refer/be-an-agent` · auth · no body

Idempotent — calling it again returns the existing code instead of creating a second agent.

**201 Created**
```json
{ "success": true, "message": "Congratulations. You can now invite others",
  "data": { "inviteCode": "DAV012MATI" } }
```

**200 OK** (already enrolled)
```json
{ "success": true, "message": "You are already a referral agent",
  "data": { "inviteCode": "DAV012MATI" } }
```

---

### 2. Dashboard

`GET /api/v1/refer/dashboard` · auth

One call that powers the whole referral screen (the three data sources are fetched in parallel server-side).

**200 OK**
```json
{
  "success": true,
  "message": "Dashboard retrieved",
  "data": {
    "inviteCode": "DAV012MATI",
    "balance": { "currency": "USD", "balance": 1520.35 },
    "earnings": [
      { "currency": "NGN", "amount": 1520.35, "date": "2026-08-10" }
    ],
    "invites": [
      { "name": "John Smith", "joined": "2026-08-28T10:15:30" }
    ]
  }
}
```

- `balance` is a nested object: `{ "currency", "balance" }`. When the agent hasn't earned yet it's `{ "currency": null, "balance": 0 }`.
- `earnings` — up to 30 daily lines. `invites` — newest first.

---

### 3. Retrieve invite code

`GET /api/v1/refer/retrieve-invite-code` · auth

**200 OK**
```json
{ "success": true, "message": "Invite code retrieved",
  "data": { "inviteCode": "DAV012MATI" } }
```

---

### 4. Check code availability

`GET /api/v1/refer/invite-code/available?code=MYCODE1` · auth

Use this to validate a custom code live (e.g. as the user types) before submitting.

**200 OK**
```json
{ "success": true, "message": "Invite code is available", "data": { "available": true } }
```
```json
{ "success": true, "message": "That invite code has been taken by another user",
  "data": { "available": false } }
```

**400** — code fails the `4–20 letters/digits` rule: `"Invite code must be 4-20 letters or numbers"`.

---

### 5. Customize invite code

`PATCH /api/v1/refer/invite-code/customize` · auth

```json
{ "code": "MYCODE1" }
```

- **200** `"Invite code updated"` → `{ "inviteCode": "MYCODE1" }` (stored uppercase).
- **409** `"That invite code has been taken by another user"`.
- **400** `"Invite code must be 4-20 letters or numbers"`.
- **404** `"You are not a referral agent yet"`.

Re-submitting your own current code is allowed (no conflict).

---

### 6. Look up a code (public)

`GET /api/v1/refer/code-lookup?code=DAV012MATI` · **no auth**

For the signup screen: when a prospective user enters/opens an invite link, confirm the code and show who invited them.

- **200** `"You were invited by John Smith"` → `{ "invitedBy": "John Smith" }`
- **404** `"Invite code is not correct"`
- **400** `"Invite code is not correct"` (blank)

---

### 7. Balance

`GET /api/v1/refer/balance` · auth

**200 OK**
```json
{ "success": true, "message": "Referral balance retrieved",
  "data": { "currency": "USD", "balance": 1520.35 } }
```
No earnings yet → `{ "currency": null, "balance": 0 }`.

---

### 8. Balance accruals (daily earnings)

`GET /api/v1/refer/balance-accruals` · auth

Up to the last 30 daily earning lines.

**200 OK**
```json
{ "success": true, "message": "Referral accruals retrieved",
  "data": [
    { "currency": "NGN", "amount": 1520.35, "date": "2026-08-10" }
  ] }
```

---

### 9. My referrals

`GET /api/v1/refer/my-referrals` · auth

The list of users this agent invited — **name and join date only** (newest first).

**200 OK**
```json
{ "success": true, "message": "Referrals retrieved",
  "data": [
    { "name": "John Smith", "joined": "2026-08-28T10:15:30" }
  ] }
```

Empty array when the agent has no referrals yet.

---

## Applying an invite code at signup

The code is **not** applied through the `/refer` endpoints — it rides along on the existing account-creation requests as an **optional** `inviteCode` field (4–20 letters/digits, any case, may be omitted/blank).

**Individual signup** — `POST /api/v1/auth/create-account`:
```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "...": "...",
  "inviteCode": "DAV012MATI"
}
```

**Corporate signup** — captured on the first step, `POST /api/v1/auth/corporate/basic-details`:
```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "email": "jane@acme.io",
  "phoneNumber": "+2348012345678",
  "gender": "FEMALE",
  "inviteCode": "DAV012MATI"
}
```

Behaviour:
- An **unknown or blank** code is ignored — account creation still succeeds (the code is silently dropped, logged server-side). It never blocks signup.
- A **valid** code links the new user to the agent (they'll appear in the agent's `/my-referrals`) and is forwarded to MyWealth.

Recommended UX: call `GET /code-lookup?code=` first and show "You were invited by …" so the user can confirm before submitting.

---

## Typical flow

1. User taps "Become an agent" → `POST /be-an-agent` → show the returned `inviteCode`.
2. (Optional) User personalises it → `GET /invite-code/available?code=` while typing → `PATCH /invite-code/customize`.
3. User shares the code / invite link.
4. Invitee at signup → `GET /code-lookup?code=` to confirm → submits `create-account` (or corporate `basic-details`) with `inviteCode`.
5. Agent opens the referral screen → `GET /dashboard` for code + balance + earnings + invite list.

## Errors cheat-sheet

| Status | When | `message` |
|--------|------|-----------|
| 400 | Bad invite-code format | `Invite code must be 4-20 letters or numbers` |
| 404 | Caller isn't an agent | `You are not a referral agent yet` |
| 404 | Unknown code on lookup | `Invite code is not correct` |
| 409 | Custom code taken | `That invite code has been taken by another user` |
| 502 | MyWealth enrol failed | `We could not enrol you as an agent. Please try again shortly.` |
