package io.github.libfdx.tools.texturepacker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** JVM-only command-line entry point; accepts one UTF-8 versioned properties request. */
public final class AtlasTool {
    private AtlasTool() { }
    public static void main(String[] args) throws IOException {
        if(args.length!=1) throw new IllegalArgumentException("Expected one libFDX tool request-file path");
        Properties p=new Properties();
        try(var reader=Files.newBufferedReader(Path.of(args[0]),StandardCharsets.UTF_8)) { p.load(reader); }
        if(!"1".equals(p.getProperty("formatVersion"))) throw new IllegalArgumentException("Unsupported request format");
        AtlasSpec spec=new AtlasSpec(Path.of(required(p,"sourceDirectory")),Path.of(required(p,"outputDirectory")),
                required(p,"name"),required(p,"assetPath"),integer(p,"maxPageSize"),integer(p,"padding"),integer(p,"extrusion"),
                bool(p,"trim"),integer(p,"trimMargin"),bool(p,"bleed"),Float.parseFloat(required(p,"pivotX")),Float.parseFloat(required(p,"pivotY")),integer(p,"maxPages"));
        System.out.println("Generated libfdx sprite atlas "+TexturePacker.generate(spec));
    }
    private static int integer(Properties p,String name) { return Integer.parseInt(required(p,name)); }
    private static boolean bool(Properties p,String name) {
        String value=required(p,name);
        if(!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid boolean: "+name);
        return Boolean.parseBoolean(value);
    }
    private static String required(Properties p,String name) {
        String value=p.getProperty(name);
        if(value==null) throw new IllegalArgumentException("Missing tool request property: "+name);
        return value;
    }
}
