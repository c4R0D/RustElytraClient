package dev.rstminecraft;

//文件解释：本文件为模组GUI实现。

import dev.rstminecraft.utils.MsgLevel;
import dev.rstminecraft.utils.RSTMsgSender;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static dev.rstminecraft.RustElytraClient.*;
import static dev.rstminecraft.utils.RSTConfig.getBoolean;
import static dev.rstminecraft.utils.RSTConfig.setBoolean;


public class RSTScr extends Screen {
    // 主菜单相关信息
    // 3行1列
    private static final int MainButtonsRow = 3;
    private static final int MainButtonsCol = 1;
    private final Screen parent;
    private final int buttonWidth;

    //主菜单组件信息
    private final SrcEntry[] MainEntry = {new SrcButtonEntry("设置", "调整Mod设置", () -> {
        if (client != null) {
            client.setScreen(new SettingsSrc(client.currentScreen));
        }
    })// 一个“设置”按钮
            , new SrcButtonEntry("飞行菜单", "输入坐标并开始自动飞行", () -> {
        if (client != null) {
            client.setScreen(new ciSrc(client.currentScreen));
        }
    })// 一个“飞行菜单”按钮
            , new SrcButtonEntry("使用指南", "请按指南要求完成必要设置", () -> {
        if (client != null && client.player != null) {
            Text linkText = Text.literal("点击查看Mod指南").styled(style -> style.withColor(Formatting.BLUE).withUnderline(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://elytra.rust3c.top/Rust%20Elytra%20Client%20v1.0.pdf")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("打开PDF指南"))));

            client.player.sendMessage(linkText,false);
            client.player.sendMessage(Text.literal("§4[Rust Elytra]pdf指南链接已经发到聊天框§r"), true);
            client.setScreen(null);
        }
    })// 一个“帮助”按钮
    };

    private boolean firstUse;
    private ButtonWidget button1;

    RSTScr(Screen parent, boolean firstUse) {
        super(Text.literal("RST Auto Elytra Menu"));
        this.buttonWidth = Math.max(100, Math.min(300, (int) (this.width * 0.3)));
        this.firstUse = firstUse;
        this.parent = parent;

    }

    /**
     * 将组件转换为Minecraft屏幕控件
     *
     * @param Entry        组件列表(使用多态接受组件)
     * @param row          组件行数
     * @param col          组件列数
     * @param widgetWidth  屏幕控件宽度
     * @param width        屏幕宽度
     * @param height       屏幕高度
     * @param textRenderer Minecraft文本渲染器
     * @return 屏幕控件列表
     */
    private static ClickableWidget @NotNull [] EntryToWidget(SrcEntry[] Entry, int row, int col, int widgetWidth, int width, int height, TextRenderer textRenderer) {
        ClickableWidget[] widget = new ClickableWidget[row * col];
        for (int i = 0; i < row; i++) {
            for (int j = 0; j < col; j++) {
                int finalI = i;
                int finalJ = j;

                // 计算组件顶点坐标
                // 所有组件均匀排列在屏幕上,矩形排列
                int widgetX = width / (col + 1) * (j + 1) - widgetWidth / 2;
                int widgetY = 40 + (height - 80) / (row + 1) * (i + 1);

                // 判断组件类型
                if (Entry[i * col + j] instanceof SrcButtonEntry)
                    // 按钮控件
                    widget[i * col + j] = ButtonWidget.builder(Text.literal(((SrcButtonEntry) Entry[i * col + j]).text), button -> ((SrcButtonEntry) Entry[finalI * col + finalJ]).onClick.onClick()).dimensions(widgetX, widgetY, widgetWidth, 20).tooltip(Tooltip.of(Text.literal(((SrcButtonEntry) Entry[i * col + j]).tooltip))).build();
                else if (Entry[i * col + j] instanceof SrcInputEntry) {
                    // 输入框控件
                    TextFieldWidget tmp = new TextFieldWidget(textRenderer, widgetX, widgetY, widgetWidth, 20, Text.literal(((SrcInputEntry) Entry[i * col + j]).title));
                    tmp.setText("");
                    tmp.setMaxLength(10);
                    tmp.setPlaceholder(Text.literal(((SrcInputEntry) Entry[i * col + j]).defaultText));
                    int finalJ1 = j;
                    int finalI1 = i;
                    tmp.setChangedListener(str -> ((SrcInputEntry) Entry[finalI1 * col + finalJ1]).onTick.onTick(str));
                    widget[i * col + finalJ1] = tmp;
                }
            }

        }
        return widget;
    }

    @Override
    protected void init() {
        realInit();
    }

    private void realInit() {
        // 转换为屏幕控件
        ClickableWidget[] mainWidget = EntryToWidget(MainEntry, MainButtonsRow, MainButtonsCol, buttonWidth, width, height, textRenderer);

        if (firstUse) {
            // 首次使用,提示信息
            button1 = ButtonWidget.builder(Text.literal("我知道了"), button -> {
                setBoolean("FirstUse", false);
                remove(button1);
                firstUse = false;
                realInit();
            }).dimensions(width / 2 - 205, 120, 200, 20).tooltip(Tooltip.of(Text.literal("阅读完毕指南"))).build();
            addDrawableChild(button1);
        } else if (ModStatus != ModStatuses.idle) {
            // 飞行途中,无法使用菜单。
            button1 = ButtonWidget.builder(Text.literal("取消飞行"), button -> {
                ModStatus = ModStatuses.canceled;
                if (client != null) {
                    client.setScreen(parent);
                }
            }).dimensions(width / 2 - buttonWidth / 2, height / 2, buttonWidth, 20).tooltip(Tooltip.of(Text.literal("关闭飞行。"))).build();
            addDrawableChild(button1);
        } else {
            // 将屏幕控件添加到屏幕上
            for (ClickableWidget i : mainWidget) {
                addDrawableChild(i);
            }
        }
    }

    @Override
    public void render(@NotNull DrawContext context, int mouseX, int mouseY, float delta) {
        realRender(context, mouseX, mouseY, delta);
    }

    private void realRender(@NotNull DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(textRenderer, "欢迎使用RSTAutoElytraMod", width / 3 * 2, 20, 16777215);
        // 渲染提示信息
        if (firstUse) {
            context.drawTextWithShadow(textRenderer, "若您是第一次使用RSTAutoElytraMod，请务必仔细阅读本指南", width / 4, height / 10, 0xFF0000);
            context.drawTextWithShadow(textRenderer, "否则可能造成物资损失或存档损坏等严重后果！", width / 4, height / 10 + 15, 0xFFFFFF);
        } else if (ModStatus != ModStatuses.idle) {
            context.drawTextWithShadow(textRenderer, "正在飞行中，若要更改设置请先取消飞行。", width / 4, height / 10, 0xFF0000);

        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private interface SrcButtonEntryOnClick {
        void onClick();
    }

    private interface SrcInputEntryOnTick {
        void onTick(String text);
    }

    // 屏幕组件抽象类
    private static abstract class SrcEntry {
    }

    // 按钮组件
    private static class SrcButtonEntry extends SrcEntry {
        public String text;
        public String tooltip;
        public SrcButtonEntryOnClick onClick;

        /**
         * @param text    按钮文本
         * @param tooltip 按钮提示文本
         * @param onClick 按钮回调
         */
        public SrcButtonEntry(String text, String tooltip, SrcButtonEntryOnClick onClick) {
            this.text = text;
            this.tooltip = tooltip;
            this.onClick = onClick;
        }
    }

    // 输入组件
    private static class SrcInputEntry extends SrcEntry {
        public String title;
        public String defaultText;
        public SrcInputEntryOnTick onTick;

        /**
         * @param title       输入框标题
         * @param defaultText 输入框默认文本
         * @param onTick      输入回调
         */
        public SrcInputEntry(String title, String defaultText, SrcInputEntryOnTick onTick) {
            this.title = title;
            this.defaultText = defaultText;
            this.onTick = onTick;
        }
    }

    // 开始飞行菜单
    private static class ciSrc extends Screen {
        // 3行一列
        private static final int ciButtonsRow = 4;
        private static final int ciButtonsCol = 1;
        private final Screen parent;
        private final int buttonWidth;
        private @Nullable String x = null;
        private @Nullable String z = null;
        private final SrcEntry[] ciEntry = {new SrcInputEntry("目的地X坐标", "目的地X坐标", str -> this.x = str), // 一个X轴输入框
                new SrcInputEntry("目的地Z坐标", "目的地Z坐标", str -> this.z = str), // 一个Y轴输入框
                new SrcButtonEntry("开始飞行(鞘翅模式)", "开始前往上方输入的坐标,并在必要时补充新的满耐久鞘翅", () -> {
                    int x1, z1;
                    // 将输入框内文本转为数字,若转换异常,则说明输入信息有误,提前返回
                    if (x == null || z == null) return;
                    try {
                        x1 = Integer.parseInt(x);
                    } catch (NumberFormatException e) {
                        return;
                    }
                    try {
                        z1 = Integer.parseInt(z);
                    } catch (NumberFormatException e) {
                        return;
                    }
                    if (client == null || client.player == null) return;
                    if (TaskThread.getModThread() != null) return;
                    // 开始飞行
                    MsgSender.SendMsg(client.player, "任务开始！", MsgLevel.warning);
                    TaskThread.StartModThread_ELY(getBoolean("isAutoLog", true), getBoolean("isAutoLogOnSeg1", false), x1, z1);
                    client.setScreen(null);
                }),// 一个“开始飞行”按钮
                new SrcButtonEntry("开始飞行(XP模式)", "开始前往上方输入的坐标,鞘翅无耐久时通过附魔之瓶补充,仅需一个鞘翅", () -> {
                    int x1, z1;
                    // 将输入框内文本转为数字,若转换异常,则说明输入信息有误,提前返回
                    if (x == null || z == null) return;
                    try {
                        x1 = Integer.parseInt(x);
                    } catch (NumberFormatException e) {
                        return;
                    }
                    try {
                        z1 = Integer.parseInt(z);
                    } catch (NumberFormatException e) {
                        return;
                    }
                    if (client == null || client.player == null) return;
                    if (TaskThread.getModThread() != null) return;
                    // 开始飞行
                    MsgSender.SendMsg(client.player, "任务开始！", MsgLevel.warning);
                    TaskThread.StartModThread_XP(getBoolean("isAutoLog", true), getBoolean("isAutoLogOnSeg1", false), x1, z1);
                    client.setScreen(null);
                })// 一个“开始飞行”按钮
        };

        public ciSrc(Screen parent) {
            super(Text.literal("RST Auto Elytra Mod Menu"));
            this.parent = parent;
            this.buttonWidth = Math.max(100, Math.min(300, (int) (this.width * 0.3)));
        }

        @Override
        protected void init() {
            realInit();
        }

        private void realInit() {
            // 将组件转换为屏幕控件
            ClickableWidget[] ciWidget = EntryToWidget(ciEntry, ciButtonsRow, ciButtonsCol, buttonWidth, width, height, textRenderer);
            for (ClickableWidget i : ciWidget) {
                addDrawableChild(i);
            }
        }

        @Override
        public void close() {
            if (client != null) {
                client.setScreen(parent);
            }
        }

    }

    // 设置屏幕
    private static class SettingsSrc extends Screen {
        // 5行一列
        private static final int SettingsButtonsRow = 3;
        private static final int SettingsButtonsCol = 1;
        private final Screen parent;
        private final int buttonWidth;
        private ClickableWidget @NotNull [] SettingsWidget = new ClickableWidget[SettingsButtonsCol * SettingsButtonsRow];
        private SrcEntry[] SettingsEntry;

        public SettingsSrc(Screen parent) {
            super(Text.literal("RST Auto Elytra Mod Settings Menu"));
            this.parent = parent;
            this.buttonWidth = Math.max(100, Math.min(300, (int) (this.width * 0.3)));
        }

        private void BuildButtons() {
            for (ClickableWidget i : SettingsWidget) {
                remove(i);
            }
            SettingsEntry = new SrcEntry[]{new SrcButtonEntry("自动退出:" + (getBoolean("isAutoLog", true) ? "开" : "关"), "在任务失败时是否自动退出服务器", () -> {
                setBoolean("isAutoLog", !getBoolean("isAutoLog", true));
                BuildButtons();
            }), new SrcButtonEntry("第一段自动退出:" + (getBoolean("isAutoLogOnSeg1", false) ? "开" : "关"), "在任务刚开始时若失败是否自动退出。假如否，您可以避免在第一次补给时因“末影箱中没有补给物品”等简单原因自动退出（造成时间浪费），但请确保第一次补给成功后再离开电脑", () -> {
                setBoolean("isAutoLogOnSeg1", !getBoolean("isAutoLogOnSeg1", false));
                BuildButtons();
            }), new SrcButtonEntry("发送调试信息:" + (getBoolean("DisplayDebug", false) ? "开" : "关"), "是否发送调试信息", () -> {
                setBoolean("DisplayDebug", !getBoolean("DisplayDebug", false));
                MsgSender = new RSTMsgSender(getBoolean("DisplayDebug", false) ? MsgLevel.debug : MsgLevel.info);
                BuildButtons();
            })};

            SettingsWidget = EntryToWidget(SettingsEntry, SettingsButtonsRow, SettingsButtonsCol, buttonWidth, width, height, textRenderer);
            for (ClickableWidget i : SettingsWidget) {
                addDrawableChild(i);
            }


        }

        @Override
        protected void init() {
            realInit();
        }

        private void realInit() {
            SettingsEntry = new SrcEntry[]{new SrcButtonEntry("自动退出:" + (getBoolean("isAutoLog", true) ? "开" : "关"), "在任务失败时是否自动退出服务器", () -> {
                setBoolean("isAutoLog", !getBoolean("isAutoLog", true));
                BuildButtons();
            }), new SrcButtonEntry("第一段自动退出:" + (getBoolean("isAutoLogOnSeg1", false) ? "开" : "关"), "在任务刚开始时若失败是否自动退出。假如否，您可以避免在第一次补给时因“末影箱中没有补给物品”等简单原因自动退出（造成时间浪费），但请确保第一次补给成功后再离开电脑", () -> {
                setBoolean("isAutoLogOnSeg1", !getBoolean("isAutoLogOnSeg1", false));
                BuildButtons();
            }), new SrcButtonEntry("发送调试信息:" + (getBoolean("DisplayDebug", false) ? "开" : "关"), "是否发送调试信息", () -> {
                setBoolean("DisplayDebug", !getBoolean("DisplayDebug", false));
                MsgSender = new RSTMsgSender(getBoolean("DisplayDebug", false) ? MsgLevel.debug : MsgLevel.info);
                BuildButtons();
            })};

            SettingsWidget = EntryToWidget(SettingsEntry, SettingsButtonsRow, SettingsButtonsCol, buttonWidth, width, height, textRenderer);
            for (ClickableWidget i : SettingsWidget) {
                addDrawableChild(i);
            }


        }

        @Override
        public void close() {
            if (client != null) {
                client.setScreen(parent);
            }
        }

    }
}
