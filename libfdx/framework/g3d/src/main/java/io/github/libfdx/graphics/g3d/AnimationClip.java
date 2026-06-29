package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.Matrix4;

/**
 * Represents an animation clip.
 *
 * @author xpenatan
 */
public final class AnimationClip {
    private final String id;
    private final float durationSeconds;
    private final NodeTransformChannel[] nodeTransformChannels;

    /**
     * Creates an animation clip.
     *
     * @param id the identifier
     * @param durationSeconds the duration seconds
     */
    public AnimationClip(String id, float durationSeconds) {
        this(id, durationSeconds, null);
    }

    /**
     * Creates an animation clip.
     *
     * @param id the identifier
     * @param durationSeconds the duration seconds
     * @param nodeTransformChannels the node transform channels
     */
    public AnimationClip(String id, float durationSeconds, NodeTransformChannel[] nodeTransformChannels) {
        if (Float.isNaN(durationSeconds) || durationSeconds < 0.0f) {
            throw new FdxException("Animation duration cannot be negative");
        }
        this.id = id != null ? id : "";
        this.durationSeconds = durationSeconds;
        this.nodeTransformChannels = nodeTransformChannels != null
                ? nodeTransformChannels.clone()
                : new NodeTransformChannel[0];
        for (int i = 0; i < this.nodeTransformChannels.length; i++) {
            if (this.nodeTransformChannels[i] == null) {
                throw new FdxException("Animation node transform channel cannot be null");
            }
        }
    }

    /**
     * Creates a node transform channel.
     *
     * @param nodeId the node identifier
     * @param keyframes the keyframes
     * @return a new node transform channel
     */
    public static NodeTransformChannel nodeTransform(String nodeId, TransformKeyframe... keyframes) {
        return new NodeTransformChannel(nodeId, keyframes);
    }

    /**
     * Creates a translation-only keyframe.
     *
     * @param timeSeconds the keyframe time
     * @param translationX the translation x
     * @param translationY the translation y
     * @param translationZ the translation z
     * @return a new transform keyframe
     */
    public static TransformKeyframe keyframe(float timeSeconds, float translationX, float translationY,
            float translationZ) {
        return keyframe(timeSeconds, translationX, translationY, translationZ,
                0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * Creates a translation, rotation, and scale keyframe.
     *
     * @param timeSeconds the keyframe time
     * @param translationX the translation x
     * @param translationY the translation y
     * @param translationZ the translation z
     * @param rotationX the rotation quaternion x
     * @param rotationY the rotation quaternion y
     * @param rotationZ the rotation quaternion z
     * @param rotationW the rotation quaternion w
     * @param scaleX the scale x
     * @param scaleY the scale y
     * @param scaleZ the scale z
     * @return a new transform keyframe
     */
    public static TransformKeyframe keyframe(float timeSeconds, float translationX, float translationY,
            float translationZ, float rotationX, float rotationY, float rotationZ, float rotationW,
            float scaleX, float scaleY, float scaleZ) {
        return new TransformKeyframe(timeSeconds, translationX, translationY, translationZ,
                rotationX, rotationY, rotationZ, rotationW, scaleX, scaleY, scaleZ);
    }

    /**
     * Returns the ID.
     *
     * @return the ID
     */
    public String id() {
        return id;
    }

    /**
     * Returns the duration seconds.
     *
     * @return the duration seconds
     */
    public float durationSeconds() {
        return durationSeconds;
    }

    /**
     * Returns the node transform channels.
     *
     * @return the node transform channels
     */
    public NodeTransformChannel[] nodeTransformChannels() {
        return nodeTransformChannels.clone();
    }

    NodeTransformChannel[] nodeTransformChannelsUnsafe() {
        return nodeTransformChannels;
    }

    /**
     * Describes a transform channel targeting one model node.
     *
     * @author xpenatan
     */
    public static final class NodeTransformChannel {
        private final String nodeId;
        private final TransformKeyframe[] keyframes;

        /**
         * Creates a node transform channel.
         *
         * @param nodeId the node identifier
         * @param keyframes the keyframes
         */
        public NodeTransformChannel(String nodeId, TransformKeyframe... keyframes) {
            if (nodeId == null || nodeId.trim().length() == 0) {
                throw new FdxException("Animation node id cannot be empty");
            }
            if (keyframes == null || keyframes.length == 0) {
                throw new FdxException("Animation node transform channel requires at least one keyframe");
            }
            this.nodeId = nodeId;
            this.keyframes = keyframes.clone();
            float previousTime = -1.0f;
            for (int i = 0; i < this.keyframes.length; i++) {
                TransformKeyframe keyframe = this.keyframes[i];
                if (keyframe == null) {
                    throw new FdxException("Animation keyframe cannot be null");
                }
                if (keyframe.timeSeconds() <= previousTime) {
                    throw new FdxException("Animation keyframes must be sorted by increasing time");
                }
                previousTime = keyframe.timeSeconds();
            }
        }

        /**
         * Returns the node id.
         *
         * @return the node id
         */
        public String nodeId() {
            return nodeId;
        }

        /**
         * Returns the keyframes.
         *
         * @return the keyframes
         */
        public TransformKeyframe[] keyframes() {
            return keyframes.clone();
        }

        /**
         * Samples this channel.
         *
         * @param timeSeconds the sample time
         * @param out the output matrix
         * @return the output matrix
         */
        public Matrix4 sample(float timeSeconds, Matrix4 out) {
            if (out == null) {
                throw new FdxException("Animation sample output cannot be null");
            }
            if (keyframes.length == 1 || timeSeconds <= keyframes[0].timeSeconds()) {
                return keyframes[0].toMatrix(out);
            }
            int last = keyframes.length - 1;
            if (timeSeconds >= keyframes[last].timeSeconds()) {
                return keyframes[last].toMatrix(out);
            }
            for (int i = 0; i < last; i++) {
                TransformKeyframe left = keyframes[i];
                TransformKeyframe right = keyframes[i + 1];
                if (timeSeconds >= left.timeSeconds() && timeSeconds <= right.timeSeconds()) {
                    float span = right.timeSeconds() - left.timeSeconds();
                    float alpha = span > 0.0f ? (timeSeconds - left.timeSeconds()) / span : 0.0f;
                    return TransformKeyframe.lerp(left, right, alpha, out);
                }
            }
            return keyframes[last].toMatrix(out);
        }
    }

    /**
     * Describes one transform keyframe.
     *
     * @author xpenatan
     */
    public static final class TransformKeyframe {
        private final float timeSeconds;
        private final float translationX;
        private final float translationY;
        private final float translationZ;
        private final float rotationX;
        private final float rotationY;
        private final float rotationZ;
        private final float rotationW;
        private final float scaleX;
        private final float scaleY;
        private final float scaleZ;

        /**
         * Creates a transform keyframe.
         *
         * @param timeSeconds the keyframe time
         * @param translationX the translation x
         * @param translationY the translation y
         * @param translationZ the translation z
         * @param rotationX the rotation quaternion x
         * @param rotationY the rotation quaternion y
         * @param rotationZ the rotation quaternion z
         * @param rotationW the rotation quaternion w
         * @param scaleX the scale x
         * @param scaleY the scale y
         * @param scaleZ the scale z
         */
        public TransformKeyframe(float timeSeconds, float translationX, float translationY, float translationZ,
                float rotationX, float rotationY, float rotationZ, float rotationW,
                float scaleX, float scaleY, float scaleZ) {
            if (Float.isNaN(timeSeconds) || timeSeconds < 0.0f) {
                throw new FdxException("Animation keyframe time cannot be negative");
            }
            this.timeSeconds = timeSeconds;
            this.translationX = translationX;
            this.translationY = translationY;
            this.translationZ = translationZ;
            this.rotationX = rotationX;
            this.rotationY = rotationY;
            this.rotationZ = rotationZ;
            this.rotationW = rotationW;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
        }

        /**
         * Returns the time seconds.
         *
         * @return the time seconds
         */
        public float timeSeconds() {
            return timeSeconds;
        }

        /**
         * Sets this keyframe transform into a matrix.
         *
         * @param out the output matrix
         * @return the output matrix
         */
        public Matrix4 toMatrix(Matrix4 out) {
            if (out == null) {
                throw new FdxException("Animation keyframe output cannot be null");
            }
            return out.setToTrs(translationX, translationY, translationZ,
                    rotationX, rotationY, rotationZ, rotationW, scaleX, scaleY, scaleZ);
        }

        private static Matrix4 lerp(TransformKeyframe left, TransformKeyframe right, float alpha, Matrix4 out) {
            float t = clamp(alpha);
            float qx0 = left.rotationX;
            float qy0 = left.rotationY;
            float qz0 = left.rotationZ;
            float qw0 = left.rotationW;
            float qx1 = right.rotationX;
            float qy1 = right.rotationY;
            float qz1 = right.rotationZ;
            float qw1 = right.rotationW;
            float dot = qx0 * qx1 + qy0 * qy1 + qz0 * qz1 + qw0 * qw1;
            if (dot < 0.0f) {
                dot = -dot;
                qx1 = -qx1;
                qy1 = -qy1;
                qz1 = -qz1;
                qw1 = -qw1;
            }

            float rotationX;
            float rotationY;
            float rotationZ;
            float rotationW;
            if (dot > 0.9995f) {
                rotationX = qx0 + (qx1 - qx0) * t;
                rotationY = qy0 + (qy1 - qy0) * t;
                rotationZ = qz0 + (qz1 - qz0) * t;
                rotationW = qw0 + (qw1 - qw0) * t;
            }
            else {
                float theta0 = (float)Math.acos(dot);
                float theta = theta0 * t;
                float sinTheta = (float)Math.sin(theta);
                float sinTheta0 = (float)Math.sin(theta0);
                float s0 = (float)Math.cos(theta) - dot * sinTheta / sinTheta0;
                float s1 = sinTheta / sinTheta0;
                rotationX = qx0 * s0 + qx1 * s1;
                rotationY = qy0 * s0 + qy1 * s1;
                rotationZ = qz0 * s0 + qz1 * s1;
                rotationW = qw0 * s0 + qw1 * s1;
            }

            return out.setToTrs(
                    mix(left.translationX, right.translationX, t),
                    mix(left.translationY, right.translationY, t),
                    mix(left.translationZ, right.translationZ, t),
                    rotationX, rotationY, rotationZ, rotationW,
                    mix(left.scaleX, right.scaleX, t),
                    mix(left.scaleY, right.scaleY, t),
                    mix(left.scaleZ, right.scaleZ, t));
        }

        private static float mix(float left, float right, float alpha) {
            return left + (right - left) * alpha;
        }

        private static float clamp(float value) {
            return Math.max(0.0f, Math.min(1.0f, value));
        }
    }
}
