package com.example.helloworldwidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.SharedPreferences; // Added import
import android.util.Log;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Implementation of App Widget functionality.
 */
public class HelloWorldWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "PregnancyWidget"; // Tag for logging
    private static final int TOTAL_PREGNANCY_DAYS = 280; // Standard 40 weeks

    // Called when the widget is updated (e.g., on placement, or at updatePeriodMillis interval, or by config activity)
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            // Load the due date from SharedPreferences for this widget instance
            String dueDateString = WidgetConfigureActivity.loadDueDatePref(context, appWidgetId);

            // If date exists, update the widget, otherwise show "Configure" text
            if (dueDateString != null) {
                updateAppWidget(context, appWidgetManager, appWidgetId, dueDateString);
            } else {
                 // If no date saved (e.g., config was cancelled or error), show default text
                 RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.helloworld_widget_layout);
                 views.setTextViewText(R.id.widget_textview, "Configure Widget");
                 appWidgetManager.updateAppWidget(appWidgetId, views);
            }
        }
    }

/**
     * Helper method to update a single widget instance. Made public static so the
     * configuration activity can call it.
     *
     * @param context The application context.
     * @param appWidgetManager The AppWidgetManager.
     * @param appWidgetId The ID of the widget instance to update.
     * @param dueDateString The due date string (YYYY-MM-DD) from preferences or config. Can be null.
     */
    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId, String dueDateString) {

        // Fallback: If called without a date string (less likely now), try loading from prefs
         if (dueDateString == null) {
            dueDateString = WidgetConfigureActivity.loadDueDatePref(context, appWidgetId);
         }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.helloworld_widget_layout);
        String widgetText = "Configure Widget"; // Default text if date is still missing

        if (dueDateString != null) { // Proceed only if we have a valid date string
            try {
                // --- Calculation ---
                LocalDate dueDate = LocalDate.parse(dueDateString); // Parse the due date string
                LocalDate currentDate = LocalDate.now();

                // Calculate days remaining until the due date
                long daysUntilDue = ChronoUnit.DAYS.between(currentDate, dueDate);

                // Calculate approximate days elapsed since estimated conception
                long daysElapsed = TOTAL_PREGNANCY_DAYS - daysUntilDue;

                // --- Set Display Text ---
                // Calculate full weeks and remaining days (works for positive/negative daysElapsed)
                long weeksElapsed = daysElapsed / 7;
                long remainingDays = daysElapsed % 7;

                // Format the text string - always show weeks and days
                widgetText = weeksElapsed + " weeks " + remainingDays + " days";

                Log.d(TAG, "Due Date: " + dueDate + ", Current: " + currentDate + ", Days Elapsed: " + daysElapsed + ", Text: " + widgetText);


            } catch (DateTimeParseException e) {
                widgetText = "Invalid date format"; // Handle error if date string is wrong
                Log.e(TAG, "Error parsing due date: " + dueDateString, e); // Log the error
            } catch (Exception e) {
                widgetText = "Error calculating"; // Generic error
                 Log.e(TAG, "Error updating widget: ", e); // Log the error
            }
        } // End if (dueDateString != null)

        // Update the TextView
        views.setTextViewText(R.id.widget_textview, widgetText);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    // Called when the widget is first placed (after configuration if applicable)
    @Override
    public void onEnabled(Context context) {
        // Enter relevant functionality for when the first widget is created
        // No specific action needed here for now
    }

    // Called when the last instance of the widget is removed
    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
        // No specific action needed here for now
    }

     // Called when a widget instance is deleted
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // When the user deletes the widget, delete the preference associated with it.
        Log.d(TAG, "Deleting preferences for widget IDs: " + java.util.Arrays.toString(appWidgetIds));
        for (int appWidgetId : appWidgetIds) {
            WidgetConfigureActivity.deleteDueDatePref(context, appWidgetId);
        }
    }
}
