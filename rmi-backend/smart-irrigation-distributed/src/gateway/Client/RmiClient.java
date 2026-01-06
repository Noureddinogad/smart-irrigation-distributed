package gateway.Client;

import core.rmi.IrrigationService;

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

    /**
     * Execute an RMI call with auto-reconnect and 1 retry.
     */
    public <T> T call(RemoteCall<T> fn) throws Exception {
        try {
            return fn.run(service);
        } catch (RemoteException firstFailure) {
            System.out.println("⚠️ RMI call failed: " + firstFailure.getMessage());
            System.out.println("🔁 Reconnecting to RMI...");

            connect(); // reconnect once

            // retry once after reconnect
            return fn.run(service);
        }
    }

    /**
     * Use this only if you know what you're doing.
     * Prefer call(...) above.
     */
    public IrrigationService service() {
        return service;
    }

    @FunctionalInterface
    public interface RemoteCall<T> {
        T run(IrrigationService svc) throws Exception;
    }
}
