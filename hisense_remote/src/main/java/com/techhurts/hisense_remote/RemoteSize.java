package com.techhurts.hisense_remote;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;

/**
 * The widget comes in several shapes, each its own provider so they all appear
 * in the launcher's widget list. The grid dimensions follow the shape.
 */
final class RemoteSize {

    final int columns;
    final int rows;
    final int layout;

    private RemoteSize(int columns, int rows, int layout) {
        this.columns = columns;
        this.rows = rows;
        this.layout = layout;
    }

    int cells() {
        return columns * rows;
    }

    // Buttons, not launcher tiles. A widget's footprint fits more of them than
    // its name suggests: a 2x2 is about 167x213dp, and a 48dp button on a 52dp
    // pitch means three across and three down with room left over. Measured,
    // not guessed — see the comment at the top of each layout.
    static final RemoteSize FLEX = new RemoteSize(4, 5, R.layout.hisense_remote_widget_layout);
    static final RemoteSize ONE_BY_ONE = new RemoteSize(1, 1, R.layout.remote_grid_1x1);
    static final RemoteSize TWO_BY_ONE = new RemoteSize(3, 1, R.layout.remote_grid_2x1);
    static final RemoteSize TWO_BY_TWO = new RemoteSize(3, 3, R.layout.remote_grid_2x2);
    static final RemoteSize THREE_BY_ONE = new RemoteSize(4, 1, R.layout.remote_grid_3x1);
    static final RemoteSize THREE_BY_THREE = new RemoteSize(4, 4, R.layout.remote_grid_3x3);

    /** Which shape a placed widget is, taken from the provider that owns it. */
    static RemoteSize of(Context context, int appWidgetId) {
        AppWidgetProviderInfo info =
                AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId);
        String className = info != null && info.provider != null
                ? info.provider.getClassName() : "";
        return forClassName(className);
    }

    static RemoteSize forClassName(String className) {
        if (className.endsWith("Remote1x1Provider")) return ONE_BY_ONE;
        if (className.endsWith("Remote2x1Provider")) return TWO_BY_ONE;
        if (className.endsWith("Remote2x2Provider")) return TWO_BY_TWO;
        if (className.endsWith("Remote3x1Provider")) return THREE_BY_ONE;
        if (className.endsWith("Remote3x3Provider")) return THREE_BY_THREE;
        return FLEX;
    }
}
