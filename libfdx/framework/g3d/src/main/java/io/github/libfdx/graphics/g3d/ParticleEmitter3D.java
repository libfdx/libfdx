package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.Texture;

/**
 * Updates and renders a fixed-capacity 3D particle emitter.
 *
 * @author xpenatan
 */
public final class ParticleEmitter3D {
    private static final float DIRECTION_EPSILON = 0.000001f;
    private static final float FULL_SPHERE_SPREAD_DEGREES = 360.0f;
    private static final float PI2 = (float)(Math.PI * 2.0);

    private final int maxParticles;
    private final float[] x;
    private final float[] y;
    private final float[] z;
    private final float[] velocityX;
    private final float[] velocityY;
    private final float[] velocityZ;
    private final float[] age;
    private final float[] lifetime;
    private final float[] startSize;
    private final float[] endSize;
    private final float[] rotationDegrees;
    private final float[] angularVelocityDegrees;
    private final float[] startRed;
    private final float[] startGreen;
    private final float[] startBlue;
    private final float[] startAlpha;
    private final float[] endRed;
    private final float[] endGreen;
    private final float[] endBlue;
    private final float[] endAlpha;
    private int activeCount;
    private int rngState = 0x1234ABCD;
    private float emitX;
    private float emitY;
    private float emitZ;
    private float emissionRate;
    private float emissionRemainder;
    private float minLifetime = 1.0f;
    private float maxLifetimeValue = 1.0f;
    private float minSpeed;
    private float maxSpeed;
    private float directionX;
    private float directionY = 1.0f;
    private float directionZ;
    private float spreadDegrees = FULL_SPHERE_SPREAD_DEGREES;
    private float gravityX;
    private float gravityY;
    private float gravityZ;
    private float minStartSize = 0.08f;
    private float maxStartSize = 0.08f;
    private float minEndSize = 0.0f;
    private float maxEndSize;
    private float startColorRed = 1.0f;
    private float startColorGreen = 1.0f;
    private float startColorBlue = 1.0f;
    private float startColorAlpha = 1.0f;
    private float endColorRed = 1.0f;
    private float endColorGreen = 1.0f;
    private float endColorBlue = 1.0f;
    private float endColorAlpha = 0.0f;
    private float minRotationDegrees;
    private float maxRotationDegrees;
    private float minAngularVelocityDegrees;
    private float maxAngularVelocityDegrees;

    /**
     * Creates a particle emitter.
     *
     * @param maxParticles the maximum active particles
     */
    public ParticleEmitter3D(int maxParticles) {
        if (maxParticles <= 0) {
            throw new FdxException("ParticleEmitter3D maxParticles must be greater than zero");
        }
        this.maxParticles = maxParticles;
        x = new float[maxParticles];
        y = new float[maxParticles];
        z = new float[maxParticles];
        velocityX = new float[maxParticles];
        velocityY = new float[maxParticles];
        velocityZ = new float[maxParticles];
        age = new float[maxParticles];
        lifetime = new float[maxParticles];
        startSize = new float[maxParticles];
        endSize = new float[maxParticles];
        rotationDegrees = new float[maxParticles];
        angularVelocityDegrees = new float[maxParticles];
        startRed = new float[maxParticles];
        startGreen = new float[maxParticles];
        startBlue = new float[maxParticles];
        startAlpha = new float[maxParticles];
        endRed = new float[maxParticles];
        endGreen = new float[maxParticles];
        endBlue = new float[maxParticles];
        endAlpha = new float[maxParticles];
    }

    /**
     * Sets the random seed and returns this emitter.
     *
     * @param seed the seed
     * @return this emitter
     */
    public ParticleEmitter3D seed(int seed) {
        rngState = seed;
        return this;
    }

    /**
     * Sets the emitter position and returns this emitter.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return this emitter
     */
    public ParticleEmitter3D position(float x, float y, float z) {
        validateFinite(x, "ParticleEmitter3D x");
        validateFinite(y, "ParticleEmitter3D y");
        validateFinite(z, "ParticleEmitter3D z");
        emitX = x;
        emitY = y;
        emitZ = z;
        return this;
    }

    /**
     * Sets the emission rate and returns this emitter.
     *
     * @param particlesPerSecond the particles per second
     * @return this emitter
     */
    public ParticleEmitter3D emissionRate(float particlesPerSecond) {
        validateFinite(particlesPerSecond, "ParticleEmitter3D emission rate");
        if (particlesPerSecond < 0.0f) {
            throw new FdxException("ParticleEmitter3D emission rate cannot be negative");
        }
        emissionRate = particlesPerSecond;
        return this;
    }

    /**
     * Sets a fixed particle lifetime and returns this emitter.
     *
     * @param seconds the lifetime in seconds
     * @return this emitter
     */
    public ParticleEmitter3D lifetime(float seconds) {
        return lifetime(seconds, seconds);
    }

    /**
     * Sets the particle lifetime range and returns this emitter.
     *
     * @param minSeconds the minimum lifetime in seconds
     * @param maxSeconds the maximum lifetime in seconds
     * @return this emitter
     */
    public ParticleEmitter3D lifetime(float minSeconds, float maxSeconds) {
        validateRange(minSeconds, maxSeconds, true, "ParticleEmitter3D lifetime");
        minLifetime = minSeconds;
        maxLifetimeValue = maxSeconds;
        return this;
    }

    /**
     * Sets a fixed particle speed and returns this emitter.
     *
     * @param unitsPerSecond the speed
     * @return this emitter
     */
    public ParticleEmitter3D speed(float unitsPerSecond) {
        return speed(unitsPerSecond, unitsPerSecond);
    }

    /**
     * Sets the particle speed range and returns this emitter.
     *
     * @param minUnitsPerSecond the minimum speed
     * @param maxUnitsPerSecond the maximum speed
     * @return this emitter
     */
    public ParticleEmitter3D speed(float minUnitsPerSecond, float maxUnitsPerSecond) {
        validateRange(minUnitsPerSecond, maxUnitsPerSecond, false, "ParticleEmitter3D speed");
        minSpeed = minUnitsPerSecond;
        maxSpeed = maxUnitsPerSecond;
        return this;
    }

    /**
     * Sets the emission direction cone and returns this emitter.
     *
     * @param x the direction x
     * @param y the direction y
     * @param z the direction z
     * @param spreadDegrees the total cone spread in degrees
     * @return this emitter
     */
    public ParticleEmitter3D direction(float x, float y, float z, float spreadDegrees) {
        validateFinite(x, "ParticleEmitter3D direction x");
        validateFinite(y, "ParticleEmitter3D direction y");
        validateFinite(z, "ParticleEmitter3D direction z");
        validateFinite(spreadDegrees, "ParticleEmitter3D spread");
        if (spreadDegrees < 0.0f) {
            throw new FdxException("ParticleEmitter3D spread cannot be negative");
        }
        float length = (float)Math.sqrt(x * x + y * y + z * z);
        if (length <= DIRECTION_EPSILON) {
            throw new FdxException("ParticleEmitter3D direction cannot be zero");
        }
        float invLength = 1.0f / length;
        directionX = x * invLength;
        directionY = y * invLength;
        directionZ = z * invLength;
        this.spreadDegrees = spreadDegrees;
        return this;
    }

    /**
     * Sets particle gravity and returns this emitter.
     *
     * @param x the gravity x
     * @param y the gravity y
     * @param z the gravity z
     * @return this emitter
     */
    public ParticleEmitter3D gravity(float x, float y, float z) {
        validateFinite(x, "ParticleEmitter3D gravity x");
        validateFinite(y, "ParticleEmitter3D gravity y");
        validateFinite(z, "ParticleEmitter3D gravity z");
        gravityX = x;
        gravityY = y;
        gravityZ = z;
        return this;
    }

    /**
     * Sets fixed start and end particle sizes and returns this emitter.
     *
     * @param start the start size
     * @param end the end size
     * @return this emitter
     */
    public ParticleEmitter3D size(float start, float end) {
        return size(start, start, end, end);
    }

    /**
     * Sets particle start and end size ranges and returns this emitter.
     *
     * @param minStart the minimum start size
     * @param maxStart the maximum start size
     * @param minEnd the minimum end size
     * @param maxEnd the maximum end size
     * @return this emitter
     */
    public ParticleEmitter3D size(float minStart, float maxStart, float minEnd, float maxEnd) {
        validateRange(minStart, maxStart, true, "ParticleEmitter3D start size");
        validateRange(minEnd, maxEnd, false, "ParticleEmitter3D end size");
        minStartSize = minStart;
        maxStartSize = maxStart;
        minEndSize = minEnd;
        maxEndSize = maxEnd;
        return this;
    }

    /**
     * Sets particle start and end colors and returns this emitter.
     *
     * @param startRed the start red
     * @param startGreen the start green
     * @param startBlue the start blue
     * @param startAlpha the start alpha
     * @param endRed the end red
     * @param endGreen the end green
     * @param endBlue the end blue
     * @param endAlpha the end alpha
     * @return this emitter
     */
    public ParticleEmitter3D color(float startRed, float startGreen, float startBlue, float startAlpha,
            float endRed, float endGreen, float endBlue, float endAlpha) {
        validateColor(startRed, "ParticleEmitter3D start red");
        validateColor(startGreen, "ParticleEmitter3D start green");
        validateColor(startBlue, "ParticleEmitter3D start blue");
        validateColor(startAlpha, "ParticleEmitter3D start alpha");
        validateColor(endRed, "ParticleEmitter3D end red");
        validateColor(endGreen, "ParticleEmitter3D end green");
        validateColor(endBlue, "ParticleEmitter3D end blue");
        validateColor(endAlpha, "ParticleEmitter3D end alpha");
        this.startColorRed = startRed;
        this.startColorGreen = startGreen;
        this.startColorBlue = startBlue;
        this.startColorAlpha = startAlpha;
        this.endColorRed = endRed;
        this.endColorGreen = endGreen;
        this.endColorBlue = endBlue;
        this.endColorAlpha = endAlpha;
        return this;
    }

    /**
     * Sets particle rotation ranges and returns this emitter.
     *
     * @param minDegrees the minimum start rotation
     * @param maxDegrees the maximum start rotation
     * @param minAngularVelocityDegrees the minimum angular velocity
     * @param maxAngularVelocityDegrees the maximum angular velocity
     * @return this emitter
     */
    public ParticleEmitter3D rotation(float minDegrees, float maxDegrees,
            float minAngularVelocityDegrees, float maxAngularVelocityDegrees) {
        validateOrderedRange(minDegrees, maxDegrees, "ParticleEmitter3D rotation");
        validateOrderedRange(minAngularVelocityDegrees, maxAngularVelocityDegrees,
                "ParticleEmitter3D angular velocity");
        minRotationDegrees = minDegrees;
        this.maxRotationDegrees = maxDegrees;
        this.minAngularVelocityDegrees = minAngularVelocityDegrees;
        this.maxAngularVelocityDegrees = maxAngularVelocityDegrees;
        return this;
    }

    /**
     * Updates particles and returns the active count.
     *
     * @param deltaSeconds the elapsed time in seconds
     * @return the active particle count
     */
    public int update(float deltaSeconds) {
        validateFinite(deltaSeconds, "ParticleEmitter3D deltaSeconds");
        if (deltaSeconds < 0.0f) {
            throw new FdxException("ParticleEmitter3D deltaSeconds cannot be negative");
        }
        updateActive(deltaSeconds);
        if (emissionRate > 0.0f && activeCount < maxParticles) {
            emissionRemainder += emissionRate * deltaSeconds;
            int emitCount = (int)emissionRemainder;
            if (emitCount > 0) {
                emissionRemainder -= emitCount;
                emit(emitCount);
            }
        }
        return activeCount;
    }

    /**
     * Emits particles immediately.
     *
     * @param count the requested count
     * @return the emitted count
     */
    public int emit(int count) {
        if (count < 0) {
            throw new FdxException("ParticleEmitter3D emit count cannot be negative");
        }
        int emitted = 0;
        while (emitted < count && activeCount < maxParticles) {
            spawn(activeCount);
            activeCount++;
            emitted++;
        }
        return emitted;
    }

    /**
     * Clears active particles.
     *
     * @return this emitter
     */
    public ParticleEmitter3D clear() {
        activeCount = 0;
        emissionRemainder = 0.0f;
        return this;
    }

    /**
     * Renders active particles through a caller-owned billboard renderer.
     *
     * @param texture the particle texture
     * @param camera the active camera
     * @param renderer the billboard renderer
     * @return the number of drawn particles
     */
    public int render(Texture texture, Camera camera, BillboardRenderer3D renderer) {
        if (texture == null) {
            throw new FdxException("ParticleEmitter3D texture cannot be null");
        }
        if (camera == null) {
            throw new FdxException("ParticleEmitter3D camera cannot be null");
        }
        if (renderer == null) {
            throw new FdxException("ParticleEmitter3D renderer cannot be null");
        }
        int drawn = 0;
        for (int i = 0; i < activeCount; i++) {
            float size = size(i);
            float alpha = alpha(i);
            if (size <= 0.0f || alpha <= 0.0f) {
                continue;
            }
            renderer.color(red(i), green(i), blue(i), alpha);
            renderer.draw(texture, camera, x[i], y[i], z[i], size, size, rotationDegrees[i]);
            drawn++;
        }
        renderer.color(1.0f, 1.0f, 1.0f, 1.0f);
        return drawn;
    }

    /**
     * Returns the maximum particle count.
     *
     * @return the maximum particle count
     */
    public int maxParticles() {
        return maxParticles;
    }

    /**
     * Returns the active particle count.
     *
     * @return the active particle count
     */
    public int activeCount() {
        return activeCount;
    }

    /**
     * Returns the particle x coordinate.
     *
     * @param index the particle index
     * @return the x coordinate
     */
    public float x(int index) {
        checkIndex(index);
        return x[index];
    }

    /**
     * Returns the particle y coordinate.
     *
     * @param index the particle index
     * @return the y coordinate
     */
    public float y(int index) {
        checkIndex(index);
        return y[index];
    }

    /**
     * Returns the particle z coordinate.
     *
     * @param index the particle index
     * @return the z coordinate
     */
    public float z(int index) {
        checkIndex(index);
        return z[index];
    }

    /**
     * Returns the particle age.
     *
     * @param index the particle index
     * @return the age in seconds
     */
    public float age(int index) {
        checkIndex(index);
        return age[index];
    }

    /**
     * Returns the particle lifetime.
     *
     * @param index the particle index
     * @return the lifetime in seconds
     */
    public float lifetime(int index) {
        checkIndex(index);
        return lifetime[index];
    }

    /**
     * Returns the particle current size.
     *
     * @param index the particle index
     * @return the current size
     */
    public float size(int index) {
        checkIndex(index);
        return lerp(startSize[index], endSize[index], progress(index));
    }

    /**
     * Returns the particle current red.
     *
     * @param index the particle index
     * @return the current red
     */
    public float red(int index) {
        checkIndex(index);
        return lerp(startRed[index], endRed[index], progress(index));
    }

    /**
     * Returns the particle current green.
     *
     * @param index the particle index
     * @return the current green
     */
    public float green(int index) {
        checkIndex(index);
        return lerp(startGreen[index], endGreen[index], progress(index));
    }

    /**
     * Returns the particle current blue.
     *
     * @param index the particle index
     * @return the current blue
     */
    public float blue(int index) {
        checkIndex(index);
        return lerp(startBlue[index], endBlue[index], progress(index));
    }

    /**
     * Returns the particle current alpha.
     *
     * @param index the particle index
     * @return the current alpha
     */
    public float alpha(int index) {
        checkIndex(index);
        return lerp(startAlpha[index], endAlpha[index], progress(index));
    }

    /**
     * Returns the particle rotation.
     *
     * @param index the particle index
     * @return the rotation in degrees
     */
    public float rotationDegrees(int index) {
        checkIndex(index);
        return rotationDegrees[index];
    }

    private void updateActive(float deltaSeconds) {
        int i = 0;
        while (i < activeCount) {
            velocityX[i] += gravityX * deltaSeconds;
            velocityY[i] += gravityY * deltaSeconds;
            velocityZ[i] += gravityZ * deltaSeconds;
            x[i] += velocityX[i] * deltaSeconds;
            y[i] += velocityY[i] * deltaSeconds;
            z[i] += velocityZ[i] * deltaSeconds;
            rotationDegrees[i] += angularVelocityDegrees[i] * deltaSeconds;
            age[i] += deltaSeconds;
            if (age[i] >= lifetime[i]) {
                remove(i);
            } else {
                i++;
            }
        }
    }

    private void spawn(int index) {
        x[index] = emitX;
        y[index] = emitY;
        z[index] = emitZ;
        age[index] = 0.0f;
        lifetime[index] = random(minLifetime, maxLifetimeValue);
        float speed = random(minSpeed, maxSpeed);
        setSpawnVelocity(index, speed);
        startSize[index] = random(minStartSize, maxStartSize);
        endSize[index] = random(minEndSize, maxEndSize);
        rotationDegrees[index] = random(minRotationDegrees, maxRotationDegrees);
        angularVelocityDegrees[index] = random(minAngularVelocityDegrees, maxAngularVelocityDegrees);
        startRed[index] = startColorRed;
        startGreen[index] = startColorGreen;
        startBlue[index] = startColorBlue;
        startAlpha[index] = startColorAlpha;
        endRed[index] = endColorRed;
        endGreen[index] = endColorGreen;
        endBlue[index] = endColorBlue;
        endAlpha[index] = endColorAlpha;
    }

    private void setSpawnVelocity(int index, float speed) {
        if (spreadDegrees >= FULL_SPHERE_SPREAD_DEGREES) {
            float zValue = nextFloat() * 2.0f - 1.0f;
            float radius = (float)Math.sqrt(Math.max(0.0f, 1.0f - zValue * zValue));
            float phi = PI2 * nextFloat();
            velocityX[index] = (float)Math.cos(phi) * radius * speed;
            velocityY[index] = zValue * speed;
            velocityZ[index] = (float)Math.sin(phi) * radius * speed;
            return;
        }

        float maxAngle = (float)Math.toRadians(spreadDegrees * 0.5f);
        float cosMax = (float)Math.cos(maxAngle);
        float cosTheta = spreadDegrees <= 0.0f ? 1.0f : lerp(1.0f, cosMax, nextFloat());
        float sinTheta = (float)Math.sqrt(Math.max(0.0f, 1.0f - cosTheta * cosTheta));
        float phi = PI2 * nextFloat();
        float cosPhi = (float)Math.cos(phi);
        float sinPhi = (float)Math.sin(phi);

        float referenceX = Math.abs(directionY) < 0.99f ? 0.0f : 1.0f;
        float referenceY = Math.abs(directionY) < 0.99f ? 1.0f : 0.0f;
        float rightX = referenceY * directionZ;
        float rightY = -referenceX * directionZ;
        float rightZ = referenceX * directionY - referenceY * directionX;
        float rightLength = (float)Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        if (rightLength <= DIRECTION_EPSILON) {
            rightX = 1.0f;
            rightY = 0.0f;
            rightZ = 0.0f;
        } else {
            float invRightLength = 1.0f / rightLength;
            rightX *= invRightLength;
            rightY *= invRightLength;
            rightZ *= invRightLength;
        }
        float upX = directionY * rightZ - directionZ * rightY;
        float upY = directionZ * rightX - directionX * rightZ;
        float upZ = directionX * rightY - directionY * rightX;

        float outX = directionX * cosTheta + (rightX * cosPhi + upX * sinPhi) * sinTheta;
        float outY = directionY * cosTheta + (rightY * cosPhi + upY * sinPhi) * sinTheta;
        float outZ = directionZ * cosTheta + (rightZ * cosPhi + upZ * sinPhi) * sinTheta;
        velocityX[index] = outX * speed;
        velocityY[index] = outY * speed;
        velocityZ[index] = outZ * speed;
    }

    private void remove(int index) {
        int last = activeCount - 1;
        if (index != last) {
            x[index] = x[last];
            y[index] = y[last];
            z[index] = z[last];
            velocityX[index] = velocityX[last];
            velocityY[index] = velocityY[last];
            velocityZ[index] = velocityZ[last];
            age[index] = age[last];
            lifetime[index] = lifetime[last];
            startSize[index] = startSize[last];
            endSize[index] = endSize[last];
            rotationDegrees[index] = rotationDegrees[last];
            angularVelocityDegrees[index] = angularVelocityDegrees[last];
            startRed[index] = startRed[last];
            startGreen[index] = startGreen[last];
            startBlue[index] = startBlue[last];
            startAlpha[index] = startAlpha[last];
            endRed[index] = endRed[last];
            endGreen[index] = endGreen[last];
            endBlue[index] = endBlue[last];
            endAlpha[index] = endAlpha[last];
        }
        activeCount--;
    }

    private float progress(int index) {
        float value = lifetime[index] > 0.0f ? age[index] / lifetime[index] : 1.0f;
        if (value <= 0.0f) {
            return 0.0f;
        }
        return value >= 1.0f ? 1.0f : value;
    }

    private float random(float min, float max) {
        if (min == max) {
            return min;
        }
        return min + (max - min) * nextFloat();
    }

    private float nextFloat() {
        rngState = rngState * 1664525 + 1013904223;
        return ((rngState >>> 8) & 0xFFFFFF) / 16777216.0f;
    }

    private float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= activeCount) {
            throw new IndexOutOfBoundsException("Particle index " + index + " outside active count " + activeCount);
        }
    }

    private void validateRange(float min, float max, boolean positive, String name) {
        validateFinite(min, name + " min");
        validateFinite(max, name + " max");
        if (positive && min <= 0.0f) {
            throw new FdxException(name + " minimum must be greater than zero");
        }
        if (!positive && min < 0.0f) {
            throw new FdxException(name + " minimum cannot be negative");
        }
        if (max < min) {
            throw new FdxException(name + " maximum cannot be less than minimum");
        }
    }

    private void validateOrderedRange(float min, float max, String name) {
        validateFinite(min, name + " min");
        validateFinite(max, name + " max");
        if (max < min) {
            throw new FdxException(name + " maximum cannot be less than minimum");
        }
    }

    private void validateColor(float value, String name) {
        validateFinite(value, name);
        if (value < 0.0f || value > 1.0f) {
            throw new FdxException(name + " must be between zero and one");
        }
    }

    private void validateFinite(float value, String name) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new FdxException(name + " must be finite");
        }
    }
}
