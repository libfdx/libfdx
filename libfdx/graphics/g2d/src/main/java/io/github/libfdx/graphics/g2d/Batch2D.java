package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.Texture;

public interface Batch2D extends Disposable {
    void begin();

    void begin(LoadOp loadOp);

    void begin(RenderPass pass);

    Batch2D color(float red, float green, float blue, float alpha);

    Batch2D viewport(int width, int height);

    void draw(Texture texture, float x, float y, float width, float height);

    void draw(Texture texture, float x, float y, float width, float height,
            float originX, float originY, float rotationDegrees);

    void draw(TextureRegion region, float x, float y, float width, float height);

    void draw(TextureRegion region, float x, float y, float width, float height,
            float originX, float originY, float rotationDegrees);

    void draw(TextureRegion region, float[] centerX, float[] centerY, int count,
            float width, float height, float originX, float originY, float rotationDegrees);

    void end();
}
