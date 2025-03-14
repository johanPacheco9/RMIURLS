package converturltopdf;

import java.io.FileOutputStream;
import java.io.IOException;
import java.rmi.Naming;
import java.util.Arrays;
import java.util.List;

public class Client {
    public static void main(String[] args) {
        try {
            // Lista de URLs a convertir
            List<String> urls = Arrays.asList(
                "https://www.youtube.com/watch?v=kI2DDw1Bbtg",
                "https://mariadb.com/kb/en/postdownload/mariadb-server-11-7-2/",
                "https://www.vanguardia.com/",
                "https://www.facebook.com/?locale=es_LA",
                "https://www.google.com/",
                "https://www.booking.com/index.es.html",
                "https://www.airbnb.com.co/",
                "https://www.avianca.com/es/",
                "https://www.decameron.com/es/co-inicio"
            );

            // Dirección del servidor donde corre el servicio RMI
            String serverAddress = "rmi://192.168.1.6:7084/ConvertServer";
            System.out.println("Conectando a: " + serverAddress);
            IConvertion service = (IConvertion) Naming.lookup(serverAddress);

            System.out.println("Solicitando conversión para " + urls.size() + " URLs");
            List<PDFResult> pdfResults = service.ejecutarConversion(urls);
            System.out.println("Cantidad de PDFs recibidos en el cliente: " + pdfResults.size());

            // Mostrar logs: nombre y tamaño de cada PDF
            for (int i = 0; i < pdfResults.size(); i++) {
                PDFResult pdfResult = pdfResults.get(i);
                byte[] pdfData = pdfResult.getPdfData();
                System.out.println("PDF " + (i+1) + " (" + pdfResult.getFileName() + ") tiene " + pdfData.length + " bytes");
                if (pdfData.length > 0) {
                    System.out.println("Primer byte: " + pdfData[0]);
                }
            }

            // Guardar los archivos en el cliente usando el nombre recibido
            guardarPDFs(pdfResults);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void guardarPDFs(List<PDFResult> pdfResults) {
        String carpetaDestino = "D:/obtenidos/";
        java.io.File destDir = new java.io.File(carpetaDestino);
        if (!destDir.exists()) {
            destDir.mkdirs();
            System.out.println("Carpeta creada: " + carpetaDestino);
        }

        for (PDFResult result : pdfResults) {
            // Extraer el nombre base del archivo generado en el nodo
            String fileName = new java.io.File(result.getFileName()).getName();
            // Se guarda en la carpeta destino
            fileName = carpetaDestino + fileName;
            byte[] pdfData = result.getPdfData();
            System.out.println("Guardando archivo " + fileName + " con " + pdfData.length + " bytes");
            try (FileOutputStream fos = new FileOutputStream(fileName)) {
                fos.write(pdfData);
                System.out.println("PDF descargado en: " + fileName);
            } catch (IOException e) {
                System.err.println("Error al guardar el archivo: " + fileName);
                e.printStackTrace();
            }
        }
    }
}
