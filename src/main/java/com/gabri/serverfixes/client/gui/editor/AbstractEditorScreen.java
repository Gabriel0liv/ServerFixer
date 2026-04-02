package com.gabri.serverfixes.client.gui.editor;

import com.gabri.serverfixes.client.gui.GuiLayoutUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractEditorScreen extends Screen {
    protected AbstractEditorScreen(Component title) {
        super(title);
    }

    protected void renderEditorFrame(@NotNull GuiGraphics graphics, @NotNull Component title,
                                     int frameX, int frameY, int frameW, int frameH,
                                     int headerY, int barY, int contentY, int footerY) {
        int midX = frameX + frameW / 2;
        GuiLayoutUtils.drawPanel(graphics, frameX, frameY, frameW, frameH, 0xCC1C2532, 0xFF4E6B8D);
        GuiLayoutUtils.drawTitleWithUnderline(graphics, this.font, title, midX, headerY + 2, 0xFFF5F5F5, 0xFF3ACAFF);

        drawDivider(graphics, frameX + 4, frameX + frameW - 4, barY - 3, 0x335C7FA0);
        drawDivider(graphics, frameX + 4, frameX + frameW - 4, contentY - 4, 0x335C7FA0);
        drawDivider(graphics, frameX + 4, frameX + frameW - 4, footerY - 6, 0x335C7FA0);
    }

    protected void drawDivider(@NotNull GuiGraphics graphics, int leftX, int rightX, int y, int color) {
        graphics.fill(leftX, y, rightX, y + 1, color);
    }
}
