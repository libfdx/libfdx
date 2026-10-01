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

final class CpuSpriteMath {
    private static final int COUNT = 1 << 14;
    private static final int MASK = COUNT - 1;
    private static final float DEG_TO_INDEX = COUNT / 360f;

    private static final class Sin {
        static final float[] table = new float[COUNT];
        static {
            float full = (float)Math.PI * 2;
            for (int i = 0; i < COUNT; i++) table[i] = (float)Math.sin((i + 0.5f) / COUNT * full);
            table[0] = 0;
            table[(int)(90 * DEG_TO_INDEX) & MASK] = 1;
            table[(int)(180 * DEG_TO_INDEX) & MASK] = 0;
            table[(int)(270 * DEG_TO_INDEX) & MASK] = -1;
        }
    }

    static float sinDeg(float degrees) { return Sin.table[(int)(degrees * DEG_TO_INDEX) & MASK]; }
    static float cosDeg(float degrees) { return Sin.table[(int)((degrees + 90) * DEG_TO_INDEX) & MASK]; }
}
