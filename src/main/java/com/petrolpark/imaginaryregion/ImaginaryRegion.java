package com.petrolpark.imaginaryregion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.*;

public class ImaginaryRegion implements BlockAndTintGetter {
    public static List<ImaginaryRegion> regions = new ArrayList<>();

    static final Direction[] DIRECTIONS = Direction.values();

    @NotNull
    public final Level level;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final BlockRenderDispatcher modelBlockRenderer = minecraft.getBlockRenderer();
    private final RandomSource randomSource;
    private final Vector3f position;
    private final Vector2f orientation;
    private final Map<BlockPos, BlockState> blocks;
    private final Map<BlockPos, ModelData> modelDatas;
    private final LightBakery lightBakery;

    public ImaginaryRegion(Vector3f pos, Vector2f orientation, Map<BlockPos, BlockState> blocks) {
        this.position = pos;
        this.orientation = orientation;
        this.blocks = blocks;
        this.modelDatas = new HashMap<>();
        this.lightBakery = new LightBakery();

        assert minecraft.level != null;
        this.level = minecraft.level;
        this.randomSource = level.getRandom();

        for (Map.Entry<BlockPos, BlockState> entry : this.blocks.entrySet()) {
            BlockPos position = entry.getKey();
            BlockState state = entry.getValue();

            if (state.getLightEmission(this, position) > 0) {
                this.lightBakery.addLightSource(position);
            }

            ModelData data = this.level.getModelData(position);
            if (data == ModelData.EMPTY) continue;

            this.modelDatas.put(position, data);
        }

        this.lightBakery.bake(this);
    }

    public Vector3f getPosition() {
        return this.position;
    }

    public BlockPos getBlockPosition() {
        this.position.round();
        return new BlockPos((( int ) this.position.x), (( int ) this.position.y), (( int ) this.position.z));
    }

    public Vector2f getOrientation() {
        return this.orientation;
    }

    public void render(float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        for ( Map.Entry<BlockPos, BlockState> blockEntry : this.blocks.entrySet()) {
            poseStack.pushPose();

            BlockPos pos = blockEntry.getKey();
            BlockPos.MutableBlockPos pos$mutable = pos.mutable();

            BlockState state = blockEntry.getValue();
            BakedModel model = modelBlockRenderer.getBlockModel(state);
            RenderType renderType = model.getRenderTypes(state, randomSource, ModelData.EMPTY).asList().getFirst();

            ModelData modelData = this.modelDatas.get(pos);

            int[] lightLevels = new int[DIRECTIONS.length + 1];

            byte occlusionMask = 0;
            for (Direction direction : DIRECTIONS) {
                pos$mutable.setWithOffset(pos, direction);

                if (Block.shouldRenderFace(state, this, pos, direction, pos$mutable)) {
                    occlusionMask |= ( byte ) (1 << direction.ordinal());
                    lightLevels[direction.ordinal()] = this.lightBakery.getLightLevel(this, state, pos$mutable);
                }
            }

            lightLevels[DIRECTIONS.length] = this.lightBakery.getLightLevel(this, state, pos);

            poseStack.translate(pos$mutable.getX(), pos$mutable.getY(), pos$mutable.getZ());
            this.renderBlockModel(state, model, occlusionMask, poseStack, bufferSource.getBuffer(renderType),
                   renderType, randomSource, modelData, lightLevels, packedOverlay);

            poseStack.popPose();
        }
    }

    private void renderBlockModel(BlockState blockState, BakedModel blockModel, byte occlusionMask, PoseStack poseStack, VertexConsumer buffer, RenderType renderType, RandomSource randomSource, @Nullable ModelData modelData, int[] packedLights, int packedOverlay) {

        for (Direction direction : DIRECTIONS) {
            if ((occlusionMask & (1 << direction.ordinal())) != 0) {
                List<BakedQuad> quads = blockModel.getQuads(blockState, direction, randomSource, modelData == null ? ModelData.EMPTY : modelData, renderType);

                for (BakedQuad quad : quads) {
                    buffer.putBulkData(poseStack.last(), quad, 1f, 1f, 1f, 1f, packedLights[direction.ordinal()], packedOverlay, true);
                }
            }
        }

        List<BakedQuad> quads = blockModel.getQuads(blockState, null, randomSource, modelData == null ? ModelData.EMPTY : modelData, renderType);

        for (BakedQuad quad : quads) {
            buffer.putBulkData(poseStack.last(), quad, 1f, 1f, 1f, 1f, packedLights[DIRECTIONS.length], packedOverlay, true);
        }
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(@NotNull BlockPos blockPos) {
        return null;
    }

    @Override
    public @NotNull BlockState getBlockState(@NotNull BlockPos blockPos) {
        return this.blocks.getOrDefault(blockPos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public @NotNull FluidState getFluidState(@NotNull BlockPos blockPos) {
        return Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public int getHeight() {
        return 32;
    }

    @Override
    public int getMinBuildHeight() {
        return -32;
    }

    static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);

    @Override
    public float getShade(Direction direction, boolean b) {
        return 0;
    }

    @Override
    public @NotNull LevelLightEngine getLightEngine() {
        return this.level.getLightEngine();
    }

    @Override
    public int getBlockTint(@NotNull BlockPos blockPos, @NotNull ColorResolver colorResolver) {
        return this.level.getBlockTint(blockPos,colorResolver);
    }

    @OnlyIn( Dist.CLIENT)
    protected enum AdjacencyInfo {
        DOWN(new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH}, 0.5F, true, new SizeInfo[]{SizeInfo.FLIP_WEST, SizeInfo.SOUTH, SizeInfo.FLIP_WEST, SizeInfo.FLIP_SOUTH, SizeInfo.WEST, SizeInfo.FLIP_SOUTH, SizeInfo.WEST, SizeInfo.SOUTH}, new SizeInfo[]{SizeInfo.FLIP_WEST, SizeInfo.NORTH, SizeInfo.FLIP_WEST, SizeInfo.FLIP_NORTH, SizeInfo.WEST, SizeInfo.FLIP_NORTH, SizeInfo.WEST, SizeInfo.NORTH}, new SizeInfo[]{SizeInfo.FLIP_EAST, SizeInfo.NORTH, SizeInfo.FLIP_EAST, SizeInfo.FLIP_NORTH, SizeInfo.EAST, SizeInfo.FLIP_NORTH, SizeInfo.EAST, SizeInfo.NORTH}, new SizeInfo[]{SizeInfo.FLIP_EAST, SizeInfo.SOUTH, SizeInfo.FLIP_EAST, SizeInfo.FLIP_SOUTH, SizeInfo.EAST, SizeInfo.FLIP_SOUTH, SizeInfo.EAST, SizeInfo.SOUTH}),
        UP(new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH}, 1.0F, true, new SizeInfo[]{SizeInfo.EAST, SizeInfo.SOUTH, SizeInfo.EAST, SizeInfo.FLIP_SOUTH, SizeInfo.FLIP_EAST, SizeInfo.FLIP_SOUTH, SizeInfo.FLIP_EAST, SizeInfo.SOUTH}, new SizeInfo[]{SizeInfo.EAST, SizeInfo.NORTH, SizeInfo.EAST, SizeInfo.FLIP_NORTH, SizeInfo.FLIP_EAST, SizeInfo.FLIP_NORTH, SizeInfo.FLIP_EAST, SizeInfo.NORTH}, new SizeInfo[]{SizeInfo.WEST, SizeInfo.NORTH, SizeInfo.WEST, SizeInfo.FLIP_NORTH, SizeInfo.FLIP_WEST, SizeInfo.FLIP_NORTH, SizeInfo.FLIP_WEST, SizeInfo.NORTH}, new SizeInfo[]{SizeInfo.WEST, SizeInfo.SOUTH, SizeInfo.WEST, SizeInfo.FLIP_SOUTH, SizeInfo.FLIP_WEST, SizeInfo.FLIP_SOUTH, SizeInfo.FLIP_WEST, SizeInfo.SOUTH}),
        NORTH(new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST}, 0.8F, true, new SizeInfo[]{SizeInfo.UP, SizeInfo.FLIP_WEST, SizeInfo.UP, SizeInfo.WEST, SizeInfo.FLIP_UP, SizeInfo.WEST, SizeInfo.FLIP_UP, SizeInfo.FLIP_WEST}, new SizeInfo[]{SizeInfo.UP, SizeInfo.FLIP_EAST, SizeInfo.UP, SizeInfo.EAST, SizeInfo.FLIP_UP, SizeInfo.EAST, SizeInfo.FLIP_UP, SizeInfo.FLIP_EAST}, new SizeInfo[]{SizeInfo.DOWN, SizeInfo.FLIP_EAST, SizeInfo.DOWN, SizeInfo.EAST, SizeInfo.FLIP_DOWN, SizeInfo.EAST, SizeInfo.FLIP_DOWN, SizeInfo.FLIP_EAST}, new SizeInfo[]{SizeInfo.DOWN, SizeInfo.FLIP_WEST, SizeInfo.DOWN, SizeInfo.WEST, SizeInfo.FLIP_DOWN, SizeInfo.WEST, SizeInfo.FLIP_DOWN, SizeInfo.FLIP_WEST}),
        SOUTH(new Direction[]{Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP}, 0.8F, true, new SizeInfo[]{SizeInfo.UP, SizeInfo.FLIP_WEST, SizeInfo.FLIP_UP, SizeInfo.FLIP_WEST, SizeInfo.FLIP_UP, SizeInfo.WEST, SizeInfo.UP, SizeInfo.WEST}, new SizeInfo[]{SizeInfo.DOWN, SizeInfo.FLIP_WEST, SizeInfo.FLIP_DOWN, SizeInfo.FLIP_WEST, SizeInfo.FLIP_DOWN, SizeInfo.WEST, SizeInfo.DOWN, SizeInfo.WEST}, new SizeInfo[]{SizeInfo.DOWN, SizeInfo.FLIP_EAST, SizeInfo.FLIP_DOWN, SizeInfo.FLIP_EAST, SizeInfo.FLIP_DOWN, SizeInfo.EAST, SizeInfo.DOWN, SizeInfo.EAST}, new SizeInfo[]{SizeInfo.UP, SizeInfo.FLIP_EAST, SizeInfo.FLIP_UP, SizeInfo.FLIP_EAST, SizeInfo.FLIP_UP, SizeInfo.EAST, SizeInfo.UP, SizeInfo.EAST}),
        WEST(new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH}, 0.6F, true, new SizeInfo[]{SizeInfo.UP, SizeInfo.SOUTH, SizeInfo.UP, SizeInfo.FLIP_SOUTH, SizeInfo.FLIP_UP, SizeInfo.FLIP_SOUTH, SizeInfo.FLIP_UP, SizeInfo.SOUTH}, new SizeInfo[]{SizeInfo.UP, SizeInfo.NORTH, SizeInfo.UP, SizeInfo.FLIP_NORTH, SizeInfo.FLIP_UP, SizeInfo.FLIP_NORTH, SizeInfo.FLIP_UP, SizeInfo.NORTH}, new SizeInfo[]{SizeInfo.DOWN, SizeInfo.NORTH, SizeInfo.DOWN, SizeInfo.FLIP_NORTH, SizeInfo.FLIP_DOWN, SizeInfo.FLIP_NORTH, SizeInfo.FLIP_DOWN, SizeInfo.NORTH}, new SizeInfo[]{SizeInfo.DOWN, SizeInfo.SOUTH, SizeInfo.DOWN, SizeInfo.FLIP_SOUTH, SizeInfo.FLIP_DOWN, SizeInfo.FLIP_SOUTH, SizeInfo.FLIP_DOWN, SizeInfo.SOUTH}),
        EAST(new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH}, 0.6F, true, new SizeInfo[]{SizeInfo.FLIP_DOWN, SizeInfo.SOUTH, SizeInfo.FLIP_DOWN, SizeInfo.FLIP_SOUTH, SizeInfo.DOWN, SizeInfo.FLIP_SOUTH, SizeInfo.DOWN, SizeInfo.SOUTH}, new SizeInfo[]{SizeInfo.FLIP_DOWN, SizeInfo.NORTH, SizeInfo.FLIP_DOWN, SizeInfo.FLIP_NORTH, SizeInfo.DOWN, SizeInfo.FLIP_NORTH, SizeInfo.DOWN, SizeInfo.NORTH}, new SizeInfo[]{SizeInfo.FLIP_UP, SizeInfo.NORTH, SizeInfo.FLIP_UP, SizeInfo.FLIP_NORTH, SizeInfo.UP, SizeInfo.FLIP_NORTH, SizeInfo.UP, SizeInfo.NORTH}, new SizeInfo[]{SizeInfo.FLIP_UP, SizeInfo.SOUTH, SizeInfo.FLIP_UP, SizeInfo.FLIP_SOUTH, SizeInfo.UP, SizeInfo.FLIP_SOUTH, SizeInfo.UP, SizeInfo.SOUTH});

        final Direction[] corners;
        final boolean doNonCubicWeight;
        final SizeInfo[] vert0Weights;
        final SizeInfo[] vert1Weights;
        final SizeInfo[] vert2Weights;
        final SizeInfo[] vert3Weights;
        private static final AdjacencyInfo[] BY_FACING = Util.make(new AdjacencyInfo[6], (p_111134_) -> {
            p_111134_[Direction.DOWN.get3DDataValue()] = DOWN;
            p_111134_[Direction.UP.get3DDataValue()] = UP;
            p_111134_[Direction.NORTH.get3DDataValue()] = NORTH;
            p_111134_[Direction.SOUTH.get3DDataValue()] = SOUTH;
            p_111134_[Direction.WEST.get3DDataValue()] = WEST;
            p_111134_[Direction.EAST.get3DDataValue()] = EAST;
        });

         AdjacencyInfo(Direction[] corners, float shadeBrightness, boolean doNonCubicWeight, SizeInfo[] vert0Weights, SizeInfo[] vert1Weights, SizeInfo[] vert2Weights, SizeInfo[] vert3Weights) {
            this.corners = corners;
            this.doNonCubicWeight = doNonCubicWeight;
            this.vert0Weights = vert0Weights;
            this.vert1Weights = vert1Weights;
            this.vert2Weights = vert2Weights;
            this.vert3Weights = vert3Weights;
        }

        public static AdjacencyInfo fromFacing(Direction facing) {
            return BY_FACING[facing.get3DDataValue()];
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class AmbientOcclusionFace {
        final float[] brightness = new float[4];
        final int[] lightmap = new int[4];

        public AmbientOcclusionFace() {
        }

        public void calculate(ImaginaryRegion level, BlockState state, BlockPos pos, Direction direction, float[] shape, BitSet shapeFlags, boolean shade) {
            BlockPos blockpos = shapeFlags.get(0) ? pos.relative(direction) : pos;
            AdjacencyInfo modelblockrenderer$adjacencyinfo = AdjacencyInfo.fromFacing(direction);
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
            Cache modelblockrenderer$cache = ImaginaryRegion.CACHE.get();

            blockpos$mutableblockpos.setWithOffset(blockpos, modelblockrenderer$adjacencyinfo.corners[0]);
            BlockState blockstate = level.getBlockState(blockpos$mutableblockpos);

            int i = modelblockrenderer$cache.getLightColor(blockstate, level, blockpos$mutableblockpos);
            float f = modelblockrenderer$cache.getShadeBrightness(blockstate, level, blockpos$mutableblockpos);

            blockpos$mutableblockpos.setWithOffset(blockpos, modelblockrenderer$adjacencyinfo.corners[1]);
            BlockState blockstate1 = level.getBlockState(blockpos$mutableblockpos);

            int j = modelblockrenderer$cache.getLightColor(blockstate1, level, blockpos$mutableblockpos);
            float f1 = modelblockrenderer$cache.getShadeBrightness(blockstate1, level, blockpos$mutableblockpos);

            blockpos$mutableblockpos.setWithOffset(blockpos, modelblockrenderer$adjacencyinfo.corners[2]);
            BlockState blockstate2 = level.getBlockState(blockpos$mutableblockpos);

            int k = modelblockrenderer$cache.getLightColor(blockstate2, level, blockpos$mutableblockpos);
            float f2 = modelblockrenderer$cache.getShadeBrightness(blockstate2, level, blockpos$mutableblockpos);

            blockpos$mutableblockpos.setWithOffset(blockpos, modelblockrenderer$adjacencyinfo.corners[3]);
            BlockState blockstate3 = level.getBlockState(blockpos$mutableblockpos);
            int l = modelblockrenderer$cache.getLightColor(blockstate3, level, blockpos$mutableblockpos);
            float f3 = modelblockrenderer$cache.getShadeBrightness(blockstate3, level, blockpos$mutableblockpos);
            BlockState blockstate4 = level.getBlockState(blockpos$mutableblockpos.setWithOffset(blockpos, modelblockrenderer$adjacencyinfo.corners[0]));
            boolean flag = !blockstate4.isViewBlocking(level, blockpos$mutableblockpos) || blockstate4.getLightBlock(level, blockpos$mutableblockpos) == 0;
            BlockState blockstate5 = level.getBlockState(blockpos$mutableblockpos.setWithOffset(blockpos, modelblockrenderer$adjacencyinfo.corners[1]));
            boolean flag1 = !blockstate5.isViewBlocking(level, blockpos$mutableblockpos) || blockstate5.getLightBlock(level, blockpos$mutableblockpos) == 0;
            BlockState blockstate6 = level.getBlockState(blockpos$mutableblockpos.setWithOffset(blockpos, modelblockrenderer$adjacencyinfo.corners[2]));
            boolean flag2 = !blockstate6.isViewBlocking(level, blockpos$mutableblockpos) || blockstate6.getLightBlock(level, blockpos$mutableblockpos) == 0;
            BlockState blockstate7 = level.getBlockState(blockpos$mutableblockpos.setWithOffset(blockpos, modelblockrenderer$adjacencyinfo.corners[3]));
            boolean flag3 = !blockstate7.isViewBlocking(level, blockpos$mutableblockpos) || blockstate7.getLightBlock(level, blockpos$mutableblockpos) == 0;
            float f4;
            int i1;
            if (!flag2 && !flag) {
                f4 = f;
                i1 = i;
            } else {
                blockpos$mutableblockpos.setWithOffset(blockpos, modelblockrenderer$adjacencyinfo.corners[0]).move(modelblockrenderer$adjacencyinfo.corners[2]);
                BlockState blockstate8 = level.getBlockState(blockpos$mutableblockpos);
                f4 = modelblockrenderer$cache.getShadeBrightness(blockstate8, level, blockpos$mutableblockpos);
                i1 = modelblockrenderer$cache.getLightColor(blockstate8, level, blockpos$mutableblockpos);
            }

            int j1;
            float f5;
            if (!flag3 && !flag) {
                f5 = f;
                j1 = i;
            } else {
                blockpos$mutableblockpos.setWithOffset(blockpos, modelblockrenderer$adjacencyinfo.corners[0]).move(modelblockrenderer$adjacencyinfo.corners[3]);
                BlockState blockstate10 = level.getBlockState(blockpos$mutableblockpos);
                f5 = modelblockrenderer$cache.getShadeBrightness(blockstate10, level, blockpos$mutableblockpos);
                j1 = modelblockrenderer$cache.getLightColor(blockstate10, level, blockpos$mutableblockpos);
            }

            int k1;
            float f6;
            if (!flag2 && !flag1) {
                f6 = f;
                k1 = i;
            } else {
                blockpos$mutableblockpos.setWithOffset(blockpos, modelblockrenderer$adjacencyinfo.corners[1]).move(modelblockrenderer$adjacencyinfo.corners[2]);
                BlockState blockstate11 = level.getBlockState(blockpos$mutableblockpos);
                f6 = modelblockrenderer$cache.getShadeBrightness(blockstate11, level, blockpos$mutableblockpos);
                k1 = modelblockrenderer$cache.getLightColor(blockstate11, level, blockpos$mutableblockpos);
            }

            int l1;
            float f7;
            if (!flag3 && !flag1) {
                f7 = f;
                l1 = i;
            } else {
                blockpos$mutableblockpos.setWithOffset(blockpos, modelblockrenderer$adjacencyinfo.corners[1]).move(modelblockrenderer$adjacencyinfo.corners[3]);
                BlockState blockstate12 = level.getBlockState(blockpos$mutableblockpos);
                f7 = modelblockrenderer$cache.getShadeBrightness(blockstate12, level, blockpos$mutableblockpos);
                l1 = modelblockrenderer$cache.getLightColor(blockstate12, level, blockpos$mutableblockpos);
            }

            int i3 = modelblockrenderer$cache.getLightColor(state, level, pos);
            blockpos$mutableblockpos.setWithOffset(pos, direction);
            BlockState blockstate9 = level.getBlockState(blockpos$mutableblockpos);
            if (shapeFlags.get(0) || !blockstate9.isSolidRender(level, blockpos$mutableblockpos)) {
                i3 = modelblockrenderer$cache.getLightColor(blockstate9, level, blockpos$mutableblockpos);
            }

            float f8 = shapeFlags.get(0) ? modelblockrenderer$cache.getShadeBrightness(level.getBlockState(blockpos), level, blockpos) : modelblockrenderer$cache.getShadeBrightness(level.getBlockState(pos), level, pos);
            AmbientVertexRemap modelblockrenderer$ambientvertexremap = AmbientVertexRemap.fromFacing(direction);
            if (shapeFlags.get(1) && modelblockrenderer$adjacencyinfo.doNonCubicWeight) {
                float f29 = (f3 + f + f5 + f8) * 0.25F;
                float f31 = (f2 + f + f4 + f8) * 0.25F;
                float f32 = (f2 + f1 + f6 + f8) * 0.25F;
                float f33 = (f3 + f1 + f7 + f8) * 0.25F;
                float f13 = shape[modelblockrenderer$adjacencyinfo.vert0Weights[0].shape] * shape[modelblockrenderer$adjacencyinfo.vert0Weights[1].shape];
                float f14 = shape[modelblockrenderer$adjacencyinfo.vert0Weights[2].shape] * shape[modelblockrenderer$adjacencyinfo.vert0Weights[3].shape];
                float f15 = shape[modelblockrenderer$adjacencyinfo.vert0Weights[4].shape] * shape[modelblockrenderer$adjacencyinfo.vert0Weights[5].shape];
                float f16 = shape[modelblockrenderer$adjacencyinfo.vert0Weights[6].shape] * shape[modelblockrenderer$adjacencyinfo.vert0Weights[7].shape];
                float f17 = shape[modelblockrenderer$adjacencyinfo.vert1Weights[0].shape] * shape[modelblockrenderer$adjacencyinfo.vert1Weights[1].shape];
                float f18 = shape[modelblockrenderer$adjacencyinfo.vert1Weights[2].shape] * shape[modelblockrenderer$adjacencyinfo.vert1Weights[3].shape];
                float f19 = shape[modelblockrenderer$adjacencyinfo.vert1Weights[4].shape] * shape[modelblockrenderer$adjacencyinfo.vert1Weights[5].shape];
                float f20 = shape[modelblockrenderer$adjacencyinfo.vert1Weights[6].shape] * shape[modelblockrenderer$adjacencyinfo.vert1Weights[7].shape];
                float f21 = shape[modelblockrenderer$adjacencyinfo.vert2Weights[0].shape] * shape[modelblockrenderer$adjacencyinfo.vert2Weights[1].shape];
                float f22 = shape[modelblockrenderer$adjacencyinfo.vert2Weights[2].shape] * shape[modelblockrenderer$adjacencyinfo.vert2Weights[3].shape];
                float f23 = shape[modelblockrenderer$adjacencyinfo.vert2Weights[4].shape] * shape[modelblockrenderer$adjacencyinfo.vert2Weights[5].shape];
                float f24 = shape[modelblockrenderer$adjacencyinfo.vert2Weights[6].shape] * shape[modelblockrenderer$adjacencyinfo.vert2Weights[7].shape];
                float f25 = shape[modelblockrenderer$adjacencyinfo.vert3Weights[0].shape] * shape[modelblockrenderer$adjacencyinfo.vert3Weights[1].shape];
                float f26 = shape[modelblockrenderer$adjacencyinfo.vert3Weights[2].shape] * shape[modelblockrenderer$adjacencyinfo.vert3Weights[3].shape];
                float f27 = shape[modelblockrenderer$adjacencyinfo.vert3Weights[4].shape] * shape[modelblockrenderer$adjacencyinfo.vert3Weights[5].shape];
                float f28 = shape[modelblockrenderer$adjacencyinfo.vert3Weights[6].shape] * shape[modelblockrenderer$adjacencyinfo.vert3Weights[7].shape];
                this.brightness[modelblockrenderer$ambientvertexremap.vert0] = f29 * f13 + f31 * f14 + f32 * f15 + f33 * f16;
                this.brightness[modelblockrenderer$ambientvertexremap.vert1] = f29 * f17 + f31 * f18 + f32 * f19 + f33 * f20;
                this.brightness[modelblockrenderer$ambientvertexremap.vert2] = f29 * f21 + f31 * f22 + f32 * f23 + f33 * f24;
                this.brightness[modelblockrenderer$ambientvertexremap.vert3] = f29 * f25 + f31 * f26 + f32 * f27 + f33 * f28;
                int i2 = this.blend(l, i, j1, i3);
                int j2 = this.blend(k, i, i1, i3);
                int k2 = this.blend(k, j, k1, i3);
                int l2 = this.blend(l, j, l1, i3);
                this.lightmap[modelblockrenderer$ambientvertexremap.vert0] = this.blend(i2, j2, k2, l2, f13, f14, f15, f16);
                this.lightmap[modelblockrenderer$ambientvertexremap.vert1] = this.blend(i2, j2, k2, l2, f17, f18, f19, f20);
                this.lightmap[modelblockrenderer$ambientvertexremap.vert2] = this.blend(i2, j2, k2, l2, f21, f22, f23, f24);
                this.lightmap[modelblockrenderer$ambientvertexremap.vert3] = this.blend(i2, j2, k2, l2, f25, f26, f27, f28);
            } else {
                float f9 = (f3 + f + f5 + f8) * 0.25F;
                float f10 = (f2 + f + f4 + f8) * 0.25F;
                float f11 = (f2 + f1 + f6 + f8) * 0.25F;
                float f12 = (f3 + f1 + f7 + f8) * 0.25F;
                this.lightmap[modelblockrenderer$ambientvertexremap.vert0] = this.blend(l, i, j1, i3);
                this.lightmap[modelblockrenderer$ambientvertexremap.vert1] = this.blend(k, i, i1, i3);
                this.lightmap[modelblockrenderer$ambientvertexremap.vert2] = this.blend(k, j, k1, i3);
                this.lightmap[modelblockrenderer$ambientvertexremap.vert3] = this.blend(l, j, l1, i3);
                this.brightness[modelblockrenderer$ambientvertexremap.vert0] = f9;
                this.brightness[modelblockrenderer$ambientvertexremap.vert1] = f10;
                this.brightness[modelblockrenderer$ambientvertexremap.vert2] = f11;
                this.brightness[modelblockrenderer$ambientvertexremap.vert3] = f12;
            }

            float f30 = level.getShade(direction, shade);

            for(int j3 = 0; j3 < this.brightness.length; ++j3) {
                this.brightness[j3] *= f30;
            }

        }

        private int blend(int lightColor0, int lightColor1, int lightColor2, int lightColor3) {
            if (lightColor0 == 0) {
                lightColor0 = lightColor3;
            }

            if (lightColor1 == 0) {
                lightColor1 = lightColor3;
            }

            if (lightColor2 == 0) {
                lightColor2 = lightColor3;
            }

            return lightColor0 + lightColor1 + lightColor2 + lightColor3 >> 2 & 16711935;
        }

        private int blend(int brightness0, int brightness1, int brightness2, int brightness3, float weight0, float weight1, float weight2, float weight3) {
            int i = (int)((float)(brightness0 >> 16 & 255) * weight0 + (float)(brightness1 >> 16 & 255) * weight1 + (float)(brightness2 >> 16 & 255) * weight2 + (float)(brightness3 >> 16 & 255) * weight3) & 255;
            int j = (int)((float)(brightness0 & 255) * weight0 + (float)(brightness1 & 255) * weight1 + (float)(brightness2 & 255) * weight2 + (float)(brightness3 & 255) * weight3) & 255;
            return i << 16 | j;
        }
    }

    @OnlyIn(Dist.CLIENT)
    enum AmbientVertexRemap {
        DOWN(0, 1, 2, 3),
        UP(2, 3, 0, 1),
        NORTH(3, 0, 1, 2),
        SOUTH(0, 1, 2, 3),
        WEST(3, 0, 1, 2),
        EAST(1, 2, 3, 0);

        final int vert0;
        final int vert1;
        final int vert2;
        final int vert3;
        private static final AmbientVertexRemap[] BY_FACING = (AmbientVertexRemap[])Util.make(new AmbientVertexRemap[6], (p_111204_) -> {
            p_111204_[Direction.DOWN.get3DDataValue()] = DOWN;
            p_111204_[Direction.UP.get3DDataValue()] = UP;
            p_111204_[Direction.NORTH.get3DDataValue()] = NORTH;
            p_111204_[Direction.SOUTH.get3DDataValue()] = SOUTH;
            p_111204_[Direction.WEST.get3DDataValue()] = WEST;
            p_111204_[Direction.EAST.get3DDataValue()] = EAST;
        });

        private AmbientVertexRemap(int vert0, int vert1, int vert2, int vert3) {
            this.vert0 = vert0;
            this.vert1 = vert1;
            this.vert2 = vert2;
            this.vert3 = vert3;
        }

        public static AmbientVertexRemap fromFacing(Direction facing) {
            return BY_FACING[facing.get3DDataValue()];
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class Cache {
        private boolean enabled;
        private final Long2IntLinkedOpenHashMap colorCache = Util.make(() -> {
            Long2IntLinkedOpenHashMap long2intlinkedopenhashmap = new Long2IntLinkedOpenHashMap(100, 0.25F) {
                protected void rehash(int newN) {
                }
            };
            long2intlinkedopenhashmap.defaultReturnValue(Integer.MAX_VALUE);
            return long2intlinkedopenhashmap;
        });
        private final Long2FloatLinkedOpenHashMap brightnessCache = (Long2FloatLinkedOpenHashMap)Util.make(() -> {
            Long2FloatLinkedOpenHashMap long2floatlinkedopenhashmap = new Long2FloatLinkedOpenHashMap(100, 0.25F) {
                protected void rehash(int newN) {
                }
            };
            long2floatlinkedopenhashmap.defaultReturnValue(Float.NaN);
            return long2floatlinkedopenhashmap;
        });

        private Cache() {
        }

        public void enable() {
            this.enabled = true;
        }

        public void disable() {
            this.enabled = false;
            this.colorCache.clear();
            this.brightnessCache.clear();
        }

        public int getLightColor(BlockState state, ImaginaryRegion region, BlockPos pos) {
            long i = pos.asLong();
            if (this.enabled) {
                int j = this.colorCache.get(i);
                if (j != Integer.MAX_VALUE) {
                    return j;
                }
            }

            int k = region.lightBakery.getLightLevel(region, state, pos);
            if (this.enabled) {
                if (this.colorCache.size() == 100) {
                    this.colorCache.removeFirstInt();
                }

                this.colorCache.put(i, k);
            }

            return k;
        }

        public float getShadeBrightness(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            long i = pos.asLong();
            if (this.enabled) {
                float f = this.brightnessCache.get(i);
                if (!Float.isNaN(f)) {
                    return f;
                }
            }

            float f1 = state.getShadeBrightness(level, pos);
            if (this.enabled) {
                if (this.brightnessCache.size() == 100) {
                    this.brightnessCache.removeFirstFloat();
                }

                this.brightnessCache.put(i, f1);
            }

            return f1;
        }
    }

    @OnlyIn(Dist.CLIENT)
    protected enum SizeInfo {
        DOWN(Direction.DOWN, false),
        UP(Direction.UP, false),
        NORTH(Direction.NORTH, false),
        SOUTH(Direction.SOUTH, false),
        WEST(Direction.WEST, false),
        EAST(Direction.EAST, false),
        FLIP_DOWN(Direction.DOWN, true),
        FLIP_UP(Direction.UP, true),
        FLIP_NORTH(Direction.NORTH, true),
        FLIP_SOUTH(Direction.SOUTH, true),
        FLIP_WEST(Direction.WEST, true),
        FLIP_EAST(Direction.EAST, true);

        final int shape;

        private SizeInfo(Direction direction, boolean flip) {
            this.shape = direction.get3DDataValue() + (flip ? ImaginaryRegion.DIRECTIONS.length : 0);
        }
    }
}