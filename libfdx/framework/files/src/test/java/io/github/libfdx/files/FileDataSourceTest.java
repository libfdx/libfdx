package io.github.libfdx.files;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class FileDataSourceTest {
    @TempDir Path temporary;

    @Test void largeFileReadsOnlyRequestedRangesAndSeeksBackwards() throws Exception {
        long size=8L*1024*1024;
        try (RandomAccessFile file=new RandomAccessFile(temporary.resolve("large.bin").toFile(),"rw")) {
            file.setLength(size); file.seek(size-4); file.write(new byte[] {11,22,33,44});
        }
        DefaultFileSystem files=new DefaultFileSystem(temporary.toFile(),temporary.toFile(),temporary.toFile());
        FileDataSource source=files.local("large.bin").openRead(8).get();
        byte[] bytes=new byte[10];
        assertEquals(size,source.length()); assertTrue(source.isSeekable());
        assertEquals(4,source.read(size-4,bytes,2,8).get());
        assertArrayEquals(new byte[] {0,0,11,22,33,44,0,0,0,0},bytes);
        assertEquals(1,source.read(0,bytes,0,1).get()); assertEquals(0,bytes[0]);
        assertEquals(-1,source.read(size,bytes,0,1).get());
        assertThrows(FdxException.class,() -> source.read(0,bytes,0,9).get());
        assertThrows(FdxException.class,() -> source.read(Long.MAX_VALUE,bytes,0,1).get());
        source.dispose(); source.dispose(); assertTrue(source.isDisposed());
        assertThrows(FdxException.class,() -> source.read(0,bytes,0,1).get());
    }

    @Test void packagedStreamsAreSequentialAndCloseTheirUnderlyingInput() {
        boolean[] closed={false};
        DefaultFileSystem files=new DefaultFileSystem(temporary.toFile(),temporary.toFile(),temporary.toFile())
                .classpathResourceResolver(path -> new ByteArrayInputStream(new byte[] {1,2,3}) {
                    @Override public void close() { closed[0]=true; }
                });
        FileDataSource source=files.classpath("packed.bin").openRead(2).get();
        assertFalse(source.isSeekable()); assertEquals(-1,source.length());
        byte[] buffer=new byte[2];
        assertEquals(2,source.read(0,buffer,0,2).get());
        assertThrows(FdxException.class,()->source.read(0,buffer,0,1).get());
        assertEquals(1,source.read(2,buffer,0,2).get()); assertEquals(3,buffer[0]);
        assertEquals(-1,source.read(3,buffer,0,2).get());
        assertEquals(0,source.read(3,buffer,0,0).get());
        source.dispose(); assertTrue(closed[0]);
    }
}
