package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.Matrix4;

/**
 * Immutable borrowed animation data shared by independent controllers. Constructors copy channel
 * and event arrays; channels/samplers own their immutable inputs. Clip times are finite seconds.
 * Application events are independent of glTF import. Playback and pose storage belong to a controller.
 *
 * @author xpenatan
 */
public final class AnimationClip {
    private final String id;
    private final float durationSeconds;
    private final NodeTransformChannel[] nodeTransformChannels;
    private final Event[] events;

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
        this(id,durationSeconds,nodeTransformChannels,null);
    }

    /** Copies ordered application event markers. Equal-time markers retain caller order; times must
     * lie in [0,duration]. Events are application metadata, independent of glTF import. */
    public AnimationClip(String id, float durationSeconds, NodeTransformChannel[] nodeTransformChannels, Event[] events) {
        if (!Float.isFinite(durationSeconds) || durationSeconds < 0.0f) {
            throw new FdxException("Animation duration must be finite and nonnegative");
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
        this.events=events == null ? new Event[0] : events.clone();
        float previous=-1;
        for (Event event : this.events) {
            if (event == null || event.timeSeconds < previous || event.timeSeconds > durationSeconds)
                throw new FdxException("Animation events must be ordered within the clip duration");
            previous=event.timeSeconds;
        }
    }

    /** Immutable application marker. The id is returned verbatim to the playback listener. */
    public static final class Event {
        private final float timeSeconds;
        private final String id;
        public Event(float timeSeconds,String id) {
            if (!Float.isFinite(timeSeconds) || timeSeconds < 0 || id == null || id.isEmpty())
                throw new FdxException("Animation event requires a finite nonnegative time and nonempty id");
            this.timeSeconds=timeSeconds; this.id=id;
        }
        public float timeSeconds() { return timeSeconds; }
        public String id() { return id; }
    }
    /** Returns a copy of the ordered immutable markers. */
    public Event[] events() { return events.clone(); }
    Event[] eventsUnsafe() { return events; }

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

    /** Creates independent immutable translation/rotation/scale tracks. A null track uses defaults;
     * defaults' time is ignored. This representation retains mixed interpolation and separate key times. */
    public static NodeTransformChannel sampledTransform(String nodeId,TransformKeyframe defaults,
            AnimationSampler translation,AnimationSampler rotation,AnimationSampler scale) {
        return new NodeTransformChannel(nodeId,defaults,translation,rotation,scale);
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
        private final TransformKeyframe defaults;
        private final AnimationSampler translation,rotation,scale;

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
            defaults=null;translation=rotation=scale=null;
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

        private NodeTransformChannel(String nodeId,TransformKeyframe defaults,AnimationSampler translation,
                AnimationSampler rotation,AnimationSampler scale) {
            if(nodeId==null||nodeId.trim().isEmpty()||defaults==null)throw new FdxException("Sampled transform requires a node and defaults");
            defaults.validateDefaults();
            if(translation!=null&&translation.isRotation()||scale!=null&&scale.isRotation()||rotation!=null&&!rotation.isRotation())
                throw new FdxException("Transform sampler kind mismatch");
            this.nodeId=nodeId;this.defaults=defaults;this.translation=translation;this.rotation=rotation;this.scale=scale;keyframes=null;
        }
        /** Whether this channel holds independent sampler tracks instead of combined linear keyframes. */
        public boolean hasSamplers(){return keyframes==null;}
        /** Borrowed immutable sampler, or null when the default component is used. */
        public AnimationSampler translationSampler(){return translation;}
        /** Borrowed immutable sampler, or null when the default component is used. */
        public AnimationSampler rotationSampler(){return rotation;}
        /** Borrowed immutable sampler, or null when the default component is used. */
        public AnimationSampler scaleSampler(){return scale;}

        /**
         * Returns the node id.
         *
         * @return the node id
         */
        public String nodeId() {
            return nodeId;
        }

        /** Samples translation XYZ, normalized quaternion XYZW and scale XYZ into ten caller-owned floats.
         * Preserves authored signed/zero scale without decomposing a matrix. Allocates no storage. */
        public void sampleTrs(float timeSeconds, float[] out, int offset) {
            if (!Float.isFinite(timeSeconds) || out == null || offset < 0 || offset > out.length-10)
                throw new FdxException("Animation TRS sample needs a finite time and ten output floats");
            if (keyframes == null) {
                defaults.copyTrs(out,offset);
                if (translation != null) translation.sample(timeSeconds,out,offset);
                if (rotation != null) rotation.sample(timeSeconds,out,offset+3);
                if (scale != null) scale.sample(timeSeconds,out,offset+7);
                return;
            }
            int low=0, high=keyframes.length;
            while (low < high) { int mid=(low+high)>>>1; if (keyframes[mid].timeSeconds <= timeSeconds) low=mid+1; else high=mid; }
            int index=Math.max(0,low-1);
            TransformKeyframe a=keyframes[index];
            a.copyTrs(out,offset);
            if (index == keyframes.length-1 || timeSeconds <= a.timeSeconds) return;
            TransformKeyframe b=keyframes[index+1];
            double t=(timeSeconds-a.timeSeconds)/((double)b.timeSeconds-a.timeSeconds);
            out[offset]=(float)((1-t)*a.translationX+t*b.translationX);
            out[offset+1]=(float)((1-t)*a.translationY+t*b.translationY);
            out[offset+2]=(float)((1-t)*a.translationZ+t*b.translationZ);
            AnimationTransforms.rotation(a.rotationX,a.rotationY,a.rotationZ,a.rotationW,
                    b.rotationX,b.rotationY,b.rotationZ,b.rotationW,t,out,offset+3);
            out[offset+7]=(float)((1-t)*a.scaleX+t*b.scaleX);
            out[offset+8]=(float)((1-t)*a.scaleY+t*b.scaleY);
            out[offset+9]=(float)((1-t)*a.scaleZ+t*b.scaleZ);
        }

        boolean copyDefaults(float[] out, int offset) {
            if (defaults == null) return false;
            defaults.copyTrs(out,offset); return true;
        }

        /**
         * Returns copied combined keyframes. Independent sampler channels have no combined linear
         * representation and throw; use hasSamplers and the individual sampler accessors.
         *
         * @return the keyframes
         */
        public TransformKeyframe[] keyframes() {
            if(keyframes==null)throw new FdxException("Independent sampler channels have no combined linear keyframes");
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
            if(!Float.isFinite(timeSeconds))throw new FdxException("Animation sample time must be finite");
            if(keyframes==null) {
                int ti=translation==null?0:translation.interval(timeSeconds),si=scale==null?0:scale.interval(timeSeconds);
                float x=translation==null?defaults.translationX:translation.component(ti,timeSeconds,0);
                float y=translation==null?defaults.translationY:translation.component(ti,timeSeconds,1);
                float z=translation==null?defaults.translationZ:translation.component(ti,timeSeconds,2);
                float sx=scale==null?defaults.scaleX:scale.component(si,timeSeconds,0);
                float sy=scale==null?defaults.scaleY:scale.component(si,timeSeconds,1);
                float sz=scale==null?defaults.scaleZ:scale.component(si,timeSeconds,2);
                if(rotation!=null)return rotation.compose(timeSeconds,out,x,y,z,sx,sy,sz);
                return out.setToTrs(x,y,z,defaults.rotationX,defaults.rotationY,defaults.rotationZ,defaults.rotationW,sx,sy,sz);
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
            if (!Float.isFinite(timeSeconds) || timeSeconds < 0.0f) {
                throw new FdxException("Animation keyframe time must be finite and nonnegative");
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
            validateDefaults();
        }

        private void copyTrs(float[] out, int offset) {
            out[offset]=translationX; out[offset+1]=translationY; out[offset+2]=translationZ;
            AnimationTransforms.rotation(rotationX,rotationY,rotationZ,rotationW,
                    rotationX,rotationY,rotationZ,rotationW,0,out,offset+3);
            out[offset+7]=scaleX; out[offset+8]=scaleY; out[offset+9]=scaleZ;
        }

        private void validateDefaults() {
            if (!Float.isFinite(translationX) || !Float.isFinite(translationY) || !Float.isFinite(translationZ)
                    || !Float.isFinite(scaleX) || !Float.isFinite(scaleY) || !Float.isFinite(scaleZ)) {
                throw new FdxException("Sampled transform defaults must be finite");
            }
            double lengthSquared = (double)rotationX * rotationX + (double)rotationY * rotationY
                    + (double)rotationZ * rotationZ + (double)rotationW * rotationW;
            if (!Double.isFinite(lengthSquared) || Math.abs(lengthSquared - 1) > .001) {
                throw new FdxException("Sampled transform default rotation must be a unit quaternion");
            }
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
