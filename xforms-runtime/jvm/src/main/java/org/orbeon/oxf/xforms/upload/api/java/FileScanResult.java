package org.orbeon.oxf.xforms.upload.api.java;


public abstract class FileScanResult {

    public final String message;

    private FileScanResult(String message) {
        this.message = message;
    }

    public static class FileScanAcceptResult extends FileScanResult {

        public final String                        mediatype;
        public final String                        filename;
        public final java.io.InputStream           content;
        public final java.util.Map<String, Object> extension;

        public FileScanAcceptResult() {
            this(null, null, null, null, null);
        }

        public FileScanAcceptResult(
            String                        message,
            String                        mediatype,
            String                        filename,
            java.io.InputStream           content,
            java.util.Map<String, Object> extension
        ) {
            super(message);
            this.mediatype = mediatype;
            this.filename  = filename;
            this.content   = content;
            this.extension = extension;
        }
    }

    public static class FileScanRejectResult extends FileScanResult {
        public FileScanRejectResult(String message) {
            super(message);
        }
    }

    public static class FileScanErrorResult extends FileScanResult {

        public final Throwable throwable;

        public FileScanErrorResult(String message, Throwable throwable) {
            super(message);
            this.throwable = throwable;
        }
    }
}
