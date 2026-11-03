package dev.tsa.integration;

import com.tom.storagemod.Config;
import com.tom.storagemod.Content;
import com.tom.storagemod.block.AbstractInventoryHopperBlock;
import com.tom.storagemod.block.InventoryCableBlock;
import com.tom.storagemod.block.entity.BasicInventoryHopperBlockEntity;
import com.tom.storagemod.block.entity.InventoryConnectorBlockEntity;
import dev.tsa.Additions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.GameType;
import com.tom.storagemod.inventory.StoredItemStack;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Real native tickers, slots and Fabric transactions; no replacement transfer implementation. */
public final class CurvatureHopperGameTests {
    private static void await(GameTestHelper t,BooleanSupplier ready,Runnable next){if(ready.getAsBoolean())next.run();else t.runAfterDelay(1,()->await(t,ready,next));}
    private record Fixture(ServerLevel level,BlockPos hopper,BlockPos barrel,ChestBlockEntity chest,InventoryConnectorBlockEntity connector){
        BasicInventoryHopperBlockEntity entity(){return (BasicInventoryHopperBlockEntity)level.getBlockEntity(hopper);}
        void enabled(boolean enabled){level.setBlock(hopper.above(),enabled?Blocks.AIR.defaultBlockState():Blocks.REDSTONE_BLOCK.defaultBlockState(),3);}
        long amount(ItemStack stack){var access=connector.getMergedHandler();access.tracker().getChangeTracker(level);return access.tracker().countItems(new StoredItemStack(stack));}
    }
    private static boolean virtual(){return Boolean.getBoolean("tsa.testVirtualWarehouse");}
    private static Block warehouse(){
        if(!virtual())return Blocks.CHEST;
        if(!FabricLoader.getInstance().isModLoaded("toms_virtual_storage"))throw new AssertionError("Virtual test requires the unchanged TVSN dependency");
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("toms_virtual_storage","tom_barrel"));
    }
    private static Fixture fixture(GameTestHelper t,boolean export,boolean basic,int z){
        var l=t.getLevel();var p=t.absolutePos(new BlockPos(3,6,z));var b=p.east(3);
        l.setBlock(b,warehouse().defaultBlockState(),3);
        l.setBlock(p.east(2),Content.connector.get().defaultBlockState(),3);
        l.setBlock(p.east(),Content.invCable.get().defaultBlockState().setValue(InventoryCableBlock.EAST,true).setValue(InventoryCableBlock.WEST,true),3);
        l.setBlock(p.west(),Blocks.CHEST.defaultBlockState(),3);l.setBlock(p.above(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
        var block=basic?Content.basicInvHopper.get():Additions.CURVATURE_HOPPER;
        l.setBlock(p,block.defaultBlockState().setValue(AbstractInventoryHopperBlock.FACING,export?Direction.WEST:Direction.EAST).setValue(AbstractInventoryHopperBlock.ENABLED,false),3);
        return new Fixture(l,p,b,(ChestBlockEntity)l.getBlockEntity(p.west()),(InventoryConnectorBlockEntity)l.getBlockEntity(p.east(2)));
    }
    private static void ready(GameTestHelper t,Fixture f,Runnable run){await(t,()->f.connector.getMergedHandler().getSlotCount()==(virtual()?64:27),run);}
    private static void seed(GameTestHelper t,Fixture f,ItemStack stack,long count){
        try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){
            Storage<ItemVariant> storage=f.connector.getMergedHandler().getPlatformHandler();long inserted=storage.insert(ItemVariant.of(stack),count,tx);
            t.assertTrue(inserted==count,"Seed the exact native variant count");tx.commit();
        }
    }
    private static void input(GameTestHelper t,ItemStack stack,boolean partial){
        var f=fixture(t,false,false,2);ready(t,f,()->{
            int limit=stack.getMaxStackSize(),count=partial?Math.max(1,limit/2):limit;
            f.chest.setItem(0,stack.copyWithCount(count));f.chest.setItem(1,stack.copyWithCount(limit));f.enabled(true);
            await(t,()->f.amount(stack)>0,()->{
                f.enabled(false);
                t.assertTrue(f.amount(stack)==count,"Exactly one physical source slot must move, expected="+count+", actual="+f.amount(stack));
                t.assertTrue(f.chest.getItem(0).isEmpty() && f.chest.getItem(1).getCount()==limit,"Never fill a short source slot using the next source slot");
                long variants=f.connector.getMergedHandler().tracker().streamWrappedStacks(false).filter(s->s.getQuantity()>0).count();
                t.assertTrue(variants==1,"Preserve all item components without introducing another variant");t.succeed();
            });
        });
    }
    @GameTest(maxTicks=1800) public void blockStackUsesCurrentGameLimit(GameTestHelper t){input(t,new ItemStack(Items.DIAMOND),false);}
    @GameTest(maxTicks=1800) public void enderPearlsUseTheirOwnCurrentLimit(GameTestHelper t){input(t,new ItemStack(Items.ENDER_PEARL),false);}
    @GameTest(maxTicks=1800) public void unstackableToolsAndComponentsArePreserved(GameTestHelper t){
        var stack=new ItemStack(Items.DIAMOND_PICKAXE);stack.set(DataComponents.MAX_STACK_SIZE,1);stack.set(DataComponents.DAMAGE,17);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Curvature test tool"));input(t,stack,false);
    }
    @GameTest(maxTicks=1800) public void perStackComponentOverridesAreNotHardcoded(GameTestHelper t){
        var stack=new ItemStack(Items.DIAMOND);stack.set(DataComponents.MAX_STACK_SIZE,37);stack.set(DataComponents.CUSTOM_NAME,Component.literal("37-item native stack"));input(t,stack,false);
    }
    @GameTest(maxTicks=1800) public void partialSourceDoesNotConsumeAnotherSlot(GameTestHelper t){input(t,new ItemStack(Items.DIAMOND),true);}

    @GameTest(maxTicks=2400)
    public void exportHasTheSameSuccessfulTransferIntervalAsTheBasicHopper(GameTestHelper t){
        var basic=fixture(t,true,true,2);var curvature=fixture(t,true,false,8);var stack=new ItemStack(Items.DIAMOND);int limit=stack.getMaxStackSize();long stock=6L*limit;
        ready(t,basic,()->ready(t,curvature,()->{
            seed(t,basic,stack,stock);seed(t,curvature,stack,stock);
            basic.entity().setFilter(stack.copy());curvature.entity().setFilter(stack.copy());basic.enabled(true);curvature.enabled(true);
            monitor(t,basic,curvature,stack,stock,new ArrayList<>(),new ArrayList<>(),stock,stock,Math.max(1,Config.get().basicHopperCooldown)+1);
        }));
    }
    private static void monitor(GameTestHelper t,Fixture basic,Fixture curvature,ItemStack stack,long stock,List<Long> basicTicks,List<Long> curvatureTicks,long oldBasic,long oldCurvature,int interval){
        long a=basic.amount(stack),b=curvature.amount(stack),now=basic.level.getGameTime();int limit=stack.getMaxStackSize();
        if(a!=oldBasic){t.assertTrue(oldBasic-a==1,"The original basic hopper must still move one item");basicTicks.add(now);}
        if(b!=oldCurvature){t.assertTrue(oldCurvature-b==limit,"The curvature hopper must export one current native stack");curvatureTicks.add(now);}
        if(basicTicks.size()>=3 && curvatureTicks.size()>=3){
            basic.enabled(false);curvature.enabled(false);
            for(var ticks:List.of(basicTicks,curvatureTicks))for(int i=1;i<ticks.size();i++)t.assertTrue(ticks.get(i)-ticks.get(i-1)==interval,"Both native successful-transfer intervals must be "+interval+" ticks, actual="+ticks);
            for(var f:List.of(basic,curvature)){long chest=0;for(int i=0;i<f.chest.getContainerSize();i++)chest+=f.chest.getItem(i).getCount();t.assertTrue(chest+f.amount(stack)==stock,"Export must conserve inventory and network stock");}
            t.succeed();return;
        }
        t.runAfterDelay(1,()->monitor(t,basic,curvature,stack,stock,basicTicks,curvatureTicks,a,b,interval));
    }

    @GameTest(maxTicks=2200)
    public void fullDestinationWaitsAndPartialSpaceMovesOnlyWhatFits(GameTestHelper t){
        var f=fixture(t,true,false,2);var stack=new ItemStack(Items.DIAMOND);int limit=stack.getMaxStackSize();long stock=2L*limit;
        ready(t,f,()->{
            seed(t,f,stack,stock);for(int i=0;i<f.chest.getContainerSize();i++)f.chest.setItem(i,stack.copyWithCount(limit));
            f.entity().setFilter(stack.copy());f.enabled(true);
            t.runAfterDelay(Math.max(1,Config.get().basicHopperCooldown)*3+8,()->{
                t.assertTrue(f.amount(stack)==stock,"A full destination must never consume source stock");int space=Math.min(3,limit);f.chest.setItem(0,stack.copyWithCount(limit-space));
                await(t,()->f.amount(stack)<stock,()->{
                    f.enabled(false);t.assertTrue(f.amount(stack)==stock-space && f.chest.getItem(0).getCount()==limit,"Only actual available destination space may be transferred");t.succeed();
                });
            });
        });
    }

    @GameTest(maxTicks=2200)
    public void redstoneAndNativeExportFilterStillControlTransfers(GameTestHelper t){
        var f=fixture(t,true,false,2);var diamond=new ItemStack(Items.DIAMOND);var emerald=new ItemStack(Items.EMERALD);int limit=diamond.getMaxStackSize();
        ready(t,f,()->{
            seed(t,f,diamond,limit);seed(t,f,emerald,emerald.getMaxStackSize());f.entity().setFilter(diamond.copy());
            t.runAfterDelay(Math.max(1,Config.get().basicHopperCooldown)*2+4,()->{
                t.assertTrue(f.chest.isEmpty() && f.amount(diamond)==limit,"Redstone must disable the curvature hopper; state="+f.level.getBlockState(f.hopper)+", BE="+f.entity().getBlockState()+", signal="+f.level.hasNeighborSignal(f.hopper)+", stock="+f.amount(diamond));f.enabled(true);
                await(t,()->f.amount(diamond)==0,()->{
                    f.enabled(false);t.assertTrue(f.amount(emerald)==emerald.getMaxStackSize(),"Native export filtering must leave all nonmatching variants untouched");t.succeed();
                });
            });
        });
    }

    @GameTest(maxTicks=1800)
    public void curvatureHopperCannotAccessABarrelDirectly(GameTestHelper t){
        var f=fixture(t,true,false,2);var stack=new ItemStack(Items.DIAMOND);
        ready(t,f,()->{
            seed(t,f,stack,9);
            var p=f.barrel.south();f.level.setBlock(p.south(),Blocks.CHEST.defaultBlockState(),3);
            f.level.setBlock(p,Additions.CURVATURE_HOPPER.defaultBlockState().setValue(AbstractInventoryHopperBlock.FACING,Direction.SOUTH),3);
            ((BasicInventoryHopperBlockEntity)f.level.getBlockEntity(p)).setFilter(stack.copy());
            t.runAfterDelay(Math.max(1,Config.get().basicHopperCooldown)*3+8,()->{
                t.assertTrue(((ChestBlockEntity)f.level.getBlockEntity(p.south())).isEmpty() && f.amount(stack)==9,"The existing direct-barrel automation restriction must remain intact");t.succeed();
            });
        });
    }

    @GameTest
    public void smithingRecipeAndNativeMenuConsumeTheSpecifiedIngredients(GameTestHelper t){
        var l=t.getLevel();var base=new ItemStack(Content.basicInvHopper.get().asItem(),3);base.set(DataComponents.CUSTOM_NAME,Component.literal("Named upgraded hopper"));
        var template=new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE,2);var addition=new ItemStack(Items.NETHERITE_INGOT,2);
        var input=new SmithingRecipeInput(template,base,addition);var recipe=l.getServer().getRecipeManager().getRecipeFor(RecipeType.SMITHING,input,l);
        t.assertTrue(recipe.isPresent(),"The native recipe manager must discover the curvature smithing recipe");
        var result=recipe.orElseThrow().value().assemble(input);t.assertTrue(result.is(Additions.CURVATURE_HOPPER_ITEM) && result.getCount()==1 && result.getHoverName().equals(base.getHoverName()),"Smithing produces one curvature hopper and preserves the native base components");
        t.assertTrue(!recipe.orElseThrow().value().matches(new SmithingRecipeInput(ItemStack.EMPTY,base,addition),l) && !recipe.orElseThrow().value().matches(new SmithingRecipeInput(template,base,new ItemStack(Items.IRON_INGOT)),l),"Missing template and incorrect metal must not match");
        var pos=t.absolutePos(new BlockPos(2,4,2));l.setBlock(pos,Blocks.SMITHING_TABLE.defaultBlockState(),3);var player=t.makeMockPlayer(GameType.SURVIVAL);var menu=new SmithingMenu(1,player.getInventory(),ContainerLevelAccess.create(l,pos));
        menu.getSlot(0).set(template.copy());menu.getSlot(1).set(base.copy());menu.getSlot(2).set(addition.copy());menu.createResult();
        t.assertTrue(menu.getSlot(3).getItem().is(Additions.CURVATURE_HOPPER_ITEM),"The actual smithing menu must show the new result");
        var taken=menu.getSlot(3).remove(1);menu.getSlot(3).onTake(player,taken);
        t.assertTrue(taken.is(Additions.CURVATURE_HOPPER_ITEM) && menu.getSlot(0).getItem().getCount()==1 && menu.getSlot(1).getItem().getCount()==2 && menu.getSlot(2).getItem().getCount()==1,"Taking the result consumes exactly one template, basic hopper and netherite ingot");t.succeed();
    }

    @GameTest
    public void nativeBlockEntityRoundTripKeepsTheNewBlockAndFilter(GameTestHelper t){
        var f=fixture(t,true,false,2);var filter=new ItemStack(Items.ENDER_PEARL);filter.set(DataComponents.CUSTOM_NAME,Component.literal("Saved curvature filter"));f.entity().setFilter(filter);
        var state=f.level.getBlockState(f.hopper);var tag=f.entity().saveWithFullMetadata(f.level.registryAccess());var loaded=BlockEntity.loadStatic(f.hopper,state,tag,f.level.registryAccess());
        t.assertTrue(loaded instanceof BasicInventoryHopperBlockEntity && loaded.getBlockState().is(Additions.CURVATURE_HOPPER) && loaded.isValidBlockState(state),"The native BE type must accept the added block after loading");
        t.assertTrue(ItemStack.isSameItemSameComponents(((BasicInventoryHopperBlockEntity)loaded).getFilter(),filter),"The native saved filter must retain all item components");
        var drops=Block.getDrops(state,f.level,f.hopper,f.entity());t.assertTrue(drops.size()==1 && drops.getFirst().is(Additions.CURVATURE_HOPPER_ITEM) && drops.getFirst().getCount()==1,"Breaking the new block must drop the new hopper rather than the basic hopper");t.runAfterDelay(2,t::succeed);
    }

    @GameTest(maxTicks=2400)
    public void changingSmallPhysicalInputKeepsDrainingWithoutReplacement(GameTestHelper t){
        var f=fixture(t,false,false,2);ready(t,f,()->{f.enabled(true);feed(t,f,f.chest,0,0,600);});
    }
    private static void feed(GameTestHelper t,Fixture f,ChestBlockEntity source,int tick,int produced,int duration){
        var stack=new ItemStack(Items.DIAMOND);int added=0;
        if(tick<duration && tick%3==0){
            int count=source.getItem(0).getCount(),batch=1+(tick/3)%3;
            if(count+batch<=stack.getMaxStackSize()){source.setItem(0,stack.copyWithCount(count+batch));added=batch;}
        }
        int total=produced+added;
        // The native aggregate can retain this tick's pre-transfer count until the
        // next tick; require convergence instead of treating that as item loss.
        if(tick>=duration && source.getItem(0).isEmpty() && f.amount(stack)==total){
            f.enabled(false);t.succeed();return;
        }
        if(tick>=duration+180){t.assertTrue(false,"Sparse input stalled: pending="+source.getItem(0).getCount()+", produced="+total+", destination="+f.amount(stack)+", waiting="+f.entity().waiting);return;}
        t.runAfterDelay(1,()->feed(t,f,source,tick+1,total,duration));
    }

    @GameTest(maxTicks=2400)
    public void emptyPhysicalSourceAutomaticallyResumesAcrossRepeatedRefills(GameTestHelper t){
        var f=fixture(t,false,false,2);ready(t,f,()->{f.enabled(true);refill(t,f,false,0,10);});
    }
    @GameTest(maxTicks=2400)
    public void emptyNetworkSourceAutomaticallyResumesAcrossRepeatedRefills(GameTestHelper t){
        var f=fixture(t,true,false,2);ready(t,f,()->{f.entity().setFilter(new ItemStack(Items.DIAMOND));f.enabled(true);refill(t,f,true,0,10);});
    }
    private static void refill(GameTestHelper t,Fixture f,boolean export,int cycle,int count){
        if(cycle==count){f.enabled(false);t.succeed();return;}
        int amount=1+cycle%3;var stack=new ItemStack(Items.DIAMOND);int previous=cycle/3*6+(cycle%3==0?0:cycle%3==1?1:3);int expected=previous+amount;
        t.runAfterDelay(Math.max(1,Config.get().basicHopperCooldown)*3+5,()->{
            if(export)seed(t,f,stack,amount);else f.chest.setItem(0,stack.copyWithCount(amount));
            await(t,()->export?f.chest.getItem(0).getCount()==expected:f.amount(stack)==expected,()->{
                t.assertTrue(export?f.amount(stack)==0:f.chest.isEmpty(),"Each small refill must drain completely before the next cycle");refill(t,f,export,cycle+1,count);
            });
        });
    }

    @GameTest(maxTicks=2400)
    public void reportedConnectorHopperCableConnectorChainDrainsChangingSmallStacks(GameTestHelper t){
        var f=fixture(t,false,false,2);var l=f.level;var sourcePos=f.hopper.west(2);
        l.setBlock(f.hopper.west(),Content.connector.get().defaultBlockState(),3);l.setBlock(sourcePos,Blocks.CHEST.defaultBlockState(),3);
        var source=(ChestBlockEntity)l.getBlockEntity(sourcePos);var input=(InventoryConnectorBlockEntity)l.getBlockEntity(f.hopper.west());
        ready(t,f,()->await(t,()->input.getMergedHandler().getSlotCount()==27,()->{
            f.entity().setFilter(new ItemStack(Items.DIAMOND));f.enabled(true);feed(t,f,source,0,0,600);
        }));
    }

    private static int farSequence;
    private record Far(ServerLevel level,BlockPos hopper,BlockPos end,int first,int last,int z){
        ChestBlockEntity source(){return (ChestBlockEntity)level.getBlockEntity(hopper.west(2));}
        BasicInventoryHopperBlockEntity entity(){return (BasicInventoryHopperBlockEntity)level.getBlockEntity(hopper);}
        InventoryConnectorBlockEntity target(){return (InventoryConnectorBlockEntity)level.getBlockEntity(end);}
        long amount(){var a=target().getMergedHandler();a.tracker().getChangeTracker(level);return a.tracker().countItems(new StoredItemStack(new ItemStack(Items.DIAMOND)));}
        void force(int from,int to,boolean forced){for(int x=from;x<=to;x++)level.setChunkForced(x,z,forced);}
        void finish(){force(first,last,false);}
    }
    private static Far far(GameTestHelper t){
        var l=t.getLevel();int first=1_000_000+(farSequence++)*32,z=750_000;var p=new BlockPos(first*16+4,70,z*16+8);var end=p.east(128);var f=new Far(l,p,end,first,end.getX()>>4,z);f.force(first,f.last,true);
        l.setBlock(p.west(2),Blocks.CHEST.defaultBlockState(),3);l.setBlock(p.west(),Content.connector.get().defaultBlockState(),3);
        var wire=Content.invCable.get().defaultBlockState().setValue(InventoryCableBlock.EAST,true).setValue(InventoryCableBlock.WEST,true);
        for(int x=1;x<128;x++)l.setBlock(p.east(x),wire,3);
        l.setBlock(end,Content.connector.get().defaultBlockState(),3);l.setBlock(end.east(),warehouse().defaultBlockState(),3);l.setBlock(p.above(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
        l.setBlock(p,Additions.CURVATURE_HOPPER.defaultBlockState().setValue(AbstractInventoryHopperBlock.FACING,Direction.EAST).setValue(AbstractInventoryHopperBlock.ENABLED,false),3);
        return f;
    }
    private static void warm(GameTestHelper t,Far f,Runnable next){
        await(t,()->f.target().getMergedHandler().getSlotCount()==(virtual()?64:27),()->{
            f.source().setItem(0,new ItemStack(Items.DIAMOND,5));f.entity().setFilter(new ItemStack(Items.DIAMOND));f.level.setBlock(f.hopper.above(),Blocks.AIR.defaultBlockState(),3);
            await(t,()->f.source().isEmpty() && f.amount()==5,next);
        });
    }
    private static Object field(Object value,String name){
        if(value==null)return null;
        for(Class<?> type=value.getClass();type!=null;type=type.getSuperclass())try{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(value);}catch(NoSuchFieldException ignored){}catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        return null;
    }
    private static void phase(Far f,String stage){
        var source=f.level.getChunkSource().getChunkNow(f.first,f.z);var target=f.level.getChunkSource().getChunkNow(f.last,f.z);var be=source==null?null:source.getBlockEntity(f.hopper);
        var destination=target==null?null:target.getBlockEntity(f.end);var handler=field(destination,"handler");
        var top=field(be,"topCache");var bottom=field(be,"bottomCache");
        if(virtual())try{
            var manager=Class.forName("dev.tpc.PersistentCables").getMethod("manager",ServerLevel.class).invoke(null,f.level);
            var lease=manager.getClass().getMethod("query",BlockPos.class).invoke(manager,f.hopper.east());
            var valid=lease.getClass().getMethod("valid").invoke(lease);var functions=(java.util.Set<?>)lease.getClass().getMethod("functions").invoke(lease);
            var network=Class.forName("dev.tpc.virtual.VirtualNetwork").getMethod("get",ServerLevel.class).invoke(null,f.level);var peers=field(network,"peers");
            var entry=((java.util.Map<?,?>)field(peers,"entries")).get(lease);
            System.out.println("TSA_GRAPH stage="+stage+", valid="+valid+", functions="+functions+", cachedPeerEntry="+entry+", endpointRemoved="+(destination!=null && destination.isRemoved())+", status="+manager.getClass().getMethod("status").invoke(manager));
        }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        System.out.println("TSA_RECOVERY stage="+stage+", tick="+f.level.getGameTime()+", sourceLoaded="+(source!=null)+", targetLoaded="+(target!=null)+", sourceCount="+(source==null?-1:f.source().getItem(0).getCount())+", waiting="+field(be,"waiting")+", cooldown="+field(be,"cooldown")+", topChange="+field(be,"topChange")+", bottomChange="+field(be,"bottomChange")+", cachedSlot="+field(be,"topSlot")+", targetHandler="+handler+", targetSlots="+(handler instanceof com.tom.storagemod.inventory.IInventoryAccess a?a.getSlotCount():-1)+", removed="+(be!=null && be.isRemoved())+", topLastUpdate="+field(top,"lastUpdate")+", bottomLastUpdate="+field(bottom,"lastUpdate")+", topLastCheck="+field(field(top,"block"),"lastCheck")+", bottomLastCheck="+field(field(bottom,"block"),"lastCheck")+", topNetwork="+field(top,"networkAccess")+", bottomNetwork="+field(bottom,"networkAccess"));
    }
    @GameTest(maxTicks=10000)
    public void remoteDestinationConnectorUnloadAndReloadDoesNotRequireReplacingTheHopper(GameTestHelper t){
        var f=far(t);t.runAfterDelay(2000,()->phase(f,"remote-diagnostic"));warm(t,f,()->{
            phase(f,"remote-warm");
            var original=f.entity();f.force(f.last-2,f.last,false);
            await(t,()->f.level.getChunkSource().getChunkNow(f.last,f.z)==null,()->{
                phase(f,"remote-unloaded");
                f.source().setItem(0,new ItemStack(Items.DIAMOND,7));
                t.runAfterDelay(80,()->{
                    if(virtual())t.assertTrue(f.source().getItem(0).getCount()==7,"With the only destination connector unloaded, input must wait without losing items");
                    f.force(f.last-2,f.last,true);
                    phase(f,"remote-reloading");
                    await(t,()->f.level.getBlockEntity(f.end) instanceof InventoryConnectorBlockEntity && f.source().isEmpty() && f.amount()==12,()->{
                        t.assertTrue(f.entity()==original,"The exact same hopper must resume after destination reload without replacement");f.finish();t.succeed();
                    });
                });
            });
        });
    }
    @GameTest(maxTicks=10000)
    public void unloadingAndReloadingTheHopperChunkAutomaticallyRestoresTransport(GameTestHelper t){
        var f=far(t);warm(t,f,()->{
            var original=f.entity();f.force(f.first,f.first+2,false);
            await(t,()->f.level.getChunkSource().getChunkNow(f.first,f.z)==null,()->{
                t.runAfterDelay(80,()->{
                    f.force(f.first,f.first+2,true);
                    await(t,()->f.level.getBlockEntity(f.hopper) instanceof BasicInventoryHopperBlockEntity,()->{
                        t.assertTrue(f.entity().getBlockState().is(Additions.CURVATURE_HOPPER),"A real chunk reload must restore the curvature block");
                        f.source().setItem(0,new ItemStack(Items.DIAMOND,7));
                        await(t,()->f.source().isEmpty() && f.amount()==12,()->{f.finish();t.succeed();});
                    });
                });
            });
        });
    }
}
