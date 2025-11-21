# Gemini Notes

- When refactoring Android package names, remember to check all XML files for hardcoded package names, especially in the `android:configure` attribute of `appwidget-provider` XML files. A misconfiguration here can prevent the widget configuration activity from launching, causing the widget to fail to install correctly.