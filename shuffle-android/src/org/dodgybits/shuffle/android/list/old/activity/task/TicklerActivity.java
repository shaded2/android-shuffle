/*
 * Copyright (C) 2009 Android Shuffle Open Source Project
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

package org.dodgybits.shuffle.android.list.old.activity.task;

import org.dodgybits.shuffle.android.core.model.Task;
import org.dodgybits.shuffle.android.core.model.persistence.selector.TaskSelector;
import org.dodgybits.shuffle.android.list.annotation.Tickler;
import org.dodgybits.shuffle.android.list.old.config.ListConfig;
import org.dodgybits.shuffle.android.list.old.config.TaskListConfig;

import android.os.Bundle;
import android.view.Menu;

import com.google.inject.Inject;

public class TicklerActivity extends AbstractTaskListActivity {

    @Inject @Tickler
    private TaskListConfig mTaskListConfig;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    protected ListConfig<Task,TaskSelector> createListConfig()
	{
        return mTaskListConfig;
	}

}