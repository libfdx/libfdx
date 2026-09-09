package io.github.libfdx.tools.texturepacker;

import io.github.libfdx.assets.loaders.AtlasData;
import java.nio.file.Path;

/**
 * Options for an explicit atlas preparation call. Output must not overlap source.
 * Use an empty asset path to write directly into the selected output directory.
 * Calls writing the same atlas must be serialized by the caller.
 */
public record AtlasSpec(Path sourceDirectory,Path outputDirectory,String name,String assetPath,
        int maxPageSize,int padding,int extrusion,boolean trim,int trimMargin,boolean bleed,float pivotX,float pivotY,int maxPages) {
    public AtlasSpec {
        if(sourceDirectory==null || outputDirectory==null) throw new IllegalArgumentException("Source and output required");
        sourceDirectory=sourceDirectory.toAbsolutePath().normalize();
        outputDirectory=outputDirectory.toAbsolutePath().normalize();
        if(outputDirectory.startsWith(sourceDirectory) || sourceDirectory.startsWith(outputDirectory)) {
            throw new IllegalArgumentException("Source and output directories must not overlap");
        }
        if(name==null || !name.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")) throw new IllegalArgumentException("Invalid atlas name");
        if(assetPath==null) throw new IllegalArgumentException("Asset path required (may be empty)");
        new AtlasData.Page(assetPath.isEmpty() ? name+".png" : assetPath+"/"+name+".png",1,1);
        if(maxPageSize<8 || maxPageSize>4096 || (maxPageSize&(maxPageSize-1))!=0 || padding<0 || padding>64
                || extrusion<0 || extrusion>64 || trimMargin<0 || trimMargin>64 || 2*(padding+extrusion)>=maxPageSize || maxPages<1 || maxPages>256
                || !Float.isFinite(pivotX) || !Float.isFinite(pivotY) || pivotX<0 || pivotX>1 || pivotY<0 || pivotY>1) {
            throw new IllegalArgumentException("Invalid page size, border, pivot, or page limit");
        }
    }
    public static AtlasSpec defaults(Path source,Path output,String name) {
        return new AtlasSpec(source,output,name,"sprites",1024,2,1,true,1,true,.5f,.5f,64);
    }
}
