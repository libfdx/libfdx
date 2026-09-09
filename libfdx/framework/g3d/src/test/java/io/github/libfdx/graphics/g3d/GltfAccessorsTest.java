package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.json.*;
import org.junit.jupiter.api.Test;
import java.nio.*;
import static org.junit.jupiter.api.Assertions.*;

final class GltfAccessorsTest {
    @Test void finalMatrixColumnMayOmitTrailingPaddingInDenseAndSparseViews() {
        byte[] bytes = {1,2,3,0, 4,5,6,0, 7,8,9,0, 9,8,7,0, 6,5,4,0, 3,2,1,0, 0,1};
        var accessor = JsonValue.object().put("bufferView",0).put("count",2).put("componentType",5121).put("type","MAT3");
        var root = root(bytes.length,accessor,view(0,23),view(24,2));
        float[] expected = {1,2,3,4,5,6,7,8,9, 9,8,7,6,5,4,3,2,1};
        assertArrayEquals(expected,new GltfAccessors(root,new byte[][]{bytes}).floats(0,9));
        root.put("accessors", JsonValue.array().add(JsonValue.object().put("count",2).put("componentType",5121).put("type","MAT3")
                .put("sparse",JsonValue.object().put("count",2)
                        .put("indices",JsonValue.object().put("bufferView",1).put("componentType",5121))
                        .put("values",JsonValue.object().put("bufferView",0)))));
        assertArrayEquals(expected,new GltfAccessors(root,new byte[][]{bytes}).floats(0,9));
        root.require("bufferViews").require(0).put("byteLength",22);
        assertThrows(FdxException.class,()->new GltfAccessors(root,new byte[][]{bytes}).floats(0,9));
    }
    @Test void sparseReplacementsSupportZeroAndStridedBasesWithoutChangingSourceBytes() {
        byte[] bytes=new byte[64];ByteBuffer buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(0,1).putFloat(4,2).putFloat(16,3).putFloat(20,4).putFloat(32,5).putFloat(36,6);
        bytes[48]=1;buffer.putFloat(52,9).putFloat(56,10);
        JsonValue sparse=JsonValue.object().put("count",1).put("indices",JsonValue.object().put("bufferView",1).put("componentType",5121))
                .put("values",JsonValue.object().put("bufferView",2));
        JsonValue accessor=JsonValue.object().put("count",3).put("componentType",5126).put("type","VEC2").put("sparse",sparse);
        JsonValue root=root(bytes.length,accessor,view(0,40).put("byteStride",16),view(48,1),view(52,8));
        var reader=new GltfAccessors(root,new byte[][]{bytes});
        assertArrayEquals(new float[]{0,0,9,10,0,0},reader.floats(0,2));assertSame(reader.floats(0,2),reader.floats(0,2));
        accessor.put("bufferView",0);reader=new GltfAccessors(root,new byte[][]{bytes});
        assertArrayEquals(new float[]{1,2,9,10,5,6},reader.floats(0,2));assertEquals(3,buffer.getFloat(16));
    }
    @Test void normalizedValuesAndPaddedMatrixColumnsAreDecodedPrecisely() {
        byte[] data={0,127,(byte)128,(byte)255,1,2,3,99,4,5,6,99,7,8,9,99};
        JsonValue normalized=JsonValue.object().put("bufferView",0).put("count",1).put("componentType",5120).put("type","VEC4").put("normalized",true);
        JsonValue matrix=JsonValue.object().put("bufferView",1).put("count",1).put("componentType",5121).put("type","MAT3");
        JsonValue root=root(data.length,normalized,view(0,4),view(4,12));root.require("accessors").add(matrix);
        var reader=new GltfAccessors(root,new byte[][]{data});
        assertArrayEquals(new float[]{0,1,-1,-1/127f},reader.floats(0,4));
        assertArrayEquals(new float[]{1,2,3,4,5,6,7,8,9},reader.floats(1,9));
        matrix.put("byteOffset",1);assertThrows(FdxException.class,()->new GltfAccessors(root,new byte[][]{data}).floats(1,9));
    }
    @Test void rangesOverflowStrideCountsNormalizationAndSparseOrderFailBeforeDecoding() {
        byte[] bytes=new byte[32];
        JsonValue accessor=JsonValue.object().put("bufferView",0).put("count",2).put("componentType",5126).put("type","VEC3");
        JsonValue root=root(32,accessor,view(0,24));
        accessor.put("count",3);assertFailure(root,bytes,"range");
        accessor.put("count",2).put("byteOffset",Integer.MAX_VALUE);assertFailure(root,bytes,"range");
        accessor.put("byteOffset",1);assertFailure(root,bytes,"alignment");
        accessor.put("byteOffset",0).put("normalized",true);assertFailure(root,bytes,"normalized");
        accessor.put("normalized",false).put("count",Integer.MAX_VALUE);assertFailure(root,bytes,"limit");
        accessor.put("count",2.5);assertFailure(root,bytes,"integer");
        accessor.put("count",2);root.require("bufferViews").require(0).put("byteStride",8);assertFailure(root,bytes,"Stride");
        root.require("bufferViews").require(0).put("byteStride",12);
        root.require("buffers").require(0).put("byteLength",8);assertFailure(root,bytes,"range");
        root.require("buffers").require(0).put("byteLength",33);JsonValue shortBufferRoot=root;
        assertThrows(FdxException.class,()->new GltfAccessors(shortBufferRoot,new byte[][]{bytes}));

        JsonValue sparse=JsonValue.object().put("count",2).put("indices",JsonValue.object().put("bufferView",0).put("componentType",5121))
                .put("values",JsonValue.object().put("bufferView",1));
        accessor=JsonValue.object().put("count",3).put("componentType",5126).put("type","VEC3").put("sparse",sparse);
        root=root(32,accessor,view(0,2),view(4,24));
        bytes[0]=1;bytes[1]=1;assertFailure(root,bytes,"increasing");
        bytes[0]=2;bytes[1]=1;assertFailure(root,bytes,"increasing");
        bytes[0]=0;bytes[1]=3;assertFailure(root,bytes,"within");
        bytes[1]=2;sparse.put("count",4);assertFailure(root,bytes,"sparse count");
        sparse.put("count",2);root.require("bufferViews").require(0).put("byteStride",4);assertFailure(root,bytes,"byteStride");
    }
    @Test void unsignedIndicesAreCheckedAndNonfiniteFloatDataIsRejected() {
        byte[] bytes={0,0,0,(byte)128};
        JsonValue accessor=JsonValue.object().put("bufferView",0).put("count",1).put("componentType",5125).put("type","SCALAR");
        JsonValue root=root(4,accessor,view(0,4));
        assertThrows(FdxException.class,()->new GltfAccessors(root,new byte[][]{bytes}).integers(0,1));
        bytes[3]=0x7f;bytes[2]=(byte)0xc0;accessor.put("componentType",5126);
        assertThrows(FdxException.class,()->new GltfAccessors(root,new byte[][]{bytes}).floats(0,1));
    }
    private static JsonValue root(int length,JsonValue accessor,JsonValue... views) {
        JsonValue array=JsonValue.array();for(JsonValue view:views)array.add(view);
        return JsonValue.object().put("buffers",JsonValue.array().add(JsonValue.object().put("byteLength",length)))
                .put("bufferViews",array).put("accessors",JsonValue.array().add(accessor));
    }
    private static JsonValue view(int offset,int length){return JsonValue.object().put("buffer",0).put("byteOffset",offset).put("byteLength",length);}
    private static void assertFailure(JsonValue root,byte[] bytes,String text) {
        var failure=assertThrows(FdxException.class,()->new GltfAccessors(root,new byte[][]{bytes}).floats(0,3));
        assertTrue(failure.getMessage().contains(text),failure.getMessage());
    }
}
