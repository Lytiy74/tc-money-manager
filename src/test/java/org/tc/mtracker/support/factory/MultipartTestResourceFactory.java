package org.tc.mtracker.support.factory;

import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

public final class MultipartTestResourceFactory {

    private MultipartTestResourceFactory() {
    }

    public static ByteArrayResource resource(String filename, String content) {
        return resource(filename, content.getBytes(StandardCharsets.UTF_8));
    }

    public static ByteArrayResource resource(String filename, byte[] content) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    public static ByteArrayResource jpegImage(String filename) {
        return resource(filename, "image");
    }

    public static ByteArrayResource pngImage(String filename) {
        return resource(filename, "image");
    }

    public static ByteArrayResource gifImage(String filename) {
        return resource(filename, "image");
    }

    public static ByteArrayResource webpImage(String filename) {
        return resource(filename, "image");
    }

    public static ByteArrayResource pdfDocument(String filename) {
        return resource(filename, "%PDF");
    }
}
