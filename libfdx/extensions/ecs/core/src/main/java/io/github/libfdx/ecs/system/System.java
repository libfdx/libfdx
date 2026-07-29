package io.github.libfdx.ecs.system;

import io.github.libfdx.ecs.World;

/**
 * Defines lifecycle and enablement shared by every system registered with a
 * {@link World}.
 *
 * <p>Phase participation is explicit through {@link UpdateSystem},
 * {@link RenderSystem}, and {@link UiRenderSystem}. A system may implement
 * more than one phase interface while attaching and detaching only once.</p>
 */
public interface System {
    void onAttach(World world);

    void onDetach(World world);

    boolean isEnabled();

    void setEnabled(boolean enabled);
}
