package io.github.libfdx.net.webrtc.signaling.server;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describes a peer join request before it is accepted into a signaling room.
 *
 * @author xpenatan
 */
public final class WebRtcSignalingJoinRequest {
    private final String roomId;
    private final String requestedPeerId;
    private final String token;
    private final String resource;
    private final String path;
    private final Map<String, String> query;
    private final Map<String, String> headers;
    private final InetSocketAddress remoteAddress;

    WebRtcSignalingJoinRequest(String roomId, String requestedPeerId, String token, String resource, String path,
            Map<String, String> query, Map<String, String> headers, InetSocketAddress remoteAddress) {
        this.roomId = roomId;
        this.requestedPeerId = requestedPeerId;
        this.token = token;
        this.resource = resource;
        this.path = path;
        this.query = unmodifiableCopy(query);
        this.headers = unmodifiableCopy(headers);
        this.remoteAddress = remoteAddress;
    }

    public String roomId() {
        return roomId;
    }

    public String requestedPeerId() {
        return requestedPeerId;
    }

    public String token() {
        return token;
    }

    public String resource() {
        return resource;
    }

    public String path() {
        return path;
    }

    public Map<String, String> query() {
        return query;
    }

    public String query(String name) {
        return name != null ? query.get(name) : null;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String header(String name) {
        if (name == null) {
            return null;
        }
        String value = headers.get(name);
        if (value != null) {
            return value;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public InetSocketAddress remoteAddress() {
        return remoteAddress;
    }

    private static Map<String, String> unmodifiableCopy(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
    }
}
