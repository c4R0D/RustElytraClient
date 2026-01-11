package dev.rstminecraft;

import baritone.api.BaritoneAPI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static dev.rstminecraft.RSTConfig.getInt;
import static dev.rstminecraft.RSTTask.scheduleTask;
import static dev.rstminecraft.RustElytraClient.*;

class RSTElytraTask {
    /**
     * 以下变量为鞘翅飞行的状态
     */
    static boolean isEating = false;
    static boolean arrived = false;
    static boolean isJumping = false;
    static int jumpingTimes = 0;
    static int LastJumpingTick = 0;
    static int SegFailed = 0;
    static int LastSegFailedTick = 0;
    static @Nullable BlockPos LastPos;
    static boolean noFirework = false;
    static boolean isJumpBlockedByBlock = false;
    static @Nullable BlockPos oldPos = null;
    static int spinTimes = 0;
    static boolean waitReset = false;
    static int elytraCount;
    static int FireworkCount;
    static @Nullable BlockPos LastDebugPos = null;
    static boolean isAutoLog;
    static boolean isAutoLogOnSeg1;
    static int inFireTick = 0;

    /**
     * 重置状态
     */
    static void resetStatus() {
        arrived = false;
        isJumping = false;
        jumpingTimes = 0;
        ModStatus = ModStatuses.flying;
        LastJumpingTick = 0;
        SegFailed = 0;
        LastSegFailedTick = -100000;
        LastPos = null;
        noFirework = false;
        isJumpBlockedByBlock = false;
        spinTimes = 0;
        waitReset = false;
        elytraCount = 0;
        FireworkCount = 0;
    }

    /**
     * 到达baritone目的地后处理(拿取补给)
     *
     * @param client   客户端对象
     * @param segments 分段列表
     * @param nowIndex 当前段数
     * @param self     鞘翅守护任务
     * @param segPos   当前段开始坐标
     */
    static void arrivedTarget(@NotNull MinecraftClient client, @NotNull List<Vec3i> segments, int nowIndex, @NotNull RSTTask self, BlockPos segPos) {
        if (client.player == null) return;
        if (client.player.getBlockPos().isWithinDistance(oldPos, 100)) {
            client.player.sendMessage(Text.literal("距离异常！"), false);
            taskFailed(client, isAutoLog, "飞行任务失败！距离异常！", isAutoLogOnSeg1, nowIndex);
            self.repeatTimes = 0;
            return;
        }
        self.repeatTimes = 0;
        arrived = true;
        if (nowIndex == segments.size() - 1) {
            BaritoneAPI.getSettings().logger.value = BaritoneAPI.getSettings().logger.defaultValue;
            client.player.sendMessage(Text.literal("到达目的地！本段飞行距离：" + Math.sqrt(client.player.getBlockPos().getSquaredDistance(segPos))), false);
        } else {
            client.player.sendMessage(Text.literal("到达阶段目的地：" + nowIndex + "本段飞行距离:" + Math.sqrt(client.player.getBlockPos().getSquaredDistance(segPos))), false);
            client.player.sendMessage(Text.literal("开始下一段补给任务：" + (nowIndex + 1)), false);
            scheduleTask((s3, a3) -> {
                if (client.player == null || s3.repeatTimes == 0) {
                    taskFailed(client, isAutoLog, "开启补给任务失败！", isAutoLogOnSeg1, nowIndex);
                    s3.repeatTimes = 0;
                    return;
                }
                if (client.player.getVelocity().getX() < 0.01 && client.player.getVelocity().getZ() < 0.01) {
                    segmentsMainSupply(segments, nowIndex + 1, client, isAutoLog, isAutoLogOnSeg1);
                    s3.repeatTimes = 0;
                }
            }, 1, 60, 5, 20000);
        }
    }

    /**
     * 自动起跳检查和实现
     *
     * @param client   客户端对象
     * @param segments 分段列表
     * @param nowIndex 当前段数
     * @param self     鞘翅守护任务
     * @return 是否需要自动退出
     */
    static boolean AutoJumping(@NotNull MinecraftClient client, @NotNull List<Vec3i> segments, int nowIndex, @NotNull RSTTask self) {
        if (client.player == null || client.getNetworkHandler() == null || client.interactionManager == null)
            return true;
        // 自动起跳次数过多，可能遭遇意外情况，auto log
        if (jumpingTimes > 7) {
            client.player.sendMessage(Text.literal("自动起跳数量过多，可能是baritone异常！"), false);
            taskFailed(client, isAutoLog, "飞行任务失败！自动退出！自动起跳数量过多，可能是baritone异常！", isAutoLogOnSeg1, nowIndex);
            self.repeatTimes = 0;
            return true;

        }
        // 玩家掉落在地上时自动起跳，继续鞘翅飞行
        if (!arrived && !isJumping && !isJumpBlockedByBlock && !isEating && client.player.isOnGround() && !client.player.isFallFlying() && !client.player.isInLava() && client.player.getVelocity().getX() < 0.01 && client.player.getVelocity().getZ() < 0.01) {
            List<BlockPos> bp = getPotentialJumpBlockingBlocks(1);
            if (!bp.isEmpty()) {
                // 玩家头顶有方块阻挡，调用baritone API清除
                isJumpBlockedByBlock = true;
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
                BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess().clearArea(new BlockPos((int) Math.floor(client.player.getPos().getX() - 0.3), bp.getFirst().getY(), (int) Math.floor(client.player.getPos().getZ() - 0.3)), new BlockPos((int) Math.floor(client.player.getPos().getX() - 0.3) + 1, bp.getFirst().getY() + 1, (int) Math.floor(client.player.getPos().getZ() - 0.3) + 1));
                scheduleTask((s, a) -> {
                    if (client.player == null) {
                        s.repeatTimes = 0;
                        return;
                    }
                    if (!BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess().isActive()) {

                        s.repeatTimes = 0;
                        oldPos = client.player.getBlockPos();
                        BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().pathTo(new BlockPos(segments.get(nowIndex)));
                        scheduleTask((s3, a3) -> isJumpBlockedByBlock = false, 1, 0, 10, 100000000);
                        jumpingTimes++;
                    } else if (s.repeatTimes == 0) {
                        client.player.sendMessage(Text.literal("挖掘异常！"), false);

                        taskFailed(client, isAutoLog, "飞行任务失败！自动退出！挖掘异常！", isAutoLogOnSeg1, nowIndex);
                        self.repeatTimes = 0;

                    }
                }, 1, 200, 0, 1000);
                return true;
            }
            isJumping = true;
            if (currentTick - LastJumpingTick < 100) {
                jumpingTimes++;
            } else {
                jumpingTimes = 0;
            }
            LastJumpingTick = currentTick;
            // 玩家暂时无法起跳，尝试使用烟花辅助起跳
            if (jumpingTimes > 4 && getPotentialJumpBlockingBlocks(8).isEmpty()) {
                client.player.sendMessage(Text.literal("自动烟花起跳！" + jumpingTimes), false);
                client.player.setPitch(-90);
                client.options.jumpKey.setPressed(true);
                scheduleTask((ss, aa) -> client.options.jumpKey.setPressed(false), 1, 0, 1, 100000000);
                double y = client.player.getPos().getY();
                scheduleTask((s4, a4) -> {
                    if (client.player == null) {
                        s4.repeatTimes = 0;
                        return;
                    }
                    if (client.player.getPos().getY() > y + 1 || s4.repeatTimes == 0) {
                        client.options.jumpKey.setPressed(true);
                        scheduleTask((s3, a3) -> client.options.jumpKey.setPressed(false), 1, 0, 1, 100000);

                        scheduleTask((s, a) -> {
                            if (client.player == null) return;
                            PlayerInventory inv = client.player.getInventory();
                            int slots = -1;
                            for (int i = 0; i < 8; i++) {
                                ItemStack s5 = inv.getStack(i);
                                if (s5.isEmpty() || s5.getItem() == Items.FIREWORK_ROCKET) slots = i;
                            }
                            if (slots == -1) {

                                client.player.sendMessage(Text.literal("烟花异常！"), false);
                                taskFailed(client, isAutoLog, "飞行任务失败！自动退出！找不到烟花！", isAutoLogOnSeg1, nowIndex);
                                self.repeatTimes = 0;
                                return;
                            } else {
                                client.player.getInventory().selectedSlot = slots;
                                client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slots));
                                client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                                client.player.sendMessage(Text.literal("已使用烟花！"), false);
                            }
                            isJumping = false;
                        }, 1, 0, 2, 25);
                        s4.repeatTimes = 0;
                    }
                }, 1, 7, 1, 25);


            } else {
                client.player.sendMessage(Text.literal("自动起跳！" + jumpingTimes), false);
                client.player.setPitch(-30);
                client.options.jumpKey.setPressed(true);
                scheduleTask((s4, a4) -> {
                    if (client.player == null) {
                        s4.repeatTimes = 0;
                        return;
                    }
                    if (client.player.getVelocity().getY() < -0.1 || s4.repeatTimes == 0) {

                        client.options.jumpKey.setPressed(false);
                        scheduleTask((s3, a3) -> client.options.jumpKey.setPressed(true), 1, 0, 1, 100000);

                        scheduleTask((s, a) -> {
                            client.options.jumpKey.setPressed(false);
                            isJumping = false;
                        }, 1, 0, 3, 25);
                        s4.repeatTimes = 0;
                    }
                }, 1, 7, 0, 25);
            }

        }
        return false;
    }

    /**
     * 自动检测并逃离烟花
     *
     * @param client   客户端对象
     * @param nowIndex 当前段数
     * @param self     鞘翅守护任务
     * @return 是否需要自动退出
     */
    static boolean AutoEscapeLava(@NotNull MinecraftClient client, int nowIndex, @NotNull RSTTask self) {
        if (client.player == null || client.getNetworkHandler() == null) return true;
        // 在岩浆中吗？
        if (client.player.isInLava()) {
            inFireTick++;
        } else {
            inFireTick = 0;
        }

        if (inFireTick > 20 || inFireTick > 5 && !client.player.isFallFlying()) {
            // 位于岩浆中？自动逃离岩浆
            inFireTick = -100;
            // 打开鞘翅
            client.options.jumpKey.setPressed(true);
            scheduleTask((ss, aa) -> {
                client.options.jumpKey.setPressed(false);
                if (client.player != null && client.interactionManager != null) {
                    client.player.sendMessage(Text.literal("位于岩浆中，已鞘翅打开"), false);
                    // 抬头
                    client.player.setPitch(-90);
                    PlayerInventory inv = client.player.getInventory();

                    // 找烟花
                    int slots = -1;
                    for (int i = 0; i < 8; i++) {
                        ItemStack s = inv.getStack(i);
                        if (s.isEmpty() || s.getItem() == Items.FIREWORK_ROCKET) slots = i;
                    }
                    if (slots == -1) {

                        client.player.sendMessage(Text.literal("烟花异常！"), false);
                        taskFailed(client, isAutoLog, "飞行任务失败！自动退出！找不到烟花！", isAutoLogOnSeg1, nowIndex);
                        self.repeatTimes = 0;
                    } else {
                        // 切换到烟花所在格子
                        client.player.getInventory().selectedSlot = slots;
                        client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slots));

                        // 使用烟花
                        client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                        client.player.sendMessage(Text.literal("已使用烟花！"), false);

                        // 延时检测是否逃离岩浆
                        scheduleTask((s4, a4) -> {
                            if (client.player == null) {
                                s4.repeatTimes = 0;
                                return;
                            }
                            if (client.player.isInLava()) {

                                client.player.sendMessage(Text.literal("无法逃离岩浆！"), false);
                                taskFailed(client, isAutoLog, "飞行任务失败！自动退出！逃离岩浆失败！", isAutoLogOnSeg1, nowIndex);
                                self.repeatTimes = 0;
                            }


                        }, 1, 0, 40, 10);
                    }

                }
            }, 1, 0, 3, 1000000);
        }
        return false;
    }

    /**
     * 检查baritone寻路情况
     *
     * @param client   客户端对象
     * @param nowIndex 当前段数
     * @param self     鞘翅守护任务
     * @return 是否需要自动退出
     */
    static boolean baritoneChecker(@NotNull MinecraftClient client, int nowIndex, @NotNull RSTTask self) {
        if (client.player == null) return true;
        // baritone寻路失败，等待重置状态或auto log
        if (SegFailed > 25) {
            if (SegFailed > 30) {
                client.player.sendMessage(Text.literal("SegFailed！"), false);
                taskFailed(client, isAutoLog, "飞行任务失败！自动退出！baritone寻路异常？！", isAutoLogOnSeg1, nowIndex);
                self.repeatTimes = 0;
                return true;
            } else if (waitReset) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().resetState();
                client.player.sendMessage(Text.literal("SegFailed！正在重置baritone!"), false);
                waitReset = false;
            }
        }

        // 玩家是不是陷入了原地绕圈？尝试重置baritone或auto log
        if (currentTick % 1000 == 0) {
            if (LastPos != null && client.player.getBlockPos().isWithinDistance(LastPos, 25)) {
                client.player.sendMessage(Text.literal("SegFailed！spin!"), false);
                if (spinTimes > 4) {
                    taskFailed(client, isAutoLog, "飞行任务失败！自动退出！baritone寻路异常？！疑似原地转圈", isAutoLogOnSeg1, nowIndex);
                    self.repeatTimes = 0;
                } else {
                    spinTimes++;
                    BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().resetState();
                }
                return true;
            }
            LastPos = client.player.getBlockPos();

        }
        return false;
    }

    /**
     * 烟花检测、自动补给与烟花、鞘翅消耗量统计
     *
     * @param client   客户端实体
     * @param segments 分段列表
     * @param nowIndex 当前段数
     * @param self     鞘翅守护任务
     * @return 是否需要自动退出
     */
    static boolean FireworkChecker(@NotNull MinecraftClient client, @NotNull List<Vec3i> segments, int nowIndex, @NotNull RSTTask self) {
        if (client.player == null || client.interactionManager == null) return true;
        // 检查玩家烟花数量
        PlayerInventory inv = client.player.getInventory();
        int count = 0;
        int slots = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || s.getItem() == Items.FIREWORK_ROCKET) slots++;
            if (s.getItem() == Items.FIREWORK_ROCKET) count += s.getCount();
        }
        if (slots == 0) {
            client.player.sendMessage(Text.literal("烟花异常！"), false);
            taskFailed(client, isAutoLog, "飞行任务失败！自动退出！任务栏没有空位放烟花了！", isAutoLogOnSeg1, nowIndex);
            self.repeatTimes = 0;
            return true;
        } else if (count < 64) {
            // 烟花数量少，从背包里拿一些
            client.setScreen(new InventoryScreen(client.player));
            Screen screen = client.currentScreen;
            if (!(screen instanceof HandledScreen<?> handled)) {
                // 当前不是带处理器的 GUI（不是容器界面） -> 重置等待状态
                client.player.sendMessage(Text.literal("窗口异常！"), false);
                taskFailed(client, isAutoLog, "飞行任务失败！自动退出！窗口异常！", isAutoLogOnSeg1, nowIndex);
                self.repeatTimes = 0;
                return true;
            }
            int c = 0;
            ScreenHandler handler = handled.getScreenHandler();
            for (int i = 9; i < 36; i++) {
                Slot s = handler.getSlot(i);
                if (s == null) continue;
                ItemStack stack = s.getStack();
                if (stack == null || stack.isEmpty()) continue;
                Item item = stack.getItem();

                if (item == Items.FIREWORK_ROCKET) {
                    c += stack.getCount();
                    client.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                }
            }
            int d = 0;
            for (int i = 0; i < 46; i++) {
                Slot s = handler.getSlot(i);
                if (s == null) continue;
                ItemStack stack = s.getStack();
                if (stack == null || stack.isEmpty()) continue;
                Item item = stack.getItem();
                if (item == Items.ELYTRA) {
                    d += stack.getMaxDamage() - stack.getDamage();
                }
            }

            handled.close();

            if (c <= 128 && !noFirework) {
                if (client.player.getBlockPos().isWithinDistance(segments.get(nowIndex), (getInt("SegLength", DEFAULT_SEGMENT_LENGTH) - 3500) * 0.6)) {
                    noFirework = true;
                    client.player.sendMessage(Text.literal("烟花不足，提前寻找位置降落！"));
                } else {

                    client.player.sendMessage(Text.literal("烟花不足，以飞行路程不足总路程60%，可能是baritone设置错误？请检查！"));
                    taskFailed(client, isAutoLog, "飞行任务失败！自动退出！烟花不足，以飞行路程不足总路程60%，可能是baritone设置错误？请检查！", isAutoLogOnSeg1, nowIndex);
                    self.repeatTimes = 0;
                    return true;

                }
            }
            // 统计玩家烟花消耗速度，调试用
            if (elytraCount != 0 && FireworkCount != 0 && LastDebugPos != null) {
                client.player.sendMessage(Text.literal("目前鞘翅剩余耐久：" + d + "鞘翅预计可以飞行" + 2160.0 / (elytraCount - d) * Math.sqrt(client.player.getBlockPos().getSquaredDistance(LastDebugPos))));
                client.player.sendMessage(Text.literal("目前烟花剩余数量：" + (c + count) + "烟花预计可以飞行" + 1344.0 / (FireworkCount - c - count) * Math.sqrt(client.player.getBlockPos().getSquaredDistance(LastDebugPos))));
            } else {
                elytraCount = d;
                FireworkCount = c + count;
                LastDebugPos = client.player.getBlockPos();
            }
        }
        return false;
    }

    /**
     * 自动进食
     *
     * @param client   客户端对象
     * @param nowIndex 当前段数
     * @param self     鞘翅守护任务
     * @return 是否需要自动退出
     */
    static boolean AutoEating(@NotNull MinecraftClient client, int nowIndex, @NotNull RSTTask self) {
        if (client.player == null || client.getNetworkHandler() == null) return true;
        int slot2 = -1;
        // 自动进食，恢复血量
        if (!isEating && !client.player.isInLava() && client.player.getVelocity().length() > 1.4 && (client.player.getHungerManager().getFoodLevel() < 16 || client.player.getHealth() < 15 && client.player.getHungerManager().getFoodLevel() < 20)) {
            client.player.sendMessage(Text.literal("准备食用"), false);
            for (int i = 0; i < 8; i++) {
                ItemStack s = client.player.getInventory().getStack(i);
                Item item = s.getItem();
                if (item == Items.GOLDEN_CARROT) {
                    slot2 = i;
                    break;
                }
            }
            if (slot2 == -1) {
                client.player.sendMessage(Text.literal("无食物了！！"), false);
                taskFailed(client, isAutoLog, "飞行任务失败！自动退出！没有足够的食物了！", isAutoLogOnSeg1, nowIndex);
                self.repeatTimes = 0;
                return true;
            } else {
                client.player.getInventory().selectedSlot = slot2;
                client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot2));
                client.options.useKey.setPressed(true);
                isEating = true;
                scheduleTask((s5, a5) -> {
                    if (client.player == null) {
                        client.options.useKey.setPressed(false);
                        isEating = false;
                        s5.repeatTimes = 0;
                        return;
                    }
                    if (client.player.getVelocity().length() < 0.9) {
                        // 速度过低，放弃吃食物，防止影响baritone寻路
                        client.player.sendMessage(Text.literal("放弃吃食物！！！"), false);
                        client.options.useKey.setPressed(false);
                        s5.repeatTimes = 0;
                        isEating = false;
                        if (client.interactionManager != null) client.interactionManager.stopUsingItem(client.player);
                    } else if (s5.repeatTimes == 0) {
                        client.options.useKey.setPressed(false);
                        isEating = false;
                    }
                }, 1, 40, 0, 10000);
            }
        }
        return false;
    }

}
