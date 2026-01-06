package core.rmi;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RmiClient {

    private final String host;
    private final int port;
    private final String name;

    private volatile IrrigationService service;

    public RmiClient(String host, int port, String name) throws Exception {
        this.host = host;
        this.port = port;
        this.name = name;
        connect(); // first connect
    }

    private synchronized void connect() throws Exception {
        Registry registry = LocateRegistry.getRegistry(host, port);
        this.service = (IrrigationService) registry.lookup(name);
        System.out.println("✅ Connected to RMI: " + name + " @ " + host + ":" + port);
    }

    /** Direct stub access (what your controller needs) */
    public IrrigationService getStub() throws Exception {
        // if something killed the connection, reconnect lazily
        if (service == null) connect();
        return service;
    }

    /**
     * Execute an RMI call with auto-reconnect and 1 retry.
     * Use this for safer calls inside controllers/services.
     */
    public <T> T call(RemoteCall<T> fn) throws Exception {
        try {
            return fn.run(getStub());
        } catch (RemoteException firstFailure) {
            System.out.println("⚠️ RMI call failed: " + firstFailure.getMessage());
            System.out.println("🔁 Reconnecting to RMI...");
            connect(); // reconnect once
            return fn.run(service); // retry once
        }
    }

    /** Backward-compatible name */
    public IrrigationService service() {
        return service;
    }

    @FunctionalInterface
    public interface RemoteCall<T> {
        T run(IrrigationService svc) throws Exception;
    }
}
