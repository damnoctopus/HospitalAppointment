# HospitalAppointment

Firebase integration is available while retaining the mock login. See [FIREBASE_SETUP.md](FIREBASE_SETUP.md) to connect your project, enable Anonymous Authentication, and publish the shared demo rules. Until `app/google-services.json` is added, the app runs locally. With Firebase configured, doctors, availability, and appointments use Firestore transactions and a local viewing cache. Run the existing instrumentation tests in local mode; live two-device checks are described in the setup guide.

A native Android college project built with **Java activities and XML layouts**. Package: `com.example.hospitalappointment`. Minimum SDK: **24**. The original Gradle configuration and Material Components dependencies are preserved.

## Run

Open this folder in Android Studio, allow Gradle sync, select an emulator or Android device (API 24+), and run `app`.

The app always opens on a single **mock login** on a fresh launch. Screen rotation preserves the current screen.

| Role | Username | Password |
| --- | --- | --- |
| Admin | admin | admin123 |
| Patient example | patient | Any non-empty password |

Any other non-empty username opens the patient interface with any non-empty password. `admin` is reserved and requires `admin123`. There is no role selector or registration step. The two demo-fill buttons are optional shortcuts. Reuse the same username to see the same local appointments. Log out to change users during a session.

## Demo walkthrough

1. Sign in as Patient. Browse specialties, search doctors, and open a doctor profile.
2. Tap **Book appointment**. The next free date is selected automatically. Swipe through the available-date chips or use the calendar to select a date within the next 30 days. Choose a time, review the booking summary, then confirm the request. Past times, reserved slots, and times that conflict with your other appointments are excluded. Dates without any free times show an empty state and a shortcut to the next free date.
3. The visit appears under **Appointments → Upcoming** with a **Pending** status.
4. Log out, sign in as Admin, and open **Appointments → Pending**. Confirm the visit.
5. Sign back in as Patient to see the same visit as **Confirmed**. Cancellation releases the reserved slot.
6. Admin can mark a confirmed visit **Completed** after its scheduled time. Past pending/confirmed visits appear in the patient's **Past** filter until resolved.
7. Admin can add, edit, or remove doctors. Removing a doctor cancels active bookings and preserves appointment history.
8. Under **Availability**, choose a doctor and date. Tap an unreserved slot to remove it, or add a time on the hour or half hour. Reserved slots require cancellation first.

## Source guide

- `MainActivity.java`: screen navigation, form validation, dialogs, and view binding.
- `LocalStore.java`: doctor and appointment models, local accounts, JSON persistence, slot reservation checks, and date helpers.
- `res/layout/screen_*.xml`: patient and admin screen layouts.
- `res/layout/item_*.xml`: reusable doctor cards, appointment cards, chips, statistics, and empty states.
- `res/menu/`: role-specific bottom navigation.
- `res/values/`: the shared light Material theme, teal palette, and XML string resources.
- `res/drawable/`: vector illustrations, icons, and rounded backgrounds.

Data is stored in this app's private SharedPreferences as small JSON collections. Four sample doctors and daily half-hour consultation slots are supplied. Both roles share the same device data. Doctor names and specialties are copied into appointments to preserve booking history when doctors are edited or removed.

This is a **local academic demo**, not production authentication or a medical records system. The login only routes to a role: patient passwords are neither checked nor saved. There is no server authentication, password recovery, cloud sync, or production access control. Older registered local accounts remain in storage for compatibility. Uninstalling the app or clearing its storage removes local data.

## Verification

```bat
gradlew.bat :app:assembleDebug
gradlew.bat :app:lintDebug :app:testDebugUnitTest
gradlew.bat :app:connectedDebugAndroidTest
```

The last command requires a running emulator or device. `HospitalFlowTest` verifies role separation, registration, persisted booking status, duplicate reservations, availability changes, doctor removal, and patient/admin screen inflation. Store tests use the test APK's separate preferences rather than clearing patient data. `PatientBookingTest` completes a real patient booking, admin confirmation, and patient cancellation through the UI. It creates and removes a temporary doctor and its visits and restores the previous login session.

Before submission, manually check the complete walkthrough, system Back navigation, keyboard behavior, rotation, large font sizes, and a tablet layout. Repeat on API 24; a successful build and a recent emulator run do not replace testing the minimum supported Android version.
