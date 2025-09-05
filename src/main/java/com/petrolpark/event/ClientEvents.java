package com.petrolpark.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.petrolpark.core.contamination.Contaminant;
import com.petrolpark.core.contamination.IContamination;
import com.petrolpark.core.contamination.ItemContamination;
import com.petrolpark.core.item.decay.ItemDecay;
import com.petrolpark.imaginaryregion.ImaginaryRegion;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.joml.Vector2f;
import org.joml.Vector3f;

@EventBusSubscriber(Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {

        // Decay Times
        ItemDecay.getTooltip(event.getItemStack()).ifPresent(event.getToolTip()::add);

        // Item Contamination
        if (event.getEntity() == null) return; // Don't populate the Intrinsics map before the world has been loaded, as the Tags have not been loaded
        IContamination<?, ?> contamination = ItemContamination.get(event.getItemStack());
        contamination.streamShownContaminants().map(Contaminant::getNameColored).forEach(event.getToolTip()::add);
        contamination.streamShownAbsentContaminants().map(Contaminant::getAbsentNameColored).forEach(event.getToolTip()::add);
    };

    @SubscribeEvent
    public static void renderRegions(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            Minecraft minecraft = Minecraft.getInstance();
            ClientLevel level = minecraft.level;
            if (level == null) return;

            ProfilerFiller profilerFiller = level.getProfiler();
            profilerFiller.popPush("imaginary_regions");

            PoseStack poseStack = event.getPoseStack();
            Camera camera = event.getCamera();
            DeltaTracker deltaTracker = event.getPartialTick();

            RenderBuffers renderBuffers = minecraft.renderBuffers();
            MultiBufferSource.BufferSource bufferSource = renderBuffers.bufferSource();

            Vec3 vec3 = camera.getPosition();

            double d0 = vec3.x();
            double d1 = vec3.y();
            double d2 = vec3.z();

            for ( ImaginaryRegion region: ImaginaryRegion.regions) {
                Vector3f pos = region.getPosition();
                Vector2f rot = region.getOrientation();

                poseStack.pushPose();
                poseStack.translate(pos.x - d0, pos.y - d1, pos.z - d2);
                poseStack.mulPose(Axis.XP.rotationDegrees(rot.x));
                poseStack.mulPose(Axis.YP.rotationDegrees(rot.y));
                region.render(deltaTracker.getGameTimeDeltaPartialTick(false), poseStack, bufferSource,
                        LevelRenderer.getLightColor(level, region.getBlockPosition()), OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
        }
    }

    public static boolean isGameActive() {
        Minecraft mc = Minecraft.getInstance();
		return !(mc.level == null || mc.player == null);
	};
};
