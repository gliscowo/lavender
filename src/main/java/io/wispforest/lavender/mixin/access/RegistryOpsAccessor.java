package io.wispforest.lavender.mixin.access;

import net.minecraft.registry.RegistryOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RegistryOps.class)
public interface RegistryOpsAccessor {

    @Accessor("registryInfoGetter")
    RegistryOps.RegistryInfoGetter lavender$getInfoGetter();

}
