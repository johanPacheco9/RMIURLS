package converturltopdf;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IConvertion extends Remote {
    //server
    List<PDFResult> ejecutarConversion(List<String> urls) throws RemoteException;
    //node
    List<PDFResult> convertToPDF(List<String> urls) throws RemoteException;
    boolean isNodeAvailable() throws RemoteException;
}