package com.omgcodes.bikelookup;

import java.util.Date;
import java.util.logging.Logger;

import com.amazonaws.services.s3.AmazonS3URI;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

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
            logger.info("Arguments: " + String.join(",", args));

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
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
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
