package converturltopdf;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IConvertion extends Remote {
    
    List<PDFResult> ejecutarConversion(List<String> urls) throws RemoteException;
}