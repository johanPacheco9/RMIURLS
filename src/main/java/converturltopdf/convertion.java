package converturltopdf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class convertion extends UnicastRemoteObject implements IConvertion {
    private final AtomicBoolean isBusy = new AtomicBoolean(false);
    
    public convertion() throws RemoteException {
        super();
    }

    @Override
    public List<PDFResult> convertToPDF(List<String> urls) throws RemoteException {
        if (isBusy.get()) {
            throw new RemoteException("Node is busy");
        }
        isBusy.set(true);
        List<PDFResult> pdfResults = new ArrayList<>();
        
        try {
            String chromePath = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
            // Usamos un único timestamp para todas las conversiones de esta llamada
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            
            for (String url : urls) {
                String fileNamePart = sanitizeFileName(url);
                // Genera la ruta completa del PDF en disco
                String outputPath = String.format("D:/%s_%s.pdf", fileNamePart, timestamp);
                String command = String.format("\"%s\" --headless --disable-gpu --print-to-pdf=\"%s\" \"%s\"",
                        chromePath, outputPath, url);
                
                System.out.println("Executing command: " + command);
                
                Process process = Runtime.getRuntime().exec(command);
                int exitCode = process.waitFor();
                System.out.println("Process exited with code: " + exitCode);
                
                File pdfFile = new File(outputPath);
                if (exitCode == 0 && pdfFile.exists()) {
                    System.out.println("PDF file created: " + outputPath);
                    try (FileInputStream fis = new FileInputStream(pdfFile);
                         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            baos.write(buffer, 0, bytesRead);
                        }
                        byte[] pdfBytes = baos.toByteArray();
                        System.out.println("Tamaño del PDF leído: " + pdfBytes.length + " bytes");
                        // Creamos un PDFResult usando el outputPath (nombre generado) y los bytes leídos.
                        PDFResult result = new PDFResult(outputPath, pdfBytes);
                        pdfResults.add(result);
                    }
                } else {
                    throw new IOException("PDF file not created for URL: " + url);
                }
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error during conversion: " + e.getMessage());
            e.printStackTrace();
            throw new RemoteException("Error during conversion", e);
        } finally {
            isBusy.set(false);
        }
        return pdfResults;
    }

    @Override
    public boolean isNodeAvailable() throws RemoteException {
        return !isBusy.get();
    }

    private String sanitizeFileName(String input) {
        return input.replaceAll("[^a-zA-Z0-9]", "_");
    }
}
