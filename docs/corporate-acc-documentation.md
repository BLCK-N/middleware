# Corporate Account API — Endpoint Reference

Every endpoint under `/api/v1/auth/corporate`, what it accepts, what it returns, and how the
frontend is expected to drive it.

This is the reference for the whole corporate flow — the onboarding steps, the read (`GET`) side of
every form page (which lets the app re-open a step with the user's answers already in it), and
what comes after the account exists.

Postman: [corporate-onboarding.postman_collection.json](./corporate-onboarding.postman_collection.json)
contains every request below, with saved example responses.

---

## Contents

1. [Response envelope](#response-envelope)
2. [Authentication model](#authentication-model)
3. [Endpoint map](#endpoint-map)
4. [Read endpoints — one per form page](#read-endpoints--one-per-form-page)
   - [GET /basic-details](#get-basic-details)
   - [GET /bvn-data](#get-bvn-data)
   - [GET /kyb](#get-kyb)
   - [GET /memart](#get-memart)
   - [GET /directors](#get-directors)
   - [GET /credentials](#get-credentials)
5. [The full submission](#the-full-submission)
   - [GET /submission](#get-submission)
   - [GET /preview](#get-preview)
6. [Write endpoints — the onboarding flow](#write-endpoints--the-onboarding-flow)
7. [Adding more logins, and signing in](#adding-more-logins-and-signing-in)
8. [Frontend implementation guide](#frontend-implementation-guide)
9. [Error handling](#error-handling)

---

## Response envelope

Every endpoint — success or failure — returns the same wrapper. Only `data` changes shape.

```json
{
  "success": true,
  "message": "Basic details retrieved successfully",
  "timestamp": "2026-08-20T11:04:22.118",
  "data": { }
}
```

Branch on the **HTTP status**, not on `message`. Several outcomes are only distinguishable by
status (409 duplicate vs 503 upstream down), and messages are user-facing copy that will change.

---

## Authentication model

| Credential | Issued by | Lifetime | Used for |
|---|---|---|---|
| **Onboarding token** | `POST /corporate/validate-otp` | 3 days | Every onboarding step, read and write |
| **Lookup token** | `POST /corporate/lookup` | 15 minutes | `create-user` only — **travels in the body** |
| **Session token** | `complete`, `create-user`, `login` | 3 days | Normal app use |

```
Authorization: Bearer <onboarding token>
```

**The onboarding token identifies the onboarding.** No step takes an id or an email in the path or
query — the server resolves the staged record from the token alone. This is why the read endpoints
need no parameters, and why one caller can never read another company's staged data.

Calling any onboarding endpoint without it:

```json
{
  "success": false,
  "message": "This step requires the corporate onboarding token returned by the KYC step.",
  "timestamp": "2026-08-20T11:04:22.118",
  "data": null
}
```
→ **403 Forbidden**. With a valid token but no staged record (e.g. it was already completed and
cleared) → **404 Not Found**, `"No corporate onboarding in progress."`

---

## Endpoint map

Each form page is one path with two methods — **`POST` to submit it, `GET` to read back what was
submitted** — with one exception: the BVN page submits at `POST /corporate/validate-bvn` but reads
at `GET /corporate/bvn-data` (a `GET /validate-bvn` would read oddly against a verb path).

| Page | Path | POST | GET |
|---|---|---|---|
| — | `/corporate/start/{email}` | — | request an OTP (public) |
| — | `/corporate/validate-otp` | verify OTP → onboarding token | — |
| 1 | `/corporate/basic-details` | submit name, phone, gender | read them back |
| 2 | `POST /corporate/validate-bvn`<br>`GET /corporate/bvn-data` | verify and store a BVN | read BVN state (masked) |
| 4 | `/corporate/kyb` | verify an RC number | read the CAC company record |
| 5 | `/corporate/memart` | attach the MEMART URL | read the attached URL |
| 6 | `/corporate/directors` | replace the director list | read the director list |
| 7 | `/corporate/credentials` | set credentials + profile | read profile, credentials as set/not set |
| 8 | `/corporate/complete` | create the account | — |
| — | `/corporate/submission` | — | **everything submitted, grouped by page** |
| — | `/corporate/preview` | — | everything submitted, as one flat record |
| — | `/corporate/lookup` | find a company (public) | — |
| — | `/corporate/create-user` | add a login (public) | — |
| — | `/corporate/login` | sign in (public) | — |

Page 3 has no backend call — it is a frontend-only screen — which is why the numbering skips it.

---

## Read endpoints — one per form page

All six require the onboarding token. All six are **safe to call at any time**, in any order, even
before the matching page has been submitted: an untouched page comes back with `collected: false`
and null fields rather than a 404.

Two rules hold across every read:

- **The PIN and password are never returned.** They come back as `pinSet` / `passwordSet` booleans.
- **BVNs are masked** — `"*********01"`. A stored BVN is corrected by re-submitting it through
  `POST /validate-bvn` (or `POST /directors`) for re-verification, never by editing what you read.

### GET /basic-details

`GET /api/v1/auth/corporate/basic-details` · **onboarding token**

**200 OK**

```json
{
  "success": true,
  "message": "Basic details retrieved successfully",
  "timestamp": "2026-08-20T11:04:22.118",
  "data": {
    "collected": true,
    "firstName": "David",
    "middleName": "Oluwaseun",
    "lastName": "Areegbe",
    "email": "david@acme.com",
    "emailVerified": true,
    "phoneNumber": "2348012345678",
    "gender": "MALE"
  }
}
```

| Field | Notes |
|---|---|
| `collected` | `false` until page 1 has been submitted at least once |
| `email` | the address the onboarding token was issued for — the identity of the onboarding |
| `emailVerified` | always `true` once the token exists (the OTP proved it) |
| `gender` | `MALE` or `FEMALE`, or `null` |

Nothing collected yet:

```json
{
  "collected": false,
  "firstName": null,
  "middleName": null,
  "lastName": null,
  "email": "david@acme.com",
  "emailVerified": true,
  "phoneNumber": null,
  "gender": null
}
```

### GET /bvn-data

`GET /api/v1/auth/corporate/bvn-data` · **onboarding token**

Reads the state of page 2. `dateOfBirth` and `maritalStatus` were filled in by the BVN lookup, not
typed by the user — show them, don't ask for them again.

**200 OK**

```json
{
  "success": true,
  "message": "BVN details retrieved successfully",
  "timestamp": "2026-08-20T11:05:02.771",
  "data": {
    "collected": true,
    "verified": true,
    "maskedBvn": "*********01",
    "middleName": "Oluwaseun",
    "dateOfBirth": "1985-03-11",
    "maritalStatus": "SINGLE"
  }
}
```

A stored BVN is by definition a verified one — the value is only written after QoreID confirms it —
so `collected` and `verified` always agree. Both are `false`, and `maskedBvn` is `null`, before the
step runs.

### GET /kyb

`GET /api/v1/auth/corporate/kyb` · **onboarding token**

Only `rcNumber` was typed by the user; everything else is the CAC record that matched it.

**200 OK**

```json
{
  "success": true,
  "message": "Business details retrieved successfully",
  "timestamp": "2026-08-20T11:06:14.900",
  "data": {
    "collected": true,
    "cacVerified": true,
    "rcNumber": "RC1234567",
    "companyName": "ACME VENTURES LIMITED",
    "companyType": "PRIVATE_COMPANY_LIMITED_BY_SHARES",
    "businessStructure": "PRIVATE_CORPORATION",
    "registrationDate": "2018-04-19T00:00:00Z",
    "classification": "COMPANY",
    "companyStatus": "ACTIVE",
    "companyEmail": "info@acme.com",
    "headOfficeAddress": "22 Admiralty Way, Lekki Phase 1",
    "branchAddress": null,
    "addressLine2": "Lekki Phase 1",
    "state": "Lagos",
    "city": "Lagos",
    "lga": "Eti-Osa",
    "postCode": "100001",
    "country": "NG",
    "shareCapital": 10000000.00,
    "shareCapitalInWords": "Ten Million Naira",
    "numberOfAffiliates": 3
  }
}
```

`registrationDate` is an ISO-8601 offset timestamp; render the date part only. `shareCapital` is a
decimal number — parse it as a decimal, not a float, if you format currency client-side.

### GET /memart

`GET /api/v1/auth/corporate/memart` · **onboarding token**

**200 OK**

```json
{
  "success": true,
  "message": "Memart file retrieved successfully",
  "timestamp": "2026-08-20T11:07:41.002",
  "data": {
    "collected": true,
    "url": "https://myrtle-bucket.s3.eu-west-1.amazonaws.com/1754661234567_memart.pdf"
  }
}
```

Use `collected` to render "MEMART uploaded — replace?" instead of an empty file picker on a
returning user.

### GET /directors

`GET /api/v1/auth/corporate/directors` · **onboarding token**

Directors are stored as a set, and `POST /directors` **replaces** the whole set — so this returns
the list as a whole, and that list is exactly what the next POST must contain plus or minus your
edits.

**200 OK**

```json
{
  "success": true,
  "message": "Directors retrieved successfully",
  "timestamp": "2026-08-20T11:08:30.415",
  "data": {
    "collected": true,
    "count": 2,
    "directors": [
      {
        "firstName": "David",
        "middleName": "Oluwaseun",
        "lastName": "Areegbe",
        "email": "david@acme.com",
        "phoneNumber": "2348012345678",
        "maskedBvn": "*********01",
        "role": "CORP_DIRECTOR",
        "beneficialOwner": true,
        "gender": "MALE",
        "title": "Mr",
        "addressLine1": "22 Admiralty Way",
        "addressLine2": "Lekki Phase 1",
        "city": "Lagos",
        "state": "Lagos",
        "postCode": "100001",
        "country": "NG",
        "birthDate": "1985-03-11",
        "maritalStatus": "SINGLE"
      },
      {
        "firstName": "Ada",
        "middleName": null,
        "lastName": "Obi",
        "email": "ada@acme.com",
        "phoneNumber": "2348022222222",
        "maskedBvn": "*********45",
        "role": "CORP_DIRECTOR",
        "beneficialOwner": false,
        "gender": "FEMALE",
        "title": "Mrs",
        "addressLine1": "5 Bourdillon Road",
        "addressLine2": null,
        "city": "Ikoyi",
        "state": "Lagos",
        "postCode": "101233",
        "country": "NG",
        "birthDate": "1990-07-02",
        "maritalStatus": "MARRIED"
      }
    ]
  }
}
```

> **Editing directors:** the response masks BVNs, so you cannot round-trip this array straight back
> into `POST /directors` — that endpoint requires 11 real digits per director. Either keep the
> unmasked values in client state for the duration of the session, or make the user re-enter the
> BVN of any director they edit. Never send `"*********01"` back; it fails validation.

### GET /credentials

`GET /api/v1/auth/corporate/credentials` · **onboarding token**

The profile half of page 7 reads back in full. The credential half reads back as two booleans.

**200 OK**

```json
{
  "success": true,
  "message": "Corporate credentials retrieved successfully",
  "timestamp": "2026-08-20T11:09:55.630",
  "data": {
    "collected": true,
    "pinSet": true,
    "passwordSet": true,
    "username": "davidareegbe",
    "profession": "DIRECTOR",
    "nationality": "Nigerian",
    "idExpDate": "2030-12-31",
    "valuationCurrency": "NGN",
    "businessStructure": "PRIVATE_CORPORATION",
    "title": "Mr",
    "maritalStatus": "SINGLE",
    "employerName": "ACME VENTURES LIMITED",
    "employerAddress": "22 Admiralty Way, Lekki, Lagos",
    "netWorth": "1000000-5000000",
    "annualIncome": "1000000-5000000",
    "liquidNetWorth": "500000-2000000",
    "sourceOfWealth": "Business Income",
    "investmentExperience": "MODERATE",
    "riskTolerance": "MODERATE",
    "investmentObjective": "GROWTH"
  }
}
```

`collected` is `true` only when **both** the PIN and the password are set — that is the condition
`complete` enforces. `idExpDate` here is the same field sent as `idExpDate` on the POST.

---

## The full submission

### GET /submission

`GET /api/v1/auth/corporate/submission` · **onboarding token**

**Everything the user has submitted, grouped by the page that collected it** — one call instead of
six. Use this to hydrate the whole wizard at once (on app start, or after a resume), and for the
final review screen.

It carries two extras the per-page reads don't:

- `nextPage` — the same resume page number `validate-otp` returns, recalculated now.
- `progress` — the progress object, including `outstandingSteps`.

**200 OK**

```json
{
  "success": true,
  "message": "Corporate onboarding submission retrieved successfully",
  "timestamp": "2026-08-20T11:12:00.004",
  "data": {
    "email": "david@acme.com",
    "emailVerified": true,
    "nextPage": 8,
    "basicDetails": {
      "collected": true,
      "firstName": "David",
      "middleName": "Oluwaseun",
      "lastName": "Areegbe",
      "email": "david@acme.com",
      "emailVerified": true,
      "phoneNumber": "2348012345678",
      "gender": "MALE"
    },
    "bvn": {
      "collected": true,
      "verified": true,
      "maskedBvn": "*********01",
      "middleName": "Oluwaseun",
      "dateOfBirth": "1985-03-11",
      "maritalStatus": "SINGLE"
    },
    "kyb": {
      "collected": true,
      "cacVerified": true,
      "rcNumber": "RC1234567",
      "companyName": "ACME VENTURES LIMITED",
      "companyType": "PRIVATE_COMPANY_LIMITED_BY_SHARES",
      "businessStructure": "PRIVATE_CORPORATION",
      "registrationDate": "2018-04-19T00:00:00Z",
      "classification": "COMPANY",
      "companyStatus": "ACTIVE",
      "companyEmail": "info@acme.com",
      "headOfficeAddress": "22 Admiralty Way, Lekki Phase 1",
      "branchAddress": null,
      "addressLine2": "Lekki Phase 1",
      "state": "Lagos",
      "city": "Lagos",
      "lga": "Eti-Osa",
      "postCode": "100001",
      "country": "NG",
      "shareCapital": 10000000.00,
      "shareCapitalInWords": "Ten Million Naira",
      "numberOfAffiliates": 3
    },
    "memart": {
      "collected": true,
      "url": "https://myrtle-bucket.s3.eu-west-1.amazonaws.com/1754661234567_memart.pdf"
    },
    "directors": {
      "collected": true,
      "count": 1,
      "directors": [
        {
          "firstName": "David",
          "middleName": "Oluwaseun",
          "lastName": "Areegbe",
          "email": "david@acme.com",
          "phoneNumber": "2348012345678",
          "maskedBvn": "*********01",
          "role": "CORP_DIRECTOR",
          "beneficialOwner": true,
          "gender": "MALE",
          "title": "Mr",
          "addressLine1": "22 Admiralty Way",
          "addressLine2": "Lekki Phase 1",
          "city": "Lagos",
          "state": "Lagos",
          "postCode": "100001",
          "country": "NG",
          "birthDate": "1985-03-11",
          "maritalStatus": "SINGLE"
        }
      ]
    },
    "credentials": {
      "collected": true,
      "pinSet": true,
      "passwordSet": true,
      "username": "davidareegbe",
      "profession": "DIRECTOR",
      "nationality": "Nigerian",
      "idExpDate": "2030-12-31",
      "valuationCurrency": "NGN",
      "businessStructure": "PRIVATE_CORPORATION",
      "title": "Mr",
      "maritalStatus": "SINGLE",
      "employerName": "ACME VENTURES LIMITED",
      "employerAddress": "22 Admiralty Way, Lekki, Lagos",
      "netWorth": "1000000-5000000",
      "annualIncome": "1000000-5000000",
      "liquidNetWorth": "500000-2000000",
      "sourceOfWealth": "Business Income",
      "investmentExperience": "MODERATE",
      "riskTolerance": "MODERATE",
      "investmentObjective": "GROWTH"
    },
    "progress": {
      "email": "david@acme.com",
      "companyName": "ACME VENTURES LIMITED",
      "rcNumber": "RC1234567",
      "maskedBvn": "*********01",
      "emailVerified": true,
      "cacVerified": true,
      "memartCollected": true,
      "directorsCollected": 1,
      "credentialsCollected": true,
      "outstandingSteps": []
    }
  }
}
```

Halfway through, the untouched sections are present but empty — the shape never changes, only the
`collected` flags and the values:

```json
{
  "email": "david@acme.com",
  "emailVerified": true,
  "nextPage": 5,
  "basicDetails": { "collected": true, "firstName": "David", "…": "…" },
  "bvn": { "collected": true, "verified": true, "maskedBvn": "*********01", "…": "…" },
  "kyb": { "collected": true, "cacVerified": true, "rcNumber": "RC1234567", "…": "…" },
  "memart": { "collected": false, "url": null },
  "directors": { "collected": false, "count": 0, "directors": [] },
  "credentials": { "collected": false, "pinSet": false, "passwordSet": false, "…": null },
  "progress": {
    "…": "…",
    "outstandingSteps": ["MEMART", "DIRECTORS", "CREDENTIALS"]
  }
}
```

`outstandingSteps` mirrors exactly what `POST /complete` enforces. **Drive any "what's left" UI
from it** rather than hardcoding a list — when the array is empty, completion will pass its checks.

### GET /preview

`GET /api/v1/auth/corporate/preview` · **onboarding token**

The same staged record as one **flat** object (no page grouping), plus a handful of fields the page
endpoints don't surface (`idType`, `idIssue`, `issuingAuthority`, `placeOfIssue`,
`mothersMaidenName`, `stateOfOrigin`, `localGovernment`, `address`).

Prefer `/submission` in new code — it groups by page, and it tells you the resume page.
`/preview` predates it and is kept for existing clients.

```json
{
  "success": true,
  "message": "Pending corporate data retrieved successfully",
  "timestamp": "2026-08-20T11:13:10.221",
  "data": {
    "email": "david@acme.com",
    "emailVerified": true,
    "firstName": "David",
    "middleName": "Oluwaseun",
    "lastName": "Areegbe",
    "phoneNumber": "2348012345678",
    "gender": "MALE",
    "maskedBvn": "*********01",
    "companyName": "ACME VENTURES LIMITED",
    "companyType": "PRIVATE_COMPANY_LIMITED_BY_SHARES",
    "businessStructure": "PRIVATE_CORPORATION",
    "rcNumber": "RC1234567",
    "cacVerified": true,
    "registrationDate": "2018-04-19T00:00:00Z",
    "memartUrl": "https://myrtle-bucket.s3.eu-west-1.amazonaws.com/1754661234567_memart.pdf",
    "directors": [ { "firstName": "David", "maskedBvn": "*********01", "…": "…" } ],
    "username": "davidareegbe",
    "valuationCurrency": "NGN",
    "profession": "DIRECTOR",
    "nationality": "Nigerian",
    "idType": null,
    "idExpiry": "2030-12-31",
    "dateOfBirth": "1985-03-11",
    "…": "…"
  }
}
```

---

## Write endpoints — the onboarding flow

The onboarding flow at a glance; the request bodies with every field are shown under each page
below and in the Postman collection.

| # | Method | Path | Auth | Body | Success | `data` |
|---|---|---|---|---|---|---|
| 0a | GET | `/corporate/start/{email}` | public | — | 200 | `{ otpReference, email }` |
| 0b | POST | `/corporate/validate-otp` | public | `{ otp, otpReference, email }` | 200 | `{ token, page }` |
| 1 | POST | `/corporate/basic-details` | onboarding | name / phone / gender | 201 | `null` |
| 2 | POST | `/corporate/validate-bvn` | onboarding | `{ bvn }` | 201 | `{ middleName, verified }` |
| 4 | POST | `/corporate/kyb` | onboarding | `{ rcNumber }` | 201 | `null` |
| 5a | POST | `/s3/upload-file` | public | `multipart` field `file` | 200 | the file URL (a string) |
| 5b | POST | `/corporate/memart` | onboarding | `{ url }` | 200 | `null` |
| 6 | POST | `/corporate/directors` | onboarding | **bare array** of directors | 200 | `null` |
| 7 | POST | `/corporate/credentials` | onboarding | credentials + profile | 200 | progress object |
| 8 | POST | `/corporate/complete` | onboarding | — | 201 | `{ accessToken }` |

Three things bite integrators here:

- **`/directors` takes a bare JSON array**, not `{ "directors": [...] }`, and re-sending it
  **replaces** every director already stored.
- **`birthDate` per director is effectively required.** Omit it and that director inherits the
  representative's date of birth, which is wrong for everyone but the representative.
- **Retrying `/complete` is always safe.** Upstream ids are checkpointed as they are created, so a
  retry resumes instead of creating a second MyWealth client.

**Page 1 — request**

```json
{
  "firstName": "David",
  "middleName": "Oluwaseun",
  "lastName": "Areegbe",
  "email": "david@acme.com",
  "phoneNumber": "2348012345678",
  "gender": "MALE"
}
```

**Page 6 — request**

```json
[
  {
    "firstName": "David",
    "middleName": "Oluwaseun",
    "lastName": "Areegbe",
    "email": "david@acme.com",
    "phoneNumber": "2348012345678",
    "bvn": "12345678901",
    "role": "CORP_DIRECTOR",
    "beneficialOwner": true,
    "gender": "MALE",
    "title": "Mr",
    "addressLine1": "22 Admiralty Way",
    "addressLine2": "Lekki Phase 1",
    "city": "Lagos",
    "state": "Lagos",
    "postCode": "100001",
    "country": "NG",
    "birthDate": "1985-03-11",
    "maritalStatus": "SINGLE"
  }
]
```

**Page 7 — request**

```json
{
  "pin": "1234",
  "password": "Password123!",
  "profession": "DIRECTOR",
  "nationality": "Nigerian",
  "idExpDate": "2030-12-31",
  "valuationCurrency": "NGN",
  "businessStructure": "PRIVATE_CORPORATION",
  "username": "davidareegbe",
  "title": "Mr",
  "maritalStatus": "SINGLE",
  "employerName": "ACME VENTURES LIMITED",
  "employerAddress": "22 Admiralty Way, Lekki, Lagos",
  "netWorth": "1000000-5000000",
  "annualIncome": "1000000-5000000",
  "liquidNetWorth": "500000-2000000",
  "sourceOfWealth": "Business Income",
  "investmentExperience": "MODERATE",
  "riskTolerance": "MODERATE",
  "investmentObjective": "GROWTH"
}
```

**Page 8 — response**

```json
{
  "success": true,
  "message": "Corporate account created successfully",
  "timestamp": "2026-08-20T11:20:41.903",
  "data": { "accessToken": "eyJhbGciOiJIUzI1NiJ9..." }
}
```

That `accessToken` is a **session** token — the user is now logged in, and the onboarding token is
spent. The staged record and its credentials are discarded, so the read endpoints above start
returning **404** from this point on.

---

## Adding more logins, and signing in

| Method | Path | Auth | Returns |
|---|---|---|---|
| POST | `/corporate/lookup` | public | `lookupToken` (**15 min**), company summary |
| POST | `/auth/send-otp` | public | `otpReference` for the new user's email |
| POST | `/corporate/create-user` | lookup token **in the body** | session token |
| POST | `/corporate/login` | public | session token + account snapshot |

Maximum **5 logins per company**. The lookup is gated on the RC number plus the BVN registered on
the account, and returns a 404 for any mismatch without saying which half was wrong.

```json
{
  "rcNumber": "RC1234567",
  "bvn": "12345678901"
}
```

```json
{
  "success": true,
  "message": "Corporate account found",
  "timestamp": "2026-08-20T11:30:33.220",
  "data": {
    "companyName": "ACME VENTURES LIMITED",
    "rcNumber": "RC1234567",
    "refCode": "MYR-CORP-8842",
    "maskedEmail": "d***d@acme.com",
    "existingUsers": 1,
    "maxUsers": 5,
    "lookupToken": "eyJhbGciOiJIUzI1NiJ9...",
    "lookupTokenExpiresInSeconds": 900
  }
}
```

Corporate users are tier 3 on creation and **cannot move money out** — withdrawal-account and
withdrawal endpoints return 403 with a support address. Hide those controls rather than letting
them fail.

---

## Frontend implementation guide

### Hydrating the wizard

**On entering the flow (fresh or resumed):**

1. `POST /validate-otp` → store `data.token`, read `data.page`.
2. `GET /submission` → hydrate every step's form state in one call.
3. Route the user to `data.nextPage` (equivalently, `page` from step 1 — they agree).

```js
const { token, page } = (await validateOtp(otp, otpReference, email)).data;
storeOnboardingToken(token);                       // persist it — it is valid for 3 days

const submission = (await api.get('/auth/corporate/submission')).data;
hydrate(submission);                               // fill all six forms
router.go(pageRoute[submission.nextPage]);
```

**On navigating back to a single step**, either read from the state you already hydrated, or
re-fetch just that page:

```js
const { data } = await api.get('/auth/corporate/basic-details');
if (data.collected) form.reset(data);
```

Prefer the single `/submission` call on entry and per-page reads only for an explicit refresh —
six round trips at start-up is a slow first paint for no extra information.

### Re-submitting a page

Every write endpoint is a **full replace** of its page, not a patch. Send the complete page body,
including fields the user did not touch — merge your edits over what the GET returned.

`POST /directors` replaces the entire director set, so the array you send must include the
directors you're keeping, not just the one you added.

### Fields you must not round-trip

| Field | Why | What to do |
|---|---|---|
| `maskedBvn` (record and per-director) | masked, fails the 11-digit check | keep the real value in session state, or re-prompt |
| `pinSet` / `passwordSet` | the values are write-only | ask again if the user wants to change them |

Everything else can be read, edited, and posted back as-is.

### Progress and completion

- Gate the "Submit" button on `progress.outstandingSteps.length === 0`.
- Label the remaining work from `outstandingSteps` (`KYC`, `KYB`, `MEMART`, `DIRECTORS`,
  `CREDENTIALS`) — it is the same list the server enforces at completion.
- After a successful `POST /complete`, **discard the onboarding token** and switch to the returned
  session token. The read endpoints now 404 by design.

### Handling a returning user

The onboarding token is valid for 3 days. Within that window, no OTP is needed — restore the token
and call `GET /submission`. Past it, or on any 401/403 from a read, send the user back to
`GET /corporate/start/{email}`; their staged data is still on the server and the new token will
resolve to the same record.

### Checklist

- [ ] Persist the onboarding token; send it as `Authorization: Bearer` on every corporate call
- [ ] Hydrate with one `GET /submission` on entry; use per-page GETs for explicit refreshes
- [ ] Route off `nextPage` / `page` rather than always starting at page 1
- [ ] Treat every page POST as a full replace — merge before sending
- [ ] Never post a masked BVN back
- [ ] Send directors as a bare array, with `birthDate` on every director
- [ ] Drive progress UI from `outstandingSteps`
- [ ] Offer a retry button on 5xx at `/complete`; never retry a 409
- [ ] Swap to the session token after completion and stop calling the read endpoints
- [ ] Hide withdrawal controls for corporate users

---

## Error handling

| Status | Meaning | Frontend behaviour |
|---|---|---|
| 400 | Validation failure, or a prerequisite step is missing | Show `message` against the field / send them to the named step |
| 401 | Missing or invalid token | Restart from `start/{email}` |
| 403 | No onboarding token, or a blocked corporate action | Re-authenticate, or hide the action |
| 404 | No onboarding in progress, or nothing matches | Restart the flow; don't reveal which field missed |
| 409 | Duplicate, or already submitted | Offer login or support — **never retry** |
| 502 / 503 | MyWealth / QoreID / CAC unavailable | **Retry is safe** — show a retry button |
| 500 | Unexpected | Retry once, then support |

Field-level validation errors (from `@Valid` on the request bodies) put the details under `data`:

```json
{
  "success": false,
  "message": "Validation failed",
  "timestamp": "2026-08-20T11:33:00.001",
  "data": {
    "bvn": "BVN must be 11 characters",
    "phoneNumber": "Phone number must be 10-15 digits (optional + prefix)"
  }
}
```

The read endpoints have a much narrower failure surface than the writes — they never touch an
upstream service, so 403 (no token) and 404 (no staged record) are the only errors you should plan
for.
