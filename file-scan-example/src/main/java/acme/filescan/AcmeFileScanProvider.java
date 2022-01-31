package acme.filescan;

import org.orbeon.oxf.xforms.upload.api.java.FileScan;
import org.orbeon.oxf.xforms.upload.api.java.FileScanProvider;
import org.orbeon.oxf.xforms.upload.api.java.FileScanStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

/**
 * This is a simple demo file scan provider. It logs calls and will reject an upload file
 * if its name contains the string "virus". This obviously is not the right way to determine
 * whether a file contains a virus or not ;)
 */
public class AcmeFileScanProvider extends FileScanProvider {

    // A public no-arguments constructor is required by the `ServiceLoader` API
    public AcmeFileScanProvider() {
        logger.info("Instantiating AcmeFileScanProvider");
    }

    private static Logger logger = LoggerFactory.getLogger(AcmeFileScanProvider.class);

    private static class AcmeFileScan implements FileScan {

        private final String filename;
        private Map<String, String[]> headers;

        AcmeFileScan(String filename, Map<String, String[]> headers) {
            this.filename = filename;
            this.headers  = headers;
        }

        public FileScanStatus bytesReceived(byte[] bytes, int offset, int length) {
            logger.info("Received " + length +" bytes to scan for " + filename);
            return FileScanStatus.ACCEPT;
        }

        public FileScanStatus complete(File file) {
            logger.info("Completing scan for " + filename);

            if (filename.contains("virus"))
                return FileScanStatus.REJECT;
            else
                return FileScanStatus.ACCEPT;
        }

        public void abort() {
            logger.info("Aborting scan for " + filename);
        }
    }

    public FileScan startStream(String filename, Map<String, String[]> headers) {
        return new AcmeFileScan(filename, headers);
    }

    public void init() {
        // Initialize scan engine here if needed
        logger.info("Initializing AcmeFileScanProvider");
    }

    public void destroy() {
        // Release scan engine and other resources here if needed
        logger.info("Destroying AcmeFileScanProvider");
    }
}