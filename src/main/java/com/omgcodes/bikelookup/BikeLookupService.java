package com.omgcodes.bikelookup;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.List;

/**
 * Server that manages startup/shutdown of a {@code Greeter} server.
 */
public class BikeLookupService {
    private static final Logger logger = Logger.getLogger(BikeLookupService.class.getName());

    private Server server;

    private void start(int port, List<BikeRack> bikeRacks) throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new BikeLookupImpl(bikeRacks))
                .build()
                .start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                BikeLookupService.this.stop();
                System.err.println("*** server shut down");
            }
        });
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
        }

        @Override
        public void lookup(BikeLookupRequest req, StreamObserver<BikeLookupReply> responseObserver) {
            logger.info("BikeLookup for " + req.getSuburb());

            BikeLookupReply.Builder replyBuilder = BikeLookupReply
                .newBuilder()
                .setTimestamp(System.currentTimeMillis());

            for (BikeRack rack : bikeRacks_) {
                if (rack.getSuburb().equals(req.getSuburb()) && (rack.getCapacity() >= req.getMinCapacity())) {
                    replyBuilder.addBikeRack(rack);
                }
            }

            responseObserver.onNext(replyBuilder.build());
            responseObserver.onCompleted();
        }

        private List<BikeRack> bikeRacks_;
    }
}
