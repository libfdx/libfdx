package io.github.libfdx.backend.psp.natives;

import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

/**
 * Exposes API access for PSP texture.
 *
 * @author xpenatan
 */
@Include("PSPTextureApi.h")
public class PSPTextureApi {

    /**
     * Calls the load texture native function.
     *
     * @param filename the filename
     * @param vram the vram
     * @return the created value
     */
    @Import(name = "load_texture")
    public static native Address load_texture(String filename, int vram);

}