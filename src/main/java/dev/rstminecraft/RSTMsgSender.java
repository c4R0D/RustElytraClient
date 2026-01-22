package dev.rstminecraft;


import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

public class RSTMsgSender {
    private static final String[] LevelStr = {"§7[Rust Elytra]", "§7[Rust Elytra]", "§8[Rust Elytra]", "§6[Rust Elytra Warn]", "§4[Rust Elytra Error]", "§4§l[Rust Elytra Fatal]"};
    final MsgLevel DisplayLevel;

    /**
     *
     * @param DL 最低打印到聊天区的消息级别
     */
    RSTMsgSender(MsgLevel DL) {
        DisplayLevel = DL;
    }

    /**
     * 发送消息
     *
     * @param player 非null玩家对象
     * @param msg 消息内容
     * @param level 消息级别
     */
    void SendMsg(@NotNull PlayerEntity player, String msg, @NotNull MsgLevel level) {
        if (level.ordinal() < DisplayLevel.ordinal()) {
            if (level == MsgLevel.tip)
                player.sendMessage(Text.literal(LevelStr[level.ordinal()] + msg + "§r"), true);
            return;
        }
        player.sendMessage(Text.literal(LevelStr[level.ordinal()] + msg + "§r"), false);
    }


}
