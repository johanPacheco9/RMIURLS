package converturltopdf;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IConvertion extends Remote {
    //nodo
    List<byte[]> convertToPDF(List<String> urls) throws RemoteException;
    
    boolean isNodeAvailable() throws RemoteException;
    //servidor
    List<byte[]> ejecutarConversion(List<String> urls) throws RemoteException;
}