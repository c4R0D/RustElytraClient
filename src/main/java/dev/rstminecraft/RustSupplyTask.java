package dev.rstminecraft;

import baritone.api.BaritoneAPI;
import dev.rstminecraft.utils.MsgLevel;
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
import static dev.rstminecraft.RustElytraClient.MsgSender;
import static dev.rstminecraft.TaskThread.RunAsMainThread;


public class RustSupplyTask {
    /**
     * 走到方块中央
     *
     * @param client 客户端对象
     */
    private static void WalkingToCenter(@NotNull MinecraftClient client) {
        if (client.player == null) throw new TaskThread.TaskException("Player为null");
        while (true) {
            BlockPos footBlock = client.player.getBlockPos();
            Vec3d CenterPos = new Vec3d(footBlock.getX() + 0.5, client.player.getY(), footBlock.getZ() + 0.5);
            Vec3d current = client.player.getPos();
            Vec3d delta = CenterPos.subtract(current);
            // 到达方块中心则停止
            if (Math.abs(delta.x) < 0.2 && Math.abs(delta.z) < 0.2) {
                client.options.forwardKey.setPressed(false);
                MsgSender.SendMsg(client.player, "行走完成", MsgLevel.tip);
                return;
            }
            // 调整朝向
            double yaw = Math.toDegrees(Math.atan2(-delta.x, delta.z));
            client.player.setYaw((float) yaw);

            // 模拟按下 W
            client.options.forwardKey.setPressed(true);
            TaskThread.delay(1);
        }
    }

    /**
     * 整理物品栏，并检查玩家是否有足够的物资
     *
     * @param client 客户端对象
     */
    private static void SortAndCheckInv(@NotNull MinecraftClient client) {
        RunAsMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null || client.interactionManager == null) throw new TaskThread.TaskException("Player为null");

            client.setScreen(new InventoryScreen(player));
            Screen screen2 = client.currentScreen;
            if (!(screen2 instanceof HandledScreen<?> handled2)) throw new TaskThread.TaskException("窗口异常！");

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
            if (enderChestCount > 2 && pickaxe && sword && goldenCarrotCount > 15) return;
            throw new TaskThread.TaskException("没有足够的物资！");
        });
    }

    /**
     * 在玩家快捷栏寻找物品
     *
     * @param player 玩家对象
     * @param item   寻找的物品
     * @return 物品位置（-1 代表没有）
     */
    private static int findItemInHotBar(@NotNull ClientPlayerEntity player, Item item) {
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                slot = i;
                break;
            }
        }
        return slot;
    }

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
     * 尝试放置并打开某个容器
     *
     * @param client     客户端实体
     * @param targetPos  目标放置位置
     * @param HotBarSlot 容器在快捷栏的位置
     */
    private static void PlaceAndOpenContainer(@NotNull MinecraftClient client, @NotNull BlockPos targetPos, int HotBarSlot) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.getNetworkHandler() == null) throw new TaskThread.TaskException("null");
        RunAsMainThread(() -> {
            // 切换槽位
            player.getInventory().selectedSlot = HotBarSlot;
            client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(HotBarSlot));
            // 看向目标
            lookAt(player, Vec3d.ofCenter(targetPos));
        });
        TaskThread.delay(3);
        // 点击数据
        BlockPos support = targetPos.down();
        Vec3d hitPos = Vec3d.ofCenter(support).add(0, 0.5, 0);
        BlockHitResult hitResult = new BlockHitResult(hitPos, Direction.UP, support, false);

        // 尝试放置
        ActionResult result = RunAsMainThread(() -> {
            if (client.interactionManager == null) throw new TaskThread.TaskException("null");
            player.swingHand(Hand.MAIN_HAND);
            return client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
        });
        // 检查结果
        if (!result.isAccepted()) throw new TaskThread.TaskException("放置失败");

        TaskThread.delay(5);
        OpenContainer(client, targetPos);
    }

    /**
     * 打开某个容器
     *
     * @param client    客户端实体
     * @param targetPos 目标放置位置
     */
    private static void OpenContainer(@NotNull MinecraftClient client, @NotNull BlockPos targetPos) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.getNetworkHandler() == null) throw new TaskThread.TaskException("null");
        // 准备打开
        MsgSender.SendMsg(client.player, "尝试放置末影箱成功，现在打开末影箱", MsgLevel.tip);
        BlockHitResult hitResult2 = new BlockHitResult(Vec3d.ofCenter(targetPos), Direction.UP, targetPos, false);
        ActionResult result = RunAsMainThread(() -> {
            if (client.interactionManager == null) throw new TaskThread.TaskException("null");
            client.player.swingHand(Hand.MAIN_HAND);
            return client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hitResult2);
        });
        player.swingHand(Hand.MAIN_HAND);
        // 检查结果
        if (!result.isAccepted()) throw new TaskThread.TaskException("打开失败");
    }

    /**
     * 检查当前玩家屏幕是不是容器屏幕
     *
     * @param client        客户端对象
     * @param ContainerName 目标容器名
     * @param checkNoEmpty  是否检查容器是否为空
     * @return 返回目标屏幕信息(handled, handler, screen)
     */
    private static @Nullable HandledScreen<?> ContainerScreenChecker(@NotNull MinecraftClient client, @NotNull String ContainerName, boolean checkNoEmpty) {
        Screen screen = client.currentScreen;
        // 不是容器界面
        if (!(screen instanceof HandledScreen<?> handled)) return null;

        // 不是目标容器
        if (!ContainerName.equalsIgnoreCase(handled.getTitle().getString())) return null;

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
                return null;
            }
        }
        return handled;

    }

    /**
     * 打印末影箱中潜影盒的内容物，并判断是否满足条件
     *
     * @param client  客户端对象
     * @param handled 已经打开的末影箱窗口的handled
     * @param checker 一个lambda,接受潜影盒内容物列表,判断是否符合条件
     * @return 符合条件的潜影盒所在位置(-1 代表没有)
     */
    private static int SupplyShulkerFinder(@NotNull MinecraftClient client, @NotNull HandledScreen<?> handled, @NotNull ShulkerInnerChecker checker) {
        if (client.player == null) throw new TaskThread.TaskException("player为null");

        StringBuilder sb = new StringBuilder();
        int totalSlots = handled.getScreenHandler().slots.size();
        int containerSlots = totalSlots - 36;
        if (containerSlots <= 0) containerSlots = 27;
        int Slot = -1;

        for (int i = 0; i < containerSlots; i++) {
            Slot s = handled.getScreenHandler().getSlot(i);
            if (s == null) continue;
            ItemStack stack = s.getStack();
            if (stack == null || stack.isEmpty()) continue;
            // 判断是否为潜影盒
            if (stack.getItem() instanceof BlockItem bi) {
                if (bi.getBlock() instanceof ShulkerBoxBlock) {
                    ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
                    sb.append(bi.getName().getString());
                    sb.append("\n");
                    if (container != null) {
                        DefaultedList<ItemStack> inner = DefaultedList.ofSize(27, ItemStack.EMPTY);
                        container.copyTo(inner); // 把 component 内容拷贝到列表
                        boolean isEmpty = true;
                        for (ItemStack innerStack : inner) {
                            if (!innerStack.isEmpty()) {
                                isEmpty = false;
                                break;
                            }
                        }
                        if (isEmpty) sb.append("  (shulker is empty)").append("\n");
                        else {
                            if (checker.check(inner)) {
                                // 符合补给盒条件
                                sb.append("  (slot ").append(i).append(") - valid shulker").append("\n");
                                sb.append("  Find shulker on slot").append(i).append("\n");
                                Slot = i;
                                break;
                            } else sb.append("  (slot ").append(i).append(") - invalid shulker").append("\n");
                        }

                    } else {
                        sb.append("  (shulker is null...warning...)").append("\n");
                    }
                }
            }
        }

        // 没找到任何目标物品
        if (sb.isEmpty()) {
            MsgSender.SendMsg(client.player, "中没有目标物品。", MsgLevel.debug);
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
     * 在潜影盒内容物列表中寻找目标物品数量
     *
     * @param item  目标物品
     * @param inner 潜影盒内容物列表
     * @return 物品数量
     */
    private static int ShulkerInnerFinder(Item item, @NotNull DefaultedList<ItemStack> inner) {
        int num = 0;
        // 遍历内存储的每个物品堆栈
        for (ItemStack stack : inner) {
            if (stack.isEmpty()) {
                continue;  // 跳过空的物品堆栈
            }
            // 判断是否为查找物品
            if (stack.getItem() == item) {
                num += stack.getCount();
            }
        }
        return num;
    }

    /**
     * 检查一个潜影盒内容物列表中高耐久度、耐久三附魔的鞘翅数量
     *
     * @param inner 潜影盒内容物列表
     * @return 鞘翅数量
     */
    private static int ShulkerElytraFinder(@NotNull DefaultedList<ItemStack> inner) {
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
        return num;
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
     * 从潜影盒窗口中取出补给，特别处理金胡萝卜
     *
     * @param client  客户端对象
     * @param handler 潜影盒窗口欧handler
     */
    private static void PutOutSupply(@NotNull MinecraftClient client, @NotNull ScreenHandler handler) {
        RunAsMainThread(() -> {
            if (client.player == null) throw new TaskThread.TaskException("Player为null");

            for (int i = 0; i < 27; i++) {
                if (client.interactionManager != null) {
                    if (handler.getSlot(i).getStack().getItem() == Items.GOLDEN_CARROT) {
                        for (int j = 0; j < 9; j++) {
                            ItemStack s = client.player.getInventory().getStack(j);
                            if (s.getItem() == Items.GOLDEN_CARROT) {
                                client.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, client.player);
                                client.interactionManager.clickSlot(handler.syncId, 54 + j, 0, SlotActionType.PICKUP, client.player);
                                client.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, client.player);
                                break;
                            }
                        }
                        continue;
                    }
                    client.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, client.player);
                    client.interactionManager.clickSlot(handler.syncId, 27 + i, 0, SlotActionType.PICKUP, client.player);
                    client.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, client.player);
                }
            }
        });
    }

    /**
     * 挖掘用过的潜影盒
     *
     * @param client     客户端对象
     * @param ShulkerPos 潜影盒位置
     */
    private static void mineSupplyShulker(@NotNull MinecraftClient client, BlockPos ShulkerPos) {
        if (client.player == null) throw new TaskThread.TaskException("Player为null");
        int count = 0;
        PlayerInventory inventory = client.player.getInventory();
        for (int i = 0; i < inventory.main.size(); i++) {
            ItemStack stack = inventory.main.get(i);
            Item item = stack.getItem();
            if (item instanceof BlockItem && ((BlockItem) item).getBlock() instanceof ShulkerBoxBlock) {
                count += stack.getCount();
            }
        }
        if (client.world == null) throw new TaskThread.TaskException("世界异常");
        // 调用BaritoneAPI挖掉用过的补给盒
        int targetCount = count + 1;
        RunAsMainThread(() -> {
            Block block = client.world.getBlockState(ShulkerPos).getBlock();
            BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mine(targetCount, block);
        });
        // 等待baritone挖掘
        for (int i = 0; i < 40; i++) {
            if (isHittingFireball()) i = 0;
            if (!BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive()) break;
            if (i == 39) {
                RunAsMainThread(() -> BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().cancel());
                throw new TaskThread.TaskException("挖掘异常？取消挖掘");
            }
            TaskThread.delay(1);
        }

        // 等待捡起潜影盒
        for (int j = 0; j < 10; j++) {
            int newCount = 0;
            for (int i = 0; i < inventory.main.size(); i++) {
                ItemStack stack = inventory.main.get(i);
                Item item = stack.getItem();
                if (item instanceof BlockItem && ((BlockItem) item).getBlock() instanceof ShulkerBoxBlock) {
                    newCount += stack.getCount();
                }
            }
            if (newCount >= targetCount) break;
            if (j == 9) throw new TaskThread.TaskException("挖掘补给箱失败!");
            TaskThread.delay(1);
        }
    }

    /**
     * 挖掘末影箱
     *
     * @param client        客户端对象
     * @param EnderChestPos 末影箱位置
     */
    private static void mineEnderChest(@NotNull MinecraftClient client, BlockPos EnderChestPos) {
        if (client.player == null || client.world == null) throw new TaskThread.TaskException("null");
        int enderCount = countItemInInventory(client.player, Items.ENDER_CHEST);
        int obsidianCount = countItemInInventory(client.player, Items.OBSIDIAN);
        RunAsMainThread(() -> BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mine(obsidianCount + 1, client.world.getBlockState(EnderChestPos).getBlock()));

        // 等待baritone挖掘
        for (int i = 0; i < 100; i++) {
            if (isHittingFireball()) i = 0;
            if (!BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive() || countItemInInventory(client.player, Items.ENDER_CHEST) > enderCount) {
                RunAsMainThread(() -> BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().cancel());
                break;
            }

            if (i == 99 || !client.player.getBlockPos().isWithinDistance(EnderChestPos, 5)) {
                RunAsMainThread(() -> BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().cancel());
                throw new TaskThread.TaskException("挖掘异常？取消挖掘!!");
            }
            TaskThread.delay(1);
        }
        MsgSender.SendMsg(client.player, "挖掘完毕", MsgLevel.tip);
    }

    /**
     * 等待当前界面变为正确容器界面
     *
     * @param client     客户端对象
     * @param ScreenName 容器名称
     * @return 正确的界面handled
     */
    private static @NotNull HandledScreen<?> WaitForScreen(@NotNull MinecraftClient client, @NotNull String ScreenName) {
        HandledScreen<?> handled = null;

        // 等待界面
        for (int i = 0; i < 20; i++) {
            HandledScreen<?> temp = ContainerScreenChecker(client, ScreenName, true);
            if (temp != null) {
                handled = temp;
                break;
            }
            TaskThread.delay(1);
        }
        if (handled == null) throw new TaskThread.TaskException(ScreenName + "疑似打开失败");
        return handled;
    }

    /**
     * 补给主函数
     *
     * @param client 客户端对象
     * @param isXP   是否为经验模式
     * @throws TaskThread.TaskException 通过抛出异常中断
     */
    static void SupplyTask(@NotNull MinecraftClient client, boolean isXP) throws TaskThread.TaskException,TaskThread.TaskCanceled {
        if (client.player == null) throw new TaskThread.TaskException("Player为null");

        // 首先走到方块中央
        WalkingToCenter(client);
        TaskThread.delay(2);

        // 整理物品栏
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null) throw new TaskThread.TaskException("player为null");
        SortAndCheckInv(client);
        TaskThread.delay(2);

        // 寻找末影箱
        int slot = findItemInHotBar(player, Items.ENDER_CHEST);
        if (slot == -1) throw new TaskThread.TaskException("无末影箱");
        String EnderChestName = player.getInventory().getStack(slot).getName().getString();

        // 寻找放置地点
        BlockPos EnderChestTargetPos = findPlaceTarget(player);
        if (EnderChestTargetPos == null) throw new TaskThread.TaskException("附近没有合适的位置放置末影箱");

        // 放置并打开末影箱
        PlaceAndOpenContainer(client, EnderChestTargetPos, slot);
        TaskThread.delay(1);

        // 等待末影箱界面
        HandledScreen<?> EnderChestHandled = WaitForScreen(client, EnderChestName);

        // 末影箱打开成功，准备寻找合适补给
        int SupplySlot = SupplyShulkerFinder(client, EnderChestHandled, (inner) -> isXP ? // 分XP和鞘翅两种模式
                ShulkerInnerFinder(Items.FIREWORK_ROCKET, inner) >= 27 * 64 : // XP模式要求一整盒烟花
                ShulkerInnerFinder(Items.FIREWORK_ROCKET, inner) >= 21 * 64 && ShulkerElytraFinder(inner) >= 5 // 鞘翅模式要求21组烟花和5个耐久三鞘翅
        );
        if (SupplySlot == -1) throw new TaskThread.TaskException("末影箱中没有目标物品");

        // 找可以用来放潜影盒的槽位
        slot = -1;
        for (int j = 0; j < 9; j++) {
            ItemStack stack2 = client.player.getInventory().getStack(j);
            if (stack2.isEmpty() || stack2.getItem() != Items.ENDER_CHEST && stack2.getItem() != Items.DIAMOND_PICKAXE && stack2.getItem() != Items.NETHERITE_PICKAXE && stack2.getItem() != Items.DIAMOND_SWORD && stack2.getItem() != Items.NETHERITE_SWORD && stack2.getItem() != Items.GOLDEN_CARROT && stack2.getItem() != Items.TOTEM_OF_UNDYING) {
                slot = j;
                break;
            }
        }
        if (slot == -1) throw new TaskThread.TaskException("没有快捷栏位置可以用于取出潜影盒");

        // 取出潜影盒
        int ShulkerSlot = slot;
        RunAsMainThread(() -> {
            client.interactionManager.clickSlot(EnderChestHandled.getScreenHandler().syncId, SupplySlot, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(EnderChestHandled.getScreenHandler().syncId, 54 + ShulkerSlot, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(EnderChestHandled.getScreenHandler().syncId, SupplySlot, 0, SlotActionType.PICKUP, client.player);
            EnderChestHandled.close();
            return null;
        });
        MsgSender.SendMsg(client.player, "取出成功！", MsgLevel.tip);

        TaskThread.delay(5);

        // 找潜影盒名称
        ItemStack ShulkerStack = client.player.getInventory().getStack(ShulkerSlot);
        String ShulkerName = ShulkerStack.getComponents().contains(DataComponentTypes.CUSTOM_NAME) ? // 潜影盒名称为“潜影盒”或自定义名称
                Objects.requireNonNull(ShulkerStack.get(DataComponentTypes.CUSTOM_NAME)).getString() : // 自定义名称
                Items.SHULKER_BOX.getName().getString(); // “潜影盒”

        // 找空位放置潜影盒
        BlockPos ShulkerTargetPos = findPlaceTarget(player);
        if (ShulkerTargetPos == null) throw new TaskThread.TaskException("附近没有合适的位置放置潜影盒");

        // 放置并打开潜影盒
        PlaceAndOpenContainer(client, ShulkerTargetPos, ShulkerSlot);
        TaskThread.delay(1);

        // 等待潜影盒窗口
        HandledScreen<?> ShulkerHandled = WaitForScreen(client, ShulkerName);

        // 拿出补给
        PutOutSupply(client, ShulkerHandled.getScreenHandler());
        MsgSender.SendMsg(client.player, "取出补给物品成功", MsgLevel.tip);

        // 取出成功，挖掉潜影盒
        mineSupplyShulker(client, ShulkerTargetPos);

        MsgSender.SendMsg(client.player, "挖掘完毕，放回末影箱", MsgLevel.tip);
        // 重新打开末影箱
        RunAsMainThread(() -> lookAt(client.player, Vec3d.ofCenter(EnderChestTargetPos)));
        TaskThread.delay(3);
        OpenContainer(client, EnderChestTargetPos);

        // 等待末影箱窗口
        HandledScreen<?> EnderChestHandled2 = WaitForScreen(client, EnderChestName);

        // 放回潜影盒
        RunAsMainThread(() -> {
            client.interactionManager.clickSlot(EnderChestHandled2.getScreenHandler().syncId, 54 + ShulkerSlot, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(EnderChestHandled2.getScreenHandler().syncId, SupplySlot, 0, SlotActionType.PICKUP, client.player);
            client.interactionManager.clickSlot(EnderChestHandled2.getScreenHandler().syncId, 54 + ShulkerSlot, 0, SlotActionType.PICKUP, client.player);
            EnderChestHandled2.close();
        });
        MsgSender.SendMsg(client.player, "放回完毕", MsgLevel.tip);

        // 挖掘末影箱
        mineEnderChest(client, EnderChestTargetPos);
        MsgSender.SendMsg(client.player, "补给任务圆满完成！", MsgLevel.tip);
    }

    // 用于检测潜影盒是否符合条件
    private interface ShulkerInnerChecker {
        /**
         * 检测某个潜影盒是否符合条件
         *
         * @param inner 潜影盒内物品列表
         * @return 是否符合条件
         */
        boolean check(DefaultedList<ItemStack> inner);
    }

}
