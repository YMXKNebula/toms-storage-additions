package dev.tsa.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import java.util.ArrayList;

/** Optional compatibility for the exact audited TVSN version; no ledger or topology changes. */
@Pseudo
@Mixin(targets="dev.tpc.virtual.PhysicalPeers",remap=false)
public abstract class TvsnUnloadedConnectorMixin {
    @Unique private boolean tsa$knownConnector;

    @ModifyExpressionValue(method="get",at=@At(value="INVOKE",target="Ldev/tpc/virtual/VirtualNetwork;knownConnector(J)Z"))
    private boolean tsa$rememberConnector(boolean known){
        tsa$knownConnector=known;
        return known;
    }

    @WrapOperation(method="get",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerChunkCache;getChunkNow(II)Lnet/minecraft/world/level/chunk/LevelChunk;"))
    private LevelChunk tsa$watchUnloadedConnector(ServerChunkCache source,int x,int z,Operation<LevelChunk> original,
            @Local BlockPos position,@Local(ordinal=1) ArrayList<BlockPos> pending){
        LevelChunk chunk=original.call(source,x,z);
        // Reuse TVSN's existing pending-endpoint checks, which only inspect loaded
        // chunks and require a live native connector before rebuilding the cache.
        if(chunk==null && tsa$knownConnector)pending.add(position.immutable());
        return chunk;
    }
}
