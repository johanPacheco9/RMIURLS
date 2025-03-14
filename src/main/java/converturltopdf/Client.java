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
            IConvertion service = (IConvertion) Naming.lookup(serverAddress);

            // Enviar URLs al servidor para conversión
            List<byte[]> pdfs = service.ejecutarConversion(urls);

            // Guardar los archivos en el cliente
            guardarPDFsEnCliente(pdfs, urls);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Método para guardar los PDFs en el cliente
    private static void guardarPDFsEnCliente(List<byte[]> pdfs, List<String> urls) {
        String carpetaDestino = "D:/PDFsCliente/";
        
        // Crear la carpeta si no existe
        new java.io.File(carpetaDestino).mkdirs();

        for (int i = 0; i < pdfs.size(); i++) {
            byte[] pdfData = pdfs.get(i);
            String fileName = carpetaDestino + "archivo_" + (i + 1) + ".pdf";

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
