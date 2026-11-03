package dev.tsa;

import com.tom.storagemod.block.BasicInventoryHopperBlock;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityType;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class Additions implements ModInitializer {
    public static final String MOD_ID="toms_storage_additions";
    private static final Identifier HOPPER_ID=Identifier.fromNamespaceAndPath(MOD_ID,"curvature_storage_hopper");
    private static final Identifier NATIVE_HOPPER_TYPE=Identifier.fromNamespaceAndPath("toms_storage","basic_inventory_hopper");
    public static final BasicInventoryHopperBlock CURVATURE_HOPPER=new BasicInventoryHopperBlock(BlockBehaviour.Properties.of()
            .strength(2.5F).sound(SoundType.METAL).noOcclusion().setId(ResourceKey.create(Registries.BLOCK,HOPPER_ID)));
    public static final Item CURVATURE_HOPPER_ITEM=new BlockItem(CURVATURE_HOPPER,new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM,HOPPER_ID)).useBlockDescriptionPrefix());

    @Override public void onInitialize(){
        Registry.register(BuiltInRegistries.BLOCK,HOPPER_ID,CURVATURE_HOPPER);
        Registry.register(BuiltInRegistries.ITEM,HOPPER_ID,CURVATURE_HOPPER_ITEM);
        // Keep the native ticker, interactions, filter persistence and Jade provider.
        // Tom registers its BE types through a deferred registration callback.
        // Both entrypoint orders must work, including on a standalone client.
        RegistryEntryAddedCallback.event(BuiltInRegistries.BLOCK_ENTITY_TYPE).register((rawId,id,type)->{
            if(id.equals(NATIVE_HOPPER_TYPE))((FabricBlockEntityType)type).addValidBlock(CURVATURE_HOPPER);
        });
        var type=BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(NATIVE_HOPPER_TYPE);
        if(type!=null)((FabricBlockEntityType)type).addValidBlock(CURVATURE_HOPPER);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries->entries.accept(CURVATURE_HOPPER_ITEM));
    }
}
