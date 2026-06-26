package io.github.libfdx.net.webrtc.signaling;

import io.github.libfdx.json.JsonValue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import io.github.libfdx.net.webrtc.platform.WebRtcIceCandidate;
import io.github.libfdx.net.webrtc.platform.WebRtcSessionDescription;
final class WebRtcSignalingCodecTest {
    @Test
    void encodesAndDecodesMessagesAndPayloads() {
        WebRtcSignalingCodec codec = new WebRtcSignalingCodec();
        WebRtcSessionDescription offer = new WebRtcSessionDescription(WebRtcSessionDescription.Type.OFFER,
                "v=0");
        WebRtcSignalingMessage message = WebRtcSignalingMessage.builder(WebRtcSignalingMessageType.OFFER)
                .roomId("room")
                .sourcePeerId("a")
                .targetPeerId("b")
                .payload(codec.writeSessionDescription(offer))
                .build();

        WebRtcSignalingMessage decoded = codec.decode(codec.encode(message));

        assertEquals(WebRtcSignalingMessageType.OFFER, decoded.type());
        assertEquals("room", decoded.roomId());
        assertEquals("a", decoded.sourcePeerId());
        assertEquals("b", decoded.targetPeerId());
        assertEquals(WebRtcSessionDescription.Type.OFFER, codec.readSessionDescription(decoded.payload()).type());
        assertEquals("v=0", codec.readSessionDescription(decoded.payload()).sdp());
    }

    @Test
    void encodesAndDecodesIceCandidates() {
        WebRtcSignalingCodec codec = new WebRtcSignalingCodec();
        JsonValue payload = codec.writeIceCandidate(new WebRtcIceCandidate("candidate", "0", 1));

        WebRtcIceCandidate decoded = codec.readIceCandidate(payload);

        assertEquals("candidate", decoded.candidate());
        assertEquals("0", decoded.sdpMid());
        assertEquals(1, decoded.sdpMLineIndex());
    }
}
