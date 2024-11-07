package io.wispforest.lavender.mixin;

import io.wispforest.lavender.client.StructureOverlayRenderer;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {

    @Inject(method = "onMouseScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getOverlay()Lnet/minecraft/client/gui/screen/Overlay;"), cancellable = true)
    private void captureMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!StructureOverlayRenderer.hasPending()) return;

        StructureOverlayRenderer.rotatePending(vertical > 0);
        ci.cancel();
    }

}
