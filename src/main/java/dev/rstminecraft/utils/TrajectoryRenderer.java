package dev.rstminecraft.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class TrajectoryRenderer {
    public static void init() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(TrajectoryRenderer::renderTrajectory);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(TrajectoryRenderer::renderPos);
    }
    public static  List<Vec3d> path = new ArrayList<>();
    private static final List<BlockPos> MARKED_POSITIONS = new ArrayList<>();


    public static void markPos(BlockPos pos) {
        if (!MARKED_POSITIONS.contains(pos)) {
            MARKED_POSITIONS.add(pos);
        }
    }

    public static void drawTrajectory(List<Vec3d> path2){
        path = path2;
    }

    public static void clear() {
        MARKED_POSITIONS.clear();
        path.clear();
    }

    private static void renderPos(@NotNull WorldRenderContext context){
        if (MARKED_POSITIONS.isEmpty()) return;

        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) return;

        // 获取相机的偏移量，将坐标系对齐到世界坐标
        double camX = context.camera().getPos().x;
        double camY = context.camera().getPos().y;
        double camZ = context.camera().getPos().z;

        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());

        for (BlockPos pos : MARKED_POSITIONS) {
            matrices.push();
            // 平移矩阵到对应的方块位置
            matrices.translate(pos.getX() - camX, pos.getY() - camY, pos.getZ() - camZ);

            // 绘制一个红色透明立方体轮廓
            WorldRenderer.drawBox(matrices, buffer, 0, 0, 0, 1, 1, 1, 1f, 0f, 0f, 1f);

            matrices.pop();
        }
    }
    private static void renderTrajectory(@NotNull WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || path.isEmpty()) return;

        MatrixStack matrices = context.matrixStack();
        Vec3d cameraPos = context.camera().getPos();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);

        matrices.push();
        // Translate to world space relative to camera
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (Vec3d point : path) {
            // Drawing bright cyan line
            buffer.vertex(matrix, (float)point.x, (float)point.y, (float)point.z)
                    .color(0, 255, 255, 255);
        }

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(2.0f); // Make it visible
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();


    }
}
