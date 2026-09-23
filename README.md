# CareLink

An Android app connecting patients with their caregivers around home visits,
symptom tracking and prescriptions.

Patients log symptoms and medications and see their upcoming visits; caregivers
schedule visits, write up visit reports, and share medical records. Both sides
communicate through built-in one-to-one chat with push notifications.

## Features

- **Accounts & roles.** Email/password sign-up and sign-in via Firebase Auth,
  with an optional biometric unlock gate on the login screen. A single user
  model distinguishes patients from caregivers, each with their own profile
  fields.
- **Visits.** A shared calendar of appointments, daily visit lists, and visit
  reports that can schedule the next follow-up in the same form.
- **Symptom tracking.** Patients log symptoms with a severity rating and
  onset date, listed newest first. Caregivers opening a patient from chat see
  the same list read-only.
- **Prescriptions.** Medication lists exportable to PDF via PDFBox.
- **Medical records.** A single tabbed screen gathering a patient's visit
  reports, symptoms and prescriptions. When a caregiver opens it from a chat,
  the prescriptions tab is hidden. Unread counts are tracked per user so each
  side sees its own badges.
- **Profile pictures and chat media.** Images picked with ImagePicker, stored in
  Firebase Storage and loaded with Glide.
- **Reminders.** Medication and appointment reminders delivered by
  `AlarmManager` and exact-alarm scheduling.
- **Chat.** Realtime one-to-one messaging on Firestore, with FCM push
  notifications.

## Tech stack

| Area | Choice |
|---|---|
| Language | Java |
| Min / target SDK | 30 / 36 |
| Backend | Firebase Auth, Firestore, Storage, Cloud Messaging |
| UI | Android Views (XML layouts), Material Components |
| PDF | PDFBox for Android |
| Images | Glide, ImagePicker |
| Lists | FirebaseUI (FirestoreRecyclerAdapter) |
| Calendar | Applandeo material-calendar-view |
| Auth | Firebase Auth (email/password), AndroidX Biometric |

## Project layout

```
app/src/main/java/com/example/carelink/
├── *Activity.java      screens (auth, visits, symptoms, chat, settings)
├── adapter/            RecyclerView adapters, mostly FirestoreRecyclerAdapter
├── fragments/          the main tabs: home, chat, symptoms, prescriptions,
│                       visit reports, profile
├── model/              Firestore POJOs
└── util/               Firebase entry points and shared UI/text helpers
```

## Getting started

### Prerequisites

- Android Studio (Ladybug or newer)
- JDK 11
- Android SDK 36
- A Firebase project

### 1. Clone and open

```bash
git clone https://github.com/<your-username>/CareLink.git
```

Open the folder in Android Studio and let Gradle sync. `local.properties` is
generated automatically and is intentionally not tracked.

### 2. Firebase setup

This project needs two Firebase files, **neither of which is in the
repository**:

**`app/google-services.json`**: download it from the Firebase console
(Project settings → Your apps → Android) after registering an app with the
application id `com.example.carelink`. Enable Authentication (Email/Password),
Firestore, Storage and Cloud Messaging.

**A service-account key in `app/src/main/assets/`**: generate one under
Project settings → Service accounts → *Generate new private key*, then:

1. Save the JSON into `app/src/main/assets/`.
2. Update the filename passed to `assetManager.open(...)` in
   `ChatActivity.sendNotification` and `CreateVisitReportActivity` to match.
3. Update the project id in the FCM endpoint in those same two files.

See `app/src/main/assets/carelink-firebase-adminsdk.json.template` for the
expected shape.

> [!WARNING]
> **The service-account approach in this codebase is not production-safe.**
> Files in `assets/` are packaged into the APK and can be extracted from any
> installed build, so an admin key shipped this way is effectively public. It
> grants full administrative access to the Firebase project.
>
> The correct design is to move notification sending into a Firebase Cloud
> Function and have the client call that function, so no admin credential ever
> reaches the device. The current code is kept as-is for transparency; treat any
> key you generate for it as disposable and restrict it accordingly.

### 3. Run

Select a device running API 30+ and run the `app` configuration. An emulator
is fine; biometric unlock needs either a physical device or an emulator with a
fingerprint enrolled.

## Permissions

The app declares three permissions, all of them used:

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | chat and reminder notifications |
| `SCHEDULE_EXACT_ALARM` | reminder alarms below API 33 |
| `USE_EXACT_ALARM` | reminder alarms on API 33 and above |

Dependencies contribute a few more to the merged manifest, notably `INTERNET`,
`ACCESS_NETWORK_STATE` and `WAKE_LOCK` from Firebase Cloud Messaging and
`USE_BIOMETRIC` from AndroidX Biometric.

## Status

Actively evolving. Known rough edges:

- Notification sending should move server-side (see the warning above).
- `isMinifyEnabled` is off for release builds, and no signing config is
  committed, so release builds come out unsigned.
- There are no unit or instrumentation tests yet; `app/src/test` and
  `app/src/androidTest` are empty.

Two earlier features were scoped but never built, and their dependencies have
now been dropped from `app/build.gradle.kts`:

- **BLE heart-rate sensors.** The Polar BLE SDK and MPAndroidChart are gone
  from the build, along with the Bluetooth, location and foreground-service
  permissions they needed. RxJava is still in the APK, but only because
  FirebaseUI pulls it in transitively.
- **Google sign-in.** Credential Manager and the Google identity library are no
  longer declared directly. They remain in the APK regardless, since
  `firebase-auth` depends on them.
