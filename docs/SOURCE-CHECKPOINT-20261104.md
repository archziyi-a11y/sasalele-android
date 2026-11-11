# SASALELE source checkpoint 2026-11-04

This is the current source checkpoint, not a retroactive v37 release tag.

Validation: Android testDebugUnitTest passed; backend experiment, knowledge, materials, generation, prompts and guidance tests: 56 passed. These are local tests, not a new production acceptance run. Backend tests used an isolated basetemp after default temporary directory setup failed.

Android versionName is 1.12-knowledge-trial. The current Gradle default versionCode is 36; v37 requires -PsalesVersionCode=37. Signing properties and service URL remain external build inputs. This checkpoint does not rebuild or redeploy APKs.

Previously delivered APK: SASALELE-1.12-knowledge-trial-v37.apk
SHA256: 40aaddc991ccb4fc20fe9ba7798747fb19152bd0d336d16fa7e47708508e56a7
Previously delivered backend bundle: sasalele-knowledge-trial-20261103.tar.gz
SHA256: efdceb5f5f749ddd56203da15a2f6c6e7269f35b60df634d00483a9c8eb6a062

These identify historical artifacts only; exact source-to-binary reproducibility has not been demonstrated. No production switch changed, no remote git push performed.

Excluded: binaries, recordings and transcripts, private keys/configuration, real-device screenshots and logs, unrelated projects, and older unreviewed documentation. Remaining dirty files are intentionally retained. Older milestones must not be tagged as if recreated by this checkpoint.
