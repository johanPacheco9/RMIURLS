package converturltopdf;

import java.io.Serializable;

public class PDFResult implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String fileName;
    private byte[] pdfData;

    public PDFResult(String fileName, byte[] pdfData) {
        this.fileName = fileName;
        this.pdfData = pdfData;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getPdfData() {
        return pdfData;
    }
}
