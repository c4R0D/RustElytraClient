package dev.rstminecraft;

import baritone.api.BaritoneAPI;
import baritone.api.utils.Helper;
import dev.rstminecraft.utils.MsgLevel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.mojang.text2speech.Narrator.LOGGER;
import static dev.rstminecraft.RustElytraClient.*;
import static dev.rstminecraft.TaskThread.RunAsMainThread;
import static dev.rstminecraft.utils.RSTConfig.getInt;
import static dev.rstminecraft.utils.RSTTask.scheduleTask;


public class RustElytraTask {
    /**
     * 以下变量为鞘翅飞行的状态
     */
    private static int isEating = 0;
    private static boolean arrived = false;
    private static boolean isJumping = false;
    private static int jumpingTimes = 0;
    private static int LastJumpingTick = 0;
    private static int SegFailed = 0;
    private static int LastSegFailedTick = 0;
    private static @Nullable BlockPos LastPos;
    private static boolean noFirework = false;
    private static boolean isJumpBlockedByBlock = false;
    private static @Nullable BlockPos oldPos = null;
    private static int spinTimes = 0;
    private static boolean waitReset = false;
    private static int elytraCount;
    private static int FireworkCount;
    private static @Nullable BlockPos LastDebugPos = null;
    private static int inFireTick = 0;

    /**
     * 重置状态
     */
    private static void resetStatus() {
        arrived = false;
        isJumping = false;
        jumpingTimes = 0;
        LastJumpingTick = 0;
        isEating = 0;
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
     * @param client 客户端对象
     * @param segPos 当前段开始坐标
     */
    private static void arrivedTarget(@NotNull MinecraftClient client, BlockPos segPos) {
        if (client.player == null) return;
        if (client.player.getBlockPos().isWithinDistance(oldPos, 100)) throw new TaskThread.TaskException("距离异常！");
        arrived = true;
        MsgSender.SendMsg(client.player, "到达目的地！本段飞行距离：" + Math.sqrt(client.player.getBlockPos().getSquaredDistance(segPos)), MsgLevel.info);
        for (int i = 0; i < 60; i++) {
            if (client.player.getVelocity().getX() < 0.01 && client.player.getVelocity().getZ() < 0.01) return;
            TaskThread.delay(1);
        }
        throw new TaskThread.TaskException("开启异常！");
    }

    /**
     * 烟花检测、自动补给与烟花、鞘翅消耗量统计
     *
     * @param client 客户端实体
     * @param x      目的地x坐标
     * @param z      目的地z坐标
     */
    private static void FireworkChecker(@NotNull MinecraftClient client, int x, int z) {
        if (client.player == null || client.interactionManager == null) throw new TaskThread.TaskException("null");
        // 检查玩家烟花数量
        PlayerInventory inv = client.player.getInventory();
        int count = 0;
        int slots = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || s.getItem() == Items.FIREWORK_ROCKET) slots++;
            if (s.getItem() == Items.FIREWORK_ROCKET) count += s.getCount();
        }
        if (slots == 0) throw new TaskThread.TaskException("无槽位放置烟花");
        else if (count < 64) {
            // 烟花数量少，从背包里拿一些
            HandledScreen<?> handled = RunAsMainThread(() -> {
                client.setScreen(new InventoryScreen(client.player));
                if (!(client.currentScreen instanceof HandledScreen<?> handled2))
                    throw new TaskThread.TaskException("窗口异常");
                return handled2;
            });
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
                    int finalI = i;
                    RunAsMainThread(() -> client.interactionManager.clickSlot(handler.syncId, finalI, 0, SlotActionType.QUICK_MOVE, client.player));
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
            RunAsMainThread(handled::close);


            if (c <= 128 && !noFirework) {
                if (client.player.getBlockPos().isWithinDistance(new BlockPos(x, 0, z), (getInt("SegLength", DEFAULT_SEGMENT_LENGTH) - 3500) * 0.6)) {
                    noFirework = true;
                    MsgSender.SendMsg(client.player, "烟花不足，提前寻找位置降落！", MsgLevel.info);
                } else
                    throw new TaskThread.TaskException("烟花不足，以飞行路程不足总路程60%，可能是baritone设置错误？请检查！");
            }
            // 统计玩家烟花消耗速度，调试用
            if (elytraCount != 0 && FireworkCount != 0 && LastDebugPos != null) {
                MsgSender.SendMsg(client.player, "目前鞘翅剩余耐久：" + d + "鞘翅预计可以飞行" + 2160.0 / (elytraCount - d) * Math.sqrt(client.player.getBlockPos().getSquaredDistance(LastDebugPos)), MsgLevel.debug);
                MsgSender.SendMsg(client.player, "目前烟花剩余数量：" + (c + count) + "烟花预计可以飞行" + 1344.0 / (FireworkCount - c - count) * Math.sqrt(client.player.getBlockPos().getSquaredDistance(LastDebugPos)), MsgLevel.debug);
            } else {
                elytraCount = d;
                FireworkCount = c + count;
                LastDebugPos = client.player.getBlockPos();
            }
        }
    }

    /**
     * 检查baritone寻路情况
     *
     * @param client 客户端对象
     */
    private static void baritoneChecker(@NotNull MinecraftClient client) {
        if (client.player == null) throw new TaskThread.TaskException("player 为null");
        // baritone寻路失败，等待重置状态或auto log
        if (SegFailed > 25) {
            if (SegFailed > 30) throw new TaskThread.TaskException("baritone寻路异常");
            else if (waitReset) {
                RunAsMainThread(() -> BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().resetState());
                MsgSender.SendMsg(client.player, "SegFailed！正在重置baritone!", MsgLevel.warning);
                waitReset = false;
            }
        }

        // 玩家是不是陷入了原地绕圈？尝试重置baritone或auto log
        if (currentTick % 1000 == 0) {
            if (LastPos != null && client.player.getBlockPos().isWithinDistance(LastPos, 25)) {
                MsgSender.SendMsg(client.player, "SegFailed！spin!", MsgLevel.warning);
                if (spinTimes > 4) throw new TaskThread.TaskException("baritone寻路异常？！疑似原地转圈");
                else {
                    spinTimes++;
                    RunAsMainThread(() -> BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().resetState());
                }
            }
            LastPos = client.player.getBlockPos();
        }
    }

    /**
     * 自动进食
     *
     * @param client 客户端对象
     */
    private static void AutoEating(@NotNull MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) throw new TaskThread.TaskException("null");
        int slot2 = -1;
        // 自动进食，恢复血量
        if (isEating == 0 && !client.player.isInLava() && client.player.getVelocity().length() > 1.4 && (client.player.getHungerManager().getFoodLevel() < 16 || client.player.getHealth() < 15 && client.player.getHungerManager().getFoodLevel() < 20)) {
            MsgSender.SendMsg(client.player, "准备食用", MsgLevel.tip);
            for (int i = 0; i < 8; i++) {
                ItemStack s = client.player.getInventory().getStack(i);
                Item item = s.getItem();
                if (item == Items.GOLDEN_CARROT) {
                    slot2 = i;
                    break;
                }
            }
            if (slot2 == -1) throw new TaskThread.TaskException("没有足够的食物了！");
            else {
                int finalSlot = slot2;
                RunAsMainThread(() -> {
                    client.player.getInventory().selectedSlot = finalSlot;
                    client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(finalSlot));
                    client.options.useKey.setPressed(true);
                });
                isEating = 1;
            }
        }
        if (isEating > 0) {
            isEating++;
            if (client.player == null) {
                client.options.useKey.setPressed(false);
                isEating = 0;
                return;
            }
            if (client.player.getVelocity().length() < 0.9) {
                // 速度过低，放弃吃食物，防止影响baritone寻路
                MsgSender.SendMsg(client.player, "放弃吃食物！！！", MsgLevel.tip);
                client.options.useKey.setPressed(false);
                isEating = 0;
                if (client.interactionManager != null)
                    RunAsMainThread(() -> client.interactionManager.stopUsingItem(client.player));
            } else if (isEating == 40) {
                client.options.useKey.setPressed(false);
                isEating = 0;
            }
        }
    }

    /**
     * 自动检测并逃离烟花
     *
     * @param client 客户端对象
     */
    private static void AutoEscapeLava(@NotNull MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) throw new TaskThread.TaskException("null");
        // 在岩浆中吗？
        if (inFireTick >= 0) {
            if (client.player.isInLava()) {
                inFireTick++;
            } else {
                inFireTick = 0;
            }
        } else {
            inFireTick++;
        }

        if (inFireTick == -1) {
            inFireTick = 0;
            if (client.player == null) return;
            if (client.player.isInLava()) throw new TaskThread.TaskException("逃离岩浆失败！");
        }


        if (inFireTick > 20 || inFireTick > 5 && !client.player.isFallFlying()) {
            // 位于岩浆中？自动逃离岩浆
            inFireTick = -45;
            // 打开鞘翅
            client.options.jumpKey.setPressed(true);
            TaskThread.delay(3);
            client.options.jumpKey.setPressed(false);
            if (client.player != null && client.interactionManager != null) {
                MsgSender.SendMsg(client.player, "位于岩浆中，已鞘翅打开", MsgLevel.tip);
                // 抬头
                client.player.setPitch(-90);
                PlayerInventory inv = client.player.getInventory();

                // 找烟花
                int slots = -1;
                for (int i = 0; i < 8; i++) {
                    ItemStack s = inv.getStack(i);
                    if (s.isEmpty() || s.getItem() == Items.FIREWORK_ROCKET) slots = i;
                }
                if (slots == -1) throw new TaskThread.TaskException("找不到烟花");
                else {
                    // 切换到烟花所在格子
                    int finalSlots = slots;
                    RunAsMainThread(() -> {
                        client.player.getInventory().selectedSlot = finalSlots;
                        client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(finalSlots));
                        client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                    });

                    // 使用烟花
                    MsgSender.SendMsg(client.player, "已使用烟花！", MsgLevel.tip);

                }

            }
        }
    }


    /**
     * 用于检查玩家头顶有无方块阻挡玩家起跳
     *
     * @param checkY 上方检查格数
     * @return 阻挡方块列表
     */
    private static @NotNull List<BlockPos> getPotentialJumpBlockingBlocks(int checkY) {
        List<BlockPos> nonAirBlocks = new ArrayList<>();

        // 获取客户端和玩家实例
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return nonAirBlocks;
        }

        World world = client.world;
        ClientPlayerEntity player = client.player;

        // 获取玩家头部位置
        Vec3d playerPos = player.getPos();
        double headX = playerPos.x;
        double headZ = playerPos.z;
        double headY = playerPos.y + player.getStandingEyeHeight();

        // 计算头顶上方一个方块层的Y坐标
        int aboveY = (int) Math.floor(headY) + 1;

        // 获取头部所在方块的整数坐标
        int baseX = (int) Math.floor(headX);
        int baseZ = (int) Math.floor(headZ);

        // 计算头部在方块内的相对偏移量
        double offsetX = headX - baseX;
        double offsetZ = headZ - baseZ;

        // 要检查的方块位置集合
        Set<BlockPos> blocksToCheck = new HashSet<>();
        blocksToCheck.add(new BlockPos(baseX, aboveY, baseZ));
        // 根据X方向偏移决定是否检查相邻方块
        if (offsetX > 0.7) {  // 靠近东侧边缘
            blocksToCheck.add(new BlockPos(baseX + 1, aboveY, baseZ));
        } else if (offsetX < 0.3) {  // 靠近西侧边缘
            blocksToCheck.add(new BlockPos(baseX - 1, aboveY, baseZ));
        }

        // 根据Z方向偏移决定是否检查相邻方块
        if (offsetZ > 0.7) {  // 靠近南侧边缘
            blocksToCheck.add(new BlockPos(baseX, aboveY, baseZ + 1));
        } else if (offsetZ < 0.3) {  // 靠近北侧边缘
            blocksToCheck.add(new BlockPos(baseX, aboveY, baseZ - 1));
        }

        // 检查对角线方向（当同时靠近两个方向的边缘时）
        if (offsetX > 0.7 && offsetZ > 0.7) {  // 东南角
            blocksToCheck.add(new BlockPos(baseX + 1, aboveY, baseZ + 1));
        } else if (offsetX > 0.7 && offsetZ < 0.3) {  // 东北角
            blocksToCheck.add(new BlockPos(baseX + 1, aboveY, baseZ - 1));
        } else if (offsetX < 0.3 && offsetZ > 0.7) {  // 西南角
            blocksToCheck.add(new BlockPos(baseX - 1, aboveY, baseZ + 1));
        } else if (offsetX < 0.3 && offsetZ < 0.3) {  // 西北角
            blocksToCheck.add(new BlockPos(baseX - 1, aboveY, baseZ - 1));
        }

        // 检查每个方块，只返回不是空气的方块
        for (BlockPos pos : blocksToCheck) {
            for (int i = 0; i < checkY; i++) {
                if (world.isInBuildLimit(pos.add(0, i, 0)) && !world.isAir(pos.add(0, i, 0))) {
                    nonAirBlocks.add(pos);
                }
            }
        }

        return nonAirBlocks;
    }


    /**
     * 自动起跳检查和实现
     *
     * @param client 客户端对象
     * @param x      x坐标
     * @param z      z坐标
     */
    private static void AutoJumping(@NotNull MinecraftClient client, int x, int z) {
        if (client.player == null || client.getNetworkHandler() == null || client.interactionManager == null)
            throw new TaskThread.TaskException("null");
        // 自动起跳次数过多，可能遭遇意外情况，auto log
        if (jumpingTimes > 7) throw new TaskThread.TaskException("自动起跳数量过多，可能是baritone异常！");
        // 玩家掉落在地上时自动起跳，继续鞘翅飞行
        if (!arrived && !isJumping && !isJumpBlockedByBlock && isEating == 0 && client.player.isOnGround() && !client.player.isFallFlying() && !client.player.isInLava() && client.player.getVelocity().getX() < 0.01 && client.player.getVelocity().getZ() < 0.01) {
            List<BlockPos> bp = RunAsMainThread(() -> getPotentialJumpBlockingBlocks(1));
            if (jumpingTimes > 4) client.player.setHeadYaw(client.player.getHeadYaw() + 180);
            if (!bp.isEmpty()) {
                // 玩家头顶有方块阻挡，调用baritone API清除
                isJumpBlockedByBlock = true;
                RunAsMainThread(() -> {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
                    BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess().clearArea(new BlockPos((int) Math.floor(client.player.getPos().getX() - 0.3), bp.getFirst().getY(), (int) Math.floor(client.player.getPos().getZ() - 0.3)), new BlockPos((int) Math.floor(client.player.getPos().getX() - 0.3) + 1, bp.getFirst().getY() + 1, (int) Math.floor(client.player.getPos().getZ() - 0.3) + 1));
                });
                for (int i = 0; i < 200; i++) {
                    if (client.player == null) return;
                    if (!BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess().isActive()) {
                        oldPos = client.player.getBlockPos();
                        RunAsMainThread(() -> BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().pathTo(new BlockPos(x, 0, z)));
                        scheduleTask((s3, a3) -> isJumpBlockedByBlock = false, 1, 0, 10, 100000000);
                        jumpingTimes++;
                        return;
                    }
                    TaskThread.delay(1);
                }
                throw new TaskThread.TaskException("挖掘异常！");
            }
            isJumping = true;
            if (currentTick - LastJumpingTick < 100) {
                jumpingTimes++;
            } else {
                jumpingTimes = 0;
            }
            LastJumpingTick = currentTick;
            // 玩家暂时无法起跳，尝试使用烟花辅助起跳
            if (jumpingTimes > 4 && RunAsMainThread(() -> getPotentialJumpBlockingBlocks(8).isEmpty())) {
                MsgSender.SendMsg(client.player, "自动烟花起跳！" + jumpingTimes, MsgLevel.tip);
                client.player.setPitch(-90);
                client.options.jumpKey.setPressed(true);
                scheduleTask((ss, aa) -> client.options.jumpKey.setPressed(false), 1, 0, 1, 100000000);
                double y = client.player.getPos().getY();
                for (int i = 0; i < 8; i++) {
                    if (client.player.getPos().getY() > y + 1 || i == 7) {
                        client.options.jumpKey.setPressed(true);
                        scheduleTask((s3, a3) -> client.options.jumpKey.setPressed(false), 1, 0, 1, 100000);
                        TaskThread.delay(2);
                        if (client.player == null) return;
                        PlayerInventory inv = client.player.getInventory();
                        int slots = -1;
                        for (int j = 0; j < 8; j++) {
                            ItemStack s5 = inv.getStack(j);
                            if (s5.isEmpty() || s5.getItem() == Items.FIREWORK_ROCKET) slots = j;
                        }
                        if (slots == -1) throw new TaskThread.TaskException("找不到烟花");
                        else {
                            int finalSlots = slots;
                            RunAsMainThread(() -> {
                                client.player.getInventory().selectedSlot = finalSlots;
                                client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(finalSlots));
                                client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                            });
                            MsgSender.SendMsg(client.player, "已使用烟花！", MsgLevel.tip);
                        }
                        isJumping = false;
                        return;
                    }
                    TaskThread.delay(1);
                }
            } else {
                MsgSender.SendMsg(client.player, "自动起跳！" + jumpingTimes, MsgLevel.tip);
                client.player.setPitch(-30);
                client.options.jumpKey.setPressed(true);
                for (int i = 0; i < 8; i++) {
                    if (client.player == null) return;
                    if (client.player.getVelocity().getY() < -0.1 || i == 7) {

                        client.options.jumpKey.setPressed(false);
                        TaskThread.delay(1);
                        client.options.jumpKey.setPressed(true);
                        TaskThread.delay(1);
                        client.options.jumpKey.setPressed(false);
                        isJumping = false;
                    }
                    TaskThread.delay(1);
                }
            }

        }
    }

    /**
     * 鞘翅主函数
     *
     * @param client 客户端对象
     * @param x      目的地X坐标
     * @param z      目的地Z坐标
     * @throws TaskThread.TaskException 任务异常
     * @throws TaskThread.TaskCanceled  任务中止
     */
    static void ElytraTask(@NotNull MinecraftClient client, int x, int z) throws TaskThread.TaskException, TaskThread.TaskCanceled {
        // 重置各个状态
        resetStatus();

        // 设置baritone
        BaritoneAPI.getSettings().elytraAutoJump.value = false;
        BaritoneAPI.getSettings().logger.value = (var1x -> {
            try {
                MessageIndicator var2 = BaritoneAPI.getSettings().useMessageTag.value ? Helper.MESSAGE_TAG : null;
                MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(var1x, null, var2);
                // 检测是否提示分段错误
                if (MinecraftClient.getInstance().player != null && (var1x.getString().contains("Failed to compute path to destination") || var1x.getString().contains("Failed to recompute segment") || var1x.getString().contains("Failed to compute next segment"))) {
                    if (currentTick - LastSegFailedTick < 5) SegFailed++;
                    else SegFailed = 1;
                    LastSegFailedTick = currentTick;
                    waitReset = true;
                }
            } catch (Throwable var3) {
                LOGGER.warn("Failed to log message to chat: {}", var1x.getString(), var3);
            }
        });

        if (client.player == null) throw new TaskThread.TaskException("player为null");

        oldPos = client.player.getBlockPos();
        BlockPos segPos = oldPos;
        client.player.setPitch(-30);
        // 调用baritoneAPI,准备开始寻路
        RunAsMainThread(() -> BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().pathTo(new BlockPos(x, 0, z)));
        TaskThread.delay(15);

        // 跳起
        client.options.jumpKey.setPressed(true);
        TaskThread.delay(7);
        client.options.jumpKey.setPressed(false);
        TaskThread.delay(1);
        client.options.jumpKey.setPressed(true);
        TaskThread.delay(1);
        client.options.jumpKey.setPressed(false);

        if (client.player == null || client.getNetworkHandler() == null || client.interactionManager == null || client.world == null)
            throw new TaskThread.TaskException("飞行任务失败！null异常！");
        // 鞘翅守护任务
        while (true) {
            if (client.player == null) throw new TaskThread.TaskException("飞行任务失败！null异常！");
            boolean result = RunAsMainThread(() -> BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().isActive());
            if (!result && !isJumpBlockedByBlock) {
                // 此时，到达阶段目的地，准备获取补给
                arrivedTarget(client, segPos);
                RunAsMainThread(() -> BaritoneAPI.getSettings().logger.value = BaritoneAPI.getSettings().logger.defaultValue);
                return;
            } else {
                FireworkChecker(client, x, z);
                baritoneChecker(client);
                AutoEating(client);
                AutoEscapeLava(client);
                AutoJumping(client, x, z);

                if (!arrived && (client.player.getBlockPos().isWithinDistance(new BlockPos(x, 0, z), 3500) || noFirework) && Objects.equals(client.world.getBiome(client.player.getBlockPos()).getKey().map(RegistryKey::getValue).orElse(null), Identifier.of("minecraft", "nether_wastes")) && !client.player.isOnFire()) {
                    scheduleTask((s6, a6) -> {
                        if (client.player != null)
                            BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().pathTo(client.player.getBlockPos());
                    }, 1, 0, 15, 1000);
                    MsgSender.SendMsg(client.player, "位于下界荒地，提前降落！", MsgLevel.tip);
                    arrived = true;
                }
            }
            TaskThread.delay(1);
        }
    }

}
