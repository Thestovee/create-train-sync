// AI-generated implementation; human playtested. See AI_DISCLOSURE.md at repository root.
package traintrace.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traintrace.Trace;

@Mixin(targets = "com.simibubi.create.content.trains.entity.Train", remap = false)
public abstract class TrainMixin {
    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void traceBefore(@Coerce Object level, CallbackInfo ci) { Trace.train(this, level, "server_before"); }
    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private void traceAfter(@Coerce Object level, CallbackInfo ci) { Trace.train(this, level, "server_after"); }
}
