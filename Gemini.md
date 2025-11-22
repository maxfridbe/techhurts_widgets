# Gemini Notes

- When refactoring Android package names, remember to check all XML files for hardcoded package names, especially in the `android:configure` attribute of `appwidget-provider` XML files. A misconfiguration here can prevent the widget configuration activity from launching, causing the widget to fail to install correctly.

## Debugging

To debug the application, you can use the `debug.sh` script to capture logcat output for 30 seconds.

```bash
./debug.sh
```

This will print the logs to the console. You can then analyze the logs to identify the cause of the issue.