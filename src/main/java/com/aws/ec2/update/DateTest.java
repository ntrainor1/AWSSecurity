package com.dish.anywhere.aws.ec2.update;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;

import com.dish.anywhere.aws.util.ApplicationConstants;
import com.dish.anywhere.aws.util.AwsPropertiesConfig;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.AttachVolumeRequest;
import software.amazon.awssdk.services.ec2.model.CreateSnapshotRequest;
import software.amazon.awssdk.services.ec2.model.CreateVolumeRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeSnapshotsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVolumesRequest;
import software.amazon.awssdk.services.ec2.model.DetachVolumeRequest;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Snapshot;
import software.amazon.awssdk.services.ec2.model.StartInstancesRequest;
import software.amazon.awssdk.services.ec2.model.StopInstancesRequest;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.Volume;
import software.amazon.awssdk.services.ec2.model.VolumeAttachment;
import software.amazon.awssdk.services.ec2.model.VolumeType;

/**
 * Takes a snapshot of an EBS volume, encrypts and reattaches the volume
 */
public class DateTest {
	private static Logger log = Logger.getLogger(DateTest.class);
	private static String format = "%s|%s|%s|%s|%s|%s|%s|%s|%s|%s\r\n";
	private static final String header = "Original Volume|Encrypted Volume|Instance Id|Instance Name|Instance Project|Status|Start Time|End Time|Original Size|Encrypted Size\r\n";

	public static void main(String[] args) throws InterruptedException {
		log.info("Started EncryptEbsVolumes process");
		
		DateTime startTime = new DateTime().now();
		
		Thread.sleep(5000);

		DateTime endTime = new DateTime().now();
		
		
		System.out.println();
					

		log.info("Finished DateTest process");
	}
}
