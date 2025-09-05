package com.petrolpark.imaginaryregion;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.petrolpark.Petrolpark;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

public class ImaginaryRegionCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal(Petrolpark.MOD_ID).then(Commands.literal("imaginaryRegion")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("start", BlockPosArgument.blockPos())
                                .then(Commands.argument("end", BlockPosArgument.blockPos())
                                        .then(Commands.argument("at", BlockPosArgument.blockPos())
                                                .then(Commands.argument("rx", FloatArgumentType.floatArg(-360f, 360f))
                                                        .then(Commands.argument("ry", FloatArgumentType.floatArg(-360f, 360f))
                                                                .executes(ctx -> create(
                                                                        ctx.getSource(),
                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "start"),
                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "end"),
                                                                        BlockPosArgument.getLoadedBlockPos(ctx, "at"),
                                                                        FloatArgumentType.getFloat(ctx, "rx"),
                                                                        FloatArgumentType.getFloat(ctx, "ry")
                                                                ))
                                                        )
                                                )
                                        )
                                )
                        )
                )
        ));
    }

    private static int create(CommandSourceStack source, BlockPos startPos, BlockPos endPos, BlockPos regionPosition, float rx, float ry) {
        PacketDistributor.sendToPlayer(source.getPlayer(), new CreateImaginaryRegionPacket(
                new Vector3f(startPos.getX(), startPos.getY(), startPos.getZ()),
                new Vector3f(endPos.getX(), endPos.getY(), endPos.getZ()),
                new Vector3f(regionPosition.getX(), regionPosition.getY(), regionPosition.getZ()),
                rx, ry));
        return 1;
    }
}
