package io.github.libfdx.samples.g2d.platformer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.libfdx.samples.g2d.platformer.Trail;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

final class PlatformerGroundArtTest {
    @Test
    void cliffArtWrapsAtBothRockEdgesAndUnderTheSurface() throws Exception {
        for (Trail trail : Trail.values()) {
            try (var source = getClass().getResourceAsStream("/levels/art/" + trail.id + "-ground.png")) {
                BufferedImage atlas = ImageIO.read(source);
                int w = atlas.getWidth() - 2 * (48 + 4), half = atlas.getHeight() / 2;
                for (int y = 0; y < atlas.getHeight(); y++) {
                    for (int pad = 0; pad < 2; pad++) {
                        assertEquals(atlas.getRGB(w - 4 + pad, y), atlas.getRGB(pad, y), trail.title);
                        assertEquals(atlas.getRGB(2 + pad, y), atlas.getRGB(w - 2 + pad, y), trail.title);
                    }
                }
                for (int x = 0; x < w; x++) {
                    for (int pad = 0; pad < 2; pad++) {
                        assertEquals(atlas.getRGB(x, half - 4 + pad), atlas.getRGB(x, pad), trail.title);
                        assertEquals(atlas.getRGB(x, 2 + pad), atlas.getRGB(x, half - 2 + pad), trail.title);
                    }
                    assertEquals(0, atlas.getRGB(x, 2 * half - 3) >>> 24, trail.title);
                }
            }
        }
    }

}
