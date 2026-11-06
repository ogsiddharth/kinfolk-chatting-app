# Kinfolk - Private Family Messenger

Kinfolk is a private, lightweight, distraction-free family messaging application built natively for Android using Jetpack Compose and Firebase. Designed exclusively for trusted family circles—initially connecting the owner and their sister—it eliminates phone numbers, public directories, and unwanted noise while delivering real-time chat, rich media, native screenshot privacy controls, an owner administration dashboard, and an integrated AI Help Assistant.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following key decisions were confirmed during the planning phase:
> - **Platform & Framework**: Native Android application using modern Kotlin and Jetpack Compose (Material 3).
> - **Cloud Backend**: Firebase Firestore for real-time messaging, invitation token management, and account profiles; Firebase Storage for media attachments.
> - **Brand Identity**: **Kinfolk - Private Family Messenger**.
> - **Initial Onboarding**: Guided owner onboarding flow with an instant invitation code generated specifically for the sister.

- **Confirmed Decision 1 (Auth Model)**: Invitation-only registration. There is no open sign-up or phone number requirement. The owner creates the family circle, and new members can only join with an admin-generated invitation code.
- **Confirmed Decision 2 (Privacy & Screenshot Policy)**: Full privacy controls including a Native Screenshot Detection notification (using Android 14+ `ScreenCaptureCallback`) and an optional Strict Privacy mode (using `FLAG_SECURE` to prevent capture entirely).
- **Confirmed Decision 3 (AI Assistant Boundary)**: The integrated AI Help Assistant is strictly a local app guide (explaining settings, privacy, and media handling) powered by Firebase AI Logic. It has zero access to private family chat conversations unless the user explicitly asks a question.

---

## 1. Overview & Core Concept

### What It Does
Kinfolk provides a sanctuary for close family communication. It pairs the speed and reliability of modern mobile messaging with zero-trust privacy:
- **No Phone Numbers or Social Graphs**: Accounts use unique usernames, passwords, and single-use invitation codes.
- **Real-Time 1-on-1 & Family Chat**: Firestore real-time listeners power instant message delivery, typing indicators, read receipts, emoji reactions, message replies, message edits, pinned messages, and conversation search.
- **Media & Voice Sharing**: Inline image previews, voice recording/playback, and document sharing with validation.
- **Owner Administration**: The owner has a dedicated dashboard to generate/revoke invite codes, review active member accounts, manage attachment size policies, and review audit logs.
- **AI Help Assistant**: A floating, friendly guide powered by Gemini via Firebase AI Logic to assist non-technical family members with app usage.

### Target Audience & Persona
- **Primary Users**: The family owner and their sister, with the ability to invite select additional family members later.
- **Context**: A safe, clutter-free channel free from commercial algorithms, spam, and unsolicited contacts.

---

## 2. User Experience & Visual Design

### Visual Identity & Theme
- **Aesthetic Direction**: Warm Editorial & Organic Minimalism. Soft, reassuring tones that evoke comfort, trust, and intimacy.
- **Color Palette**:
  - *Light Mode*: Warm Sand/Alabaster base (`#FBF9F5`), Deep Forest Slate (`#1E2E28`) for primary text and dominant accents, Sage Green (`#4D6A5D`) for sent bubbles and active states, Muted Linen (`#EFECE6`) for received bubbles.
  - *Dark Mode*: Deep OLED Charcoal (`#121614`), Muted Pine (`#2A4237`) for accents, Soft Sage (`#8EAA9C`) for highlights, Slate Surface (`#1C2320`) for cards.
- **Typography**: Clean humanist typography with comfortable line heights, legible metadata timestamps, and distinct status badges.
- **Component Styling**:
  - Smooth rounded corners (16dp–24dp) for chat bubbles.
  - Subtle micro-elevation (1dp–3dp) for cards and action sheets.
  - Minimum touch targets of 48dp on all interactive elements.
  - Animated message state transitions (sending spinner -> single check -> double check).

### Key User Flows

#### 1. Onboarding & Invitation Flow
```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│ Welcome Screen  │ ───►  │  Enter Invite   │ ───►  │ Create Profile  │
│ (Login or Join) │       │   Code / Token  │       │ (User & Pass)   │
└─────────────────┘       └─────────────────┘       └─────────────────┘
        │
        ▼ (First Run Only)
┌─────────────────┐       ┌─────────────────┐
│ Owner Bootstrap │ ───►  │ Sister Invite   │
│ & Recovery Code │       │ Code Displayed  │
└─────────────────┘       └─────────────────┘
```

#### 2. Chat Experience Flow
- **Active Chat View**:
  - Top app bar displays member name, avatar, and live online/typing indicator.
  - Pinned message banner if any message is pinned.
  - Scrollable message stream with date dividers, reply quote bubbles, emoji reaction bars, and read status ticks.
  - Bottom composer with attachment picker (photo, document, voice memo), text field, and dynamic send/mic button.
- **Long-Press Action Sheet**:
  - Reply, React (👍, ❤️, 😂, 😮, 😢, 🙏), Copy, Edit (own messages), Pin, and Delete.
- **Search & Filter**:
  - Search icon in top bar expands inline search to query messages within the conversation.

#### 3. Privacy & Screenshot Settings
- **Live Screenshot Notification**: When a party takes a screenshot during an open chat, a system message (`"⚠️ [Name] took a screenshot"`) is logged in the conversation stream.
- **Screen Protection Mode**: Optional toggle in Privacy Settings to apply `FLAG_SECURE` to the window, preventing screenshots and screen recordings completely.

#### 4. Owner Dashboard
- Metric cards: Total members, active sessions, storage used.
- Invitation Manager: Generate new invite code with custom expiration, view active codes, revoke codes.
- Member Management: View accounts, lock/unlock, or revoke sessions.
- Security Audit Log: History of invitations generated, joined members, and security events.

---

## 3. Key Product Decisions & Trade-Offs

| Decision | Chosen Approach | Rationale & Trade-Offs |
| :--- | :--- | :--- |
| **Authentication** | Username + Password + Single-Use Invitation Code | Eliminates phone numbers and public sign-ups while keeping registration simple and secure. Recovery code is saved on device for account reset. |
| **Database Architecture** | Firestore with Real-Time Listeners (`snapshots()`) | Instant message propagation, robust offline caching, and sub-second delivery without complex WebSocket servers. |
| **Screenshot Handling** | Dual Approach: Android 14+ `ScreenCaptureCallback` + Optional `FLAG_SECURE` | Android 14+ provides native OS screenshot events. For users wanting 100% prevention, `FLAG_SECURE` can be enabled in settings. |
| **Media Attachments** | Secure Firebase Storage with MIME Validation | Images compressed locally before upload, audio voice memos recorded with Android `MediaRecorder`, documents validated for safe extensions. |
| **AI Help Assistant** | Gemini 3.8 Flash via Firebase AI Logic | Provides instant, context-aware help regarding settings and features without passing private family conversations to the AI. |

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Kinfolk Android App                           │
│                                                                        │
│  ┌─────────────────────────┐         ┌──────────────────────────────┐  │
│  │     Jetpack Compose     │         │     Navigation & Screens     │  │
│  │   UI Layer (M3 Theme)   │ ◄─────► │ (Auth, Chat, Admin, Profile) │  │
│  └────────────┬────────────┘         └──────────────┬───────────────┘  │
│               │                                     │                  │
│               ▼                                     ▼                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                       ViewModels & UI State                      │  │
│  │        (AuthViewModel, ChatViewModel, AdminViewModel, etc.)      │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
│                                     │                                  │
│               ┌─────────────────────┴─────────────────────┐            │
│               ▼                                           ▼            │
│  ┌─────────────────────────┐               ┌────────────────────────┐  │
│  │  Kinfolk Repository     │               │  Firebase AI Logic     │  │
│  │  (Auth, Messages, Admin)│               │  (Gemini 3.8 Flash)    │  │
│  └────────────┬────────────┘               └────────────┬───────────┘  │
└───────────────┼─────────────────────────────────────────┼──────────────┘
                │                                         │
                ▼                                         ▼
┌─────────────────────────────────┐       ┌──────────────────────────────┐
│       Firebase Firestore        │       │       Firebase Storage       │
│  (Users, Invites, Messages)     │       │  (Encrypted Media & Voice)   │
└─────────────────────────────────┘       └──────────────────────────────┘
```

### Firestore Data Model

1. **`users/{userId}`**:
   - `username: String` (unique)
   - `displayName: String`
   - `role: "owner" | "member"`
   - `passwordHash: String` (salted PBKDF2/SHA-256 hash)
   - `recoveryCodeHash: String`
   - `avatarUrl: String?`
   - `isOnline: Boolean`
   - `lastSeen: Timestamp?`
   - `privacySettings: Map<String, Boolean>` (showLastSeen, showReadReceipts, showTyping, blockScreenshots)
   - `createdAt: Timestamp`

2. **`invitations/{inviteCode}`**:
   - `code: String` (e.g. "KIN-7842")
   - `createdBy: String` (userId)
   - `status: "pending" | "used" | "revoked"`
   - `intendedRole: "member"`
   - `usedBy: String?`
   - `usedAt: Timestamp?`
   - `expiresAt: Timestamp`
   - `createdAt: Timestamp`

3. **`conversations/{conversationId}`**:
   - `participants: List<String>` (user IDs)
   - `lastMessage: Map<String, Any>` (text, timestamp, senderId)
   - `pinnedMessageId: String?`
   - `updatedAt: Timestamp`

4. **`conversations/{conversationId}/messages/{messageId}`**:
   - `senderId: String`
   - `senderName: String`
   - `content: String`
   - `type: "text" | "image" | "audio" | "document" | "system_screenshot"`
   - `mediaUrl: String?`
   - `replyTo: Map<String, String>?` (messageId, senderName, snippet)
   - `reactions: Map<String, List<String>>` (emoji -> list of userIds)
   - `readBy: List<String>`
   - `isEdited: Boolean`
   - `createdAt: Timestamp`
   - `updatedAt: Timestamp`

5. **`audit_logs/{logId}`**:
   - `action: String` ("INVITE_CREATED", "USER_REGISTERED", "SESSION_REVOKED", "ACCOUNT_LOCKED")
   - `performedBy: String`
   - `targetUser: String?`
   - `details: String`
   - `timestamp: Timestamp`

---

## 5. Development Milestones & Phased Execution

- **Milestone 1: Project Foundation & Security Configuration**
  - Enable Firestore and Firebase dependencies in `build.gradle.kts`.
  - Configure Material 3 Theme (Kinfolk Warm Palette) in `Theme.kt` and `Color.kt`.
  - Establish `firebase-blueprint.json` and zero-trust `firestore.rules`.
- **Milestone 2: Invitation & Authentication System**
  - Implement cryptographic password hashing and recovery key generation.
  - Build Owner Bootstrap onboarding flow (initial launch generates sister's invite code).
  - Build Member Registration flow with invitation code redemption.
  - Implement Session Manager with active session tracking and revocation.
- **Milestone 3: Core Messaging & Real-Time Engine**
  - Implement real-time conversation repository with Firestore snapshot flows.
  - Build the main Chat screen: conversation stream, message bubbles, replies, reactions, editing, pinning, and message deletion.
  - Add typing indicator and live online/last-seen status.
  - Implement chat search and message jump.
- **Milestone 4: Media Sharing & Voice Memos**
  - Implement image picker with client-side compression and inline preview.
  - Implement audio voice memo recorder (`MediaRecorder`) and playback controller (`MediaPlayer`).
  - Document file picker with file size caps (max 10MB) and extension safety checks.
- **Milestone 5: Privacy Controls & Native Screenshot Detection**
  - Implement Android 14+ `ScreenCaptureCallback` to log screenshot events in the conversation.
  - Implement Privacy Settings toggle for `FLAG_SECURE` (complete capture prevention).
  - Add visibility toggles for Last Seen, Online Status, and Read Receipts.
- **Milestone 6: Owner Administration Dashboard**
  - Build protected Admin Dashboard accessible only by the owner account.
  - Invitation management: generate, inspect, and revoke invitation codes.
  - Member management: view registered members, lock/unlock accounts.
  - System activity and security audit log viewer.
- **Milestone 7: Integrated AI Help Assistant**
  - Configure Firebase AI Logic with Gemini 3.8 Flash.
  - Floating Help icon opening a clean assistance bottom sheet.
  - Pre-defined quick question pills ("How do I invite someone?", "How does screenshot protection work?", "How do I share voice notes?").
  - Clear data privacy guarantee: AI assistant runs on-demand and does not read family chat messages.
- **Milestone 8: Verification & Compilation**
  - Run `compile_applet` and verify end-to-end functionality.
