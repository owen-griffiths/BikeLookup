package com.silverrail.omg;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.silverrail.omg.bikelookup.BikeLookupGrpc;
import com.silverrail.omg.bikelookup.BikeLookupRequest;
import com.silverrail.omg.bikelookup.BikeLookupReply;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Server that manages startup/shutdown of a {@code Greeter} server.
 */
public class BikeLookupService {
    private static final Logger logger = Logger.getLogger(BikeLookupService.class.getName());

    private Server server;

    private void start() throws IOException {
    /* The port on which the server should run */
        int port = 50052;
        server = ServerBuilder.forPort(port)
                .addService(new BikeLookupImpl())
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
    public static void runService() throws IOException, InterruptedException {
        final BikeLookupService server = new BikeLookupService();
        server.start();
        server.blockUntilShutdown();
    }

    static class BikeLookupImpl extends BikeLookupGrpc.BikeLookupImplBase {

        @Override
        public void lookup(BikeLookupRequest req, StreamObserver<BikeLookupReply> responseObserver) {
            BikeLookupReply reply = BikeLookupReply
                    .newBuilder()
                    .setTimestamp(System.currentTimeMillis())
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }
}
