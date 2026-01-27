package dev.rstminecraft;

import baritone.api.BaritoneAPI;
import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static dev.rstminecraft.RSTFireballProtect.isHittingFireball;
import static dev.rstminecraft.RSTTask.scheduleTask;
import static dev.rstminecraft.RustElytraClient.ModStatus;
import static dev.rstminecraft.RustElytraClient.MsgSender;

class RSTSupplyTask {

    private static int putTryTimes = 0;

    /**
     * 寻找可放置末影箱或潜影盒的位置
     *
     * @param player 玩家对象
     * @return 可以放置目标方块的坐标
     */
    private static @Nullable BlockPos findPlaceTarget(@NotNull ClientPlayerEntity player) {
        BlockPos origin = player.getBlockPos();
        World world = player.getWorld();

        // 搜索范围：以玩家为中心的 3×3×3 区域
        int radius = 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // 不能与玩家重合
                    if (0 == dx && 0 == dz) continue;
                    BlockPos target = origin.add(dx, dy, dz);

                    // 目标必须是空气或可替换方块（如草）
                    if (!world.getBlockState(target).isAir() && !world.getBlockState(target).isReplaceable()) continue;

                    // 下方必须是实心方块
                    BlockPos below = target.down();
                    if (!world.getBlockState(below).isSolidBlock(world, below)) continue;
                    // 上方必须是空气
                    BlockPos up = target.up();
                    if (!world.getBlockState(up).isAir()) continue;

                    return target;
                }
            }
        }
        return null;
    }

    /**
     * 在玩家物品栏搜索物品
     *
     * @param player        玩家对象
     * @param SearchingItem 寻找的物品
     * @return 目标物品数量
     */
    private static int countItemInInventory(@NotNull ClientPlayerEntity player, @NotNull Item SearchingItem) {
        int count = 0;
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack stack = inventory.main.get(i);
            if (stack.getItem() == SearchingItem.asItem()) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * 让玩家看向某一个坐标
     *
     * @param player 玩家对象
     * @param target 需要看向的目标方块坐标
     */
    private static void lookAt(@NotNull ClientPlayerEntity player, @NotNull Vec3d target) {
        Vec3d eyes = player.getEyePos();
        Vec3d dir = target.subtract(eyes);

        double distXZ = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dir.y, distXZ));

        player.setYaw(yaw);
        player.setPitch(pitch);
    }

    /**
     * 检查当前玩家屏幕是不是容器屏幕
     *
     * @param client        客户端对象
     * @param ContainerName 目标容器名
     * @param checkNoEmpty  是否检查容器是否为空
     * @return 返回目标屏幕信息(handled, handler, screen)
     */
    private static @NotNull RSTScreen ContainerScreenChecker(@NotNull MinecraftClient client, @NotNull String ContainerName, boolean checkNoEmpty) {
        Screen screen = client.currentScreen;
        if (!(screen instanceof HandledScreen<?> handled)) {
            // 当前不是带处理器的 GUI（不是容器界面） -> 重置等待状态
            return new RSTScreen(false, null, null);
        }

        boolean isObjectContainer = false;
        String titleStr = handled.getTitle().getString();
        if (ContainerName.equalsIgnoreCase(titleStr)) isObjectContainer = true;
        if (!isObjectContainer) {
            // 不是目标容器
            return new RSTScreen(false, null, null);
        }
        ScreenHandler handler = handled.getScreenHandler();
        if (checkNoEmpty) {
            int totalSlots = handler.slots.size();
            int containerSlots = totalSlots - 36;
            if (containerSlots <= 0) containerSlots = 27;
            boolean anyNonEmpty = false;
            for (int i = 0; i < containerSlots; i++) {
                Slot s = handler.getSlot(i);
                if (s != null) {
                    ItemStack st = s.getStack();
                    if (st != null && !st.isEmpty()) {
                        anyNonEmpty = true;
                        break;
                    }
                }
            }
            if (!anyNonEmpty) {
                return new RSTScreen(false, null, null);
            }
        }
        return new RSTScreen(true, handled, handler);

    }

    /**
     * 搜索合适的潜影盒,多tick寻找
     *
     * @param ContainerName 当前容器名
     * @param client        客户端实体
     * @param recall        找到后回调
     * @param recallArgs    回调函数变量
     */
    private static void SearchShulkerInContainer(@NotNull String ContainerName, @NotNull MinecraftClient client, @NotNull SearchingConsumer recall, Object... recallArgs) {
        scheduleTask((self, args) -> {
            if (ModStatus == RustElytraClient.ModStatuses.canceled) {
                ModStatus = RustElytraClient.ModStatuses.idle;
                self.repeatTimes = 0;
                return;
            }
            // 尝试寻找
            // -2:没有合适补给
            // -1:容器打开失败
            int result = SupplyFinder(client, ContainerName);
            if (result != -1 && result != -2) {
                self.repeatTimes = 0;
                recall.accept(client, result, recallArgs);
            } else if (result == -2) {
                self.repeatTimes = 0;
                if (client.player != null) {
                    MsgSender.SendMsg(client.player, ContainerName + "中没有符合条件的补给。", MsgLevel.error);
                }
                recall.accept(client, -1, recallArgs);
            } else if (self.repeatTimes == 0) {
                if (client.player != null) {
                    MsgSender.SendMsg(client.player, ContainerName + "中没有物品,或容器没有打开。", MsgLevel.error);
                }
                recall.accept(client, -1, recallArgs);
            }


        }, 1, 20, 1, 100, 0);
    }

    /**
     * 检查潜影盒是否符合烟花条件
     *
     * @param inner 潜影盒内部物品列表
     * @return 是否符合条件
     */
    private static boolean isValidContainerWithFirework(@NotNull DefaultedList<ItemStack> inner) {
        int num = 0;

        // 遍历内存储的每个物品堆栈
        for (ItemStack stack : inner) {
            if (stack.isEmpty()) {
                continue;  // 跳过空的物品堆栈
            }

            // 判断烟花
            if (stack.getItem() == Items.FIREWORK_ROCKET) {
                num += stack.getCount();  // 累加烟花数量
            }
        }

        // 判断是否符合条件：超过21组烟花
        return num >= 21 * 64;
    }

    /**
     * 检查 潜影盒是否符合鞘翅条件
     *
     * @param inner 潜影盒内部物品列表
     * @return 是否符合条件
     */
    private static boolean isValidContainerWithElytra(@NotNull DefaultedList<ItemStack> inner) {
        int num = 0;
        // 遍历内存储的每个物品堆栈
        for (ItemStack stack : inner) {
            if (stack.isEmpty()) {
                continue;  // 跳过空的物品堆栈
            }
            // 判断烟花
            if (stack.getItem() == Items.ELYTRA) {
                if (stack.getDamage() > 15) continue;
                var enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
                if (enchantments != null) {
                    var enc = enchantments.getEnchantments();
                    for (RegistryEntry<Enchantment> entry : enc) {
                        // 耐久三
                        if (entry.getKey().isPresent() && entry.getKey().get() == Enchantments.UNBREAKING && EnchantmentHelper.getLevel(entry, stack) == 3) {
                            num += stack.getCount();
                        }

                    }
                }
            }
        }

        // 判断是否符合条件: 超过5个鞘翅
        return num >= 5;
    }

    /**
     * 用于在末影箱中寻找符合条件的补给箱
     *
     * @param client        客户端对象
     * @param searchingName 当前容器名
     * @return 补给位于哪一个格子
     */
    private static int SupplyFinder(@NotNull MinecraftClient client, @NotNull String searchingName) {
        if (client.player == null) {
            // 玩家不存在时重置状态
            return -1;
        }

        RSTScreen screen = ContainerScreenChecker(client, searchingName, true);
        if (!screen.result) {
            return -1;
        }
        // 到这里，准备读取目标容器的容器槽并打印所有目标物品内部物品
        StringBuilder sb = new StringBuilder();
        int totalSlots = screen.handler.slots.size();
        int containerSlots = totalSlots - 36;
        if (containerSlots <= 0) containerSlots = 27;
        int Slot = -2;
        for (int i = 0; i < containerSlots; i++) {
            Slot s = screen.handler.getSlot(i);
            if (s == null) continue;
            ItemStack stack = s.getStack();
            if (stack == null || stack.isEmpty()) continue;
            // 判断是否为目标物品（BlockItem）
            if (stack.getItem() instanceof BlockItem bi) {
                if (bi.getBlock() instanceof ShulkerBoxBlock) {
                    ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
                    sb.append(bi.getName().getString());
                    sb.append("\n");
                    if (container != null) {
                        DefaultedList<ItemStack> inner = DefaultedList.ofSize(27, ItemStack.EMPTY);
                        container.copyTo(inner); // 把 component 内容拷贝到列表
                        if (inner.isEmpty()) {
                            sb.append("  (shulker is empty))").append("\n");
                        } else {
                            boolean isEmpty = true;
                            for (ItemStack innerStack : inner) {
                                if (!innerStack.isEmpty()) {
                                    isEmpty = false;
                                    break;
                                }
                            }
                            if (isEmpty) sb.append("  (shulker is empty)").append("\n");
                            else {
                                boolean isValid = isValidContainerWithFirework(inner) && isValidContainerWithElytra(inner);
                                if (isValid) {
                                    // 符合补给盒条件
                                    sb.append("  (slot ").append(i).append(") - valid shulker").append("\n");
                                    sb.append("  Find shulker on slot").append(i).append("\n");

                                    Slot = i;
                                    break;
                                } else sb.append("  (slot ").append(i).append(") - invalid shulker").append("\n");


                            }
                        }
                    } else {
                        sb.append("  (shulker is null...warning...)").append("\n");
                    }
                }
            }
        }

        // 没找到任何目标物品
        if (sb.isEmpty()) {
            MsgSender.SendMsg(client.player, searchingName + "中没有目标物品。", MsgLevel.debug);
        } else {
            String[] lines = sb.toString().split("\n");
            for (String line : lines) {
                if (line == null || line.isEmpty()) continue;
                MsgSender.SendMsg(client.player, line, MsgLevel.debug);
            }
        }

        return Slot;
    }

    /**
     * 将废弃的补给箱放回末影箱
     *
     * @param client                客户端对象
     * @param enderChestName        末影箱名称
     * @param shulkerSlotID         潜影盒在当前玩家物品栏中的位置
     * @param EnderChestShulkerSlot 应该放回末影箱中的位置
     * @return 是否放回成功
     */
    private static boolean putBackShulker(@NotNull MinecraftClient client, @NotNull String enderChestName, int shulkerSlotID, int EnderChestShulkerSlot) {
        if (client.player == null || client.interactionManager == null) {
            return false;
        }

        RSTScreen screen = ContainerScreenChecker(client, enderChestName, true);
        if (!screen.result) {
            return false;
        }
        client.interactionManager.clickSlot(screen.handler.syncId, shulkerSlotID, 0, SlotActionType.PICKUP, client.player);
        client.interactionManager.clickSlot(screen.handler.syncId, EnderChestShulkerSlot, 0, SlotActionType.PICKUP, client.player);
        client.interactionManager.clickSlot(screen.handler.syncId, shulkerSlotID, 0, SlotActionType.PICKUP, client.player);
        screen.handled.close();
        return true;
    }

    /**
     * 用于从补给箱中拿出补给
     *
     * @param client        客户端对象
     * @param searchingName 补给箱名称
     * @return 是否拿出成功
     */
    private static boolean putOutSupplyMain(@NotNull MinecraftClient client, @NotNull String searchingName) {
        if (client.player == null) {
            // 玩家不存在时重置状态
            return false;
        }

        RSTScreen screen = ContainerScreenChecker(client, searchingName, true);
        if (!screen.result) {
            return false;
        }
        for (int i = 0; i < 27; i++) {
            if (client.interactionManager != null) {
                if (screen.handler.getSlot(i).getStack().getItem() == Items.GOLDEN_CARROT) {
                    for (int j = 0; j < 9; j++) {
                        ItemStack s = client.player.getInventory().getStack(j);
                        if (s.getItem() == Items.GOLDEN_CARROT) {
                            client.interactionManager.clickSlot(screen.handler.syncId, i, 0, SlotActionType.PICKUP, client.player);
                            client.interactionManager.clickSlot(screen.handler.syncId, 54 + j, 0, SlotActionType.PICKUP, client.player);
                            client.interactionManager.clickSlot(screen.handler.syncId, i, 0, SlotActionType.PICKUP, client.player);
                            break;
                        }
                    }
                    continue;
                }
                client.interactionManager.clickSlot(screen.handler.syncId, i, 0, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(screen.handler.syncId, 27 + i, 0, SlotActionType.PICKUP, client.player);
                client.interactionManager.clickSlot(screen.handler.syncId, i, 0, SlotActionType.PICKUP, client.player);
            }
        }
        return true;
    }

    /**
     * 多次尝试拿出补给，避免网络延迟造成影响，并调用BaritoneAPI挖掘潜影盒和末影箱
     *
     * @param client                客户端实体
     * @param targetPos             补给盒位置
     * @param searchingName         补给盒标题
     * @param enderChestPos         末影箱位置
     * @param enderChestName        末影箱名称
     * @param ShulkerSlot           补给盒所在槽位
     * @param EnderChestShulkerSlot 补给盒在原来末影箱中的槽位
     */
    private static void putOutSupply(@NotNull MinecraftClient client, @NotNull BlockPos targetPos, @NotNull String searchingName, @NotNull BlockPos enderChestPos, @NotNull String enderChestName, int ShulkerSlot, int EnderChestShulkerSlot) {
        scheduleTask((self, args) -> {
            if (ModStatus == RustElytraClient.ModStatuses.canceled) {
                ModStatus = RustElytraClient.ModStatuses.idle;
                return;
            }
            if (client.player == null || client.interactionManager == null) {
                ModStatus = RustElytraClient.ModStatuses.failed;
                return;
            }
            MsgSender.SendMsg(client.player, "尝试放置补给箱成功，现在打开补给箱", MsgLevel.tip);
            BlockHitResult chestHit = new BlockHitResult(Vec3d.ofCenter(targetPos), Direction.UP, targetPos, false);
            ActionResult result1 = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, chestHit);
            client.player.swingHand(Hand.MAIN_HAND);
            if (result1.isAccepted()) {
                MsgSender.SendMsg(client.player, "打开补给箱成功", MsgLevel.tip);
                scheduleTask((self1, args1) -> {
                    if (ModStatus == RustElytraClient.ModStatuses.canceled) {
                        ModStatus = RustElytraClient.ModStatuses.idle;
                        self1.repeatTimes = 0;
                        return;
                    }
                    boolean result = putOutSupplyMain(client, searchingName);
                    if (result) {
                        self1.repeatTimes = 0;
                        MsgSender.SendMsg(client.player, "取出补给物品成功", MsgLevel.tip);
                        int count = 0;
                        PlayerInventory inventory = client.player.getInventory();
                        for (int i = 0; i < inventory.main.size(); i++) {
                            ItemStack stack = inventory.main.get(i);
                            Item item = stack.getItem();
                            if (item instanceof BlockItem && ((BlockItem) item).getBlock() instanceof ShulkerBoxBlock) {
                                count += stack.getCount();
                            }
                        }
                        if (client.world == null) {
                            MsgSender.SendMsg(client.player, "世界异常", MsgLevel.error);
                            ModStatus = RustElytraClient.ModStatuses.failed;
                            return;
                        }
                        // 调用BaritoneAPI挖掉用过的补给盒
                        Block block = client.world.getBlockState(targetPos).getBlock();
                        BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mine(count + 1, block);
                        int finalCount = count;
                        putTryTimes = -1;
                        scheduleTask((s, a) -> {
                            if (ModStatus == RustElytraClient.ModStatuses.canceled) {
                                BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().cancel();
                                ModStatus = RustElytraClient.ModStatuses.idle;
                                s.repeatTimes = 0;
                                return;
                            }
                            if (isHittingFireball())
                                s.repeatTimes = 40;
                            if (!BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive()) {
                                int newCount = 0;
                                for (int i = 0; i < inventory.main.size(); i++) {
                                    ItemStack stack = inventory.main.get(i);
                                    Item item = stack.getItem();
                                    if (item instanceof BlockItem && ((BlockItem) item).getBlock() instanceof ShulkerBoxBlock) {
                                        newCount += stack.getCount();
                                    }
                                }
                                if (newCount < finalCount + 1) {
                                    putTryTimes++;
                                    if (putTryTimes > 8) {
                                        MsgSender.SendMsg(client.player, "挖掘补给箱失败！", MsgLevel.error);
                                        s.repeatTimes = 0;
                                        ModStatus = RustElytraClient.ModStatuses.failed;
                                    }
                                    return;
                                }

                                MsgSender.SendMsg(client.player, "挖掘完毕，放回末影箱", MsgLevel.tip);

                                lookAt(client.player, Vec3d.ofCenter(enderChestPos));
                                scheduleTask((ss, aa) -> {
                                    // 构造点击数据：点击下面方块的顶面
                                    // 打开末影箱
                                    if (ModStatus == RustElytraClient.ModStatuses.canceled) {
                                        ModStatus = RustElytraClient.ModStatuses.idle;
                                        self1.repeatTimes = 0;
                                        return;
                                    }
                                    if (client.player == null) {
                                        ModStatus = RustElytraClient.ModStatuses.failed;
                                        s.repeatTimes = 0;
                                        return;
                                    }
                                    BlockHitResult hitResult = new BlockHitResult(Vec3d.ofCenter(enderChestPos), Direction.UP, enderChestPos, false);
                                    if (client.interactionManager == null) {
                                        MsgSender.SendMsg(client.player, "client.interactionManager为null", MsgLevel.error);
                                        ModStatus = RustElytraClient.ModStatuses.failed;
                                        s.repeatTimes = 0;
                                        return;
                                    }
                                    ActionResult result2 = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hitResult);
                                    client.player.swingHand(Hand.MAIN_HAND);

                                    if (result2.isAccepted()) {
                                        MsgSender.SendMsg(client.player, "打开末影箱完毕", MsgLevel.tip);
                                        scheduleTask((s3, a3) -> {
                                            if (ModStatus == RustElytraClient.ModStatuses.canceled) {
                                                s3.repeatTimes = 0;
                                                ModStatus = RustElytraClient.ModStatuses.idle;
                                                return;
                                            }
                                            //尝试放回末影箱
                                            if (putBackShulker(client, enderChestName, ShulkerSlot + 54, EnderChestShulkerSlot)) {
                                                MsgSender.SendMsg(client.player, "放回完毕", MsgLevel.tip);
                                                s3.repeatTimes = 0;
                                                int enderCount = countItemInInventory(client.player, Items.ENDER_CHEST);
                                                int obsidianCount = countItemInInventory(client.player, Items.OBSIDIAN);
                                                // 调用BaritoneAPI挖掉末影箱
                                                BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mine(obsidianCount + 1, client.world.getBlockState(enderChestPos).getBlock());
                                                scheduleTask((s2, a2) -> {
                                                    if (ModStatus == RustElytraClient.ModStatuses.canceled) {
                                                        ModStatus = RustElytraClient.ModStatuses.idle;
                                                        s2.repeatTimes = 0;
                                                        BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().cancel();
                                                        return;
                                                    }
                                                    if (isHittingFireball())
                                                        s.repeatTimes = 100;
                                                    int enderCount2 = countItemInInventory(client.player, Items.ENDER_CHEST);
                                                    if (!BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive() || enderCount2 > enderCount) {
                                                        MsgSender.SendMsg(client.player, "补给任务圆满完成！", MsgLevel.tip);
                                                        BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().cancel();
                                                        s2.repeatTimes = 0;
                                                        ModStatus = RustElytraClient.ModStatuses.success;

                                                    } else if (s2.repeatTimes == 0 || !client.player.getBlockPos().isWithinDistance(enderChestPos, 5)) {
                                                        MsgSender.SendMsg(client.player, "挖取末影箱失败！危险！", MsgLevel.error);
                                                        BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().cancel();
                                                        s2.repeatTimes = 0;
                                                        ModStatus = RustElytraClient.ModStatuses.failed;
                                                    }

                                                }, 1, 100, 0, 100);

                                            } else if (s3.repeatTimes == 0) {
                                                MsgSender.SendMsg(client.player, "放回异常", MsgLevel.error);
                                                ModStatus = RustElytraClient.ModStatuses.failed;
                                            }


                                        }, 1, 20, 0, 80);
                                    } else {
                                        MsgSender.SendMsg(client.player, "打开失败", MsgLevel.error);
                                        ModStatus = RustElytraClient.ModStatuses.failed;
                                    }
                                }, 1, 0, 3, 10000000);
                                s.repeatTimes = 0;
                            } else if (s.repeatTimes == 0) {
                                MsgSender.SendMsg(client.player, "挖掘异常？取消挖掘", MsgLevel.error);
                                BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().cancel();
                                ModStatus = RustElytraClient.ModStatuses.failed;
                            }
                        }, 1, 40, 1, 20);

                    } else if (self1.repeatTimes == 0) {
                        MsgSender.SendMsg(client.player, "补给物品取出失败！", MsgLevel.error);
                        ModStatus = RustElytraClient.ModStatuses.failed;
                    }

                }, 1, 20, 1, 100, 0);

            } else {
                MsgSender.SendMsg(client.player, "打开补给箱失败！", MsgLevel.error);
                ModStatus = RustElytraClient.ModStatuses.failed;
            }
        }, 9, 0, 5, 100);
    }

    /**
     * 整理物品栏，并检查玩家是否有足够的物资
     *
     * @param client 客户端实体
     * @return 玩家是否有足够的物资
     */
    private static boolean SortAndCheckInv(@NotNull MinecraftClient client) {
        PlayerEntity player = client.player;
        if (player == null || client.interactionManager == null) return false;
        client.setScreen(new InventoryScreen(player));
        Screen screen2 = client.currentScreen;
        if (!(screen2 instanceof HandledScreen<?> handled2)) {
            // 当前不是带处理器的 GUI（不是容器界面） -> 重置等待状态
            MsgSender.SendMsg(client.player, "窗口异常！", MsgLevel.warning);
            return false;
        }

        // 整理物品栏
        ScreenHandler handler2 = handled2.getScreenHandler();
        for (int i = 9; i < 36; i++) {
            Slot s = handler2.getSlot(i);
            if (s == null) continue;
            ItemStack stack = s.getStack();
            if (stack == null || stack.isEmpty()) continue;
            Item item = stack.getItem();
            while (!(item != Items.NETHERITE_PICKAXE && item != Items.DIAMOND_PICKAXE && item != Items.NETHERITE_SWORD && item != Items.DIAMOND_SWORD && item != Items.ENDER_CHEST && item != Items.GOLDEN_CARROT && item != Items.TOTEM_OF_UNDYING)) {
                if (item == Items.NETHERITE_PICKAXE || item == Items.DIAMOND_PICKAXE) {
                    // 镐放到快捷栏第一格
                    if (player.getInventory().getStack(0).getItem() == Items.DIAMOND_PICKAXE || player.getInventory().getStack(0).getItem() == Items.NETHERITE_PICKAXE) {
                        break;
                    }
                    client.interactionManager.clickSlot(handler2.syncId, i, 0, SlotActionType.PICKUP, player);
                    client.interactionManager.clickSlot(handler2.syncId, 36, 0, SlotActionType.PICKUP, player);
                    client.interactionManager.clickSlot(handler2.syncId, i, 0, SlotActionType.PICKUP, player);

                } else if (item == Items.NETHERITE_SWORD || item == Items.DIAMOND_SWORD) {
                    // 剑放到第二格
                    if (player.getInventory().getStack(1).getItem() == Items.DIAMOND_SWORD || player.getInventory().getStack(1).getItem() == Items.NETHERITE_SWORD) {
                        break;
                    }
                    client.interactionManager.clickSlot(handler2.syncId, i, 0, SlotActionType.PICKUP, player);
                    client.interactionManager.clickSlot(handler2.syncId, 37, 0, SlotActionType.PICKUP, player);
                    client.interactionManager.clickSlot(handler2.syncId, i, 0, SlotActionType.PICKUP, player);
                } else if (item == Items.ENDER_CHEST) {
                    // 末影箱放到第三格
                    client.interactionManager.clickSlot(handler2.syncId, i, 0, SlotActionType.PICKUP, player);
                    client.interactionManager.clickSlot(handler2.syncId, 38, 0, SlotActionType.PICKUP, player);
                    client.interactionManager.clickSlot(handler2.syncId, i, 0, SlotActionType.PICKUP, player);
                    if (stack.getItem() == Items.ENDER_CHEST) break;
                } else if (item == Items.TOTEM_OF_UNDYING) {
                    // 图腾放到第四和第五格
                    if (player.getInventory().getStack(3).getItem() == Items.TOTEM_OF_UNDYING) {
                        if (player.getInventory().getStack(4).getItem() == Items.TOTEM_OF_UNDYING) break;
                        client.interactionManager.clickSlot(handler2.syncId, i, 0, SlotActionType.PICKUP, player);
                        client.interactionManager.clickSlot(handler2.syncId, 40, 0, SlotActionType.PICKUP, player);
                        client.interactionManager.clickSlot(handler2.syncId, i, 0, SlotActionType.PICKUP, player);
                    } else {
                        client.interactionManager.clickSlot(handler2.syncId, i, 0, SlotActionType.PICKUP, player);
                        client.interactionManager.clickSlot(handler2.syncId, 39, 0, SlotActionType.PICKUP, player);
                        client.interactionManager.clickSlot(handler2.syncId, i, 0, SlotActionType.PICKUP, player);
                    }
                } else {
                    // 金胡萝卜放到第六格
                    client.interactionManager.clickSlot(handler2.syncId, i, 0, SlotActionType.PICKUP, player);
                    client.interactionManager.clickSlot(handler2.syncId, 41, 0, SlotActionType.PICKUP, player);
                    client.interactionManager.clickSlot(handler2.syncId, i, 0, SlotActionType.PICKUP, player);
                    if (stack.getItem() == Items.GOLDEN_CARROT) break;

                }
                item = stack.getItem();
            }
        }
        handled2.close();

        // 检查物品栏
        int enderChestCount = 0;
        boolean pickaxe = false;
        boolean sword = false;
        int goldenCarrotCount = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (s.getItem() == Items.NETHERITE_PICKAXE || s.getItem() == Items.DIAMOND_PICKAXE) pickaxe = true;
            else if (s.getItem() == Items.NETHERITE_SWORD || s.getItem() == Items.DIAMOND_SWORD) sword = true;
            else if (s.getItem() == Items.ENDER_CHEST) enderChestCount += s.getCount();
            else if (s.getItem() == Items.GOLDEN_CARROT) goldenCarrotCount += s.getCount();
        }
        if (enderChestCount > 2 && pickaxe && sword && goldenCarrotCount > 15) return true;
        MsgSender.SendMsg(client.player, "没有足够的物资！", MsgLevel.error);
        return false;
    }

    /**
     * 走到方块中央(行走过程中每tick调用一次)，并放下末影箱
     *
     * @param client 客户端对象
     * @param task   任务(以在行走完成后取消每tick的调用)
     */
    static void autoPlace(@NotNull MinecraftClient client, @NotNull RSTTask task) {
        if (client.player == null) return;

        BlockPos footBlock = client.player.getBlockPos();
        Vec3d CenterPos = new Vec3d(footBlock.getX() + 0.5, client.player.getY(), footBlock.getZ() + 0.5);
        Vec3d current = client.player.getPos();
        Vec3d delta = CenterPos.subtract(current);
        if (ModStatus == RustElytraClient.ModStatuses.canceled) {
            client.options.forwardKey.setPressed(false);
            ModStatus = RustElytraClient.ModStatuses.idle;
            task.repeatTimes = 0;
            return;
        }

        // 到达方块中心则停止
        if (Math.abs(delta.x) < 0.2 && Math.abs(delta.z) < 0.2) {
            task.repeatTimes = 0;
            client.options.forwardKey.setPressed(false);
            MsgSender.SendMsg(client.player, "行走完成", MsgLevel.tip);
            scheduleTask((ss, aa) -> {
                ClientPlayerEntity player = client.player;
                if (player == null || client.interactionManager == null) {
                    ModStatus = RustElytraClient.ModStatuses.failed;
                    return;
                }
                if (!SortAndCheckInv(client)) {
                    ModStatus = RustElytraClient.ModStatuses.failed;
                    return;
                }

                // 找末影箱
                String ContainerName = "";
                int slot = -1;
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (!stack.isEmpty() && stack.getItem() == Items.ENDER_CHEST) {
                        slot = i;
                        ContainerName = stack.getItem().getName().getString();
                        break;
                    }
                }
                if (slot == -1) {
                    MsgSender.SendMsg(client.player, "快捷栏没有末影箱", MsgLevel.error);
                    ModStatus = RustElytraClient.ModStatuses.failed;
                    return;
                }

                // 找目标位置
                BlockPos targetPos = findPlaceTarget(player);
                if (targetPos == null) {
                    MsgSender.SendMsg(client.player, "附近没有合适的位置放置末影箱", MsgLevel.error);
                    ModStatus = RustElytraClient.ModStatuses.failed;
                    return;
                }

                if (client.getNetworkHandler() == null) {
                    MsgSender.SendMsg(client.player, "client.getNetworkHandler()为null", MsgLevel.error);
                    ModStatus = RustElytraClient.ModStatuses.failed;
                    return;
                }

                // 切换到末影箱所在槽位
                player.getInventory().selectedSlot = slot;
                client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));

                // 让玩家看向目标方块中心
                lookAt(player, Vec3d.ofCenter(targetPos));
                String finalContainerName1 = ContainerName;

                scheduleTask((ss2, aa2) -> {
                    // 构造点击数据：点击下面方块的顶面
                    BlockPos support = targetPos.down();
                    Vec3d hitPos = Vec3d.ofCenter(support).add(0, 0.5, 0);
                    BlockHitResult hitResult = new BlockHitResult(hitPos, Direction.UP, support, false);

                    // 交互放置
                    ActionResult result = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
                    player.swingHand(Hand.MAIN_HAND);

                    if (result.isAccepted()) {
                        MsgSender.SendMsg(client.player, "放置成功", MsgLevel.tip);
                        scheduleTask((self, args) -> {
                            if (ModStatus == RustElytraClient.ModStatuses.canceled) {
                                ModStatus = RustElytraClient.ModStatuses.idle;
                                return;
                            }
                            if (client.interactionManager == null) {
                                ModStatus = RustElytraClient.ModStatuses.failed;
                                return;
                            }
                            player.swingHand(Hand.MAIN_HAND);
                            MsgSender.SendMsg(client.player, "尝试放置末影箱成功，现在打开末影箱", MsgLevel.tip);
                            BlockHitResult chestHit = new BlockHitResult(Vec3d.ofCenter(targetPos), Direction.UP, targetPos, false);
                            ActionResult result1 = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, chestHit);
                            player.swingHand(Hand.MAIN_HAND);
                            if (result1.isAccepted()) {
                                MsgSender.SendMsg(client.player, "打开容器成功:" + args[0], MsgLevel.tip);
                                // 现在在末影箱里寻找补给盒
                                SearchShulkerInContainer((String) args[0], client, (cli, res, a1) -> {
                                    if (ModStatus == RustElytraClient.ModStatuses.canceled) {
                                        ModStatus = RustElytraClient.ModStatuses.idle;
                                        return;
                                    }
                                    if (res == -1) {
                                        MsgSender.SendMsg(client.player, "寻找补给失败！", MsgLevel.error);
                                        ModStatus = RustElytraClient.ModStatuses.failed;
                                    } else {
                                        if (client.getNetworkHandler() == null || client.interactionManager == null) {
                                            MsgSender.SendMsg(client.player, "interactionManager为null！", MsgLevel.error);
                                            ModStatus = RustElytraClient.ModStatuses.failed;
                                            return;
                                        }
                                        MsgSender.SendMsg(client.player, "寻找补给成功！", MsgLevel.tip);
                                        // 准备取出潜影盒
                                        RSTScreen screen = ContainerScreenChecker(client, (String) args[0], false);
                                        if (!screen.result) {
                                            // 不是目标容器
                                            MsgSender.SendMsg(client.player, "界面异常！", MsgLevel.error);
                                            ModStatus = RustElytraClient.ModStatuses.failed;
                                            return;
                                        }
                                        // 找可以用来放潜影盒的槽位
                                        int slot2 = -1;
                                        for (int j = 0; j < 9; j++) {
                                            ItemStack stack2 = client.player.getInventory().getStack(j);
                                            if (stack2.isEmpty() || stack2.getItem() != Items.ENDER_CHEST && stack2.getItem() != Items.DIAMOND_PICKAXE && stack2.getItem() != Items.NETHERITE_PICKAXE && stack2.getItem() != Items.DIAMOND_SWORD && stack2.getItem() != Items.NETHERITE_SWORD && stack2.getItem() != Items.GOLDEN_CARROT && stack2.getItem() != Items.TOTEM_OF_UNDYING) {
                                                slot2 = j;
                                                break;
                                            }
                                        }

                                        if (client.interactionManager != null && slot2 != -1) {
                                            client.interactionManager.clickSlot(screen.handler.syncId, res, 0, SlotActionType.PICKUP, client.player);
                                            client.interactionManager.clickSlot(screen.handler.syncId, 54 + slot2, 0, SlotActionType.PICKUP, client.player);
                                            client.interactionManager.clickSlot(screen.handler.syncId, res, 0, SlotActionType.PICKUP, client.player);
                                            MsgSender.SendMsg(client.player, "取出成功！", MsgLevel.tip);
                                            screen.handled.close();
                                            int finalSlot = slot2;

                                            scheduleTask((self2, args2) -> {
                                                if (ModStatus == RustElytraClient.ModStatuses.canceled) {
                                                    ModStatus = RustElytraClient.ModStatuses.idle;
                                                    return;
                                                }
                                                // 找地方放置潜影盒
                                                BlockPos targetPosShulker = findPlaceTarget(player);
                                                if (targetPosShulker == null) {
                                                    MsgSender.SendMsg(client.player, "附近没有合适的位置放置补给！", MsgLevel.error);
                                                    ModStatus = RustElytraClient.ModStatuses.failed;
                                                    return;
                                                }
                                                player.getInventory().selectedSlot = (int) args2[0];
                                                client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket((int) args2[0]));
                                                ItemStack s = client.player.getInventory().getStack((int) args2[0]);
                                                String SearchingNameShulker;
                                                // 潜影盒名称为“潜影盒”或自定义名称
                                                if (s.getComponents().contains(DataComponentTypes.CUSTOM_NAME)) {

                                                    SearchingNameShulker = Objects.requireNonNull(s.get(DataComponentTypes.CUSTOM_NAME)).getString();
                                                } else {
                                                    SearchingNameShulker = Items.SHULKER_BOX.getName().getString();
                                                }
                                                lookAt(player, Vec3d.ofCenter(targetPosShulker));

                                                // 构造点击数据：点击下面方块的顶面
                                                BlockPos supportShulker = targetPosShulker.down();
                                                Vec3d hitPosShulker = Vec3d.ofCenter(supportShulker).add(0, 0.5, 0);
                                                BlockHitResult hitResultShulker = new BlockHitResult(hitPosShulker, Direction.UP, supportShulker, false);
                                                if (client.interactionManager == null) {
                                                    MsgSender.SendMsg(client.player, "interactionManager为null！", MsgLevel.error);
                                                    ModStatus = RustElytraClient.ModStatuses.failed;
                                                }
                                                // 交互放置
                                                ActionResult resultShulker = client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResultShulker);
                                                player.swingHand(Hand.MAIN_HAND);

                                                if (resultShulker.isAccepted()) {
                                                    MsgSender.SendMsg(client.player, "放置补给成功！", MsgLevel.tip);
                                                    putOutSupply(client, targetPosShulker, SearchingNameShulker, targetPos, finalContainerName1, finalSlot, res);
                                                } else {
                                                    MsgSender.SendMsg(client.player, "放置补给失败！", MsgLevel.error);
                                                    ModStatus = RustElytraClient.ModStatuses.failed;
                                                }
                                            }, 10, 0, 5, 50, slot2);
                                        } else {
                                            MsgSender.SendMsg(client.player, "取出失败！可能由于界面异常或快捷栏没有合适位置放置补给！", MsgLevel.error);
                                            ModStatus = RustElytraClient.ModStatuses.failed;
                                        }
                                    }


                                });
                            } else {
                                MsgSender.SendMsg(client.player, "打开失败", MsgLevel.error);
                                ModStatus = RustElytraClient.ModStatuses.failed;
                            }
                        }, 9, 0, 5, 100, finalContainerName1);

                    } else {
                        MsgSender.SendMsg(client.player, "放置失败", MsgLevel.error);
                        ModStatus = RustElytraClient.ModStatuses.failed;
                    }
                }, 1, 0, 3, 1000000);

            }, 1, 0, 2, 10000000);
            return;
        }

        // 调整朝向
        double yaw = Math.toDegrees(Math.atan2(-delta.x, delta.z));
        client.player.setYaw((float) yaw);

        // 模拟按下 W
        client.options.forwardKey.setPressed(true);
    }

    interface SearchingConsumer {
        void accept(MinecraftClient client, int result, Object... args);
    }

    //    用于传输屏幕信息
    static class RSTScreen {
        public boolean result;
        public HandledScreen<?> handled;
        public ScreenHandler handler;

        RSTScreen(boolean result, HandledScreen<?> handled, ScreenHandler handler) {
            this.result = result;
            this.handled = handled;
            this.handler = handler;

        }
    }

}
