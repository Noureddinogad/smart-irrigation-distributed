package gateway;

import gateway.Client.RmiClient;
import gateway.http.Esp32HttpServer;

public class Main {
    public static void main(String[] args) {
        try {
            int httpPort = 8080;

            // SAME AS YOUR RmiTestClient
            String rmiHost = "127.0.0.1";
            int rmiPort = 1099;
            String rmiName = "IrrigationService";

            RmiClient rmiClient = new RmiClient(rmiHost, rmiPort, rmiName);

            Esp32HttpServer.setRmiClient(rmiClient);
            Esp32HttpServer.start(httpPort);

            System.out.println("✅ Gateway started with RMI");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
