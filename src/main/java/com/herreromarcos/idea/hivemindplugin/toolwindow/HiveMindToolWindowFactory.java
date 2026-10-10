package com.herreromarcos.idea.hivemindplugin.toolwindow;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/** Toolwindow "HiveMind" mit der Liste aller Services. */
public class HiveMindToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        final ServicesPanel panel = new ServicesPanel(project);
        final Content content = ContentFactory.getInstance().createContent(panel, "Services", false);
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
    }
}
