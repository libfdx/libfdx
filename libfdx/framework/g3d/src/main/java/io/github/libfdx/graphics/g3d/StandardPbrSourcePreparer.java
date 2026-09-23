package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.shader.ShaderProfile;
import java.util.function.Consumer;

/** Borrowed CPU strategy for the framework's standard PBR graph recipe. Inputs contain no device,
 * material state, or GPU handles. Results are immutable source data. Implementations may complete
 * on a platform event loop; fallback must use the supplied preparation executor. Application-owned
 * implementations must outlive their ModelShaderPlan and pending preparation operations. */
@FunctionalInterface
public interface StandardPbrSourcePreparer {
    FdxFuture<StandardPbrSources> prepare(ShaderProfile profile, Consumer<Runnable> execute);

    /** A strategy whose lifetime is owned by the model plan that created it. */
    interface Owned extends StandardPbrSourcePreparer, Disposable { }

}
