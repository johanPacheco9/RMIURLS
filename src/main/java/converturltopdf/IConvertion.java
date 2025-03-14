package converturltopdf;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IConvertion extends Remote {
    //interfaces nodo
    List<byte[]> convertToPDF(List<String> urls) throws RemoteException;
    boolean isNodeAvailable() throws RemoteException;
    //interface servidor
}