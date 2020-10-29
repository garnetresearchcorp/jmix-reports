/*
 * Copyright (c) 2008-2019 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.jmix.reportsui.gui.actions;

import com.haulmont.cuba.core.global.Metadata;
import io.jmix.core.metamodel.model.MetaClass;
import io.jmix.core.JmixEntity;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.Messages;
import io.jmix.reportsui.gui.ReportGuiManager;
import io.jmix.ui.Notifications;
import io.jmix.ui.component.Component;
import io.jmix.ui.screen.EditorScreen;
import io.jmix.ui.screen.FrameOwner;
import io.jmix.ui.screen.Screen;
import io.jmix.ui.screen.UiControllerUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Nullable;

public class EditorPrintFormAction extends AbstractPrintFormAction {

    @Autowired
    protected Messages messages;

    @Autowired
    protected Metadata metadata;

    protected final EditorScreen editor;
    protected final String reportOutputName;

    public EditorPrintFormAction(EditorScreen editor, @Nullable String reportOutputName) {
        this("editorReport", editor, reportOutputName);
    }

    public EditorPrintFormAction(String id, EditorScreen editor, @Nullable String reportOutputName) {
        super(id);

        this.editor = editor;
        this.caption = messages.getMessage(getClass(), "actions.Report");
        this.reportOutputName = reportOutputName;
        this.icon = "icons/reports-print.png";
    }

    @Override
    public void actionPerform(Component component) {
        if (beforeActionPerformedHandler != null) {
            if (!beforeActionPerformedHandler.beforeActionPerformed())
                return;
        }
        JmixEntity entity = editor.getEditedEntity();
        if (entity != null) {
            MetaClass metaClass = metadata.getClass(entity);
            openRunReportScreen((Screen) editor, entity, metaClass, reportOutputName);
        } else {
            Notifications notifications = UiControllerUtils.getScreenContext((FrameOwner) editor).getNotifications();

            notifications.create()
                    .withCaption(messages.getMessage(ReportGuiManager.class, "notifications.noSelectedEntity"))
                    .show();
        }
    }
}