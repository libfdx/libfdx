package io.github.libfdx.audio;

import io.github.libfdx.core.ProviderId;

/** Explicit provider setup. Backends own the service returned by create(). */
public interface AudioProvider {
    /** Logical provider identity, not a resource-domain identity. */
    ProviderId providerId();
    /** Creates a new service on the application thread, or throws on device failure. */
    Audio create();
}
