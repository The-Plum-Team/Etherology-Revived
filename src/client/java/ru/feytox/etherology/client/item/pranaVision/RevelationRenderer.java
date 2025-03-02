package ru.feytox.etherology.client.item.pranaVision;

import lombok.experimental.UtilityClass;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.item.PranaVisionItem;
import ru.feytox.etherology.magic.aspects.RevelationAspectProvider;
import ru.feytox.etherology.magic.ether.EtherDisplay;

@UtilityClass
public class RevelationRenderer {

    private static final int DATA_TICK_RATE = 5;

    // data cache
    private static @Nullable BlockPos lastTargetPos = null;
    private static @Nullable RevelationData data = null;
    private static @Nullable Vec3d targetPos = null;
    private static @Nullable Vec3d offsetVec = null;
    private static float progress = 0.0f;

    public static void tickData(World world, PlayerEntity player) {
        if (world.getTime() % DATA_TICK_RATE != 0) return;
        if (!PranaVisionItem.isEquipped(player)) return;

        MinecraftClient client = MinecraftClient.getInstance();
        HitResult hitResult = client.crosshairTarget;
        if (hitResult == null) {
            data = null;
            targetPos = null;
            offsetVec = null;
            return;
        }

        refreshData(world, player, hitResult);
    }

    private static void refreshData(World world, PlayerEntity player, HitResult hitResult) {
        data = getSortedAspects(world, hitResult);
        if (data == null)
            data = getChannelData(world, hitResult);

        targetPos = getPosFromTarget(hitResult);
        offsetVec = getOffset(world, player, hitResult);
    }

    @Nullable
    private static RevelationData.Aspects getSortedAspects(World world, HitResult hitResult) {
        var data = RevelationAspectProvider.getData(world, hitResult);
        if (data == null) return null;
        var aspects = data.getFirst();
        Integer limit = data.getSecond();
        if (aspects == null || limit == null) return null;

        return new RevelationData.Aspects(aspects.sorted(true, limit));
    }

    public static void registerRendering() {
        WorldRenderEvents.LAST.register(RevelationRenderer::renderBlockRevelationOverlay);
    }

    private static void renderBlockRevelationOverlay(WorldRenderContext context) {
        if (data == null || data.isEmpty() || targetPos == null || offsetVec == null) return;
        var client = MinecraftClient.getInstance();
        var world = context.world();
        var hitResult = client.crosshairTarget;
        if (world == null || hitResult == null || !PranaVisionItem.isEquipped(client.player)) return;

        if (isNewTarget(hitResult)) {
            progress = 0.0f;
            refreshData(world, client.player, hitResult);
        }

        progress = MathHelper.lerp(0.1f * context.tickCounter().getTickDelta(false), progress, 1.0f);
        var matrices = context.matrixStack();
        if (data == null || data.isEmpty() || targetPos == null || offsetVec == null || matrices == null) return;

        matrices.push();

        var camera = context.camera();
        var cameraPos = camera.getPos();
        var renderPos = targetPos.subtract(cameraPos).add(offsetVec.multiply(progress));
        matrices.translate(renderPos.x, renderPos.y, renderPos.z);
        matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

        data.render(client, matrices, progress);
        matrices.pop();
    }

    @Nullable
    private static RevelationData getChannelData(World world, HitResult hitResult) {
        if (!(hitResult instanceof BlockHitResult blockHit))
            return null;

        var blockEntity = world.getBlockEntity(blockHit.getBlockPos());
        if (!(blockEntity instanceof EtherDisplay display))
            return null;

        return new RevelationData.Ether(display.getDisplayEther(), display.getDisplayMaxEther());
    }

    @Nullable
    private static Vec3d getPosFromTarget(HitResult hitResult) {
        return switch (hitResult.getType()) {
            case MISS, ENTITY -> null;
            case BLOCK -> {
                if (!(hitResult instanceof BlockHitResult target)) yield null;
                yield target.getBlockPos().toCenterPos();
            }
        };
    }

    private static boolean isNewTarget(HitResult hitResult) {
        return switch (hitResult.getType()) {
            case MISS, ENTITY -> true;
            case BLOCK -> {
                if (!(hitResult instanceof BlockHitResult target)) yield true;
                boolean result = !target.getBlockPos().equals(lastTargetPos);
                lastTargetPos = target.getBlockPos();
                yield result;
            }
        };
    }

    private static Vec3d getOffset(World world, PlayerEntity player, HitResult hitResult) {
        return switch (hitResult.getType()) {
            case MISS, ENTITY -> Vec3d.ZERO;
            case BLOCK -> {
                if (!(hitResult instanceof BlockHitResult target)) yield new Vec3d(0, 1, 0);
                if (player.getPitch() > 0.0f && world.getBlockState(target.getBlockPos().up()).isAir()) {
                    yield new Vec3d(0, 1, 0);
                }

                Vec3i sideVec = target.getSide().getVector();
                Vec3d offset = Vec3d.of(sideVec);
                if (!world.getBlockState(target.getBlockPos().add(sideVec)).isAir()) offset = offset.multiply(0.5);
                yield offset;
            }
        };
    }
}
