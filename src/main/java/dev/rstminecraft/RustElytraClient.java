package dev.rstminecraft;

//提示：本代码完全由RSTminecraft 编写，部分内容可能不符合编程规范，有意愿者请修改。
//关于有人质疑后门的事，请自行阅读代码，你要是能找出后门，我把电脑吃了。
//本模组永不收费，永远开源，许可证相关事项正在考虑。

//文件解释：本文件为模组主文件。

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.rstminecraft.utils.MsgLevel;
import dev.rstminecraft.utils.RSTMsgSender;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static dev.rstminecraft.utils.RSTConfig.getBoolean;
import static dev.rstminecraft.utils.RSTConfig.loadConfig;
import static dev.rstminecraft.utils.RSTTask.scheduleTask;
import static dev.rstminecraft.utils.RSTTask.tick;

public class RustElytraClient implements ClientModInitializer {

    public static final Logger MODLOGGER = LoggerFactory.getLogger("rust-elytra-client");
    public static final AtomicReference<TaskHolder<?>> currentTask = new AtomicReference<>();
    static final Object ThreadLock = new Object();
    public static int currentTick = 0;
    static RSTMsgSender MsgSender;
    static @NotNull ModStatuses ModStatus = ModStatuses.idle;
    private static KeyBinding openCustomScreenKey;
    FabricLoader loader = FabricLoader.getInstance();

    // mixin相关变量
    public static boolean cameraMixinSwitch = false;
    public static float fixedYaw = 0f,fixedPitch = 0f;

    // timer mixin相关
    public static float timerMultiplier = 1f;

    @Override
    public void onInitializeClient() {
        boolean hasBaritone = loader.isModLoaded("baritone") || loader.isModLoaded("baritone-meteor");
        if (!hasBaritone) {
            MODLOGGER.error(" [MyMod] 需要安装 Baritone（baritone / baritone-meteor 任选其一");
        }
        loadConfig(FabricLoader.getInstance().getConfigDir().resolve("RSTConfig.json"));
        MsgSender = new RSTMsgSender(getBoolean("DisplayDebug", false) ? MsgLevel.debug : MsgLevel.info);
        // GUI按键注册
        openCustomScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("RST Auto Elytra Mod主界面", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "RST Auto Elytra Mod"));

        // tick末事件注册
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            currentTick++;
            if (TaskThread.isThreadRunning()) {
                synchronized (ThreadLock) {
                    ThreadLock.notify();
                }
                try {
                    while (TaskThread.getModThread() != null && !(TaskThread.getModThread().getState() == Thread.State.TERMINATED || TaskThread.getModThread().getState() == Thread.State.TIMED_WAITING)) {
                        TaskHolder<?> task = currentTask.get();
                        if (task != null) {
                            task.execute();
                            currentTask.set(null);
                        }
                    }
                } catch (NullPointerException e) {
                    if (!e.getMessage().contains("TaskThread.getState")) throw e;
                }
            }
            tick();
            if (openCustomScreenKey.isPressed())
                client.setScreen(new RSTScr(MinecraftClient.getInstance().currentScreen, getBoolean("FirstUse", true)));
        });
        // 本命令用于进入主菜单GUI(也可以通过上方按键进入)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ClientCommandManager.literal("RSTAutoElytraMenu").executes(context -> {
            scheduleTask((s, a) -> MinecraftClient.getInstance().setScreen(new RSTScr(MinecraftClient.getInstance().currentScreen, getBoolean("FirstUse", true))), 1, 0, 2, 100000);
            return 1;
        })));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ClientCommandManager.literal("RSTDebug-IDLE").executes(context -> {
            ModStatus = ModStatuses.idle;
            return 1;
        })));
        // 命令开启飞行，不推荐，优先使用GUI
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(ClientCommandManager.literal("RSTAutoElytra").then(ClientCommandManager.argument("x", IntegerArgumentType.integer()).then(ClientCommandManager.argument("z", IntegerArgumentType.integer()).executes(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                return 0;
            }
            int targetX = IntegerArgumentType.getInteger(context, "x");
            int targetZ = IntegerArgumentType.getInteger(context, "z");

            if (TaskThread.getModThread() != null) return 0;
            MsgSender.SendMsg(client.player, "任务开始！", MsgLevel.warning);
            TaskThread.StartModThread_ELY(getBoolean("isAutoLog", true), getBoolean("isAutoLogOnSeg1", false), targetX, targetZ);
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
        idle, running, canceled
    }

    public static class TaskHolder<T> {
        private final Supplier<T> lambda;
        private final CountDownLatch latch;
        private T result;
        private Throwable error;

        TaskHolder(Supplier<T> lambda, CountDownLatch latch) {
            this.lambda = lambda;
            this.latch = latch;
        }

        void execute() {
            try {
                this.result = lambda.get();
            } catch (Throwable t) {
                this.error = t;
            } finally {
                latch.countDown();
            }
        }

        T getResult() {
            if (error != null) throw new TaskThread.TaskException(error.getMessage());
            return result;
        }
    }
}