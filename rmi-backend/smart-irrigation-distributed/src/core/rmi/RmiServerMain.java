package core.rmi;

import core.db.IrrigationDao;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;

public class RmiServerMain {

    public static void main(String[] args) throws Exception {

        // Same-machine testing
        System.setProperty("java.rmi.server.hostname", "127.0.0.1");

        int port = 1099;
        String name = "IrrigationService";

        Registry registry;
        try {
            registry = LocateRegistry.createRegistry(port);
            System.out.println("✅ Created new RMI registry on port " + port);
        } catch (ExportException alreadyRunning) {
            registry = LocateRegistry.getRegistry(port);
            System.out.println("ℹ️ RMI registry already running on port " + port + " (reusing it)");
        }

        IrrigationDao dao = new IrrigationDao();
        IrrigationService service = new IrrigationServiceImpl(dao);

        registry.rebind(name, service);

        System.out.println("✅ Bound name: " + name);
        System.out.println("Press Ctrl+C to stop.");
    }
}
