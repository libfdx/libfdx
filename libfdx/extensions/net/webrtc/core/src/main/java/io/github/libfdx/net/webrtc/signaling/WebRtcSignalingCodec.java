package io.github.libfdx.net.webrtc.signaling;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.json.Json;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.net.webrtc.platform.WebRtcIceCandidate;
import io.github.libfdx.net.webrtc.platform.WebRtcSessionDescription;

/**
 * Encodes and decodes the libFDX WebRTC JSON signaling protocol.
 *
 * @author xpenatan
 */
public final class WebRtcSignalingCodec {
    private final Json json = new Json();

    public String encode(WebRtcSignalingMessage message) {
        if (message == null) {
            throw new FdxException("WebRTC signaling message cannot be null");
        }
        JsonValue root = JsonValue.object()
                .put("type", message.type().wireName())
                .put("payload", message.payload());
        putOptional(root, "room", message.roomId());
        putOptional(root, "source", message.sourcePeerId());
        putOptional(root, "target", message.targetPeerId());
        return json.write(root);
    }

    public WebRtcSignalingMessage decode(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new FdxException("WebRTC signaling text cannot be empty");
        }
        JsonValue root = json.read(text);
        WebRtcSignalingMessageType type = WebRtcSignalingMessageType.fromWireName(root.requireString("type"));
        JsonValue payload = root.get("payload");
        if (payload == null || payload.isNull()) {
            payload = JsonValue.object();
        }
        return WebRtcSignalingMessage.builder(type)
                .roomId(root.stringValue("room", null))
                .sourcePeerId(root.stringValue("source", null))
                .targetPeerId(root.stringValue("target", null))
                .payload(payload)
                .build();
    }

    public JsonValue writeSessionDescription(WebRtcSessionDescription description) {
        if (description == null) {
            throw new FdxException("WebRTC session description cannot be null");
        }
        return JsonValue.object()
                .put("descriptionType", description.type().wireName())
                .put("sdp", description.sdp());
    }

    public WebRtcSessionDescription readSessionDescription(JsonValue value) {
        if (value == null) {
            throw new FdxException("WebRTC session description payload cannot be null");
        }
        return new WebRtcSessionDescription(
                WebRtcSessionDescription.Type.fromWireName(value.requireString("descriptionType")),
                value.requireString("sdp"));
    }

    public JsonValue writeIceCandidate(WebRtcIceCandidate candidate) {
        if (candidate == null) {
            throw new FdxException("WebRTC ICE candidate cannot be null");
        }
        return JsonValue.object()
                .put("candidate", candidate.candidate())
                .put("sdpMid", candidate.sdpMid())
                .put("sdpMLineIndex", candidate.sdpMLineIndex());
    }

    public WebRtcIceCandidate readIceCandidate(JsonValue value) {
        if (value == null) {
            throw new FdxException("WebRTC ICE candidate payload cannot be null");
        }
        return new WebRtcIceCandidate(
                value.requireString("candidate"),
                value.stringValue("sdpMid", null),
                value.intValue("sdpMLineIndex", 0));
    }

    private static void putOptional(JsonValue root, String name, String value) {
        if (value != null) {
            root.put(name, value);
        }
    }
}
