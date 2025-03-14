package converturltopdf;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IConvertion extends Remote {
    //node
    List<PDFResult> convertToPDF(List<String> urls) throws RemoteException;
    boolean isNodeAvailable() throws RemoteException;
}