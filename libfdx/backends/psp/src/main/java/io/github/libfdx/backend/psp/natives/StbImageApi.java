package io.github.libfdx.backend.psp.natives;

import org.teavm.interop.Address;
import org.teavm.interop.Import;

/**
 * Exposes API access for stb image.
 *
 * @author xpenatan
 */
public class StbImageApi {

    public static final int STBI_default = 0; // only used for desired_channels
    public static final int STBI_grey       = 1;
    public static final int STBI_grey_alpha = 2;
    public static final int STBI_rgb        = 3;
    public static final int STBI_rgb_alpha  = 4;

    /**
     * Calls the stbi set flip vertically on load native function.
     *
     * @param flag_true_if_should_flip the flag true if should flip
     */
    @Import(name = "stbi_set_flip_vertically_on_load")
    public static native void stbi_set_flip_vertically_on_load(int flag_true_if_should_flip);

    /**
     * Calls the stbi load native function.
     *
     * @param filename the filename
     * @param x the x coordinate
     * @param y the y coordinate
     * @param comp the comp
     * @param req_comp the req comp
     * @return the stbi load
     */
    @Import(name = "stbi_load")
    public static native Address stbi_load(String filename, Address x, Address y, Address comp, int req_comp);

    /**
     * Calls the stbi image free native function.
     *
     * @param retval_from_stbi_load the retval from stbi load
     */
    @Import(name = "stbi_image_free")
    public static native void stbi_image_free(Address retval_from_stbi_load);

}