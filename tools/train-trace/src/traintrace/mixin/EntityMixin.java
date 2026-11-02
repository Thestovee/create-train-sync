// AI-generated implementation; human playtested. See AI_DISCLOSURE.md at repository root.
package traintrace.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traintrace.Trace;

@Mixin(targets = "com.simibubi.create.content.trains.entity.CarriageContraptionEntity", remap = false)
public abstract class EntityMixin {
    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private void traceTick(CallbackInfo ci) { Trace.entity(this); }
}
