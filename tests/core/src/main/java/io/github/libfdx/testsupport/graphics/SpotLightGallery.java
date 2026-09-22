package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.Environment;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.MaterialAttributes;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.ModelBuilder;
import io.github.libfdx.graphics.g3d.ModelVertexUsage;
import io.github.libfdx.graphics.g3d.PbrAttributes;
import io.github.libfdx.graphics.g3d.SpotLight;
import io.github.libfdx.math.Color;

import java.util.ArrayList;

/** Owns the courtyard meshes; instances borrow them and allocate only during setup. */
public final class SpotLightGallery implements Disposable {
    private static final long LIT = ModelVertexUsage.STANDARD_PBR;
    private static final float HALF_PI = (float) (Math.PI * .5);
    private static final float POST_Z = -2.15f;
    private static final float ARM_Y = 4.5f;
    private static final float HEAD_Y = 4.12f;
    private static final float HEAD_HEIGHT = .42f;
    private static final float LENS_Y = HEAD_Y - HEAD_HEIGHT * .5f;
    private static final float PLINTH_TOP = .64f;

    private final ArrayList<Model> models = new ArrayList<>();
    private final ArrayList<DefaultModelInstance> instances = new ArrayList<>();
    private DefaultModelInstance[] shadowCasters;
    private boolean disposed;

    public SpotLightGallery(GraphicsContext graphics, Environment environment, Model dragon) {
        try {
            build(new ModelBuilder(graphics), environment, dragon);
            shadowCasters = instances.toArray(new DefaultModelInstance[0]);
        } catch (RuntimeException | Error failure) {
            dispose();
            throw failure;
        }
    }

    private void build(ModelBuilder builder, Environment environment, Model dragon) {
        Material paving = surface("slate paving", .38f, .43f, .46f, .88f, 0);
        Material limestone = surface("honed limestone", .64f, .61f, .53f, .8f, 0);
        Material metal = surface("graphite steel", .24f, .29f, .31f, .48f, .35f);
        Material timber = surface("oiled cedar", .43f, .23f, .105f, .8f, 0);
        Material bronze = surface("satin bronze", .70f, .42f, .18f, .36f, .5f);
        Material silver = surface("brushed aluminium", .56f, .66f, .69f, .38f, .55f);
        Model stoneBox = own(builder.material(limestone).cube(1, LIT));
        Model metalBox = own(builder.material(metal).cube(1, LIT));
        Model woodBox = own(builder.material(timber).cube(1, LIT));
        Model metalRod = own(builder.material(metal).cylinder(1, 1, 20, LIT));

        courtyard(builder, paving, stoneBox, metalBox, woodBox);
        sculptures(builder, bronze, silver, stoneBox, metalRod, dragon);
        lamps(builder, environment, metal, metalBox, metalRod);
        planting(builder, stoneBox);
        bench(woodBox, metalBox, -2.8f, 2.8f);
        bench(woodBox, metalBox, 2.8f, 2.8f);
    }

    private void courtyard(ModelBuilder builder, Material paving, Model stone, Model metal, Model wood) {
        Model ground = own(builder.material(surface("courtyard surroundings", .12f, .16f, .19f, 1, 0))
                .box(200, .2f, 200, LIT));
        place(ground, 0, -.64f, 0);
        // The slab ends at the tile bottoms, avoiding coplanar surfaces.
        place(stone, 0, -.31f, 0, 12.6f, .46f, 8.8f);
        Model tile = own(builder.material(paving).box(1.48f, .08f, 1.38f, LIT));
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 6; z++) {
                place(tile, (x - 3.5f) * 1.5f, -.04f, (z - 2.5f) * 1.4f);
            }
        }
        // Continuous border and two broad entrance steps give the slab a grounded edge.
        place(stone, -6.12f, .04f, 0, .24f, .08f, 8.5f);
        place(stone, 6.12f, .04f, 0, .24f, .08f, 8.5f);
        place(stone, 0, -.34f, 4.63f, 4.8f, .40f, .5f);
        place(stone, 0, -.44f, 5.03f, 5.4f, .20f, .5f);
        place(stone, 0, .36f, -3.95f, 12.2f, .72f, .35f);
        // Three freestanding cedar screens, with posts embedded in the rear wall.
        for (int bay = -1; bay <= 1; bay++) {
            float x = bay * 3.5f;
            place(metal, x - 1.48f, 1.47f, -3.95f, .12f, 2.1f, .16f);
            place(metal, x + 1.48f, 1.47f, -3.95f, .12f, 2.1f, .16f);
            place(metal, x, .84f, -3.95f, 3, .10f, .16f);
            place(metal, x, 2.46f, -3.95f, 3, .10f, .16f);
            for (int slat = 0; slat < 15; slat++) {
                place(wood, x + (slat - 7) * .19f, 1.65f, -3.92f, .12f, 1.72f, .14f);
            }
        }
    }

    private void sculptures(ModelBuilder builder, Material bronze, Material silver,
                            Model stone, Model rod, Model dragon) {
        Model foot = own(builder.material(surface("plinth foot", .2f, .23f, .24f, .8f, 0))
                .cylinder(1.03f, .12f, 64, LIT));
        Model plinth = own(builder.material(surface("sculpture plinth", .57f, .55f, .49f, .8f, 0))
                .cylinder(.96f, .52f, 64, LIT));
        for (int i = -1; i <= 1; i++) {
            float x = i * 3.3f;
            float width = i == 0 ? 1.4f : 1;
            place(foot, x, .06f, 0, width, 1, width);
            place(plinth, x, .38f, 0, width, 1, width);
            // A small tilted information plaque stands on its own wedge support.
            float plaqueZ = i == 0 ? 1.72f : 1.43f;
            place(stone, x, .12f, plaqueZ, .42f, .24f, .24f);
            place(stone, x, .26f, plaqueZ).transform().rotateX(-.35f).scale(.48f, .035f, .28f);
        }
        // This bundled glTF is already upright with its feet at y=0 (node transform included).
        // Its 3.52-unit width becomes 2.54 units, fitting the enlarged central plinth.
        for (int i = 0; i < dragon.materials().size(); i++) {
            dragon.materials().get(i).set(MaterialAttributes.baseColor(.58f, .36f, .17f, 1))
                    .set(PbrAttributes.metallicFactor(.35f)).set(PbrAttributes.roughnessFactor(.48f));
        }
        place(dragon, 0, PLINTH_TOP - .0012f, 0).transform().rotateY(-.25f).scale(.72f, .72f, .72f);

        // Two concentric upright rings share a central hub and a grounded stem.
        Model outer = own(builder.material(bronze).torus(.86f, .115f, 64, LIT));
        Model inner = own(builder.material(silver).torus(.57f, .085f, 64, LIT));
        float ringY = 1.68f;
        place(outer, -3.3f, ringY, 0).transform().rotateX(HALF_PI);
        place(inner, -3.3f, ringY, 0).transform().rotateY(.8f).rotateX(HALF_PI);
        segment(rod, -3.3f, PLINTH_TOP, 0, -3.3f, ringY + .90f, 0, .045f);
        segment(rod, -4.2f, ringY, 0, -2.4f, ringY, 0, .035f);
        Model hub = own(builder.material(bronze).sphere(.14f, 24, LIT));
        place(hub, -3.3f, ringY, 0);

        Model slab = own(builder.material(silver).box(1.04f, .155f, 1.04f, LIT));
        for (int i = 0; i < 12; i++) {
            place(slab, 3.3f, PLINTH_TOP + .0775f + i * .155f, 0)
                    .transform().rotateY(i * .15f);
        }
    }

    private void lamps(ModelBuilder builder, Environment environment, Material metal,
                       Model box, Model rod) {
        Model head = own(builder.material(metal).cylinder(.27f, HEAD_HEIGHT, 40, LIT));
        Model collar = own(builder.material(metal).cylinder(.30f, .08f, 40, LIT));
        Color[] colors = {new Color(1, .69f, .39f, 1), new Color(1, .86f, .65f, 1),
                new Color(.48f, .76f, 1, 1)};
        for (int i = 0; i < colors.length; i++) {
            float x = (i - 1) * 3.3f;
            // Every lamp is built from the same endpoints: foot -> post -> arm -> head.
            place(box, x, .07f, POST_Z, .50f, .14f, .50f);
            segment(rod, x, .1f, POST_Z, x, ARM_Y, POST_Z, .075f);
            segment(rod, x, ARM_Y, POST_Z, x, ARM_Y, 0, .075f);
            segment(rod, x, ARM_Y - .7f, POST_Z, x, ARM_Y, POST_Z + .7f, .038f);
            segment(rod, x, ARM_Y, 0, x, HEAD_Y, 0, .055f);
            place(head, x, HEAD_Y, 0);
            place(collar, x, LENS_Y + .015f, 0);
            Material luminous = surface("warm diffuser", .8f, .77f, .66f, .6f, 0)
                    .set(MaterialAttributes.emissiveColor(colors[i]));
            Model lens = own(builder.material(luminous).cylinder(.235f, .025f, 40, LIT));
            place(lens, x, LENS_Y - .02f, 0);
            environment.add(new SpotLight().position(x, LENS_Y - .04f, 0)
                    .direction(0, -1, 0).color(colors[i]).intensity(10).range(8)
                    .cone(i == 1 ? 17 : 22, i == 1 ? 29 : 34));
        }
    }

    private void planting(ModelBuilder builder, Model stone) {
        Model soil = own(builder.material(surface("soil", .10f, .075f, .04f, 1, 0)).cube(1, LIT));
        Model trunk = own(builder.material(surface("tree bark", .22f, .13f, .065f, 1, 0))
                .cylinder(1, 1, 12, LIT));
        Model leaf = own(builder.material(surface("olive leaves", .19f, .32f, .16f, .92f, 0))
                .sphere(1, 12, LIT));
        for (int side = -1; side <= 1; side += 2) {
            float x = side * 5.35f;
            float z = -2.85f;
            place(stone, x, .30f, z, 1.15f, .60f, 1.2f);
            place(soil, x, .605f, z, .96f, .035f, 1.01f);
            segment(trunk, x, .60f, z, x, 2.05f, z, .075f);
            for (int branch = 0; branch < 5; branch++) {
                float angle = branch * 2.399963f;
                float bx = x + .42f * (float) Math.cos(angle);
                float bz = z + .42f * (float) Math.sin(angle);
                float by = 1.7f + branch * .16f;
                segment(trunk, x, 1.15f, z, bx, by, bz, .035f);
                place(leaf, bx, by + .2f, bz, .52f, .55f, .48f);
            }
        }
    }

    private void bench(Model wood, Model metal, float x, float z) {
        place(metal, x - .8f, .22f, z, .12f, .44f, .6f);
        place(metal, x + .8f, .22f, z, .12f, .44f, .6f);
        for (int slat = 0; slat < 4; slat++) {
            place(wood, x, .48f, z + (slat - 1.5f) * .16f, 2.1f, .10f, .13f);
        }
    }

    /** Places a unit Y cylinder between endpoints, so joints cannot drift apart. */
    private void segment(Model cylinder, float ax, float ay, float az,
                         float bx, float by, float bz, float radius) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
        float length = (float) Math.sqrt(horizontal * horizontal + dy * dy);
        place(cylinder, (ax + bx) * .5f, (ay + by) * .5f, (az + bz) * .5f).transform()
                .rotateY((float) Math.atan2(dx, dz)).rotateX((float) Math.atan2(horizontal, dy))
                .scale(radius, length, radius);
    }

    private static Material surface(String name, float r, float g, float b, float roughness, float metal) {
        return new Material(name).set(MaterialAttributes.baseColor(r, g, b, 1))
                .set(PbrAttributes.roughnessFactor(roughness)).set(PbrAttributes.metallicFactor(metal));
    }

    private Model own(Model model) {
        models.add(model);
        return model;
    }

    private DefaultModelInstance place(Model model, float x, float y, float z) {
        DefaultModelInstance instance = new DefaultModelInstance(model);
        instance.transform().setToTranslation(x, y, z);
        instances.add(instance);
        return instance;
    }

    private void place(Model model, float x, float y, float z, float sx, float sy, float sz) {
        place(model, x, y, z).transform().scale(sx, sy, sz);
    }

    /** Borrowed, stable caster array shared by the main and shadow passes. */
    public DefaultModelInstance[] shadowCasters() {
        return shadowCasters;
    }

    public void render(ModelBatch batch) {
        for (int i = 0; i < instances.size(); i++) {
            batch.render(instances.get(i));
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        for (int i = models.size() - 1; i >= 0; i--) {
            models.get(i).dispose();
        }
        models.clear();
        instances.clear();
        shadowCasters = null;
    }
}
