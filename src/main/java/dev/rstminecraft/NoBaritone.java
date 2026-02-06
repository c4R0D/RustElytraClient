package dev.rstminecraft;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class NoBaritone extends Screen {

    public NoBaritone() {
        super(Text.literal("缺少核心依赖"));
    }

    @Override
    protected void init() {
        // 添加一个“退出游戏”按钮或“我已知晓”按钮
        this.addDrawableChild(ButtonWidget.builder(Text.literal("退出游戏"),
                        button -> {
                            if (this.client != null) {
                                this.client.scheduleStop();
                            }
                        })
                .dimensions(this.width / 2 - 100, this.height / 2 + 20, 200, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, "Rust Elytra Client 未检测到 Baritone 模组！", this.width / 2, this.height / 2 - 50, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "请安装原生 Baritone 或 Meteor 版以运行模组！", this.width / 2, this.height / 2 - 30, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer, "手机版用户可使用BaritonePE。（详见QQ群）", this.width / 2, this.height / 2 - 10, 0xAAAAAA);

    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // 强制玩家必须处理，按 ESC 不会关闭
    }
}
