# SASALELE Android

Android sales-assistance client with a floating workspace, title-region OCR,
customer identity selection, profile views, reply drafting, polishing, sales
guidance, follow-ups and shared materials. Built with Kotlin and Jetpack Compose.

## Build and test

Install the Android SDK required by `app/build.gradle.kts` and configure
`ANDROID_HOME` (or an untracked `local.properties`). The Gradle wrapper and daemon
JVM criteria are included. Then run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

The application does not include a production endpoint or device credential.
Supply the backend address with `-PsalesServiceUrl=https://your-service-host`.
Production builds also require `-PsalesSigningProperties=<external-file>` and
an appropriate `-PsalesVersionCode=<integer>`. Keep signing material outside Git.
The backend is a separate service and is not included in this repository.

Instrumentation tests use synthetic local services and an emulator; they are not
production acceptance tests. See each test's fixture endpoint before running it.

## Source and evidence

This is a source-only publication snapshot, not an exact reconstruction of a
historically delivered APK. The historical checkpoint is documented in
`docs/SOURCE-CHECKPOINT-20261104.md`. Historical test results in documents describe
their original scope, not a fresh verification of this export.

Included: application source, build definitions, synthetic tests, application
icons and reviewed technical notes. Excluded: credentials, signing keys, APKs,
customer profiles, real-device screenshots, logs, business materials and private
deployment configuration. Original development history and excluded material are
retained locally, not embedded in this publication history.
