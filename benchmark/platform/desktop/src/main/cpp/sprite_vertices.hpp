#pragma once

namespace sprite_vertices {
constexpr int sineCount = 1 << 14;
constexpr float degreesToIndex = static_cast<float>(sineCount) / 360.0f;

inline void writePositions(float* vertices, float worldX, float worldY, float rotation,
        float localX, float localY, float farX, float farY, const float* sine) {
    if (rotation != 0.0f) {
        const float c = sine[static_cast<int>((rotation + 90.0f) * degreesToIndex) & (sineCount - 1)];
        const float s = sine[static_cast<int>(rotation * degreesToIndex) & (sineCount - 1)];
        const float x1 = localX * c - localY * s + worldX;
        const float y1 = localY * c + localX * s + worldY;
        const float x2 = localX * c - farY * s + worldX;
        const float y2 = farY * c + localX * s + worldY;
        const float x3 = farX * c - farY * s + worldX;
        const float y3 = farY * c + farX * s + worldY;
        vertices[0] = x1;
        vertices[1] = y1;
        vertices[5] = x2;
        vertices[6] = y2;
        vertices[10] = x3;
        vertices[11] = y3;
        vertices[15] = x1 + (x3 - x2);
        vertices[16] = y3 - (y2 - y1);
    } else {
        const float x1 = localX + worldX;
        const float y1 = localY + worldY;
        const float x2 = farX + worldX;
        const float y2 = farY + worldY;
        vertices[0] = x1;
        vertices[1] = y1;
        vertices[5] = x1;
        vertices[6] = y2;
        vertices[10] = x2;
        vertices[11] = y2;
        vertices[15] = x2;
        vertices[16] = y1;
    }
}
}
