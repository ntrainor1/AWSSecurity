package com.dish.anywhere.aws.ec2.read;

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

import com.dish.anywhere.aws.util.AwsPropertiesConfig;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.Volume;

/**
 * List all EBS Volumes and their encryption
 */
public class ListEbsVolumes {
	private static Logger log = Logger.getLogger(ListEbsVolumes.class);
	private static String format = "%s|%s|%s|%s|%s|%s\r\n";
	private static final String header = "Volume|State|Encryption|Instance Id|Instance Name|Instance Project\r\n";
	private static final String name = "Name";
	private static final String project = "Project";
	
	public static void main(String[] args) {
		log.info("Started ListEbsVolumes process");
		String outputFileName = ".\\output\\ListEbsVolumesOutput-"
				+ new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss").format(new Date()) + ".csv";
		print(outputFileName, header, null);
		
		Region region = Region.US_EAST_1;
		Ec2Client ec2Client = Ec2Client.builder()
				.credentialsProvider(ProfileCredentialsProvider.create(AwsPropertiesConfig.AWS_WRITE_PROFILE))
				.region(region).build();
		List<Volume> volumes = ec2Client.describeVolumes().volumes();
		for (Volume volume : volumes) {
			describeVolumes(outputFileName, volume, ec2Client);
		}
		
		log.info("Finished ListEbsVolumes process");
	}

	/***
	 * Format the content before writing
	 * 
	 * @param outputFileName
	 * @param ec2Client 
	 * @param policy
	 * @param permissions
	 * @param policyRoles
	 * @param policyUsers
	 */
	private static void describeVolumes(String outputFileName, Volume volume, Ec2Client ec2Client) {
		String output = "";
		String volumeId = volume.volumeId();
		String state = volume.stateAsString();
		String encrypted = volume.encrypted() ? "Y" : "N";
		String instanceId = null;
		String instanceName = null;
		String instanceProject = null;

		if (volume.hasAttachments() && volume.attachments().size() >= 1) {
			instanceId = volume.attachments().get(0).instanceId();
			Filter filter = Filter.builder().name("instance-id").values(instanceId).build();
			Filter[] filters = { filter };
			DescribeInstancesRequest diRequest = DescribeInstancesRequest.builder().filters(filters).build();
			DescribeInstancesResponse diResponse = ec2Client.describeInstances(diRequest);
			List<Tag> instanceTags = diResponse.reservations().get(0).instances().get(0).tags();
			Map<String, String> instanceTagsMap = getTagsMap(instanceTags);
			instanceName = instanceTagsMap.get(name);
			instanceProject = instanceTagsMap.get(project);
		}
		output = String.format(format, volumeId, state, encrypted, instanceId, instanceName, instanceProject);
		print(outputFileName, output, StandardOpenOption.APPEND);
	}

	/***
	 * Write the content to the file
	 * 
	 * @param fileName
	 * @param content
	 * @param standardOpenOption
	 * @catches IOException
	 */
	private static void print(String fileName, String content, StandardOpenOption standardOpenOption) {
		try {
			if (standardOpenOption != null) {
				Files.write(Paths.get(fileName), content.getBytes(), StandardOpenOption.APPEND);
			} else {
				Files.write(Paths.get(fileName), content.getBytes());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/***
	 * returns a Map of the tag key values
	 * 
	 * @param tags
	 * @return
	 */
	private static Map<String, String> getTagsMap(List<Tag> tags) {
		Map<String, String> tagsMap = new HashMap<String, String>();
		if (tags != null && !tags.isEmpty()) {
			for (Tag tag : tags) {
				tagsMap.put(tag.key(), tag.value());
			}
		}
		return tagsMap;
	}
}
