package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.ProviderId;

import java.util.Arrays;

/**
 * Identifies one native GL resource share group.
 */
final class GLResourceDomain {
    private final ProviderId providerId;
    private GLGraphicsAttachment[] attachments = new GLGraphicsAttachment[2];
    private int attachmentCount;

    GLResourceDomain(ProviderId providerId) {
        this.providerId = providerId;
    }

    ProviderId providerId() {
        return providerId;
    }

    void add(GLGraphicsAttachment attachment) {
        if (attachmentCount == attachments.length) {
            attachments = Arrays.copyOf(attachments, attachments.length * 2);
        }
        attachments[attachmentCount++] = attachment;
    }

    void remove(GLGraphicsAttachment attachment) {
        for (int i = 0; i < attachmentCount; i++) {
            if (attachments[i] != attachment) {
                continue;
            }
            int moved = attachmentCount - i - 1;
            if (moved > 0) {
                System.arraycopy(attachments, i + 1, attachments, i, moved);
            }
            attachments[--attachmentCount] = null;
            return;
        }
    }

    boolean makeAnyContextCurrent() {
        for (int i = 0; i < attachmentCount; i++) {
            GLGraphicsAttachment attachment = attachments[i];
            if (!attachment.isDisposed()) {
                attachment.makeCurrent();
                return true;
            }
        }
        return false;
    }
}
