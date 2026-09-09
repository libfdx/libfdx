package io.github.libfdx.testsupport.graphics;

/** Independently constructed reference mesh for the imported glTF flight fixtures. */
public final class AnimationDroneGeometry {
    private AnimationDroneGeometry() { }

    public static float[] positions() {
        float[][] boxes = {{0,0,0,1.05f,.38f,.5f}, {.12f,0,.34f,.46f,.3f,.2f},
                {-.12f,0,0,.25f,1.35f,.16f}, {-.12f,-.62f,0,.65f,.2f,.32f},
                {-.12f,.62f,0,.65f,.2f,.32f}};
        float[] result = new float[boxes.length * 36 * 3];
        int offset = 0;
        for (float[] box : boxes) {
            for (int axis = 0; axis < 3; axis++) {
                int u = (axis + 1) % 3, v = (axis + 2) % 3;
                for (int sign = -1; sign <= 1; sign += 2) {
                    int[] order = sign > 0 ? new int[]{0,1,2,0,2,3} : new int[]{0,2,1,0,3,2};
                    for (int corner : order) {
                        for (int c = 0; c < 3; c++) {
                            int direction = c == axis ? sign : c == u
                                    ? (corner == 1 || corner == 2 ? 1 : -1)
                                    : (corner >= 2 ? 1 : -1);
                            result[offset++] = box[c] + direction * box[c + 3] * .5f;
                        }
                    }
                }
            }
        }
        return result;
    }
}
