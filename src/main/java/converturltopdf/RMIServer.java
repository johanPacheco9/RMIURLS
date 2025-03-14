package converturltopdf;

import java.io.FileOutputStream;
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

    public RMIServer() throws RemoteException {
        super();
        executor = Executors.newFixedThreadPool(5);
        nodosActivos = obtenerNodosDisponibles();
    }

    @Override
    public List<byte[]> ejecutarConversion(List<String> urls) throws RemoteException {
        List<byte[]> pdfFiles = new ArrayList<>();
        List<Future<List<byte[]>>> futures = new ArrayList<>();

        if (nodosActivos.isEmpty()) {
            throw new RemoteException("No hay nodos activos disponibles.");
        }

        int numNodos = nodosActivos.size();
        int index = 0;

        for (String url : urls) {
            String nodoSeleccionado = nodosActivos.get(index % numNodos);
            index++;

            futures.add(executor.submit(() -> {
                List<byte[]> result = new ArrayList<>();
                try {
                    IConvertion nodeService = (IConvertion) Naming.lookup(nodoSeleccionado);
                    List<byte[]> pdfDataList = nodeService.convertToPDF(Collections.singletonList(url));

                    if (pdfDataList != null && !pdfDataList.isEmpty()) {
                        for (byte[] pdfData : pdfDataList) {
                            String fileName = "D:/converted_" + sanitizeFileName(url) + ".pdf";
                            try (FileOutputStream fos = new FileOutputStream(fileName)) {
                                fos.write(pdfData);
                                System.out.println("PDF guardado: " + fileName);
                            }

                            try (Connection connection = DriverManager.getConnection(
                                    "jdbc:mysql://localhost:3306/convertpdfs", "root", "1098825894")) {
                                int nodeId = getOrInsertNode(connection, nodoSeleccionado);
                                int conversionId = registrarConversion(connection, fileName, fileName, nodeId);
                                long elapsedTime = System.currentTimeMillis();
                                registrarLog(connection, conversionId, elapsedTime);
                            }
                            result.add(pdfData);
                        }
                    } else {
                        System.out.println("La conversión de " + url + " falló en " + nodoSeleccionado);
                    }
                } catch (Exception e) {
                    System.out.println("Error procesando URL: " + url + " en nodo: " + nodoSeleccionado);
                    e.printStackTrace();
                }
                return result;
            }));
        }

        // Esperar a que todas las tareas terminen
        for (Future<List<byte[]>> future : futures) {
            try {
                pdfFiles.addAll(future.get());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Todas las conversiones han finalizado.");
        return pdfFiles;
    }

    private List<String> obtenerNodosDisponibles() {
        List<String> nodos = Arrays.asList(
                "rmi://192.168.1.6:7380/ConversionService",
                "rmi://192.168.1.7:7380/ConversionService",
                "rmi://192.168.1.8:7380/ConversionService"
        );

        List<String> nodosActivos = new ArrayList<>();
        for (String nodo : nodos) {
            try {
                IConvertion nodeService = (IConvertion) Naming.lookup(nodo);
                nodeService.convertToPDF(Collections.singletonList("test_url")); // Prueba de conexión
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
    public List<byte[]> convertToPDF(List<String> urls) throws RemoteException {
        throw new UnsupportedOperationException("Método no implementado en este servidor.");
    }
}
