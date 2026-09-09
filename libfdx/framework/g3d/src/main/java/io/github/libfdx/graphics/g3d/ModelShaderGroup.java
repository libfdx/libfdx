package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.runtime.PreparedShaderPass;
import io.github.libfdx.graphics.shader.runtime.ResolvedShaderPass;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadRecipe;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOrigin;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationState;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.VertexLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/** Renderer-owned required pass dependencies. All declared passes for one renderable become
 * visible together. Call beginFrame after preparation.update and before any dependent pass;
 * pass this group to every participating ModelBatch. Clear/rebuild shadow targets normally even
 * while models are omitted; never reuse previous shadow commands or stale shadow content.
 * Renderable references are borrowed and must remain stable, as in DefaultModelInstance.
 * This group pins its entries until disposed and never schedules a render graph. */
public final class ModelShaderGroup implements Disposable {
    private final ShaderPreparation preparation;
    private final ModelShaderPlan plan;
    private final String label;
    private final TreeMap<ShaderPassId, ModelShaderPlan> passPlans = new TreeMap<>();
    private final List<Member> members = new ArrayList<>();
    private long frame = -1;
    private boolean disposed;

    public ModelShaderGroup(ShaderPreparation preparation, ModelShaderPlan plan) {
        this(preparation, plan, "required-model-passes");
    }

    public ModelShaderGroup(ShaderPreparation preparation, ModelShaderPlan plan, String label) {
        this.preparation = Objects.requireNonNull(preparation, "preparation");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.label = Objects.requireNonNull(label, "label");
        plan.requireDomain(preparation.device());
    }

    ShaderPreparation preparation() { return preparation; }
    ModelShaderPlan plan() { return plan; }

    /** Borrows different definitions for a dependent pass, such as a packed-depth shadow renderer.
     * Configure between frame snapshots, before beginning any dependent pass. */
    public ModelShaderGroup usePlan(ShaderPassId pass, ModelShaderPlan value) {
        requireActive();
        if (frame == preparation.frameIndex()) throw new FdxException("Cannot change a shader group after its frame snapshot");
        Objects.requireNonNull(value).requireDomain(preparation.device());
        passPlans.put(Objects.requireNonNull(pass), value);
        return this;
    }

    private ModelShaderPlan plan(ShaderPassId pass) { return passPlans.getOrDefault(pass, plan); }

    /** Declares required pass/target pairs for a renderable before beginFrame. No native work.
     * Use a separate group for another content lifetime or independent optional effects. */
    public ModelShaderGroup include(Renderable3D renderable, ShaderPassId[] passes, RenderTargetLayout[] targets) {
        requireActive();
        if (frame == preparation.frameIndex()) throw new FdxException("Cannot change a shader group after its frame snapshot");
        if (passes == null || targets == null || passes.length == 0 || passes.length != targets.length) {
            throw new FdxException("Shader group requires matching nonempty pass and target arrays");
        }
        for (Member member : members) if (member.renderable == renderable) throw new FdxException("Renderable already belongs to this group");
        for (int i = 0; i < passes.length; i++) { Objects.requireNonNull(passes[i]); Objects.requireNonNull(targets[i]); }
        members.add(new Member(Objects.requireNonNull(renderable), passes.clone(), targets.clone()));
        return this;
    }

    public ModelShaderGroup include(ModelInstance instance, ShaderPassId[] passes, RenderTargetLayout[] targets) {
        DefaultRenderQueue3D queue = new DefaultRenderQueue3D();
        instance.collectRenderables(queue);
        for (int i = 0; i < queue.size(); i++) include(queue.get(i), passes, targets);
        return this;
    }

    /** Freezes one readiness decision per renderable for every dependent pass this frame.
     * Structural changes are observed here; changing material structure afterward is an error. */
    public void beginFrame() {
        requireActive();
        if (frame == preparation.frameIndex()) return;
        plan.requireDomain(preparation.device());
        for (int i = 0; i < members.size(); i++) {
            Member member = members.get(i);
            PrimitiveTopology topology = member.renderable.meshPart().primitiveTopology();
            boolean changed = false, structural = topology != member.topology;
            for (int p = 0; p < member.passes.length; p++) {
                ModelShaderPlan selected = plan(member.passes[p]);
                selected.requireDomain(preparation.device());
                ShaderProvider provider = selected.provider(member.renderable);
                VertexLayout layout = selected.layout(member.renderable);
                String variant = selected.variant(member.renderable);
                long revision = provider != null ? provider.revision() : -1;
                structural |= selected != member.plans[p] || member.providers[p] != provider
                        || !layout.equals(member.layouts[p]) || !variant.equals(member.variants[p]);
                changed |= revision != member.revisions[p];
            }
            if (structural || changed) {
                member.releasePending();
                if (structural) member.releaseReady();
                member.topology = topology;
                for (int p = 0; p < member.passes.length; p++) {
                    ModelShaderPlan selected = plan(member.passes[p]);
                    ShaderProvider provider = selected.provider(member.renderable);
                    member.plans[p] = selected; member.providers[p] = provider;
                    member.layouts[p] = selected.layout(member.renderable);
                    member.variants[p] = selected.variant(member.renderable);
                    member.revisions[p] = provider != null ? provider.revision() : -1;
                    if (provider != null) {
                        member.pending[p] = preparation.request(provider,
                                selected.request(member.renderable, member.passes[p], member.targets[p]));
                        if (member.ready[p] != null && !provider.canRenderPreparedRevision(
                                member.pending[p].request(), member.ready[p].readyPass())) member.releaseReady();
                        ShaderPreparationOrigin origin = selected.origin(member.renderable, member.pending[p].request());
                        ShaderPreloadRecipe recipe = origin.recipe();
                        if (recipe != null) {
                            var parameters = new TreeMap<>(recipe.parameters());
                            StringBuilder passes = new StringBuilder(), roles = new StringBuilder();
                            for (int r = 0; r < member.passes.length; r++) {
                                if (r > 0) { passes.append(','); roles.append(','); }
                                passes.append(member.passes[r].value());
                                String role = plan(member.passes[r]).targets().role(member.targets[r]);
                                roles.append(role != null ? role : "REQUIRES_INPUT");
                            }
                            parameters.put("group", label); parameters.put("requiredPasses", passes.toString()); parameters.put("requiredTargets", roles.toString());
                            recipe = new ShaderPreloadRecipe(recipe.factory(), recipe.version(), recipe.targetRole(), parameters, recipe.conditions());
                        }
                        member.origins[p] = new ShaderPreparationOrigin(origin.renderer(), origin.content(), origin.material(), label, recipe);
                    }
                }
            }
            boolean complete = true;
            member.state = ShaderPreparationState.READY;
            for (PreparedShaderPass handle : member.pending) {
                if (handle == null) { complete = false; member.state = ShaderPreparationState.UNSUPPORTED; continue; }
                ShaderPreparationState state = handle.state();
                if (state != ShaderPreparationState.READY) {
                    complete = false;
                    if (member.state == ShaderPreparationState.READY || state.terminal()) member.state = state;
                }
            }
            if (complete) {
                for (int p = 0; p < member.pending.length; p++) {
                    if (member.ready[p] != member.pending[p]) {
                        if (member.ready[p] != null) member.ready[p].dispose();
                        member.ready[p] = member.pending[p];
                    }
                }
            }
            member.visible = member.ready[0] != null && member.ready[0].readyPass() != null;
        }
        frame = preparation.frameIndex();
    }

    public boolean isReady(Renderable3D renderable) { return member(renderable).visible; }
    public ShaderPreparationState state(Renderable3D renderable) {
        Member member = member(renderable);
        return member.visible ? ShaderPreparationState.READY : member.state;
    }

    ResolvedShaderPass resolve(Renderable3D renderable, ShaderPassId pass, RenderTargetLayout target) {
        return resolve(renderable, pass, target, plan(pass));
    }

    ResolvedShaderPass resolve(Renderable3D renderable, ShaderPassId pass, RenderTargetLayout target, ModelShaderPlan batchPlan) {
        Member member = member(renderable);
        for (int p = 0; p < member.passes.length; p++) {
            ModelShaderPlan selected = plan(member.passes[p]);
            if (selected != member.plans[p] || selected.provider(renderable) != member.providers[p]
                    || !selected.layout(renderable).equals(member.layouts[p]) || !selected.variant(renderable).equals(member.variants[p])
                    || renderable.meshPart().primitiveTopology() != member.topology
                    || (member.providers[p] != null && member.providers[p].revision() != member.revisions[p])) {
                throw new FdxException("Model shader structure changed after group beginFrame");
            }
        }
        for (int i = 0; i < member.passes.length; i++) {
            if (member.passes[i].equals(pass) && member.targets[i].equals(target)) {
                if (batchPlan != member.plans[i]) throw new FdxException("Batch plan does not match the declared dependent pass");
                if (member.pending[i] != null) member.pending[i].recordDraws(member.origins[i],
                        renderable.meshPart().mesh().id(), renderable.material().id(), 1, !member.visible);
                return member.visible ? member.ready[i].readyPass() : null;
            }
        }
        throw new FdxException("Pass/target was not declared in the model shader group");
    }

    private Member member(Renderable3D renderable) {
        requireActive();
        if (frame != preparation.frameIndex()) throw new FdxException("Call ModelShaderGroup.beginFrame before dependent passes");
        for (int i = 0; i < members.size(); i++) if (members.get(i).renderable == renderable) return members.get(i);
        throw new FdxException("Renderable was not declared in the model shader group");
    }

    private void requireActive() { if (disposed) throw new FdxException("Model shader group is disposed"); }
    @Override public boolean isDisposed() { return disposed; }
    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        for (Member member : members) { member.releasePending(); member.releaseReady(); }
        members.clear();
    }

    private static final class Member {
        final Renderable3D renderable;
        final ShaderPassId[] passes;
        final RenderTargetLayout[] targets;
        final PreparedShaderPass[] pending, ready;
        final ShaderPreparationOrigin[] origins;
        final ModelShaderPlan[] plans;
        final ShaderProvider[] providers;
        final VertexLayout[] layouts;
        final String[] variants;
        final long[] revisions;
        PrimitiveTopology topology;
        boolean visible;
        ShaderPreparationState state = ShaderPreparationState.QUEUED;
        Member(Renderable3D renderable, ShaderPassId[] passes, RenderTargetLayout[] targets) {
            this.renderable = renderable; this.passes = passes; this.targets = targets;
            pending = new PreparedShaderPass[passes.length]; ready = new PreparedShaderPass[passes.length];
            origins = new ShaderPreparationOrigin[passes.length];
            plans = new ModelShaderPlan[passes.length]; providers = new ShaderProvider[passes.length];
            layouts = new VertexLayout[passes.length]; variants = new String[passes.length]; revisions = new long[passes.length];
        }
        void releasePending() {
            for (int i = 0; i < pending.length; i++) {
                if (pending[i] != null && pending[i] != ready[i]) pending[i].dispose();
                pending[i] = null;
            }
        }
        void releaseReady() {
            for (int i = 0; i < ready.length; i++) {
                if (ready[i] != null) ready[i].dispose();
                ready[i] = null;
            }
        }
    }
}
