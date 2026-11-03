package dev.tsa.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/** Never require TVSN, and never apply a private-local compatibility patch to unaudited versions. */
public final class AdditionsMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String targetClassName,String mixinClassName){
        if(!mixinClassName.endsWith(".TvsnUnloadedConnectorMixin"))return true;
        return FabricLoader.getInstance().getModContainer("toms_virtual_storage")
                .map(mod->mod.getMetadata().getVersion().getFriendlyString().equals("0.2.2-dev")).orElse(false);
    }
    @Override public void acceptTargets(Set<String> myTargets,Set<String> otherTargets){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String targetClassName,ClassNode targetClass,String mixinClassName,IMixinInfo info){}
    @Override public void postApply(String targetClassName,ClassNode targetClass,String mixinClassName,IMixinInfo info){}
}
