package com.petrolpark.imaginaryregion;

import com.petrolpark.PetrolparkPackets;
import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public record CreateImaginaryRegionPacket(Vector3f start, Vector3f to, Vector3f pos, float rx, float ry) implements ClientboundPacketPayload {
    public static final StreamCodec<RegistryFriendlyByteBuf, CreateImaginaryRegionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VECTOR3F, CreateImaginaryRegionPacket::start,
            ByteBufCodecs.VECTOR3F, CreateImaginaryRegionPacket::to,
            ByteBufCodecs.VECTOR3F, CreateImaginaryRegionPacket::pos,
            ByteBufCodecs.FLOAT, CreateImaginaryRegionPacket::rx,
            ByteBufCodecs.FLOAT, CreateImaginaryRegionPacket::ry,
            CreateImaginaryRegionPacket::new
    );

    @Override
    @OnlyIn( Dist.CLIENT)
    public void handle(LocalPlayer player) {
        float ax = Math.min(start.x, to.x);
        float bx = Math.max(start.x, to.x);

        float ay = Math.min(start.y, to.y);
        float by = Math.max(start.y, to.y);

        float az = Math.min(start.z, to.z);
        float bz = Math.max(start.z, to.z);

        Level level = player.level();
        Map<BlockPos, BlockState> blocks = new HashMap<>();

        for ( float x = ax; x <= bx; x+= 1 ) {
            for ( float y = ay; y <= by; y += 1 ) {
                for ( float z = az; z <= bz; z += 1 ) {
                    BlockPos blockPos = new BlockPos(( int ) x, ( int ) y, ( int ) z);
                    BlockState state = level.getBlockState(blockPos);
                    if (state.isAir()) continue;

                    blocks.put(blockPos.subtract(new Vec3i(( int )start.x, ( int )start.y, ( int )start.z)), state);
                }
            }
        }

        ImaginaryRegion.regions.add(
                new ImaginaryRegion(pos, new Vector2f(rx, ry), blocks)
        );
    }

    @Override
    public PacketTypeProvider getTypeProvider() {
        return PetrolparkPackets.CREATE_REGION;
    }
}
