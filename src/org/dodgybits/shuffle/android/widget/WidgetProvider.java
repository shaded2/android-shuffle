/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dodgybits.shuffle.android.widget;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViewsService;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.dodgybits.shuffle.android.persistence.provider.ContextProvider;
import org.dodgybits.shuffle.android.persistence.provider.ProjectProvider;
import org.dodgybits.shuffle.android.persistence.provider.TaskProvider;
import org.dodgybits.shuffle.android.preference.model.ListSettings;
import roboguice.RoboGuice;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Widget provider for honeycomb+ devices.
 */
public class WidgetProvider extends RoboAppWidgetProvider {


    @Inject
    private WidgetManager mWidgetManager;

    @Override
    public void onDisabled(Context context) {
        context.stopService(new Intent(context, WidgetService.class));
        super.onDisabled(context);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);
        mWidgetManager.updateWidgets(context, appWidgetIds);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        mWidgetManager.deleteWidgets(context, appWidgetIds);
        super.onDeleted(context, appWidgetIds);
    }

    @Override
    public void handleReceive(android.content.Context context, Intent intent) {
        super.handleReceive(context, intent);

        String action = intent.getAction();
        if (TaskProvider.UPDATE_INTENT.equals(action) ||
                ProjectProvider.UPDATE_INTENT.equals(action) ||
                ContextProvider.UPDATE_INTENT.equals(action) ||
                ListSettings.LIST_PREFERENCES_UPDATED.equals(action)) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            // Retrieve the identifiers for each instance of your chosen widget.
            ComponentName thisWidget = new ComponentName(context, getClass());
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            if (appWidgetIds != null && appWidgetIds.length > 0) {
                this.onUpdate(context, appWidgetManager, appWidgetIds);
            }
        }
    }

    /**
     * We use the WidgetService for two purposes:
     *  1) To provide a widget factory for RemoteViews, and
     *  2) Catch our command Uri's (i.e. take actions on user clicks) and let TaskWidget
     *     handle them.
     */
    public static class WidgetService extends RemoteViewsService {

        @Inject
        private WidgetManager mWidgetManager;

        @Override
        public void onCreate() {
            final Injector injector = RoboGuice.getInjector(this);
            injector.injectMembers(this);

            super.onCreate();
        }

        @Override
        public void onDestroy() {
            try {
                RoboGuice.destroyInjector(this);
            } finally {
                super.onDestroy();
            }
        }

        @Override
        public RemoteViewsFactory onGetViewFactory(Intent intent) {
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (widgetId == -1) return null;
            // Find the existing widget or create it
            return mWidgetManager.getOrCreateWidget(this, widgetId);
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (intent.getData() != null) {
                // TaskWidget creates intents, so it knows how to handle them.
                TaskWidget.processIntent(this, intent);
            }
            return Service.START_NOT_STICKY;
        }

        @Override
        protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
            WidgetManager.dump(fd, writer, args);
        }
    }
 }
