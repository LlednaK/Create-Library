package com.petrolpark.imaginaryregion;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Map;

public class LightBakery{
    private final ArrayList<BlockPos> lightSources = new ArrayList<>();
    private final Long2IntOpenHashMap lightMap = new Long2IntOpenHashMap();

    public void addLightSource(BlockPos position) {
        lightSources.add(position);
    }

    public int getLightLevel(BlockGetter level, BlockState state, BlockPos pos) {
        if (state.emissiveRendering(level, pos)) {
            return 15728880;
        } else {
            int i = 1;
            int j = this.lightMap.getOrDefault(pos.asLong(), 0);

            int k = state.getLightEmission(level, pos);
            if (k > j) {
                j = k;
            }
            return i << 20 | j<<4;
        }
    }

    public void bake(BlockGetter level) {
        for (BlockPos lightPos : lightSources) {
            BlockState lightState = level.getBlockState(lightPos);
            int lightLevel = lightState.getLightEmission(level, lightPos);

            propagateLight(level, lightPos, lightLevel);
        }
    }

    private void propagateLight(BlockGetter level, BlockPos startPos, int lightLevel) {
        ArrayList<BlockPos> blocksToCheck = new ArrayList<>();
        ArrayList<BlockPos> nextBlocks = new ArrayList<>();
        Long2IntOpenHashMap tempLightMap = new Long2IntOpenHashMap();

        blocksToCheck.add(startPos);

        for (int i = lightLevel; i > 0; i--) {
            for (BlockPos pos : blocksToCheck) {
                tempLightMap.put(pos.asLong(), i);

                BlockState state = level.getBlockState(pos);

                for ( Direction direction : Direction.values() ) {
                    BlockPos offsetPos = pos.offset(direction.getNormal());
                    BlockState offsetState = level.getBlockState(offsetPos);

                    if (!tempLightMap.containsKey(offsetPos.asLong()) && !shapeOccludes(level, pos, state, offsetPos, offsetState, direction.getOpposite())) {
                        nextBlocks.add(offsetPos);
                    }
                }
            }

            blocksToCheck.clear();
            blocksToCheck.addAll(nextBlocks);
            nextBlocks.clear();
        }
        mergeLightMaps(lightMap, tempLightMap);
    }

    private boolean shapeOccludes(BlockGetter level, BlockPos pos1, BlockState state1, BlockPos pos2, BlockState state2, Direction direction) {
        VoxelShape voxelshape = getOcclusionShape(level, pos1, state1, direction);
        VoxelShape voxelshape1 = getOcclusionShape(level, pos2, state2, direction.getOpposite());
        return Shapes.faceShapeOccludes(voxelshape, voxelshape1);
    }

    public static VoxelShape getOcclusionShape(BlockGetter level, BlockPos pos, BlockState state, Direction direction) {
        if (state.canOcclude() && state.getOcclusionShape(level, pos) == Shapes.block()) {
            return Shapes.block();
        }
        return isEmptyShape(state) ? Shapes.empty() : state.getFaceOcclusionShape(level, pos, direction);
    }

    protected static boolean isEmptyShape(BlockState state) {
        return !state.canOcclude() || !state.useShapeForLightOcclusion();
    }

    private static void mergeLightMaps(Long2IntOpenHashMap target, Long2IntOpenHashMap other) {
        for ( Map.Entry<Long, Integer> lights : other.long2IntEntrySet()) {
            long pos = lights.getKey();
            int i = lights.getValue();

            if (target.containsKey(pos)) {
                target.put(pos, Math.max(target.get(pos), i));
            } else {
                target.put(pos, i);
            }
        }
    }
}
