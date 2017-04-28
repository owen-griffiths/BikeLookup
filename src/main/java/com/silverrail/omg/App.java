package com.silverrail.omg;

import com.amazonaws.services.s3.AmazonS3URI;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

import com.silverrail.omg.bikelookup.BikeLookupReply;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        try {
			AmazonS3URI source = new AmazonS3URI("http://s3-ap-southeast-2.amazonaws.com/experimental-syd/bikeRacks.pb");
			
			System.out.println("Getting data from: " + source.toString());
			System.out.println("Region: " + source.getRegion());
			System.out.println("Bucket: " + source.getBucket());
			System.out.println("Key   : " + source.getKey());

			AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();
			builder.setRegion(source.getRegion());
			AmazonS3 s3Client = builder.build();
			System.out.println("Created S3 client in region: " + s3Client.getRegion());
			
			S3Object object = s3Client.getObject(new GetObjectRequest(source.getBucket(), source.getKey()));
			S3ObjectInputStream objectData = object.getObjectContent();
			
			BikeLookupReply allBikeRacks = BikeLookupReply.parseFrom(objectData);
			System.out.println("Loaded " + allBikeRacks.getBikeRackList().size() + " bike racks");
			
			// Process the objectData stream.
			objectData.close();
			
            //BikeLookupService.runService();

            System.out.println("Finished");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
