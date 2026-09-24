package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.math.BoundingBox;

/**
 * Owns CPU-skinned geometry for one model instance. Borrows the instance, model and graphics
 * context, which must outlive this animator. Construction copies retained, nonindexed mesh
 * attributes and binds the copies to this instance only; the shared model remains unchanged.
 * Dispose before the model/context to restore the original meshes and release the copies.
 * Create, update and dispose on the graphics thread, outside queued draws of this instance.
 * Only one CPU animator may be attached to an instance at a time.
 */
public final class CpuSkinnedModelAnimator implements Disposable {
    private final DefaultModelInstance instance;
    private final AnimationController controller;
    private final Mesh[] meshes;
    private final CpuSkinningMeshUpdater[] updaters;
    private final SkinningPalette[] palettes;
    private final Renderable3D[] originals, replacements;
    private final int[] partIndices;
    private final int skinCount;
    private boolean disposed;

    /** Creates and initially skins instance-owned copies. Missing retained attributes, unsupported
     * layouts or indexed meshes fail without rebinding the instance; partial copies are released. */
    public CpuSkinnedModelAnimator(GraphicsContext graphics, DefaultModelInstance instance) {
        if (graphics == null || instance == null)
            throw new FdxException("CPU animator needs a graphics context and model instance");
        this.instance=instance;
        controller=new AnimationController(instance);
        int count=0;
        for (int i=0;i<instance.skinningPartCount();i++)
            if (instance.skinningSourcePart(i).skin() != null) count++;
        meshes=new Mesh[count]; updaters=new CpuSkinningMeshUpdater[count]; palettes=new SkinningPalette[count];
        originals=new Renderable3D[count]; replacements=new Renderable3D[count]; partIndices=new int[count];
        instance.claimCpuSkinning(this);
        int uniqueSkins=0;
        try {
            int next=0;
            for (int i=0;i<instance.skinningPartCount();i++) {
                ModelNodePart source=instance.skinningSourcePart(i);
                if (source.skin() == null) continue;
                MeshPart part=source.meshPart(); Mesh mesh=part.mesh();
                int[] joints=source.joints(); float[] weights=source.weights();
                if (mesh.indexCount() != 0 || part.indexCount() != 0 || !mesh.hasPositionColor3DSource()
                        || joints.length != mesh.vertexCount()*4 || weights.length != joints.length)
                    throw new FdxException("CPU animation requires retained, nonindexed geometry and four influences: "+part.id());
                boolean pbr=Mesh.isPbrLayout(mesh.vertexLayout());
                if ((!pbr && mesh.vertexLayout()!=Mesh.POSITION_COLOR_LAYOUT)
                        || pbr && (mesh.sourceNormals()==null || mesh.sourceTexCoords()==null
                        || mesh.sourcePbr()==null || mesh.sourceEmissive()==null))
                    throw new FdxException("CPU animation requires retained PBR or position/color attributes: "+part.id());
                Renderable3D original=instance.skinningRenderable(i);
                BoundingBox bounds=instance.skinningBounds(i);
                if (bounds == null) throw new FdxException("CPU animation requires prepared skin bounds: "+part.id());
                Mesh copy=Mesh.positionColor3D(graphics,mesh.id()+"-cpu",mesh.sourcePositions(),mesh.sourceColors(),
                        mesh.sourceBakedColors(),mesh.sourceNormals(),mesh.sourceTexCoords(),mesh.sourcePbr(),
                        mesh.sourceBakedPbr(),mesh.sourceEmissive(),mesh.sourceBakedEmissive(),null,null,bounds,true,
                        mesh.sourceTexCoords1(),mesh.sourceTangents());
                meshes[next]=copy;
                updaters[next]=new CpuSkinningMeshUpdater(graphics,copy,joints,weights);
                palettes[next]=original.skinningPalette();
                boolean seen=false;
                for (int p=0;p<next;p++) if (palettes[p]==palettes[next]) { seen=true; break; }
                if (!seen) uniqueSkins++;
                updaters[next].update(palettes[next]);
                MeshPart copiedPart=new MeshPart(part.id(),copy,part.primitiveTopology(),part.firstVertex(),part.vertexCount());
                originals[next]=original;
                replacements[next]=new Renderable3D(copiedPart,original.material(),original.worldTransform(),bounds,palettes[next])
                        .cullingBounds(mesh.hasPbrSkinning() || instance.skinningCullingOverride(i) ? original.cullingBounds() : bounds);
                partIndices[next++]=i;
            }
            // Commit only after every copy and initial upload succeeds.
            for (int i=0;i<count;i++) instance.skinningRenderable(partIndices[i],replacements[i]);
        } catch (RuntimeException | Error failure) {
            for (Mesh mesh : meshes) if (mesh != null) {
                try { mesh.dispose(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            }
            instance.releaseCpuSkinning(this);
            throw failure;
        }
        skinCount=uniqueSkins;
    }

    /** Starts a borrowed clip at zero and updates geometry. Null stops playback. */
    public CpuSkinnedModelAnimator play(AnimationClip clip, boolean looping) {
        checkOpen(); controller.play(clip,looping); return updateSkinning();
    }

    /** Begins a TRS crossfade and updates geometry; see {@link AnimationController#crossFade}. */
    public CpuSkinnedModelAnimator crossFade(AnimationClip clip, boolean looping, float durationSeconds) {
        checkOpen(); controller.crossFade(clip,looping,durationSeconds); return updateSkinning();
    }

    /** Seeks without events and updates geometry. */
    public CpuSkinnedModelAnimator time(float timeSeconds) {
        checkOpen(); controller.time(timeSeconds); return updateSkinning();
    }

    /** Advances playback and updates geometry. A throwing event callback still synchronizes
     * geometry to the committed pose before its exception is propagated. */
    public CpuSkinnedModelAnimator update(float deltaSeconds) {
        checkOpen();
        try { controller.update(deltaSeconds); }
        catch (RuntimeException | Error failure) {
            try { if (!disposed) updateSkinning(); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        // A callback may dispose this animator.
        return disposed ? this : updateSkinning();
    }

    /** Stops playback and preserves the current pose/geometry. */
    public CpuSkinnedModelAnimator stop() { checkOpen(); controller.stop(); return this; }

    /** Updates copies from current instance palettes, including changes made directly through
     * nodeTransform() or controller(). No shared model vertices or source arrays are modified. */
    public CpuSkinnedModelAnimator updateSkinning() {
        checkOpen();
        for (int i=0;i<updaters.length;i++) updaters[i].update(palettes[i]);
        return this;
    }

    /** Returns the borrowed instance. */
    public DefaultModelInstance instance() { return instance; }
    /** Returns the owned playback controller. Direct pose mutations require updateSkinning()
     * before drawing. Do not retain/use it after disposing this animator. */
    public AnimationController controller() { checkOpen(); return controller; }
    /** Returns the number of distinct skins used by copied parts. */
    public int skinCount() { return skinCount; }
    /** Returns the number of independently owned skinned mesh parts. */
    public int skinnedPartCount() { return meshes.length; }
    @Override
    public boolean isDisposed() { return disposed; }

    /** Restores shared meshes, preserving instance material overrides, and releases every copy.
     * Idempotent, including when a provider reports a disposal failure. */
    @Override
    public void dispose() {
        if (disposed) return;
        disposed=true;
        controller.stop();
        for (int i=0;i<meshes.length;i++) {
            originals[i].material(replacements[i].material());
            if (originals[i].meshPart().mesh().hasPbrSkinning() || instance.skinningCullingOverride(partIndices[i]))
                originals[i].cullingBounds(replacements[i].cullingBounds());
            instance.skinningRenderable(partIndices[i],originals[i]);
        }
        instance.releaseCpuSkinning(this);
        Throwable first=null;
        for (Mesh mesh : meshes) {
            try { mesh.dispose(); }
            catch (RuntimeException | Error failure) { if (first==null) first=failure; else first.addSuppressed(failure); }
        }
        if (first instanceof RuntimeException) throw (RuntimeException)first;
        if (first instanceof Error) throw (Error)first;
    }

    private void checkOpen() { if (disposed) throw new FdxException("CPU animator is disposed"); }
}
