# CareLink

An Android app connecting patients with their caregivers around home visits,
symptom tracking and prescriptions.

Patients log symptoms and medications and see their upcoming visits; caregivers
schedule visits, write up visit reports, and share medical records. Both sides
communicate through built-in one-to-one chat with push notifications.

## Features

- **Accounts & roles.** Email/password and Google sign-in, plus biometric
  unlock. A single user model distinguishes patients from caregivers, each with
  their own profile fields.
- **Visits.** A shared calendar of appointments, daily visit lists, and visit
  reports that can schedule the next follow-up in the same form.
- **Symptom tracking.** Patients log severity over time, charted with
  MPAndroidChart.
- **Prescriptions.** Medication lists exportable to PDF via PDFBox.
- **Medical records.** File upload and sharing through Firebase Storage, with
  per-user unread counts.
- **Reminders.** Medication and appointment reminders delivered by
  `AlarmManager` and exact-alarm scheduling.
- **Chat.** Realtime one-to-one messaging on Firestore, with FCM push
  notifications.
- **Heart-rate streaming.** Live data from Polar BLE chest straps and watches
  via the Polar BLE SDK.

## Tech stack

| Area | Choice |
|---|---|
| Language | Java |
| Min / target SDK | 30 / 36 |
| Backend | Firebase Auth, Firestore, Storage, Cloud Messaging |
| UI | Android Views (XML layouts), Material Components |
| Charts | MPAndroidChart |
| PDF | PDFBox for Android |
| Images | Glide, ImagePicker |
| BLE | Polar BLE SDK, RxJava 3 |
| Calendar | Applandeo material-calendar-view |

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
application id `com.example.carelink`. Enable Authentication (Email/Password
and Google), Firestore, Storage and Cloud Messaging.

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

Select a device running API 30+ and run the `app` configuration. Heart-rate
streaming needs a physical device with Bluetooth and a Polar strap; everything
else works on an emulator.

## Permissions

`POST_NOTIFICATIONS` for push, `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` plus
location for BLE device discovery (an Android requirement for scanning),
`SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` for reminders, and
`FOREGROUND_SERVICE_CONNECTED_DEVICE` to keep sensor streaming alive.

## Status

Actively evolving. Known rough edges:

- Notification sending should move server-side (see the warning above).
- `isMinifyEnabled` is off for release builds; no signing config is committed.
- Test coverage is minimal.
