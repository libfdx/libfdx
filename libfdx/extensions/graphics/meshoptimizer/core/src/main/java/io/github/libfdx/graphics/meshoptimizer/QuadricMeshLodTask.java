package io.github.libfdx.graphics.meshoptimizer;

import java.util.Arrays;

/** Preparation state for the portable simplifier; every type and buffer is CPU-only. */
final class QuadricMeshLodTask implements MeshLodTask {
    private static final int[] UV_CHANNELS = {MeshLodData.UV0, MeshLodData.UV1};
    private final MeshLodData source;
    private final ModelLodSettings settings;
    private final int vertices, faces, target;
    private final double limit;
    private final int[] indices, representative, seamTwin, marks, neighbors, versions, bestTarget;
    private final int[] positionRepresentative, positionHead, positionNext;
    private int[] affected = new int[32], opposite = new int[32], output;
    private int outputCursor;
    private boolean hasSeams;
    private LodMeshOrderingTask ordering;
    private LodDeformationQuadric deformation;
    private final LodVertexIndex exactIndex, positionIndex;
    private final boolean[] alive, fixed, boundary;
    private final LodIntList[] incident;
    private final double[] position, quadrics;
    private final int[] attrChannel, attrComponent;
    private final double[] attrWeight;
    private final int attributes, stride;
    private final LodCollapseHeap heap = new LodCollapseHeap();
    private final double[] minimum = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
    private final double[] maximum = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
    private int phase, cursor, triangles, stamp = 1;
    private double scale, attained;
    private MeshLodResult result;

    QuadricMeshLodTask(MeshLodData source, ModelLodTarget target, ModelLodSettings settings) {
        this.source = source; this.settings = settings;
        vertices = source.vertexCount(); faces = source.triangleCount();
        this.target = Math.max(1, (int)Math.floor(faces * (double)target.triangleRatio()));
        limit = target.maxError() * (double)target.maxError();
        indices = source.indices().clone(); representative = new int[vertices];
        exactIndex = source.compact ? null : new LodVertexIndex(source, false); positionIndex = new LodVertexIndex(source, true);
        positionRepresentative = new int[vertices]; positionHead = new int[vertices]; positionNext = new int[vertices];
        seamTwin = new int[vertices]; Arrays.fill(seamTwin, -1);
        marks = new int[vertices]; neighbors = new int[vertices]; versions = new int[vertices];
        bestTarget = new int[vertices]; Arrays.fill(bestTarget, -1);
        alive = new boolean[faces]; fixed = new boolean[vertices]; boundary = new boolean[vertices];
        incident = new LodIntList[vertices]; position = new double[Math.multiplyExact(vertices, 3)];
        attrChannel = new int[11]; attrComponent = new int[11]; attrWeight = new double[11];
        int count = 0;
        for (int channel : new int[] {MeshLodData.NORMAL, MeshLodData.UV0, MeshLodData.UV1, MeshLodData.COLOR}) {
            double weight = channel == MeshLodData.NORMAL ? settings.normalWeight()
                    : channel == MeshLodData.COLOR ? settings.colorWeight() : settings.uvWeight();
            if (source.channel(channel) == null || weight == 0) continue;
            for (int c = 0; c < MeshLodData.components(channel); c++) {
                attrChannel[count] = channel; attrComponent[count] = c; attrWeight[count++] = weight * weight;
            }
        }
        attributes = count; stride = 11 + attributes * 4;
        quadrics = new double[Math.multiplyExact(vertices, stride)];
    }

    @Override public boolean step(int budget) {
        if (budget < 1) throw new IllegalArgumentException("Positive work budget required");
        if (deformation!=null && !deformation.initializeStep()) return false;
        while (budget-- > 0 && result == null) {
            switch (phase) {
                case 0 -> {
                    if (cursor < vertices) bounds(cursor++);
                    else { scale = Math.max(maximum[0] - minimum[0], Math.max(maximum[1] - minimum[1], maximum[2] - minimum[2]));
                        if (!(scale > 0)) scale = 1;
                        if (source.deformation()!=null) { deformation=new LodDeformationQuadric(source,scale);next();return false; }
                        next(); }
                }
                case 1 -> { if (cursor < vertices) weld(cursor++); else next(); }
                case 2 -> { if (cursor < faces) triangle(cursor++); else next(); }
                case 3 -> { if (cursor < vertices) borders(cursor++); else next(); }
                case 4 -> { if (cursor < vertices) candidate(cursor++); else next(); }
                case 5 -> {
                    if (triangles <= target || heap.size == 0) next();
                    else collapseNext();
                }
                case 6 -> {
                    if (triangles == 0) { result = new MeshLodResult(source,0); continue; }
                    if (output == null) { output = new int[triangles*3]; return false; }
                    if (cursor < faces) { if (alive[cursor]) { System.arraycopy(indices,cursor*3,output,outputCursor,3); outputCursor += 3; } cursor++; }
                    else { ordering = new LodMeshOrderingTask(source,output,settings.optimizeCache(),settings.optimizeFetch()); next(); return false; }
                }
                default -> {
                    if (ordering.step(budget+1)) result = new MeshLodResult(ordering.result(),(float)Math.sqrt(attained),triangles==source.triangleCount());
                    return result != null;
                }
            }
        }
        return result != null;
    }
    @Override public MeshLodResult result() {
        if (result == null) throw new IllegalStateException("Simplification is incomplete");
        return result;
    }
    private void next() { phase++; cursor = 0; }
    private void bounds(int v) {
        float[] p = source.channel(MeshLodData.POSITION);
        for (int k = 0; k < 3; k++) { minimum[k] = Math.min(minimum[k], p[v * 3 + k]); maximum[k] = Math.max(maximum[k], p[v * 3 + k]); }
    }
    private void weld(int v) {
        representative[v] = exactIndex == null ? v : exactIndex.add(v);
        if (representative[v] != v) return;
        incident[v] = new LodIntList();
        fixed[v] = source.vertexLocked(v);
        float[] p = source.channel(0);
        for (int k = 0; k < 3; k++) position[v * 3 + k] = (p[v * 3 + k] - minimum[k]) / scale;
        int other = positionIndex.add(v);
        positionRepresentative[v] = other;
        positionNext[v] = other == v ? -1 : positionHead[other];
        positionHead[other] = v;
        if (other == v) return;
        hasSeams = true;
        if (seamTwin[other] == -1) { seamTwin[v] = other; seamTwin[other] = v; }
        else {
            // Three or more wedges form a corner/junction. Keep it fixed.
            if (seamTwin[other] >= 0) { fixed[seamTwin[other]] = true; seamTwin[seamTwin[other]] = -2; }
            fixed[other] = true; fixed[v] = true; seamTwin[other] = -2; seamTwin[v] = -2;
        }
    }
    private void triangle(int f) {
        int offset = f * 3;
        int a = indices[offset] = representative[indices[offset]], b = indices[offset + 1] = representative[indices[offset + 1]], c = indices[offset + 2] = representative[indices[offset + 2]];
        if (a == b || b == c || c == a) return;
        double ux = x(b) - x(a), uy = y(b) - y(a), uz = z(b) - z(a);
        double vx = x(c) - x(a), vy = y(c) - y(a), vz = z(c) - z(a);
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (!(length > 1e-20)) {
            if (deformation!=null) {
                alive[f]=true;triangles++;incident[a].add(f);incident[b].add(f);incident[c].add(f);deformation.triangle(a,b,c);
            }
            return;
        }
        alive[f] = true; triangles++;
        incident[a].add(f); incident[b].add(f); incident[c].add(f);
        if (deformation!=null) deformation.triangle(a,b,c);
        nx /= length; ny /= length; nz /= length;
        double h = -(nx * x(a) + ny * y(a) + nz * z(a)), area = length * .5;
        addPlane(a, nx, ny, nz, h, area); addPlane(b, nx, ny, nz, h, area); addPlane(c, nx, ny, nz, h, area);
        quadrics[a * stride + 10] += area; quadrics[b * stride + 10] += area; quadrics[c * stride + 10] += area;
        double uu = ux * ux + uy * uy + uz * uz, uv = ux * vx + uy * vy + uz * vz, vv = vx * vx + vy * vy + vz * vz;
        double determinant = length * length;
        for (int k = 0; k < attributes; k++) {
            double aa = attribute(a, k), ab = attribute(b, k) - aa, ac = attribute(c, k) - aa;
            double s = (ab * vv - ac * uv) / determinant, t = (ac * uu - ab * uv) / determinant;
            double gx = ux * s + vx * t, gy = uy * s + vy * t, gz = uz * s + vz * t;
            double gh = aa - gx * x(a) - gy * y(a) - gz * z(a), weight = area * attrWeight[k];
            addAttribute(a, k, gx, gy, gz, gh, weight); addAttribute(b, k, gx, gy, gz, gh, weight); addAttribute(c, k, gx, gy, gz, gh, weight);
        }
    }
    private void addPlane(int vertex, double x, double y, double z, double h, double w) {
        int q = vertex * stride;
        quadrics[q] += x*x*w; quadrics[q+1] += x*y*w; quadrics[q+2] += x*z*w; quadrics[q+3] += x*h*w;
        quadrics[q+4] += y*y*w; quadrics[q+5] += y*z*w; quadrics[q+6] += y*h*w;
        quadrics[q+7] += z*z*w; quadrics[q+8] += z*h*w; quadrics[q+9] += h*h*w;
    }
    private void addAttribute(int v, int k, double x, double y, double z, double h, double w) {
        addPlane(v, x, y, z, h, w);
        int offset = v * stride + 11 + k * 4;
        quadrics[offset] += x*w; quadrics[offset+1] += y*w; quadrics[offset+2] += z*w; quadrics[offset+3] += h*w;
    }
    private int neighbors(int v) {
        int count = 0; stamp++;
        LodIntList list = incident[v]; if (list == null) return 0;
        int write = 0;
        for (int i = 0; i < list.size; i++) {
            int f = list.data[i]; if (!alive[f] || !contains(f, v)) continue;
            list.data[write++] = f;
            for (int j = 0; j < 3; j++) {
                int n = indices[f*3+j];
                if (n != v && marks[n] != stamp) { marks[n] = stamp; neighbors[count++] = n; }
            }
        }
        list.size = write;
        return count;
    }
    private int edgeFaces(int a, int b) {
        int count = 0; LodIntList list = incident[a];
        for (int i = 0; i < list.size; i++) if (alive[list.data[i]] && contains(list.data[i], a) && contains(list.data[i], b)) count++;
        return count;
    }
    private void borders(int v) {
        if (incident[v] == null) return;
        int count = neighbors(v), borderEdges = 0;
        for (int i = 0; i < count; i++) {
            int n = neighbors[i], edge = edgeFaces(v, n);
            if (edge > 2) fixed[v] = true;
            if (edge != 1) continue;
            borderEdges++; boundary[v] = true;
            if (settings.lockBorders()) fixed[v] = true;
            // A perpendicular constraint plane prevents silhouette shrinkage along open edges.
            int face = -1; LodIntList list = incident[v];
            for (int j = 0; j < list.size; j++) if (alive[list.data[j]] && contains(list.data[j], n)) { face = list.data[j]; break; }
            if (face < 0) continue;
            int other = indices[face*3];
            for (int j = 0; j < 3; j++) if (indices[face*3+j] != v && indices[face*3+j] != n) other = indices[face*3+j];
            if (deformation!=null) deformation.border(v,n,other);
            double ux = x(n)-x(v), uy = y(n)-y(v), uz = z(n)-z(v);
            double vx = x(other)-x(v), vy = y(other)-y(v), vz = z(other)-z(v);
            double dot = (ux*vx+uy*vy+uz*vz)/(ux*ux+uy*uy+uz*uz);
            double nx = vx-ux*dot, ny = vy-uy*dot, nz = vz-uz*dot, length = Math.sqrt(nx*nx+ny*ny+nz*nz);
            if (length > 0) { nx /= length; ny /= length; nz /= length;
                addPlane(v, nx, ny, nz, -(nx*x(v)+ny*y(v)+nz*z(v)), Math.sqrt(ux*ux+uy*uy+uz*uz)*10); }
        }
        if (borderEdges != 0 && borderEdges != 2) fixed[v] = true;
    }
    private double error(int from, int to) {
        double error=Math.max(0, evaluate(from, to) + evaluate(to, to)) /
                Math.max(1e-30, quadrics[from*stride+10]+quadrics[to*stride+10]);
        return deformation==null ? error : Math.max(error,deformation.error(from,to));
    }
    private double evaluate(int v, int target) {
        int q = v*stride; double x = x(target), y = y(target), z = z(target);
        double error = quadrics[q]*x*x + 2*quadrics[q+1]*x*y + 2*quadrics[q+2]*x*z + 2*quadrics[q+3]*x
                + quadrics[q+4]*y*y + 2*quadrics[q+5]*y*z + 2*quadrics[q+6]*y
                + quadrics[q+7]*z*z + 2*quadrics[q+8]*z + quadrics[q+9];
        for (int k = 0; k < attributes; k++) {
            int o = q+11+k*4; double a = attribute(target, k);
            error += a*a*quadrics[q+10]*attrWeight[k] - 2*a*(quadrics[o]*x+quadrics[o+1]*y+quadrics[o+2]*z+quadrics[o+3]);
        }
        return error;
    }
    private void candidate(int v) {
        versions[v]++; bestTarget[v] = -1;
        if (fixed[v] || incident[v] == null) return;
        int twin = seamTwin[v];
        if (twin >= 0 && (v > twin || fixed[twin] || incident[twin] == null)) return;
        int count = neighbors(v); double best = Double.POSITIVE_INFINITY; int chosen = -1;
        for (int i = 0; i < count; i++) {
            int n = neighbors[i];
            if (boundary[v] && (!boundary[n] || edgeFaces(v, n) != 1)) continue;
            double cost = error(v, n);
            if (twin >= 0) {
                int targetTwin = seamTwin[n];
                if (targetTwin < 0 || edgeFaces(v,n) != 1 || edgeFaces(twin,targetTwin) != 1) continue;
                cost = Math.max(cost, error(twin,targetTwin));
            }
            if (cost <= limit && (cost < best || cost == best && n < chosen) && validCollapse(v, n)) { chosen = n; best = cost; }
        }
        if (chosen >= 0) { bestTarget[v] = chosen; heap.add(v, chosen, versions[v], best); }
    }
    private boolean validCollapse(int a, int b) {
        if (!valid(a,b)) return false;
        int twin = seamTwin[a];
        if (twin >= 0) {
            int targetTwin = seamTwin[b];
            if(targetTwin < 0 || fixed[twin] || incident[twin] == null || incident[targetTwin] == null
                    || edgeFaces(a,b) != 1 || edgeFaces(twin,targetTwin) != 1
                    || triangles <= 2 || !valid(twin,targetTwin)) return false;
        }
        return !hasSeams || physicalLink(a,b);
    }
    /** Check the link across all attribute wedges too, so a seam cannot create nonmanifold edges. */
    private boolean physicalLink(int a,int b) {
        int from=positionRepresentative[a],to=positionRepresentative[b];
        int faces=0,neighborStamp=++stamp;
        for(int wedge=positionHead[to];wedge>=0;wedge=positionNext[wedge]) {
            LodIntList list=incident[wedge];if(list==null) continue;
            for(int i=0;i<list.size;i++) {
                int f=list.data[i];if(!alive[f] || !contains(f,wedge)) continue;
                for(int k=0;k<3;k++) {
                    int n=positionRepresentative[indices[f*3+k]];
                    if(n==from) faces++;
                    marks[n]=neighborStamp;
                }
            }
        }
        if(faces<1 || faces>2) return false;
        int common=0,visitedStamp=++stamp;
        for(int wedge=positionHead[from];wedge>=0;wedge=positionNext[wedge]) {
            LodIntList list=incident[wedge]; if(list==null) continue;
            for(int i=0;i<list.size;i++) {
                int f=list.data[i]; if(!alive[f] || !contains(f,wedge)) continue;
                for(int k=0;k<3;k++) {
                    int n=positionRepresentative[indices[f*3+k]];
                    if(n==from || n==to || marks[n]!=neighborStamp) continue;
                    marks[n]=visitedStamp;common++;
                }
            }
        }
        return common==faces;
    }
    private boolean valid(int a, int b) {
        int sharedFaces = edgeFaces(a, b);
        if (sharedFaces < 1 || sharedFaces > 2 || triangles-sharedFaces < Math.min(target, 1)) return false;
        // Link condition: common neighbors must be exactly the edge's opposite vertices.
        int common = 0; LodIntList left = incident[a];
        int localStamp = ++stamp;
        for (int i = 0; i < left.size; i++) {
            int f = left.data[i]; if (!alive[f] || !contains(f, a)) continue;
            for (int j = 0; j < 3; j++) {
                int n = indices[f*3+j]; if (n == a || n == b || marks[n] == localStamp) continue;
                marks[n] = localStamp; if (edgeFaces(b, n) > 0) common++;
            }
        }
        if (common != sharedFaces) return false;
        for (int i = 0; i < left.size; i++) {
            int f = left.data[i]; if (!alive[f] || !contains(f, a) || contains(f, b)) continue;
            if (deformation!=null && !deformation.oriented(indices[f*3],indices[f*3+1],indices[f*3+2],a,b)) return false;
            int p = -1, q = -1;
            for (int j = 0; j < 3; j++) if (indices[f*3+j] != a) { if (p < 0) p = indices[f*3+j]; else q = indices[f*3+j]; }
            LodIntList targetFaces = incident[b];
            for (int j = 0; j < targetFaces.size; j++) {
                int other = targetFaces.data[j];
                if (alive[other] && contains(other,b) && contains(other,p) && contains(other,q)) return false;
            }
            double ux=x(p)-x(a), uy=y(p)-y(a), uz=z(p)-z(a), vx=x(q)-x(a), vy=y(q)-y(a), vz=z(q)-z(a);
            double nx=uy*vz-uz*vy, ny=uz*vx-ux*vz, nz=ux*vy-uy*vx;
            ux=x(p)-x(b); uy=y(p)-y(b); uz=z(p)-z(b); vx=x(q)-x(b); vy=y(q)-y(b); vz=z(q)-z(b);
            double mx=uy*vz-uz*vy, my=uz*vx-ux*vz, mz=ux*vy-uy*vx;
            double oldArea=nx*nx+ny*ny+nz*nz, newArea=mx*mx+my*my+mz*mz;
            if (newArea < oldArea*1e-8 || nx*mx+ny*my+nz*mz <= Math.sqrt(oldArea*newArea)*.1) return false;
            for (int channel : UV_CHANNELS) {
                float[] uv = source.channel(channel); if (uv == null) continue;
                double before=uvArea(uv,a,p,q), after=uvArea(uv,b,p,q);
                if (Math.abs(before) > 1e-15 && before*after <= 0) return false;
            }
        }
        return true;
    }
    private void collapseNext() {
        int a = heap.from[0], b = heap.to[0], revision = heap.revision[0]; double cost = heap.error[0]; heap.pop();
        if (versions[a] != revision || bestTarget[a] != b || incident[a] == null || incident[b] == null) return;
        if (!validCollapse(a,b)) { candidate(a); return; }
        attained = Math.max(attained,cost);
        int twin = seamTwin[a], targetTwin = seamTwin[b];
        collapse(a,b);
        if (twin >= 0) { collapse(twin,targetTwin); seamTwin[a] = -1; seamTwin[twin] = -1; }
        int count = neighbors(b);
        // Candidate evaluation reuses neighbor scratch, so collect affected rings first.
        if (affected.length<count) affected=Arrays.copyOf(affected,Math.max(count,affected.length*2));
        System.arraycopy(neighbors,0,affected,0,count);
        int affectedCount=count,oppositeCount=0;
        if (twin >= 0) {
            oppositeCount=neighbors(targetTwin);
            if(opposite.length<oppositeCount) opposite=Arrays.copyOf(opposite,Math.max(oppositeCount,opposite.length*2));
            System.arraycopy(neighbors,0,opposite,0,oppositeCount);
        }
        refresh(b);
        if (twin >= 0) refresh(targetTwin);
        for (int i=0;i<affectedCount;i++) refresh(affected[i]);
        for (int i=0;i<oppositeCount;i++) refresh(opposite[i]);
    }
    private void refresh(int v) {
        int twin = seamTwin[v];
        candidate(twin >= 0 ? Math.min(v,twin) : v);
    }
    private void collapse(int a, int b) {
        LodIntList list = incident[a];
        for (int i = 0; i < list.size; i++) {
            int f = list.data[i]; if (!alive[f] || !contains(f,a)) continue;
            if (contains(f,b)) { alive[f] = false; triangles--; }
            else { for (int j = 0; j < 3; j++) if (indices[f*3+j] == a) indices[f*3+j] = b; incident[b].add(f); }
        }
        for (int k = 0; k < stride; k++) quadrics[b*stride+k] += quadrics[a*stride+k];
        if (deformation!=null) deformation.collapse(a,b);
        incident[a] = null; versions[a]++; bestTarget[a] = -1;
    }
    private boolean contains(int face,int v) { int o=face*3; return indices[o]==v || indices[o+1]==v || indices[o+2]==v; }
    private double x(int v) { return position[v*3]; }
    private double y(int v) { return position[v*3+1]; }
    private double z(int v) { return position[v*3+2]; }
    private double attribute(int v,int a) { int c=attrChannel[a]; return source.channel(c)[v*MeshLodData.components(c)+attrComponent[a]]; }
    private static double uvArea(float[] uv,int a,int b,int c) {
        return ((double)uv[b*2]-uv[a*2])*(uv[c*2+1]-uv[a*2+1])-((double)uv[b*2+1]-uv[a*2+1])*(uv[c*2]-uv[a*2]);
    }
}
