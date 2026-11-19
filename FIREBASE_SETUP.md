# Firebase setup

The app keeps its mock login: `admin / admin123` opens admin; any other username and a non-empty password opens a patient account. Firebase uses anonymous sign-in behind the scenes. Patient passwords, local account hashes, and salts are not uploaded.

## Connect your project

1. Create a separate college demo project in the [Firebase console](https://console.firebase.google.com/).
2. Register an Android app with package **com.example.hospitalappointment**.
3. Download its **google-services.json** and place it in **app/google-services.json**. Use the real configuration; no example credentials are included.
4. In **Authentication → Sign-in method**, enable **Anonymous**.
5. Create a **Cloud Firestore** database (the default database, Standard edition). Choose its location and use production rules initially.
6. In Firestore's **Rules** tab, paste the contents of **firestore.rules** and click **Publish**. Alternatively, with Firebase CLI installed, run `firebase deploy --only firestore:rules --project YOUR_PROJECT_ID`.
7. Sync Gradle, rebuild, and run the app with internet access. The connection banner should become **Cloud connected · Shared demo**.

Without the configuration file, the app builds and runs in local mode. With Firebase configured, writes require a successful cloud connection; failed requests never show a booking-success dialog. The banner offers reconnection after errors. Cloud transactions fail offline; the last received records remain available for viewing.

## Data and migration

All configured devices use **hospitalDemos/collegeDemo** in the same Firebase project. The first device seeds this document from its existing local doctors, appointments, and availability. Later devices use the existing cloud document; their old local bookings are not automatically merged or uploaded.

For this small assignment, the document holds three JSON string fields (`doctors`, `appointments`, `availability`) and `schemaVersion: 1`. This directly reuses the existing Java models and keeps atomic updates straightforward. It is intended for the supplied small dataset, not an unbounded hospital database. Rules limit payload sizes below the Firestore document limit.

Every update reads the latest cloud document in a transaction, then runs the existing validation. Competing bookings for a doctor/time or overlapping bookings for the same mock patient cannot both succeed through the app. Removing doctors and changing availability use the same transaction path. Live listeners refresh the local SharedPreferences cache. Local mock profile names remain on each device; each appointment includes the patient name and normalized username key.

This is deliberately a **shared demo dataset**. Anonymous connections may read and edit this one document. The hardcoded admin password and unverified usernames cannot enforce trustworthy database roles or patient privacy. Use fictional data only. Real records would require real authentication and server-enforced roles. The rules deny other document paths and document deletion.

## Verify with two devices

1. Install builds containing the same real configuration on two emulators/devices.
2. On device A, change a doctor's available slots as admin. Open booking on device B and verify those changes.
3. Request the same doctor/date/time on both devices. Only one request should succeed; the other should show a conflict.
4. Confirm on A and check the patient's appointment status on B. Cancel on B and check that the slot becomes available on A.
5. Disable network access and try a write. It must show an error rather than a saved appointment. Reconnect using the banner.
6. Force-stop and reopen: the mock login must still appear, and the same patient username must show the cloud bookings after connecting.

Live verification requires your Firebase configuration and enabled services. No Firebase project or rules are deployed by merely building this repository.

References: [Android setup](https://firebase.google.com/docs/android/setup), [anonymous connections](https://firebase.google.com/docs/auth/android/anonymous-auth), [transactions](https://firebase.google.com/docs/firestore/manage-data/transactions).
