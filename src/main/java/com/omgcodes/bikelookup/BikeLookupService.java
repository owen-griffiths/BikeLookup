package com.omgcodes.bikelookup;

import com.google.protobuf.TextFormat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetSocketAddress;
import java.util.logging.Logger;
import java.util.List;

/**
 * Server that manages startup/shutdown of a {@code Greeter} server.
 */
public class BikeLookupService {
    private static final Logger logger = Logger.getLogger(BikeLookupService.class.getName());

    private Server server;

    private void start(int port, List<BikeRack> bikeRacks) throws IOException {
        logger.info("Attempting to listen on port: " + port);

        BikeLookupImpl impl = new BikeLookupImpl(bikeRacks);

        server = ServerBuilder.forPort(port)
                .addService(impl)
                .build()
                .start();
        logger.info("GRPC Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                BikeLookupService.this.stop();
                System.err.println("*** server shut down");
            }
        });

        startHttp(port + 1, impl);
    }

    private void startHttp(int port, BikeLookupImpl impl) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 10);
        server.createContext("/lookup", new BikeHttpHandler(impl));
        server.start();
        logger.info(String.format("Listening for HTTP. Port %d", port));

        BikeLookupRequest sample = BikeLookupRequest.newBuilder().setSuburb("City").setMinCapacity(5).build();
        String reqStr = TextFormat.printToString(sample);
        logger.info("Sample request string: " + reqStr);
    }

    private static class BikeHttpHandler implements HttpHandler {
        public BikeHttpHandler(BikeLookupImpl impl) {
            impl_ = impl;
        }

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            InputStream reqStream = httpExchange.getRequestBody();
            Readable reqReader = new InputStreamReader(reqStream);
            BikeLookupRequest.Builder reqBuilder = BikeLookupRequest.newBuilder();
            TextFormat.getParser().merge(reqReader, reqBuilder);
            reqStream.close();
            BikeLookupRequest req = reqBuilder.build();

            //logger.info("Got HTTP request: " + req.toString());
            BikeLookupReply result = impl_.doLookup(req);

            String response = TextFormat.printToString(result);
            httpExchange.sendResponseHeaders(200, response.length());
            OutputStream os = httpExchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        private BikeLookupImpl impl_;
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main launches the server from the command line.
     */
    public static void runService(int port, List<BikeRack> bikeRacks) throws IOException, InterruptedException {
        final BikeLookupService server = new BikeLookupService();
        server.start(port, bikeRacks);
        server.blockUntilShutdown();
    }

    static class BikeLookupImpl extends BikeLookupGrpc.BikeLookupImplBase {
        public BikeLookupImpl(List<BikeRack> bikeRacks) {
            logger.info("BikeLookupImpl created with " + bikeRacks.size() + " racks");
            bikeRacks_ = bikeRacks;
            osBean_ = ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
            logger.info(String.format("OS Arch '%s' with %d CPUs", osBean_.getArch(), osBean_.getAvailableProcessors()));
        }

        @Override
        public void lookup(BikeLookupRequest req, StreamObserver<BikeLookupReply> responseObserver) {
            BikeLookupReply result = doLookup(req);
            responseObserver.onNext(result);
            responseObserver.onCompleted();
        }

        public BikeLookupReply doLookup(BikeLookupRequest req) {
            BikeLookupReply.Builder replyBuilder = BikeLookupReply
                    .newBuilder()
                    .setTimestamp(System.currentTimeMillis())
                    .setTotalCpuTime(osBean_.getProcessCpuTime());

            for (BikeRack rack : bikeRacks_) {
                if (rack.getSuburb().equals(req.getSuburb()) && (rack.getCapacity() >= req.getMinCapacity())) {
                    replyBuilder.addBikeRack(rack);
                }
            }

            return replyBuilder.build();
        }

        private List<BikeRack> bikeRacks_;
        com.sun.management.OperatingSystemMXBean osBean_;
    }
}
