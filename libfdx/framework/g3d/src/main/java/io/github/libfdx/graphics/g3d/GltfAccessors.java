package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.json.JsonValue;
import java.math.BigDecimal;

/** CPU-only checked accessor decoding. Source arrays and returned decoded arrays are borrowed within one import. */
final class GltfAccessors {
    private static final int MAX_VALUES=16_777_216;
    private final JsonValue root;
    private final byte[][] buffers;
    private final int[] lengths;
    private final Accessor[] accessors;

    GltfAccessors(JsonValue root,byte[][] buffers) {
        this.root=root;this.buffers=buffers;lengths=new int[buffers.length];
        for(int i=0;i<buffers.length;i++) {
            lengths[i]=integer(element(root,"buffers",i),"byteLength",-1);
            if(lengths[i]<1||buffers[i]==null||buffers[i].length<lengths[i])throw error("buffer "+i+" is shorter than its declared byteLength");
        }
        JsonValue array=root.get("accessors");
        accessors=new Accessor[array==null?0:array.arrayValues().size()];
    }
    float[] floats(int index,int components) {
        Accessor accessor=accessor(index);
        if(accessor.components!=components)throw error("accessor "+index+" component count mismatch");
        if(accessor.componentType==5125)throw error("unsigned-int accessor cannot supply floating attributes");
        if(accessor.floats==null) {
            float[] out=new float[accessor.count*components];
            for(int i=0;i<accessor.count;i++)for(int c=0;c<components;c++) {
                out[i*components+c]=accessor.base==null?0:component(accessor.base.bytes,accessor.base.offset+i*accessor.stride+accessor.componentOffset(c),accessor);
            }
            for(int i=0;i<accessor.sparseCount;i++)for(int c=0;c<components;c++) {
                out[accessor.sparseIndices[i]*components+c]=component(accessor.sparseValues.bytes,
                        accessor.sparseValues.offset+i*accessor.elementBytes+accessor.componentOffset(c),accessor);
            }
            accessor.floats=out;
        }
        return accessor.floats;
    }
    int count(int index) { return accessor(index).count; }
    float[] skinWeights(int index) {
        Accessor accessor = accessor(index);
        if (accessor.skinWeights == null) {
            float[] weights = attribute(index, "WEIGHTS_0").clone();
            for (int i = 0; i < weights.length; i += 4) {
                double total = 0;
                for (int j = 0; j < 4; j++) {
                    if (!Float.isFinite(weights[i+j]) || weights[i+j] < 0) throw error("skin weights must be finite and nonnegative");
                    total += weights[i+j];
                }
                if (total <= 0) throw error("skinned vertices require a positive weight sum");
                for (int j = 0; j < 4; j++) weights[i+j] = (float)(weights[i+j] / total);
            }
            accessor.skinWeights = weights;
        }
        return accessor.skinWeights;
    }
    float[] inverseBindMatrices(int index, int jointCount) {
        Accessor accessor = accessor(index);
        if (!"MAT4".equals(accessor.type) || accessor.componentType != 5126 || accessor.count < jointCount)
            throw error("inverseBindMatrices requires FLOAT MAT4 data with at least one matrix per joint");
        float[] values = floats(index, 16);
        for (int i = 0; i < values.length; i += 16)
            if (values[i+3] != 0 || values[i+7] != 0 || values[i+11] != 0 || values[i+15] != 1)
                throw error("inverse bind matrices must be affine");
        return values;
    }
    float[] animationValues(int index, String type) {
        Accessor accessor = accessor(index);
        if (!type.equals(accessor.type) || accessor.componentType != 5126) {
            throw error("animation accessor " + index + " requires FLOAT " + type);
        }
        JsonValue declaration = element(root, "accessors", index);
        int view = integer(declaration, "bufferView", -1);
        if (view >= 0 && element(root, "bufferViews", view).get("byteStride") != null) {
            throw error("animation accessor " + index + " cannot use byteStride");
        }
        return floats(index, accessor.components);
    }

    float[] attribute(int index, String semantic) {
        Accessor accessor = accessor(index);
        boolean uv = semantic.startsWith("TEXCOORD_");
        boolean weights = "WEIGHTS_0".equals(semantic);
        String type = uv ? "VEC2" : weights || "TANGENT".equals(semantic) ? "VEC4" : "VEC3";
        if (!type.equals(accessor.type) || accessor.componentType != 5126
                && (!(uv || weights) || !accessor.normalized
                || accessor.componentType != 5121 && accessor.componentType != 5123))
            throw error(semantic + " requires " + type + " FLOAT" + (uv || weights ? " or normalized unsigned byte/short" : ""));
        float[] data = floats(index, accessor.components);
        if ("NORMAL".equals(semantic) || "TANGENT".equals(semantic)) {
            for (int i = 0; i < data.length; i += accessor.components) {
                double length = (double)data[i]*data[i] + (double)data[i+1]*data[i+1] + (double)data[i+2]*data[i+2];
                if (Math.abs(length - 1) > .001 || accessor.components == 4 && Math.abs(data[i+3]) != 1)
                    throw error(semantic + " must be unit length; tangent W must be +1 or -1");
            }
        }
        return data;
    }

    int[] joints(int index) {
        Accessor accessor = accessor(index);
        if (!"VEC4".equals(accessor.type) || accessor.componentType != 5121 && accessor.componentType != 5123)
            throw error("JOINTS_0 requires unsigned byte/short VEC4");
        return integers(index, 4);
    }
    float[] colors(int index) {
        Accessor accessor=accessor(index);
        if(!"VEC3".equals(accessor.type)&&!"VEC4".equals(accessor.type))throw error("COLOR_0 must be VEC3 or VEC4");
        if(accessor.componentType!=5126&&(!accessor.normalized||accessor.componentType!=5121&&accessor.componentType!=5123))
            throw error("COLOR_0 requires float or normalized unsigned byte/short values");
        return floats(index,accessor.components);
    }
    int[] integers(int index,int components) {
        Accessor accessor=accessor(index);
        if(accessor.components!=components||accessor.normalized
                ||accessor.componentType!=5121&&accessor.componentType!=5123&&accessor.componentType!=5125)
            throw error("accessor "+index+" requires unnormalized unsigned integer components");
        if(accessor.integers==null) {
            int[] out=new int[accessor.count*components];
            for(int i=0;i<accessor.count;i++)for(int c=0;c<components;c++) {
                out[i*components+c]=accessor.base==null?0:unsigned(accessor.base.bytes,
                        accessor.base.offset+i*accessor.stride+accessor.componentOffset(c),accessor.componentType);
            }
            for(int i=0;i<accessor.sparseCount;i++)for(int c=0;c<components;c++) {
                out[accessor.sparseIndices[i]*components+c]=unsigned(accessor.sparseValues.bytes,
                        accessor.sparseValues.offset+i*accessor.elementBytes+accessor.componentOffset(c),accessor.componentType);
            }
            accessor.integers=out;
        }
        return accessor.integers;
    }
    byte[] viewBytes(int index) {
        View view=view(index);byte[] out=new byte[view.length];System.arraycopy(view.bytes,view.offset,out,0,out.length);return out;
    }
    private Accessor accessor(int index) {
        if(index<0||index>=accessors.length)throw error("accessor index outside range: "+index);
        if(accessors[index]!=null)return accessors[index];
        try {
            JsonValue value=element(root,"accessors",index);Accessor accessor=new Accessor();
            accessor.type=value.require("type").stringValue();
            accessor.rows=switch(accessor.type){case "MAT2"->2;case "MAT3"->3;case "MAT4"->4;default->0;};
            accessor.components=switch(accessor.type){case "SCALAR"->1;case "VEC2"->2;case "VEC3"->3;case "VEC4","MAT2"->4;case "MAT3"->9;case "MAT4"->16;default->throw error("unsupported accessor type "+accessor.type);};
            accessor.componentType=integer(value,"componentType",-1);accessor.componentBytes=componentBytes(accessor.componentType);
            accessor.columnBytes=accessor.rows==0?0:(accessor.rows*accessor.componentBytes+3)/4*4;
            accessor.elementBytes=accessor.rows==0?accessor.components*accessor.componentBytes:accessor.rows*accessor.columnBytes;
            int finalElementBytes = accessor.componentOffset(accessor.components-1) + accessor.componentBytes;
            accessor.count=integer(value,"count",-1);
            if(accessor.count<1||(long)accessor.count*accessor.components>MAX_VALUES)throw error("accessor values exceed limit "+MAX_VALUES+" or count is not positive");
            JsonValue normalized=value.get("normalized");accessor.normalized=normalized!=null&&normalized.booleanValue();
            if(accessor.normalized&&(accessor.componentType==5125||accessor.componentType==5126))throw error("float/unsigned-int accessors cannot be normalized");
            int offset=integer(value,"byteOffset",0),base=integer(value,"bufferView",-1);
            if(base<0) {
                if(value.get("bufferView")!=null||offset!=0)throw error("bufferless accessor requires no bufferView or byteOffset");
                accessor.stride=accessor.elementBytes;
            } else {
                View source=view(base);int stride=integer(element(root,"bufferViews",base),"byteStride",accessor.elementBytes);
                if(stride<accessor.elementBytes||stride%accessor.componentBytes!=0
                        ||element(root,"bufferViews",base).get("byteStride")!=null&&(stride<4||stride>252||stride%4!=0))throw error("invalid accessor byteStride");
                accessor.base=slice(source,offset,(long)(accessor.count-1)*stride+finalElementBytes,accessor.rows==0?accessor.componentBytes:4);
                accessor.stride=stride;
            }
            JsonValue sparse=value.get("sparse");
            if(sparse!=null) {
                accessor.sparseCount=integer(sparse,"count",-1);
                if(accessor.sparseCount<1||accessor.sparseCount>accessor.count)throw error("invalid sparse count");
                JsonValue indices=sparse.require("indices"),values=sparse.require("values");
                int indexType=integer(indices,"componentType",-1);
                if(indexType!=5121&&indexType!=5123&&indexType!=5125)throw error("invalid sparse index component type");
                int indexBytes=componentBytes(indexType),indexView=integer(indices,"bufferView",-1),valueView=integer(values,"bufferView",-1);
                sparseView(indexView);sparseView(valueView);
                View source=slice(view(indexView),integer(indices,"byteOffset",0),(long)accessor.sparseCount*indexBytes,indexBytes);
                accessor.sparseValues=slice(view(valueView),integer(values,"byteOffset",0),
                        (long)(accessor.sparseCount-1)*accessor.elementBytes+finalElementBytes,accessor.rows==0?accessor.componentBytes:4);
                accessor.sparseIndices=new int[accessor.sparseCount];int previous=-1;
                for(int i=0;i<accessor.sparseCount;i++) {
                    int next=unsigned(source.bytes,source.offset+i*indexBytes,indexType);
                    if(next<=previous||next>=accessor.count)throw error("sparse indices must be strictly increasing and within accessor count");
                    accessor.sparseIndices[i]=previous=next;
                }
            }
            accessors[index]=accessor;return accessor;
        } catch(RuntimeException failure){throw new FdxException("glTF accessor "+index+": "+failure.getMessage(),failure);}
    }
    private void sparseView(int index) {
        JsonValue value=element(root,"bufferViews",index);
        if(value.get("byteStride")!=null||value.get("target")!=null)throw error("sparse bufferView cannot declare byteStride/target");
    }
    private View view(int index) {
        JsonValue value=element(root,"bufferViews",index);
        int buffer=integer(value,"buffer",-1),offset=integer(value,"byteOffset",0),length=integer(value,"byteLength",-1);
        if(buffer<0||buffer>=buffers.length||offset<0||length<1||(long)offset+length>lengths[buffer])throw error("bufferView "+index+" range is invalid");
        return new View(buffers[buffer],offset,length);
    }
    private static View slice(View view,int offset,long length,int alignment) {
        if(offset<0||length<0||(long)offset+length>view.length||offset%alignment!=0||((long)view.offset+offset)%alignment!=0)
            throw error("accessor/sparse byte range or alignment is invalid");
        return new View(view.bytes,view.offset+offset,(int)length);
    }
    private static float component(byte[] bytes,int offset,Accessor accessor) {
        float value=switch(accessor.componentType) {
            case 5126->Float.intBitsToFloat(bits(bytes,offset));
            case 5120->accessor.normalized?Math.max(bytes[offset]/127f,-1):bytes[offset];
            case 5121->accessor.normalized?(bytes[offset]&255)/255f:bytes[offset]&255;
            case 5122->accessor.normalized?Math.max((short)shortBits(bytes,offset)/32767f,-1):(short)shortBits(bytes,offset);
            case 5123->accessor.normalized?shortBits(bytes,offset)/65535f:shortBits(bytes,offset);
            default->throw error("unsupported float component type");
        };
        if(!Float.isFinite(value))throw error("accessor component must be finite");return value;
    }
    private static int unsigned(byte[] bytes,int offset,int type) {
        long value=type==5121?bytes[offset]&255:type==5123?shortBits(bytes,offset):bits(bytes,offset)&0xffffffffL;
        if(value>Integer.MAX_VALUE)throw error("unsigned accessor/index exceeds Java integer range");return (int)value;
    }
    private static int shortBits(byte[] bytes,int offset){return (bytes[offset]&255)|((bytes[offset+1]&255)<<8);}
    private static int bits(byte[] bytes,int offset){return shortBits(bytes,offset)|(shortBits(bytes,offset+2)<<16);}
    private static int componentBytes(int type){return switch(type){case 5120,5121->1;case 5122,5123->2;case 5125,5126->4;default->throw error("invalid component type "+type);};}
    static int integer(JsonValue object,String key,int fallback) {
        JsonValue value=object.get(key);if(value==null)return fallback;
        return integerValue(value, key);
    }
    static int integerValue(JsonValue value, String key) {
        try {return new BigDecimal(value.numberLiteral()).intValueExact();}
        catch(RuntimeException failure){throw new FdxException("glTF "+key+" must be an exact signed integer",failure);}
    }
    private static JsonValue element(JsonValue root,String key,int index) {
        JsonValue array=root.get(key);
        if(array==null||!array.isArray()||index<0||index>=array.arrayValues().size())throw error(key+" index outside range: "+index);
        JsonValue value=array.require(index);if(!value.isObject())throw error(key+" entry must be an object");return value;
    }
    private static FdxException error(String message){return new FdxException("glTF "+message);}
    private record View(byte[] bytes,int offset,int length) { }
    private static final class Accessor {
        String type;int components,componentType,componentBytes,rows,columnBytes,elementBytes,count,stride,sparseCount;
        boolean normalized;View base,sparseValues;int[] sparseIndices,integers;float[] floats,skinWeights;
        int componentOffset(int component){return rows==0?component*componentBytes:component/rows*columnBytes+component%rows*componentBytes;}
    }
}
