package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.json.JsonValue;

/** Import-level checks that must precede external dependencies and GPU allocation. */
final class GltfValidation {
    private static final int MAX_NODES = 65_536, MAX_DEPTH = 256;
    private GltfValidation() { }
    static int[] document(JsonValue root) {
        JsonValue asset=root.get("asset");
        if(asset==null||!asset.isObject()||!"2.0".equals(asset.require("version").stringValue()))
            throw new FdxException("glTF asset.version must be 2.0");
        if(asset.get("minVersion")!=null&&!"2.0".equals(asset.get("minVersion").stringValue()))
            throw new FdxException("Unsupported glTF asset.minVersion");
        JsonValue used=extensions(root,"extensionsUsed"),required=extensions(root,"extensionsRequired");
        if(required!=null)for(int i=0;i<required.arrayValues().size();i++) {
            String name=required.require(i).stringValue();boolean listed=false;
            if(used!=null)for(int j=0;j<used.arrayValues().size();j++)if(name.equals(used.require(j).stringValue()))listed=true;
            if(!listed)throw new FdxException("glTF required extension is absent from extensionsUsed: "+name);
            if(!"KHR_materials_unlit".equals(name) && !"KHR_texture_transform".equals(name))
                throw new FdxException("Unsupported required glTF extension: "+name);
        }
        return hierarchy(root);
    }

    /** Validates a bounded forest before recursion or external dependency discovery. */
    private static int[] hierarchy(JsonValue root) {
        JsonValue nodes = array(root, "nodes"), scenes = array(root, "scenes"), skins = array(root, "skins");
        int count = size(nodes), meshCount = size(array(root, "meshes"));
        if (count > MAX_NODES) throw error("node count exceeds " + MAX_NODES);
        int[] parents = new int[count], depths = new int[count], roots = new int[count], path = new int[MAX_DEPTH];
        java.util.Arrays.fill(parents, -1);
        for (int i = 0; i < count; i++) {
            JsonValue node = object(nodes.require(i), "node " + i);
            transform(node, "node " + i);
            if (node.get("mesh") != null) index(node.get("mesh"), meshCount, "node.mesh");
            if (node.get("skin") != null) {
                index(node.get("skin"), size(skins), "node.skin");
                if (node.get("mesh") == null) throw error("node.skin requires a mesh");
            }
            JsonValue children = array(node, "children");
            for (int j = 0; j < size(children); j++) {
                int child = index(children.require(j), count, "node " + i + " child");
                if (parents[child] >= 0) throw error("node " + child + " has duplicate or multiple parents");
                parents[child] = i;
            }
        }
        // Walk parent links with reusable marks. No recursive traversal of untrusted input.
        byte[] states = new byte[count];
        for (int i = 0; i < count; i++) if (states[i] == 0) {
            int current = i, length = 0;
            while (current >= 0 && states[current] == 0) {
                if (length == MAX_DEPTH) throw error("node hierarchy depth exceeds " + MAX_DEPTH);
                states[current] = 1; path[length++] = current; current = parents[current];
            }
            if (current >= 0 && states[current] == 1) throw error("node hierarchy contains a cycle at " + current);
            int depth = current >= 0 ? depths[current] : 0;
            int forestRoot = current >= 0 ? roots[current] : path[length-1];
            while (length > 0) {
                int node = path[--length];
                if (++depth > MAX_DEPTH) throw error("node hierarchy depth exceeds " + MAX_DEPTH);
                states[node] = 2; depths[node] = depth; roots[node] = forestRoot;
            }
        }
        int selected = root.get("scene") == null ? 0 : index(root.get("scene"), size(scenes), "scene");
        boolean[] selectedRoots = new boolean[count];
        int[] seen = new int[count];
        for (int i = 0; i < size(scenes); i++) {
            JsonValue sceneRoots = array(object(scenes.require(i), "scene " + i), "nodes");
            for (int j = 0; j < size(sceneRoots); j++) {
                int node = index(sceneRoots.require(j), count, "scene root");
                if (parents[node] >= 0 || seen[node] == i+1) throw error("scene contains a child or duplicate root node " + node);
                seen[node] = i+1;
                if (i == selected) selectedRoots[node] = true;
            }
        }
        if (size(scenes) == 0) for (int i = 0; i < count; i++) selectedRoots[i] = parents[i] < 0;
        java.util.Arrays.fill(seen, 0);
        int[] skinRoots = new int[size(skins)];
        for (int i = 0; i < size(skins); i++) {
            JsonValue skin = object(skins.require(i), "skin " + i), joints = array(skin, "joints");
            if (size(joints) == 0 || size(joints) > count) throw error("skin requires distinct joints");
            int commonRoot = -1;
            int skeleton = skin.get("skeleton") == null ? -1 : index(skin.get("skeleton"), count, "skin.skeleton");
            for (int j = 0; j < size(joints); j++) {
                int node = index(joints.require(j), count, "skin joint");
                if (seen[node] == i+1) throw error("skin contains a duplicate joint");
                seen[node] = i+1;
                if (j == 0) commonRoot = roots[node];
                if (commonRoot != roots[node]) throw error("skin joints have no common root");
                if (skeleton >= 0) {
                    int ancestor = node;
                    while (ancestor >= 0 && ancestor != skeleton) ancestor = parents[ancestor];
                    if (ancestor < 0) throw error("skin.skeleton is not an ancestor of every joint");
                }
            }
            skinRoots[i] = commonRoot;
            if (skin.get("inverseBindMatrices") != null)
                index(skin.get("inverseBindMatrices"), size(array(root, "accessors")), "inverseBindMatrices");
        }
        for (int i = 0; i < count; i++) if (selectedRoots[roots[i]] && nodes.require(i).get("skin") != null) {
            int skin = index(nodes.require(i).get("skin"), skinRoots.length, "node.skin");
            if (!selectedRoots[skinRoots[skin]]) throw error("skin joints are outside the selected scene");
        }
        return parents;
    }

    private static void transform(JsonValue node, String name) {
        JsonValue matrix = node.get("matrix");
        if (matrix != null) {
            if (node.get("translation") != null || node.get("rotation") != null || node.get("scale") != null)
                throw error(name + " mixes matrix and TRS");
            vector(matrix, 16, name + " matrix");
            if (number(matrix.require(3)) != 0 || number(matrix.require(7)) != 0
                    || number(matrix.require(11)) != 0 || number(matrix.require(15)) != 1)
                throw error(name + " matrix must be affine");
            for (int a = 0; a < 3; a++) for (int b = a+1; b < 3; b++) {
                double dot = 0, lengthA = 0, lengthB = 0;
                for (int row = 0; row < 3; row++) {
                    double x = number(matrix.require(a*4+row)), y = number(matrix.require(b*4+row));
                    dot += x*y; lengthA += x*x; lengthB += y*y;
                }
                if (Math.abs(dot) > 1e-4 * Math.sqrt(lengthA*lengthB)) throw error(name + " matrix contains shear");
            }
        }
        if (node.get("translation") != null) vector(node.get("translation"), 3, name + " translation");
        if (node.get("scale") != null) vector(node.get("scale"), 3, name + " scale");
        JsonValue rotation = node.get("rotation");
        if (rotation != null) {
            vector(rotation, 4, name + " rotation");
            double norm = 0;
            for (int i = 0; i < 4; i++) { double value = number(rotation.require(i)); norm += value*value; }
            if (Math.abs(norm-1) > .001) throw error(name + " rotation must be a unit quaternion");
        }
    }
    private static void vector(JsonValue value, int count, String name) {
        if (!value.isArray() || value.arrayValues().size() != count) throw error(name + " requires " + count + " numbers");
        for (int i = 0; i < count; i++) number(value.require(i));
    }
    private static double number(JsonValue value) {
        double result;
        try { result = Double.parseDouble(value.numberLiteral()); }
        catch (RuntimeException ex) { throw new FdxException("glTF transform must contain numbers", ex); }
        if (!Float.isFinite((float)result)) throw error("transform components must be finite floats");
        return result;
    }
    private static int index(JsonValue value, int count, String name) {
        int index = GltfAccessors.integerValue(value, name);
        if (index < 0 || index >= count) throw error(name + " index outside range: " + index);
        return index;
    }
    private static JsonValue array(JsonValue object, String key) {
        JsonValue value = object.get(key);
        if (value != null && !value.isArray()) throw error(key + " must be an array");
        return value;
    }
    private static int size(JsonValue array) { return array == null ? 0 : array.arrayValues().size(); }
    private static JsonValue object(JsonValue value, String name) {
        if (!value.isObject()) throw error(name + " must be an object");
        return value;
    }
    private static FdxException error(String message) { return new FdxException("glTF " + message); }
    private static JsonValue extensions(JsonValue root,String field) {
        JsonValue value=root.get(field);if(value==null)return null;
        if(!value.isArray())throw new FdxException("glTF "+field+" must be an array");
        for(int i=0;i<value.arrayValues().size();i++) {
            String name=value.require(i).stringValue();
            if(name.isEmpty())throw new FdxException("glTF extension name cannot be empty");
            for(int j=0;j<i;j++)if(name.equals(value.require(j).stringValue()))throw new FdxException("Duplicate glTF extension: "+name);
        }
        return value;
    }
}
