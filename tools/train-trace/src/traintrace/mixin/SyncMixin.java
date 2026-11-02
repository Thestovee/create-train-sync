// AI-generated implementation; human playtested. See AI_DISCLOSURE.md at repository root.
package traintrace.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import traintrace.Trace;

@Mixin(targets = "com.simibubi.create.content.trains.entity.CarriageSyncData", remap = false)
public abstract class SyncMixin {
    @Inject(method = "copy", at = @At("RETURN"), remap = false)
    private void traceCopy(CallbackInfoReturnable<Object> ci) { Trace.copied(this, ci.getReturnValue()); }
    @Inject(method = "update", at = @At("RETURN"), remap = false)
    private void tracePrepared(@Coerce Object entity, @Coerce Object carriage, CallbackInfo ci) { Trace.prepared(this, entity, carriage); }
    @Inject(method = "write", at = @At("HEAD"), remap = false)
    private void traceSerialized(@Coerce Object buffer, CallbackInfo ci) { Trace.serialized(this); }
    @Inject(method = "apply", at = @At("HEAD"), remap = false)
    private void traceBefore(@Coerce Object entity, @Coerce Object carriage, CallbackInfo ci) { Trace.beginApply(this, entity, carriage); }
    @Inject(method = "apply", at = @At("RETURN"), remap = false)
    private void traceAfter(@Coerce Object entity, @Coerce Object carriage, CallbackInfo ci) { Trace.endApply(this, entity, carriage); }
    @Inject(method = "getDistanceTo", at = @At("RETURN"), remap = false)
    private void traceSearch(@Coerce Object graph, @Coerce Object current, @Coerce Object target, float limit, boolean forward, CallbackInfoReturnable<Float> ci) {
        Trace.searched(graph, current, target, limit, forward, ci.getReturnValue());
    }
    @Inject(method = "approach", at = @At("HEAD"), remap = false)
    private void traceApproachBefore(@Coerce Object entity, @Coerce Object carriage, float partial, CallbackInfo ci) { Trace.beginApproach(this); }
    @Inject(method = "approach", at = @At("RETURN"), remap = false)
    private void traceApproachAfter(@Coerce Object entity, @Coerce Object carriage, float partial, CallbackInfo ci) { Trace.endApproach(this, entity, carriage); }
}
