package acme.filescan;

import org.orbeon.oxf.resources.ResourceManagerWrapper;
import org.orbeon.oxf.xforms.upload.api.java.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.util.Map;

/**
 * This is a simple demo file scan provider. It logs calls and will reject an upload file
 * if its name contains the string "virus". This obviously is not the right way to determine
 * whether a file contains a virus or not ;)
 */
public class AcmeFileScanProvider2 implements FileScanProvider2 {

    // A public no-arguments constructor is required by the `ServiceLoader` API
    public AcmeFileScanProvider2() {
        logger.info("Instantiating AcmeFileScanProvider");
    }

    private static Logger logger = LoggerFactory.getLogger(AcmeFileScanProvider2.class);

    private static class AcmeFileScan2 implements FileScan2 {

        private final String                filename;
        private final Map<String, String[]> headers;
        private final String                language;
        private final Map<String, Object>   extension;

        AcmeFileScan2(String filename, Map<String, String[]> headers, String language, Map<String, Object> extension) {
            this.filename  = filename;
            this.headers   = headers;
            this.language  = language;
            this.extension = extension;
        }

        public FileScanResult bytesReceived(byte[] bytes, int offset, int length) {
            logger.info("Received " + length +" bytes to scan for " + this.filename);
            return new FileScanResult.FileScanAcceptResult(); // keep going
        }

        public FileScanResult complete(File file) {

            final URI requestUri = (URI) extension.get("request.uri");

            logger.info("Completing scan for file `" + this.filename + "` for request URI `" + requestUri + "`");

            if (this.filename.contains("virus"))
                return new FileScanResult.FileScanRejectResult(
                    "fr".equals(this.language)
                        ? "Le fichier semble contenir un virus!"
                        : "The file appears to contain a virus!"
                );
            else if (this.filename.contains("replace"))
                return new FileScanResult.FileScanAcceptResult(
                    "fr".equals(this.language)
                        ? "Le contenu du fichier a été remplacé!"
                        : "The content of the file has been replaced!",
                    null, // mediatype
                    "My " + this.filename,
                    ResourceManagerWrapper.instance().getContentAsStream("/forms/orbeon/assets/acme-cat.jpg"),
                    null  // extension
                );
            else
                return new FileScanResult.FileScanAcceptResult(
                    "fr".equals(this.language)
                        ? "Le nom du fichier a été remplacé!"
                        : "The filename has been modified!",
                    null, // mediatype
                    "My " + this.filename,
                    null, // content
                    null  // extension
                );
        }

        public void abort() {
            logger.info("Aborting scan for " + filename);
        }
    }

    @Override
    public FileScan2 startStream(String filename, Map<String, String[]> headers, String language, Map<String, Object> extension) {
        logger.info("Starting scan for " + filename + " with language " + language);
        return new AcmeFileScan2(filename, headers, language, extension);
    }

    public void init() {
        // Initialize scan engine here if needed
        logger.info("Initializing AcmeFileScanProvider2");
    }

    public void destroy() {
        // Release scan engine and other resources here if needed
        logger.info("Destroying AcmeFileScanProvider2");
    }
}