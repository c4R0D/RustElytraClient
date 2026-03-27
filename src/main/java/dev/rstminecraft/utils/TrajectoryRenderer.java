package dev.rstminecraft.utils;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.*;
import net.minecraft.client.render.debug.DebugRenderer;
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


        for (BlockPos pos : MARKED_POSITIONS) {
            matrices.push();
            // 平移矩阵到对应的方块位置
            matrices.translate(pos.getX() - camX, pos.getY() - camY, pos.getZ() - camZ);

            // 绘制一个红色透明立方体轮廓
            DebugRenderer.drawBox(matrices, consumers, 0, 0, 0, 1, 1, 1, 1f, 0f, 0f, 1f);

            matrices.pop();
        }
    }
    private static void renderTrajectory(@NotNull WorldRenderContext context) {
        MatrixStack matrices = context.matrixStack();
        Vec3d cameraPos = context.camera().getPos();
        VertexConsumerProvider consumers = context.consumers();

        VertexConsumer lineBuffer = consumers.getBuffer(RenderLayer.getLines());

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (int i = 0; i < path.size() - 1; i++) {
            Vec3d start = path.get(i);
            Vec3d end = path.get(i + 1);

            // 起点
            lineBuffer.vertex(matrix, (float)(start.x - cameraPos.x), (float)(start.y - cameraPos.y), (float)(start.z - cameraPos.z))
                    .color(0f, 1f, 1f, 1f)
                    .normal(1f, 1f, 1f);

            // 终点
            lineBuffer.vertex(matrix, (float)(end.x - cameraPos.x), (float)(end.y - cameraPos.y), (float)(end.z - cameraPos.z))
                    .color(0f, 1f, 1f, 1f)
                    .normal(1f, 1f, 1f);
        }

    }
}
