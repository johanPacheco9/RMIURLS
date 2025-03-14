package converturltopdf;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

public class Nodo {
     public static void main(String[] args) {
        try {
            convertion service = new convertion();
            LocateRegistry.createRegistry(7380);
            Naming.rebind("rmi://192.168.1.6:7380/ConversionService", service);
            System.out.println("convertion  node rmi is runing");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}



