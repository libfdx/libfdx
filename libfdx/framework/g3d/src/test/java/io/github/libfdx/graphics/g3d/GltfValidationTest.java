package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GltfValidationTest {
    private static JsonValue document(String fields) {
        return new JsonReader().parse("{\"asset\":{\"version\":\"2.0\"}," + fields + "}");
    }

    @Test
    void rejectsInvalidForestsAndSceneReferencesWithoutRecursion() {
        String[] invalid = {
                "\"nodes\":[{\"children\":[0]}]",
                "\"nodes\":[{\"children\":[1]},{\"children\":[0]}]",
                "\"nodes\":[{\"children\":[2]},{\"children\":[2]},{}]",
                "\"nodes\":[{\"children\":[1,1]},{}]",
                "\"nodes\":[{\"children\":[2]},{}]",
                "\"nodes\":[{\"children\":[1.5]},{}]",
                "\"nodes\":[{\"children\":[\"1\"]},{}]",
                "\"nodes\":[{\"children\":[-1]},{}]",
                "\"nodes\":[{\"children\":null}]",
                "\"nodes\":[{\"mesh\":0}]",
                "\"nodes\":[{\"children\":[1]},{}],\"scenes\":[{\"nodes\":[1]}]",
                "\"nodes\":[{}],\"scenes\":[{\"nodes\":[0,0]}]",
                "\"nodes\":[{}],\"scenes\":[{\"nodes\":[1]}]",
                "\"nodes\":[{}],\"scenes\":[{\"nodes\":[0]}],\"scene\":-1",
                "\"nodes\":[{}],\"scene\":0"
        };
        for (String fields : invalid) assertThrows(FdxException.class, () -> GltfValidation.document(document(fields)), fields);
        assertArrayEquals(new int[] {-1,0,0,-1}, GltfValidation.document(document(
                "\"nodes\":[{\"children\":[1,2]},{},{},{}],\"scenes\":[{\"nodes\":[0,3]},{\"nodes\":[3]}]")));
        JsonValue deep = document("\"nodes\":[]");
        for (int i = 0; i < 256; i++) deep.require("nodes").add(i == 255 ? JsonValue.object()
                : JsonValue.object().put("children", JsonValue.array().add(i+1)));
        assertEquals(256, GltfValidation.document(deep).length);
        deep.require("nodes").require(255).put("children", JsonValue.array().add(256));
        deep.require("nodes").add(JsonValue.object());
        assertThrows(FdxException.class, () -> GltfValidation.document(deep));
    }

    @Test
    void validatesSkinCommonRootSkeletonAndSelectedSceneMembership() {
        String[] invalid = {
                "\"nodes\":[{}],\"skins\":[{\"joints\":[]}]",
                "\"nodes\":[{},{}],\"skins\":[{\"joints\":[0,0]}]",
                "\"nodes\":[{},{}],\"skins\":[{\"joints\":[0,1]}]",
                "\"nodes\":[{}],\"skins\":[{\"joints\":[1]}]",
                "\"nodes\":[{\"children\":[1]},{}],\"skins\":[{\"joints\":[0],\"skeleton\":1}]",
                "\"nodes\":[{\"skin\":0}],\"skins\":[{\"joints\":[0]}]",
                "\"meshes\":[{}],\"nodes\":[{\"mesh\":0,\"skin\":0},{}],\"skins\":[{\"joints\":[1]}],\"scenes\":[{\"nodes\":[0]}]"
        };
        for (String fields : invalid) assertThrows(FdxException.class, () -> GltfValidation.document(document(fields)), fields);
        assertDoesNotThrow(() -> GltfValidation.document(document(
                "\"meshes\":[{}],\"nodes\":[{\"mesh\":0,\"skin\":0},{\"children\":[2]},{}],"
                + "\"skins\":[{\"joints\":[2],\"skeleton\":1}],\"scenes\":[{\"nodes\":[0,1]}]")));
    }

    @Test
    void validatesTransformShapeNumbersAndTrsDecomposability() {
        String[] invalid = {
                "\"translation\":[1,2]", "\"translation\":[1,2,1e80]", "\"scale\":[1,1,\"1\"]",
                "\"rotation\":[0,0,0,0]", "\"matrix\":[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,2]",
                "\"matrix\":[1,0,0,0,1,1,0,0,0,0,1,0,0,0,0,1]",
                "\"matrix\":[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1],\"scale\":[1,1,1]"
        };
        for (String fields : invalid) assertThrows(FdxException.class,
                () -> GltfValidation.document(document("\"nodes\":[{" + fields + "}]")), fields);
        assertDoesNotThrow(() -> GltfValidation.document(document("\"nodes\":[{\"scale\":[0,-2,3]}]")));
        assertDoesNotThrow(() -> GltfValidation.document(document(
                "\"nodes\":[{\"matrix\":[-1,0,0,0,0,0,0,0,0,0,3,0,4,5,6,1]}]")));
    }
}
