// AI-generated implementation; human playtested. See AI_DISCLOSURE.md at repository root.
package trainsyncfix.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import trainsyncfix.FractionBudget;
import trainsyncfix.Recovery;

@Mixin(targets="com.simibubi.create.content.trains.entity.CarriageSyncData", remap=false)
public abstract class SyncMixin {
    @Unique private final FractionBudget trainsyncfix$budget = new FractionBudget();
    @Unique private Recovery.Point[] trainsyncfix$beforeApply;
    @Unique private float[] trainsyncfix$beforeApproach;

    @Inject(method="apply", at=@At("HEAD"), remap=false)
    private void beforeApply(@Coerce Object entity, @Coerce Object carriage, CallbackInfo ci) {
        trainsyncfix$budget.reset();
        trainsyncfix$beforeApply = Recovery.capture(carriage);
    }
    @Inject(method="apply", at=@At("RETURN"), remap=false)
    private void afterApply(@Coerce Object entity, @Coerce Object carriage, CallbackInfo ci) {
        if (Recovery.afterApply(this, carriage, trainsyncfix$beforeApply)) trainsyncfix$budget.finish();
        trainsyncfix$beforeApply = null;
    }
    @Inject(method="approach", at=@At("HEAD"), remap=false)
    private void beforeApproach(@Coerce Object entity, @Coerce Object carriage, float fraction, CallbackInfo ci) {
        trainsyncfix$beforeApproach = Recovery.deltas(this);
    }
    // Verified on Create 6.0.10 bytecode: final float partial is the second float local (slot 6).
    // The original TPS scaling and late-update scaling happen before this store.
    @ModifyVariable(method="approach", at=@At("STORE"), ordinal=1, remap=false, require=1)
    private float boundedFraction(float fraction) {
        return Recovery.capEnabled() ? trainsyncfix$budget.consume(fraction) : fraction;
    }
    @Inject(method="approach", at=@At("RETURN"), remap=false)
    private void afterApproach(@Coerce Object entity, @Coerce Object carriage, float fraction, CallbackInfo ci) {
        if (Recovery.afterApproach(this, carriage, trainsyncfix$beforeApproach)) trainsyncfix$budget.finish();
        trainsyncfix$beforeApproach = null;
    }
}
