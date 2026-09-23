package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.g3d.*;

/** Owns the solid display housings surrounding the original material probes. */
public final class MaterialInspectionBay implements Disposable {
    private final Model housing, floor, strip;
    private final DefaultModelInstance[] housings = new DefaultModelInstance[13];
    private final DefaultModelInstance[] strips = new DefaultModelInstance[13];
    private final DefaultModelInstance base;
    private boolean disposed;

    public MaterialInspectionBay(GraphicsContext graphics) {
        housing = new ModelBuilder(graphics).material(new Material("anodized sample housing")
                .set(MaterialAttributes.baseColor(.13f,.21f,.29f,1))
                .set(PbrAttributes.metallicFactor(.35f)).set(PbrAttributes.roughnessFactor(.5f)))
                .box(1.88f,1.86f,.65f);
        floor = new ModelBuilder(graphics).material(new Material("inspection platform")
                .set(MaterialAttributes.baseColor(.07f,.12f,.18f,1))).box(6.5f,.2f,2.3f);
        strip = new ModelBuilder(graphics).material(new Material("sample indicator")
                .shadingModel(ShadingModel.UNLIT)
                .set(MaterialAttributes.baseColor(.12f,.65f,.8f,1))).box(.35f,.035f,.025f);
        for (int i = 0; i < housings.length; i++) {
            float x = (i % 3 - 1) * 2, y = 4 - i / 3 * 2;
            housings[i] = new DefaultModelInstance(housing);
            housings[i].transform().setToTranslation(x,y,-.35f);
            strips[i] = new DefaultModelInstance(strip);
            strips[i].transform().setToTranslation(x-.62f,y+.84f,.005f);
        }
        base = new DefaultModelInstance(floor);
        base.transform().setToTranslation(0,-5.05f,-.25f);
    }

    public void render(ModelBatch batch) {
        batch.render(base);
        for (var instance : housings) batch.render(instance);
        for (var instance : strips) batch.render(instance);
    }

    @Override
    public boolean isDisposed() { return disposed; }
    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        try { housing.dispose(); }
        finally { try { floor.dispose(); } finally { strip.dispose(); } }
    }
}
