# Shayan Phone 2.3.0 diagnostics

- Open the gear icon in the top app bar to view the local diagnostic log.
- The log is stored only in the app's private SharedPreferences.
- The log keeps the latest 100 entries.
- It records timestamps, component/event names, exception class names, and a short stack trace.
- It intentionally excludes exception messages, phone numbers, contact names, and call contents.
- The user can copy the report manually or clear it from the diagnostics dialog.
- Uncaught process crashes are recorded by the Application-level handler before the platform crash handler is called.
- No diagnostic data is uploaded or transmitted automatically.
