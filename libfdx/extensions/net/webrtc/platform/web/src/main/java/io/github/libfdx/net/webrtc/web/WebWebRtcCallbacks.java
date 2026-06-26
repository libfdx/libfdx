package io.github.libfdx.net.webrtc.web;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

interface WebWebRtcCallbacks {
    @JSFunctor
    interface StringCallback extends JSObject {
        void call(String value);
    }

    @JSFunctor
    interface ErrorCallback extends JSObject {
        void call(String value);
    }

    @JSFunctor
    interface IceCallback extends JSObject {
        void call(String candidate, String sdpMid, int sdpMLineIndex);
    }

    @JSFunctor
    interface DataChannelCallback extends JSObject {
        void call(int handle, String label, boolean ordered);
    }

    @JSFunctor
    interface BytesCallback extends JSObject {
        void call(String base64);
    }
}
