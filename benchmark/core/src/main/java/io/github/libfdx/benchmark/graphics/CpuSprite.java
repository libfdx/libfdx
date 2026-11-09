/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

// Adapted from libGDX Sprite, MathUtils and SpriteBatch for the libFDX CPU benchmark.
package io.github.libfdx.benchmark.graphics;

import io.github.libfdx.graphics.Texture;

/** Per-object, dirty-vertex workload for the libFDX CPU sprite benchmark. */
final class CpuSprite {
    final float[] vertices = new float[20];
    private final Texture texture;
    private float x, y, rotation;
    private float scaleX = 1, scaleY = 1;
    private boolean dirty = true;

    CpuSprite(Texture texture, float rotation) {
        this.texture = texture;
        this.rotation = rotation;
        initializeVertices(vertices, 0);
    }

    static void initializeVertices(float[] vertices, int offset) {
        float white = Float.intBitsToFloat(0xfeffffff);
        vertices[offset + 2] = vertices[offset + 7] = vertices[offset + 12] = vertices[offset + 17] = white;
        vertices[offset + 3] = 0; vertices[offset + 4] = 1;
        vertices[offset + 8] = 0; vertices[offset + 9] = 0;
        vertices[offset + 13] = 1; vertices[offset + 14] = 0;
        vertices[offset + 18] = 1; vertices[offset + 19] = 1;
    }

    void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        dirty = true;
    }

    void rotate(float degrees) {
        if (degrees == 0) return;
        rotation += degrees;
        dirty = true;
    }

    void setScale(float scale) {
        scaleX = scale;
        scaleY = scale;
        dirty = true;
    }

    void draw(CpuSpriteBatch batch) {
        batch.draw(texture, getVertices(), 0, 20);
    }

    Texture texture() { return texture; }

    float[] getVertices() {
        if (dirty) {
            dirty = false;
            writePositions(vertices, 0, x, y, rotation, scaleX, scaleY);
        }
        return vertices;
    }

    static void writePositions(float[] vertices, int offset, float x, float y, float rotation,
            float scaleX, float scaleY) {
        float localX = -16f;
        float localY = -16f;
        float localX2 = 16f;
        float localY2 = 16f;
        float worldOriginX = x + 16f;
        float worldOriginY = y + 16f;
        if (scaleX != 1f || scaleY != 1f) {
            localX *= scaleX;
            localY *= scaleY;
            localX2 *= scaleX;
            localY2 *= scaleY;
        }
        if (rotation != 0) {
            float cos = CpuSpriteMath.cosDeg(rotation);
            float sin = CpuSpriteMath.sinDeg(rotation);
            float localXCos = localX * cos;
            float localXSin = localX * sin;
            float localYCos = localY * cos;
            float localYSin = localY * sin;
            float localX2Cos = localX2 * cos;
            float localX2Sin = localX2 * sin;
            float localY2Cos = localY2 * cos;
            float localY2Sin = localY2 * sin;

            float x1 = localXCos - localYSin + worldOriginX;
            float y1 = localYCos + localXSin + worldOriginY;
            vertices[offset] = x1;
            vertices[offset + 1] = y1;
            float x2 = localXCos - localY2Sin + worldOriginX;
            float y2 = localY2Cos + localXSin + worldOriginY;
            vertices[offset + 5] = x2;
            vertices[offset + 6] = y2;
            float x3 = localX2Cos - localY2Sin + worldOriginX;
            float y3 = localY2Cos + localX2Sin + worldOriginY;
            vertices[offset + 10] = x3;
            vertices[offset + 11] = y3;
            vertices[offset + 15] = x1 + (x3 - x2);
            vertices[offset + 16] = y3 - (y2 - y1);
        } else {
            float x1 = localX + worldOriginX;
            float y1 = localY + worldOriginY;
            float x2 = localX2 + worldOriginX;
            float y2 = localY2 + worldOriginY;
            vertices[offset] = x1;
            vertices[offset + 1] = y1;
            vertices[offset + 5] = x1;
            vertices[offset + 6] = y2;
            vertices[offset + 10] = x2;
            vertices[offset + 11] = y2;
            vertices[offset + 15] = x2;
            vertices[offset + 16] = y1;
        }
    }
}
