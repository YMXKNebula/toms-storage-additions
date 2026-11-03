package dev.tsa.mixin;

import com.tom.storagemod.block.entity.BasicInventoryHopperBlockEntity;
import com.tom.storagemod.inventory.InventorySlot;
import dev.tsa.Additions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Change only the new block's batch size; all native scheduling and transfers remain intact. */
@Mixin(value=BasicInventoryHopperBlockEntity.class,remap=false)
public abstract class CurvatureHopperMixin {
    @Shadow private InventorySlot topSlot;

    @ModifyArg(method="updateServer",at=@At(value="INVOKE",target="Lcom/tom/storagemod/inventory/InventorySlot;transferTo(ILcom/tom/storagemod/inventory/InventorySlot;)Z"),index=0)
    private int tsa$oneNativeStack(int original){
        var self=(BasicInventoryHopperBlockEntity)(Object)this;
        if(!self.getBlockState().is(Additions.CURVATURE_HOPPER))return original;
        var stack=topSlot.getStack();
        return stack.isEmpty()?0:Math.min(stack.getCount(),stack.getMaxStackSize());
    }
}
