Place your embedded server payload here:

- Required file name: server.dex
- Required location: app/src/main/assets/server.dex

At runtime, the app copies this file to:
/data/user/0/<applicationId>/files/embedded/server.dex

Then users can run the generated adb command from the app to activate
the embedded shell bridge process.

Gradle helper commands:

1) Sync from external file into assets/server.dex
   ./gradlew :app:syncEmbeddedServerDex -PembeddedServerDexPath=/absolute/path/server.dex

2) Verify assets/server.dex exists
   ./gradlew :app:verifyEmbeddedServerDex

3) One-shot prepare
   ./gradlew :app:prepareEmbeddedServerDex -PembeddedServerDexPath=/absolute/path/server.dex
