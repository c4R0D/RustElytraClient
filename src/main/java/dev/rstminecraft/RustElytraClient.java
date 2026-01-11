package dev.rstminecraft;

//提示：本代码完全由RSTminecraft 编写，部分内容可能不符合编程规范，有意愿者请修改。
//关于有人质疑后门的事，请自行阅读代码，你要是能找出后门，我把电脑吃了。
//本模组永不收费，永远开源，许可证相关事项正在考虑。

//文件解释：本文件为模组主文件。


import baritone.api.BaritoneAPI;
import baritone.api.utils.Helper;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static com.mojang.text2speech.Narrator.LOGGER;
import static dev.rstminecraft.RSTConfig.*;
import static dev.rstminecraft.RSTElytraTask.*;
import static dev.rstminecraft.RSTSupplyTask.autoPlace;
import static dev.rstminecraft.RSTTask.scheduleTask;
import static dev.rstminecraft.RSTTask.tick;


public class RustElytraClient implements ClientModInitializer {


    static final int DEFAULT_SEGMENT_LENGTH = 140000; // 每段路径长度

    static @NotNull ModStatuses ModStatus = ModStatuses.idle;
    static int currentTick = 0;


    private static KeyBinding openCustomScreenKey;

    /**
     * 任务失败时调用这个,根据条件决定是否auto log
     *
     * @param client          客户端对象
     * @param isAutoLog       是否auto log
     * @param str             失败原因
     * @param isAutoLogOnSeg1 是否在第一段auto log
     * @param seg             此时任务段数
     */
    static void taskFailed(@NotNull MinecraftClient client, boolean isAutoLog, @NotNull String str, boolean isAutoLogOnSeg1, int seg) {
        if (seg == -1 && isAutoLogOnSeg1 || seg != -1 && isAutoLog) {
            MutableText text = Text.literal("[RSTAutoLog] ");
            text.append(Text.literal(str));
            if (client.player != null) {
                client.player.networkHandler.onDisconnect(new DisconnectS2CPacket(text));
            }
        } else if (client.player != null) {
            client.player.sendMessage(Text.literal("§4任务结束。" + str + "§r"), false);
        }
        ModStatus = ModStatuses.idle;

    }

    /**
     * 计算分段
     *
     * @param client  客户端对象
     * @param targetX 目标地点X轴
     * @param targetZ 目标地点Y轴
     * @param segLen  每段长度
     * @return 分段列表
     */
    static @NotNull List<Vec3i> calculatePathSegments(@NotNull MinecraftClient client, double targetX, double targetZ, double segLen) {
        List<Vec3i> segmentEndpoints = new ArrayList<>();
        if (client.player == null) {
            return new ArrayList<>();
        }
        // 获取玩家当前位置（只使用X和Z）
        Vec3d playerPos = client.player.getPos();
        double startX = playerPos.x;
        double startZ = playerPos.z;

        // 计算到目标点的方向向量（只考虑XZ平面）
        double dx = targetX - startX;
        double dz = targetZ - startZ;

        // 计算总距离（在XZ平面上）
        double totalDistance = Math.sqrt(dx * dx + dz * dz);

        // 如果总距离为0，直接返回空列表
        if (totalDistance == 0) return segmentEndpoints;

        // 计算方向向量的单位向量
        double unitX = dx / totalDistance;
        double unitZ = dz / totalDistance;

        // 计算分段数量
        int segments = (int) Math.ceil(totalDistance / segLen);

        // 生成每个分段的终点坐标（Y坐标设为0）
        for (int i = 1; i <= segments; i++) {
            double currentSegmentLength = Math.min(i * segLen, totalDistance);

            double endX = startX + unitX * currentSegmentLength;
            double endZ = startZ + unitZ * currentSegmentLength;

            segmentEndpoints.add(new Vec3i((int) endX, 0, (int) endZ));
        }

        return segmentEndpoints;
    }


    /**
     * 用于检查玩家头顶有无方块阻挡玩家起跳
     *
     * @param checkY 上方检查格数
     * @return 阻挡方块列表
     */
    public static @NotNull List<BlockPos> getPotentialJumpBlockingBlocks(int checkY) {
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
     * 玩家鞘翅飞行daemon函数,保护玩家并在必要时获取补给
     * 1.自动起跳
     * 2.自动逃离岩浆
     * 3.自动进食
     * 4.过于危险时（情况较少）log
     *
     * @param segments        任务分段
     * @param nowIndex        当前段数
     * @param client          客户端对象
     * @param isAutoLog       是否auto log
     * @param isAutoLogOnSeg1 是否在第一段auto log
     */
    private static void segmentsMainElytra(@NotNull List<Vec3i> segments, int nowIndex, @NotNull MinecraftClient client, boolean isAutoLog, boolean isAutoLogOnSeg1) {
        // 重置各个状态
        resetStatus();

        RSTElytraTask.isAutoLog = isAutoLog;
        RSTElytraTask.isAutoLogOnSeg1 = isAutoLogOnSeg1;
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

        if (client.player == null) {
            taskFailed(client, isAutoLog, "飞行任务失败！player为null！", isAutoLogOnSeg1, nowIndex);
            return;
        }

        oldPos = client.player.getBlockPos();
        BlockPos segPos = oldPos;
        client.player.setPitch(-30);
        // 调用baritoneAPI,准备开始寻路
        BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().pathTo(new BlockPos(segments.get(nowIndex)));
        scheduleTask((s2, a2) -> {
            // 跳起
            client.options.jumpKey.setPressed(true);
            scheduleTask((self2, args2) -> {

                client.options.jumpKey.setPressed(false);
                // 打开鞘翅
                scheduleTask((s3, a3) -> client.options.jumpKey.setPressed(true), 1, 0, 1, 100000);
                scheduleTask((s, a) -> client.options.jumpKey.setPressed(false), 1, 0, 3, 25);

                if (client.player == null || client.getNetworkHandler() == null || client.interactionManager == null || client.world == null) {
                    taskFailed(client, isAutoLog, "飞行任务失败！null异常！", isAutoLogOnSeg1, nowIndex);
                    return;
                }
                client.player.sendMessage(Text.literal("elytra任务创建！"), false);
                // 鞘翅守护任务
                scheduleTask((self, args) -> {
                    if (client.player == null) {
                        taskFailed(client, isAutoLog, "飞行任务失败！player为null！", isAutoLogOnSeg1, nowIndex);
                        self.repeatTimes = 0;
                        return;
                    }
                    if (ModStatus == ModStatuses.canceled) {
                        ModStatus = ModStatuses.idle;
                        self.repeatTimes = 0;
                        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
                        client.player.sendMessage(Text.literal("任务取消"), false);
                        return;
                    }
                    boolean result = BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().isActive();
                    if (!result && !isJumpBlockedByBlock) {
                        // 此时，到达阶段目的地，准备获取补给
                        arrivedTarget(client, segments, nowIndex, self, segPos);
                    } else {
                        // 进行检查
                        if (AutoJumping(client, segments, nowIndex, self)// 自动起跳检查
                                || AutoEscapeLava(client, nowIndex, self)// 自动逃离岩浆
                                || baritoneChecker(client, nowIndex, self)// 检查baritone寻路
                                || FireworkChecker(client, segments, nowIndex, self)// 检查烟花
                                || AutoEating(client, nowIndex, self)// 自动进食
                        ) return;
                        // 下界荒地怪物较少，提前降落领取补给
                        if (!arrived && (client.player.getBlockPos().isWithinDistance(segments.get(nowIndex), 3500) || noFirework) && Objects.equals(client.world.getBiome(client.player.getBlockPos()).getKey().map(RegistryKey::getValue).orElse(null), Identifier.of("minecraft", "nether_wastes"))) {
                            scheduleTask((s6, a6) -> {
                                if (client.player != null)
                                    BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().pathTo(client.player.getBlockPos());
                            }, 1, 0, 15, 1000);
                            client.player.sendMessage(Text.literal("位于下界荒地，提前降落！"), false);
                            arrived = true;
                        }
                    }

                }, 1, -1, 2, 2);
            }, 1, 0, 7, 200);
        }, 15, 0, 15, 300);

    }

    /**
     * 补给任务函数
     *
     * @param segments        任务分段
     * @param nowIndex        当前段数
     * @param client          客户端对象
     * @param isAutoLog       是否auto log
     * @param isAutoLogOnSeg1 是否在第一段auto log
     */
    static void segmentsMainSupply(@NotNull List<Vec3i> segments, int nowIndex, @NotNull MinecraftClient client, boolean isAutoLog, boolean isAutoLogOnSeg1) {
        ModStatus = ModStatuses.running;
        if (client.player == null) {
            taskFailed(client, isAutoLog, "补给任务失败！client为null", isAutoLogOnSeg1, nowIndex - 1);
            return;
        }

        float h = client.player.getHealth();
        // 等待行走到目标位置
        // 行走完毕后,autoPlace内将关闭本任务
        scheduleTask((self, args) -> {
            if (ModStatus == ModStatuses.canceled) {
                self.repeatTimes = 0;
                ModStatus = ModStatuses.idle;
                return;
            }
            autoPlace(client, self);
        }, 1, -1, 0, 10);
        // 等待补给完成
        scheduleTask((self, args) -> {
            if (ModStatus == ModStatuses.canceled) {
                self.repeatTimes = 0;
                return;
            }
            if (client.player == null) {
                self.repeatTimes = 0;
                taskFailed(client, isAutoLog, "补给任务失败！client为null", isAutoLogOnSeg1, nowIndex - 1);
                ModStatus = ModStatuses.canceled;
                return;
            }
            if (client.player.getHealth() < h) {
                self.repeatTimes = 0;

                BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().cancel();
                taskFailed(client, isAutoLog, "补给过程受伤！紧急！", isAutoLogOnSeg1, nowIndex - 1);
                ModStatus = ModStatuses.canceled;
                return;
            }
            switch (ModStatus) {
                case failed -> {
                    // 补给失败
                    client.player.sendMessage(Text.literal("补给任务失败"), false);
                    taskFailed(client, isAutoLog, "补给任务失败！自动退出！", isAutoLogOnSeg1, nowIndex - 1);
                    self.repeatTimes = 0;
                }
                case success -> {
                    // 补给成功，继续飞行
                    client.player.sendMessage(Text.literal("补给任务成功:" + nowIndex), false);
                    client.player.sendMessage(Text.literal("进行下一段飞行任务：" + nowIndex), false);
                    self.repeatTimes = 0;
                    segmentsMainElytra(segments, nowIndex, client, isAutoLog, isAutoLogOnSeg1);
                }
            }
        }, 1, -1, 1, 100);


    }

    @Override
    public void onInitializeClient() {
        loadConfig(FabricLoader.getInstance().getConfigDir().resolve("RSTConfig.json"));
        // GUI按键注册
        openCustomScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("RST Auto Elytra Mod主界面", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "RST Auto Elytra Mod"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            currentTick++;
            tick();
            if (openCustomScreenKey.isPressed())
                client.setScreen(new RSTScr(MinecraftClient.getInstance().currentScreen, getBoolean("FirstUse", true)));
        });
        // 本命令用于进入主菜单GUI(也可以通过上方按键进入)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var dis = dispatcher.register(ClientCommandManager.literal("RSTAutoElytraMenu").executes(context -> {
                scheduleTask((s, a) -> MinecraftClient.getInstance().setScreen(new RSTScr(MinecraftClient.getInstance().currentScreen, getBoolean("FirstUse", true))), 1, 0, 2, 100000);
                return 1;
            }));
            dispatcher.register(ClientCommandManager.literal("raem").redirect(dis));
        });
        // 命令开启飞行，不推荐，优先使用GUI
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ClientCommandManager.literal("RSTAutoElytra").then(ClientCommandManager.argument("x", IntegerArgumentType.integer()).then(ClientCommandManager.argument("z", IntegerArgumentType.integer()).executes(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                return 0;
            }
            int targetX = IntegerArgumentType.getInteger(context, "x");
            int targetZ = IntegerArgumentType.getInteger(context, "z");
            int sl = getInt("SegLength", DEFAULT_SEGMENT_LENGTH);
            List<Vec3i> segments = calculatePathSegments(client, targetX, targetZ, sl);
            if (segments.isEmpty()) {
                client.player.sendMessage(Text.literal("分段失败！"), false);
                return 0;
            }
            client.player.sendMessage(Text.literal("任务开始！补给距离：" + sl), false);
            segmentsMainSupply(segments, 0, client, true, true);
            return 1;
        })))));


        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            // 确保 client.world 为 null 时不崩溃
            if (ModStatus != ModStatuses.idle) {
                ModStatus = ModStatuses.canceled;
            }
        });
    }

    enum ModStatuses {
        idle, success, failed, running, flying, canceled
    }


}




