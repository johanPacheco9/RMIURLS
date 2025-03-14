package converturltopdf;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.rmi.server.UnicastRemoteObject;

public class RMIServer extends UnicastRemoteObject implements IConvertion {
    private final ExecutorService executor;
    private final List<String> nodosActivos;
    
    // Bloque para cargar el driver de MariaDB.
    static {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
            System.out.println("Driver MariaDB cargado correctamente.");
        } catch (ClassNotFoundException e) {
            System.err.println("No se pudo cargar el driver de MariaDB.");
            e.printStackTrace();
        }
    }
    
    public RMIServer() throws RemoteException {
        super();
        executor = Executors.newFixedThreadPool(5);
        nodosActivos = obtenerNodosDisponibles();
    }
    
    @Override
    public List<PDFResult> ejecutarConversion(List<String> urls) throws RemoteException {
        List<PDFResult> pdfResults = new ArrayList<>();
        List<Future<List<PDFResult>>> futures = new ArrayList<>();
        
        if (nodosActivos.isEmpty()) {
            throw new RemoteException("No hay nodos activos disponibles.");
        }
        
        int numNodos = nodosActivos.size();
        int index = 0;
        
        for (String url : urls) {
            final int currentIndex = index;  // Variable final para usar en la lambda
            String nodoInicial = nodosActivos.get(currentIndex % numNodos);
            index++;
            
            futures.add(executor.submit(() -> {
                List<PDFResult> result = new ArrayList<>();
                int maxAttempts = 3;
                int attempt = 0;
                boolean success = false;
                String nodoSeleccionado = nodoInicial;
                
                while (attempt < maxAttempts && !success) {
                    try {
                        IConvertion nodeService = (IConvertion) Naming.lookup(nodoSeleccionado);
                        // Intentamos convertir la URL en el nodo seleccionado.
                        List<PDFResult> pdfDataList = nodeService.convertToPDF(Collections.singletonList(url));
                        
                        if (pdfDataList != null && !pdfDataList.isEmpty()) {
                            // Guardamos cada PDF en disco (este guardado se hace en el servidor para registro)
                            for (PDFResult pdfRes : pdfDataList) {
                                String fileName = pdfRes.getFileName();
                                // Guardamos el PDF en una ruta "local" del servidor
                                try (FileOutputStream fos = new FileOutputStream(fileName)) {
                                    fos.write(pdfRes.getPdfData());
                                    System.out.println("PDF guardado: " + fileName);
                                }
                                
                                // Registrar la conversión en la base de datos.
                                try (Connection connection = DriverManager.getConnection(
                                        "jdbc:mariadb://localhost:3306/convertpdfs", "root", "1098825894")) {
                                    int nodeId = getOrInsertNode(connection, nodoSeleccionado);
                                    int conversionId = registrarConversion(connection, fileName, fileName, nodeId);
                                    long elapsedTime = System.currentTimeMillis();
                                    registrarLog(connection, conversionId, elapsedTime);
                                }
                                result.add(pdfRes);
                            }
                            System.out.println("Conversión exitosa para URL: " + url + " en nodo: " + nodoSeleccionado);
                            success = true; // Conversión exitosa.
                        } else {
                            System.out.println("La conversión de " + url + " falló en " + nodoSeleccionado);
                            break; // No se generó PDF y no es error "Node is busy"
                        }
                    } catch (RemoteException re) {
                        if (re.getMessage() != null && re.getMessage().contains("Node is busy")) {
                            System.out.println("Nodo " + nodoSeleccionado + " está ocupado para URL: " + url + ". Intento " + (attempt + 1));
                            attempt++;
                            int nextNodeIndex = (currentIndex + attempt) % numNodos;
                            nodoSeleccionado = nodosActivos.get(nextNodeIndex);
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException ie) {
                                ie.printStackTrace();
                            }
                        } else {
                            System.out.println("Error procesando URL: " + url + " en nodo: " + nodoSeleccionado);
                            re.printStackTrace();
                            break;
                        }
                    }
                }
                if (!success) {
                    System.out.println("No se pudo procesar la URL " + url + " después de " + maxAttempts + " intentos.");
                }
                return result;
            }));
        }
        
        for (Future<List<PDFResult>> future : futures) {
            try {
                pdfResults.addAll(future.get());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        
        System.out.println("Cantidad total de PDFs retornados: " + pdfResults.size());
        System.out.println("Todas las conversiones han finalizado.");
        return pdfResults;
    }
    
    private List<String> obtenerNodosDisponibles() {
        List<String> nodos = Arrays.asList(
                "rmi://192.168.1.6:7380/ConversionService",
                "rmi://192.168.1.6:7381/ConversionService",
                "rmi://192.168.1.8:7380/ConversionService"
        );
        
        List<String> nodosActivos = new ArrayList<>();
        for (String nodo : nodos) {
            try {
                IConvertion nodeService = (IConvertion) Naming.lookup(nodo);
                nodosActivos.add(nodo);
                System.out.println("Nodo activo: " + nodo);
            } catch (Exception e) {
                System.out.println("Nodo no disponible: " + nodo);
            }
        }
        return nodosActivos;
    }
    
    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.createRegistry(7084);
            RMIServer server = new RMIServer();
            Naming.rebind("rmi://192.168.1.6:7084/ConvertServer", server);
            System.out.println("Servidor de conversión en ejecución en el puerto 7084...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Métodos para base de datos
    private static int getOrInsertNode(Connection connection, String nodeIdentifier) throws SQLException {
        String selectSql = "SELECT id FROM Node WHERE estado = ?";
        try (PreparedStatement stmt = connection.prepareStatement(selectSql)) {
            stmt.setString(1, nodeIdentifier);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        String insertSql = "INSERT INTO Node (estado, numeroTareas, contrasena) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, nodeIdentifier);
            stmt.setInt(2, 0);
            stmt.setString(3, "default");
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    throw new SQLException("Error al obtener el id del nodo insertado");
                }
            }
        }
    }
    
    private static int registrarConversion(Connection connection, String fileName, String filePath, int nodeId) throws SQLException {
        String sql = "INSERT INTO Conversion (file_name, file_path, nodeId) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, fileName);
            stmt.setString(2, filePath);
            stmt.setInt(3, nodeId);
            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Error al obtener el ID de la conversión");
                }
            }
        }
    }
    
    private static void registrarLog(Connection connection, int conversionId, long elapsedTime) throws SQLException {
        String sql = "INSERT INTO Log (spent_time, ConversionId) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            Timestamp ts = new Timestamp(elapsedTime);
            stmt.setTimestamp(1, ts);
            stmt.setInt(2, conversionId);
            stmt.executeUpdate();
        }
    }
    
    private static String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to generate hash", e);
        }
    }
    
    private static String sanitizeFileName(String input) {
        return input.replaceAll("[^a-zA-Z0-9]", "_");
    }
    
    @Override
    public boolean isNodeAvailable() throws RemoteException {
        return true;
    }
    
    @Override
    public List<PDFResult> convertToPDF(List<String> urls) throws RemoteException {
        // En el nodo, se implementa la conversión. Por ejemplo:
        List<PDFResult> pdfResults = new ArrayList<>();
        // Para cada URL se genera un PDF, se guarda en disco y se leen los bytes.
        // Aquí usamos la misma lógica que antes, pero generamos el PDFResult usando el nombre de archivo.
        // NOTA: Este método debe ser implementado en cada nodo.
        throw new UnsupportedOperationException("Método no implementado en este servidor.");
    }
}
