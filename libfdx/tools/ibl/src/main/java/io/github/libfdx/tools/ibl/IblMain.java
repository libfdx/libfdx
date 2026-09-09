package io.github.libfdx.tools.ibl;

import io.github.libfdx.graphics.g3d.ImageBasedLightingData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Offline HDR-to-FDXI command-line entry point. */
public final class IblMain {
    private IblMain() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=2 && args.length!=6) {
            throw new IllegalArgumentException("Usage: IblMain input.hdr output.fdxibl [specularWidth diffuseWidth brdfSize samples]");
        }
        Path input=Path.of(args[0]).toRealPath(),output=Path.of(args[1]).toAbsolutePath().normalize();
        if(input.equals(output) || (Files.exists(output) && Files.isSameFile(input,output))) {
            throw new IllegalArgumentException("Output cannot replace the HDR source");
        }
        if(Files.size(input)>128*1024*1024) throw new IllegalArgumentException("HDR source exceeds 128 MiB");
        byte[] source=Files.readAllBytes(input);
        RadianceImage image=RadianceImage.decode(source);
        int spec=args.length==6?Integer.parseInt(args[2]):256;
        int diffuse=args.length==6?Integer.parseInt(args[3]):32;
        int lut=args.length==6?Integer.parseInt(args[4]):64;
        int samples=args.length==6?Integer.parseInt(args[5]):256;
        ImageBasedLightingData data=IblPreparer.prepare(image.rgb,image.width,image.height,spec,diffuse,lut,samples);
        byte[] encoded=data.encode();
        Files.createDirectories(output.getParent());
        Path temporary=Files.createTempFile(output.getParent(),"ibl-", ".tmp");
        try {
            Files.write(temporary,encoded);
            Files.move(temporary,output,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        System.out.println("Prepared " + output + ": " + encoded.length + " bytes; samples=" + samples
                + "; source SHA-256=" + hash(source) + "; output SHA-256=" + hash(encoded));
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
