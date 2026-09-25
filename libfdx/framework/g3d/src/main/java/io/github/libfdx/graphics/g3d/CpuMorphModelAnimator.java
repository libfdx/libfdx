package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Vector3;

/** Owns morph-deformed copies for one instance, including morph-before-skin deformation.
 * Source meshes/materials and the graphics context are borrowed. Construct, update and dispose
 * on the graphics thread before submitting this instance. Indexed and unindexed PBR meshes
 * are supported. A separate AnimationController owns playback; call update after pose changes.
 * Dispose before the source/context. No allocation occurs during steady-state updates. */
public final class CpuMorphModelAnimator implements Disposable {
    private final DefaultModelInstance instance;
    private final int[] parts;
    private final Mesh[] meshes;
    private final CpuSkinningMeshUpdater[] updaters;
    private final Renderable3D[] originals, replacements;
    private final BoundingBox[] bounds;
    private final MorphBounds3D[] conservativeBounds;
    private boolean disposed;

    public CpuMorphModelAnimator(GraphicsContext graphics,DefaultModelInstance instance) {
        if (graphics == null || instance == null) throw new IllegalArgumentException("Morph animation requires graphics and an instance");
        this.instance=instance; int count=0;
        for (int i=0;i<instance.skinningPartCount();i++) if (instance.skinningSourcePart(i).morphTargetCount()>0) count++;
        parts=new int[count]; meshes=new Mesh[count]; updaters=new CpuSkinningMeshUpdater[count];
        originals=new Renderable3D[count]; replacements=new Renderable3D[count]; bounds=new BoundingBox[count];
        conservativeBounds=new MorphBounds3D[count];
        if (count != 0) instance.claimCpuSkinning(this);
        try {
            int at=0;
            for (int i=0;i<instance.skinningPartCount();i++) {
                ModelNodePart source=instance.skinningSourcePart(i); if (source.morphTargetCount()==0) continue;
                MeshPart part=source.meshPart(); Mesh mesh=part.mesh();
                if (!Mesh.isPbrLayout(mesh.vertexLayout()) || mesh.sourcePositions()==null || mesh.sourceNormals()==null
                        || mesh.sourceTexCoords()==null || mesh.sourcePbr()==null || mesh.sourceEmissive()==null
                        || mesh.indexCount()>0 && mesh.sourceIndices()==null)
                    throw new IllegalArgumentException("Morph animation needs retained PBR vertex/index data");
                BoundingBox box=new BoundingBox(new Vector3(),new Vector3()); bounds[at]=box;
                conservativeBounds[at]=new MorphBounds3D(source);
                Mesh.PositionColor3DPreparation preparation=Mesh.preparePositionColor3D(mesh.sourcePositions(),mesh.sourceColors(),mesh.sourceBakedColors(),
                        mesh.sourceNormals(),mesh.sourceTexCoords(),mesh.sourcePbr(),mesh.sourceBakedPbr(),mesh.sourceEmissive(),mesh.sourceBakedEmissive(),
                        null,null,box,true,mesh.sourceTexCoords1(),mesh.sourceTangents());
                while (!preparation.step(1024)) { }
                Mesh.PositionColor3DUpload upload=preparation.beginUpload(graphics,mesh.id()+"-morph",mesh.sourceIndices());
                try { while (!upload.step(1024*1024)) { } meshes[at]=upload.take(); } finally { upload.dispose(); }
                int[] joints=source.skin()==null ? new int[mesh.vertexCount()*4] : source.joints();
                float[] weights=source.skin()==null ? new float[joints.length] : source.weights();
                updaters[at]=new CpuSkinningMeshUpdater(graphics,meshes[at],joints,weights,source.morphTargets());
                originals[at]=instance.skinningRenderable(i); parts[at]=i;
                MeshPart copied=new MeshPart(part.id(),meshes[at],part.primitiveTopology(),part.firstVertex(),part.vertexCount(),part.firstIndex(),part.indexCount());
                replacements[at]=new Renderable3D(copied,originals[at].material(),originals[at].worldTransform(),box,originals[at].skinningPalette())
                        .cullingBounds(instance.skinningCullingOverride(i) ? originals[at].cullingBounds() : box);
                at++;
            }
            update();
            for (int i=0;i<count;i++) instance.skinningRenderable(parts[i],replacements[i]);
        } catch (RuntimeException | Error failure) {
            for (Mesh mesh : meshes) if (mesh != null) try { mesh.dispose(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            instance.releaseCpuSkinning(this); throw failure;
        }
    }
    public CpuMorphModelAnimator update() {
        if (disposed) throw new IllegalStateException("Morph animator is disposed");
        for (int i=0;i<meshes.length;i++) {
            updaters[i].update(originals[i].skinningPalette(),instance.skinningMorphWeights(parts[i]));
            float[] positions=meshes[i].sourcePositions();
            float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
            for (int v=0;v<positions.length;v+=3) {
                minX=Math.min(minX,positions[v]); maxX=Math.max(maxX,positions[v]);
                minY=Math.min(minY,positions[v+1]); maxY=Math.max(maxY,positions[v+1]);
                minZ=Math.min(minZ,positions[v+2]); maxZ=Math.max(maxZ,positions[v+2]);
            }
            float margin=Math.max(1e-5f,Math.max(Math.max(Math.abs(minX),Math.abs(maxX)),Math.max(Math.max(Math.abs(minY),Math.abs(maxY)),Math.max(Math.abs(minZ),Math.abs(maxZ))))*2e-6f);
            bounds[i].min().set(minX-margin,minY-margin,minZ-margin); bounds[i].max().set(maxX+margin,maxY+margin,maxZ+margin);
        }
        return this;
    }
    public int partCount() { return parts.length; }
    /** Updates conservative bounds without deforming or uploading vertices. This does not prepare
     * geometry for drawing; call update() if this instance is subsequently selected for rendering. */
    public CpuMorphModelAnimator updateBounds() {
        if (disposed) throw new IllegalStateException("Morph animator is disposed");
        for (int i=0;i<bounds.length;i++) conservativeBounds[i].update(originals[i].skinningPalette(),instance.skinningMorphWeights(parts[i]),bounds[i]);
        return this;
    }
    public DefaultModelInstance instance() { return instance; }
    @Override public boolean isDisposed() { return disposed; }
    @Override public void dispose() {
        if (disposed) return; disposed=true;
        for (int i=0;i<parts.length;i++) {
            originals[i].material(replacements[i].material());
            originals[i].cullingBounds(instance.skinningCullingOverride(parts[i]) ? replacements[i].cullingBounds()
                    : instance.skinningBounds(parts[i])!=null ? instance.skinningBounds(parts[i]) : originals[i].bounds());
            instance.skinningRenderable(parts[i],originals[i]);
        }
        instance.releaseCpuSkinning(this); Throwable failure=null;
        for (Mesh mesh : meshes) try { mesh.dispose(); } catch (RuntimeException | Error next) {
            if (failure==null) failure=next; else failure.addSuppressed(next);
        }
        if (failure instanceof RuntimeException next) throw next;
        if (failure instanceof Error next) throw next;
    }
}
