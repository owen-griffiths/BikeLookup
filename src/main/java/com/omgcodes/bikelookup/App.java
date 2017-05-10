package com.omgcodes.bikelookup;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.amazonaws.services.s3.AmazonS3URI;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.google.protobuf.TextFormat;
import com.sun.management.OperatingSystemMXBean;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * Hello world!
 *
 */
public class App 
{
    private static final Logger logger = Logger.getLogger(App.class.getName());

    public static void main( String[] args )
    {
        try {
            logger.info(String.format("Arguments: '%s'", String.join(",", args)));

            if (args[0].equals("client")) {
                logger.info("Running in client mode");
                runClient(args);
            } else {
                logger.info("Running in server mode");
                runSever(args);
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Start BikelookupService, and listen for requests. This blocks until application exit.
    private static void runSever(String[] args) throws IOException, InterruptedException {
        String sourceUrl = getArgument("source", args);
        AmazonS3URI source = new AmazonS3URI(sourceUrl);

        logger.info("Getting data from: " + source.toString());
        logger.info("Region: " + source.getRegion());
        logger.info("Bucket: " + source.getBucket());
        logger.info("Key   : " + source.getKey());

        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();
        builder.setRegion(source.getRegion());

        AmazonS3 s3Client = builder.build();
        logger.info("Created S3 client in region: " + s3Client.getRegion());

        S3Object object = s3Client.getObject(new GetObjectRequest(source.getBucket(), source.getKey()));
        S3ObjectInputStream objectData = object.getObjectContent();

        BikeLookupReply allBikeRacks = BikeLookupReply.parseFrom(objectData);
        logger.info("Loaded " + allBikeRacks.getBikeRackList().size() + " bike racks");

        // Process the objectData stream.
        objectData.close();

        Date timestamp = new Date(allBikeRacks.getTimestamp());
        logger.info("Timestamp of data: " + timestamp);

        int port = getIntArgument("port", args);

        BikeLookupService.runService(port, allBikeRacks.getBikeRackList());

        logger.info("Finished");
    }

    // Run the app in mode to run test queries against the another instance running as a server.
    private static void runClient(String[] args) throws InterruptedException, IOException {
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        String host = getArgument("host", args);
        int port = getIntArgument("port", args);
        int repeatCount = getIntArgument("repeat", args);
        String method = getArgument("method", args);
        BikeLookupRequest request = BikeLookupRequest.newBuilder()
                .setMinCapacity(5)
                .setSuburb("City")
                .build();

        if (method.equals("grpc")) {
            runGrpcClient(host, port, repeatCount, request, osBean);
        } else {
            runHttpClient(host, port, repeatCount, request, osBean);
        }
    }

    // Query the server using Http transport, with data contracts serialized to strings.
    private static void runHttpClient(
            String host, int port,
            int repeatCount,
            BikeLookupRequest request,
            OperatingSystemMXBean osBean) throws IOException {
        String url = String.format("http://%s:%d/lookup", host, port);
        logger.info(String.format("Running HTTP client against %s %,d times", url, repeatCount));

        long startTimeNs = System.nanoTime();
        long startCpuTime = osBean.getProcessCpuTime();
        int totalRackCount = 0;
        long firstCpuTime = -1;
        long finalCpuTime = -1;
        int responseSize = -1;

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            for (int i = 0; i < repeatCount; i++) {
                HttpPost post = new HttpPost(url);
                HttpEntity body = new StringEntity(TextFormat.printToString(request));
                post.setEntity(body);
                try (CloseableHttpResponse response = client.execute(post)) {
                    InputStream resultInputStream = response.getEntity().getContent();
                    Readable resultReader = new InputStreamReader(resultInputStream);
                    BikeLookupReply.Builder resultBuilder = BikeLookupReply.newBuilder();
                    TextFormat.getParser().merge(resultReader, resultBuilder);
                    BikeLookupReply result = resultBuilder.build();
                    //logger.info("Got result: " + result.toString());

                    totalRackCount += result.getBikeRackCount();
                    finalCpuTime = result.getTotalCpuTime();
                    if (firstCpuTime < 0) {
                        firstCpuTime = finalCpuTime;
                        responseSize = TextFormat.printToString(result).length();
                    }
                }
            }
        }

        long clientCpuTimeUsedNs = osBean.getProcessCpuTime() - startCpuTime;
        reportStats(repeatCount, startTimeNs, totalRackCount, firstCpuTime, finalCpuTime, responseSize, clientCpuTimeUsedNs);
    }

    private static void reportStats(
            int repeatCount,
            long startTimeNs,
            int totalRackCount,
            long firstCpuTime,
            long finalCpuTime,
            int responseSize,
            long clientCpuTimeUsedNs) {
        long takenNs = System.nanoTime() - startTimeNs;
        logger.info(String.format("%,d calls returned total %,d racks", repeatCount, totalRackCount));
        logger.info(String.format("Server CPU time %,d -> %,d [ns]", firstCpuTime, finalCpuTime));
        logger.info(String.format("Server CPU time used: %,d[ns]", finalCpuTime - firstCpuTime));
        logger.info(String.format("Total Elapsed Time: %,d[ns]", takenNs));
        logger.info(String.format("Total Client CPU time %,d[ns]", clientCpuTimeUsedNs));
        logger.info(String.format("Each Response size: %,d[b]", responseSize));
    }

    // Query the service using GRPC transport.
    private static void runGrpcClient(
            String host, int port,
            int repeatCount,
            BikeLookupRequest request,
            OperatingSystemMXBean osBean) throws InterruptedException {
        logger.info(String.format("Running GRPC client against %s:%d %,d times", host, port, repeatCount));
        long startTimeNs = System.nanoTime();
        long startCpuTime = osBean.getProcessCpuTime();
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext(true)
                .build();
        try {
            BikeLookupGrpc.BikeLookupBlockingStub stub = BikeLookupGrpc.newBlockingStub(channel);
            int totalRackCount = 0;
            long firstCpuTime = -1;
            long finalCpuTime = -1;
            int responseSize = -1;

            for (int i = 0; i < repeatCount; i++) {
                BikeLookupReply reply = stub.lookup(request);
                totalRackCount += reply.getBikeRackCount();
                finalCpuTime = reply.getTotalCpuTime();
                if (firstCpuTime < 0) {
                    firstCpuTime = finalCpuTime;
                    responseSize = reply.getSerializedSize();
                }
            }

            long clientCpuTimeUsedNs = osBean.getProcessCpuTime() - startCpuTime;
            reportStats(repeatCount, startTimeNs, totalRackCount, firstCpuTime, finalCpuTime, responseSize, clientCpuTimeUsedNs);
        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static String getArgument(String name, String[] args) {
        String flagValue = "-" + name;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(flagValue)) {
                return args[i+1];
            }
        }

        throw new IllegalArgumentException("Missing argument: " + name);
    }

    private static int getIntArgument(String name, String[] args) {
        String flagValue = "-" + name;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(flagValue)) {
                return Integer.parseInt(args[i+1]);
            }
        }

        throw new IllegalArgumentException("Missing argument: " + name);
    }

}
