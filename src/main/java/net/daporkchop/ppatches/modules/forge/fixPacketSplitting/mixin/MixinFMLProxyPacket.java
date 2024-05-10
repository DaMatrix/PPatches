package net.daporkchop.ppatches.modules.forge.fixPacketSplitting.mixin;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * @author DaPorkchop_
 */
@Mixin(value = FMLProxyPacket.class, remap = false)
abstract class MixinFMLProxyPacket {
    @Unique
    private static final byte[] ppatches_fixPacketSplitting_emptyByteArray = new byte[0];

    @Shadow
    @Final
    static int PART_SIZE;

    @Shadow
    @Final
    private PacketBuffer payload;

    @Redirect(method = "toS3FPackets()Ljava/util/List;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/PacketBuffer;array()[B"),
            allow = 1, require = 1)
    private byte[] fp2_fixPacketSplitting_dontAccessBufferArray(PacketBuffer payload) {
        //return an empty byte array so that all of the forge code which tries to access the buffer's length returns zero
        return ppatches_fixPacketSplitting_emptyByteArray;
    }

    @ModifyConstant(method = "toS3FPackets()Ljava/util/List;",
            slice = @Slice(
                    from = @At(value = "INVOKE:ONE",
                            target = "Lnet/minecraft/network/PacketBuffer;array()[B"),
                    to = @At(value = "INVOKE:ONE",
                            target = "Lnet/minecraft/network/PacketBuffer;duplicate()Lio/netty/buffer/ByteBuf;")),
            constant = @Constant(intValue = 0x100000 - 0x50),
            allow = 1, require = 1)
    private int fp2_fixPacketSplitting_fixNeedsMultipartTest(int PART_SIZE) {
        //if the packet is small enough to be sent as a single part, return something greater than 0
        return this.payload.readableBytes() < PART_SIZE ? 1 : 0;
    }

    //the following three injectors all handle replacing references to 'data.length' with the actual payload size

    @ModifyArg(method = "toS3FPackets()Ljava/util/List;",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/Math;ceil(D)D"),
            allow = 1, require = 1)
    private double fp2_fixPacketSplitting_useCorrectPacketLengthToComputePartCount(double zero) {
        return this.payload.readableBytes() / (double) (PART_SIZE - 1);
    }

    @ModifyArg(method = "toS3FPackets()Ljava/util/List;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/PacketBuffer;writeInt(I)Lio/netty/buffer/ByteBuf;"),
            allow = 1, require = 1)
    private int fp2_fixPacketSplitting_useCorrectPacketLengthToWritePreamble(int zero) {
        return this.payload.readableBytes();
    }

    @ModifyArg(method = "toS3FPackets()Ljava/util/List;",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/Math;min(II)I"),
            index = 1,
            allow = 1, require = 1)
    private int fp2_fixPacketSplitting_useCorrectPacketLengthToComputePartLength(int zeroMinusOffsetPlusOne) {
        return this.payload.readableBytes() + zeroMinusOffsetPlusOne;
    }

    //copy data from the buffer instead of using System.arraycopy(), so that direct buffers will also work

    @Redirect(method = "toS3FPackets()Ljava/util/List;",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/System;arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V"),
            allow = 1, require = 1)
    private void fp2_fixPacketSplitting_copyFromCorrectBuffer(Object src, int srcOffset, Object dst, int dstOffset, int length) {
        this.payload.getBytes(srcOffset, (byte[]) dst, dstOffset, length);
    }
}
