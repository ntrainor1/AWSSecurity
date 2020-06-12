package com.aws.ec2.delete;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.Logger;

import com.aws.util.ApplicationConstants;
import com.aws.util.AwsPropertiesConfig;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.autoscaling.model.DescribeLoadBalancersRequest;
import software.amazon.awssdk.services.autoscaling.model.DescribeLoadBalancersResponse;
import software.amazon.awssdk.services.autoscaling.model.LoadBalancerState;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DeleteSecurityGroupRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsResponse;
import software.amazon.awssdk.services.ec2.model.Filter;

/**
 * Deletes EC2 security groups
 */
public class DeleteSecurityGroups {
	private static Logger log = Logger.getLogger(DeleteSecurityGroups.class);
	private static String format = "%s|%s|%s|%s\r\n";
	private static final String header = "SecurityGroupName|SecurityGroupId|SecurityGroupStatus|SecurityGroupMessage\r\n";

	public static void main(String[] args) {
		log.info("Started Delete SecurityGroups process");
		String inputFileName = ".\\input\\DeleteSecurityGroups.csv";
		String outputFileName = ".\\output\\DeleteSecurityGroups-"
				+ new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss").format(new Date()) + ".csv";
		print(outputFileName, header, null);
		try {
			BufferedReader csvReader = new BufferedReader(new FileReader(inputFileName));
			String row = null;
			Ec2Client ec2Client = Ec2Client.builder()
					.credentialsProvider(ProfileCredentialsProvider.create(AwsPropertiesConfig.AWS_WRITE_PROFILE))
					.region(Region.US_EAST_1).build();
			while ((row = csvReader.readLine()) != null) {
				String[] data = row.split(ApplicationConstants.COMMA);
				if (data != null) {
					String securityGroupId = data[0];
					deleteSecurityGroup(securityGroupId, outputFileName, ec2Client);
				}
			}
			csvReader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		log.info("Completed Delete SecurityGroups process");
	}

	/***
	 * write the content to the file
	 * 
	 * @param fileName
	 * @param content
	 * @param standardOpenOption
	 * @throws IOException
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

	private static void deleteSecurityGroup(String securityGroupId, String outputFileName, Ec2Client ec2Client)
			throws IOException {
		String status = ApplicationConstants.RETRY;
		String message = ApplicationConstants.RETRY_MESSAGE;
		String securityGroupName = "";
		if (ec2Client != null) {
			try {
				DescribeSecurityGroupsRequest sgRequest = DescribeSecurityGroupsRequest.builder().groupIds(securityGroupId)
						.build();
				DescribeSecurityGroupsResponse sgResponse = ec2Client.describeSecurityGroups(sgRequest);
				securityGroupName = sgResponse.securityGroups().get(0).groupName();

				if (!validateSecurityGroupInUse(securityGroupId, securityGroupName, outputFileName, ec2Client)
						&& !validateSecurityGroupNotDefault(securityGroupId, securityGroupName, outputFileName, ec2Client)) {
					DeleteSecurityGroupRequest request = DeleteSecurityGroupRequest.builder().groupId(securityGroupId)
							.build();
					ec2Client.deleteSecurityGroup(request);
					status = ApplicationConstants.COMPLETED;
					message = ApplicationConstants.SUCCESS_MESSAGE;
					String output = String.format(format, securityGroupName, securityGroupId, status, message);
					print(outputFileName, output, StandardOpenOption.APPEND);
				}
			} catch (Exception e) {
				log.info(String.format("Exception SecurityGroupId : %s | Message : %s", securityGroupId, e.getMessage()));
				status = ApplicationConstants.ERROR;
				message = e.getMessage().replace(",", " & ");
				String output = String.format(format, securityGroupName, securityGroupId, status, message);
				print(outputFileName, output, StandardOpenOption.APPEND);
			}
		}
		log.info(String.format("SecurityGroupId : %s | Status : %s", securityGroupId, status));
	}

	private static boolean validateSecurityGroupInUse(String securityGroupId, String securityGroupName, String outputFileName, Ec2Client ec2Client) {
		boolean inUse = false;
		Filter filter = Filter.builder().name("group-id").values(securityGroupId).build();
		Filter[] filters = { filter };
		String status = "";
		String message = "";
		DescribeInstancesRequest insRequest = DescribeInstancesRequest.builder().filters(filters).build();
		DescribeInstancesResponse insResponse = ec2Client.describeInstances(insRequest);
		if (insResponse != null && insResponse.hasReservations() && !insResponse.reservations().isEmpty()
				&& insResponse.reservations().get(0).hasInstances()) {
			inUse = true;
			status = ApplicationConstants.SKIPPED;
			message = message + "Please remove attached instances. Used by ";
			for (int i = 0; i < insResponse.reservations().get(0).instances().size(); i++) {
				message = message + insResponse.reservations().get(0).instances().get(i).instanceId() + "&";
			}
		}
		DescribeNetworkInterfacesRequest intfRequest = DescribeNetworkInterfacesRequest.builder().filters(filters)
				.build();
		DescribeNetworkInterfacesResponse intfResponse = ec2Client.describeNetworkInterfaces(intfRequest);
		if (intfResponse != null && intfResponse.networkInterfaces() != null
				&& !intfResponse.networkInterfaces().isEmpty()) {
			inUse = true;
			status = ApplicationConstants.SKIPPED;
			message = message + "Please remove attached network interfaces. Used by ";
			for (int i = 0; i < intfResponse.networkInterfaces().size(); i++) {
				message = message + intfResponse.networkInterfaces().get(i).networkInterfaceId() + "&";
			}
		}
		if (inUse) {
			String output = String.format(format, securityGroupName, securityGroupId, status, message);
			print(outputFileName, output, StandardOpenOption.APPEND);
		}
		return inUse;
	}

	private static boolean validateSecurityGroupNotDefault(String securityGroupId, String securityGroupName, String outputFileName, Ec2Client ec2Client) {
		boolean defaultSg = false;
		String status = "";
		String message = "";
		DescribeSecurityGroupsRequest sgRequest = DescribeSecurityGroupsRequest.builder().groupIds(securityGroupId)
				.build();
		DescribeSecurityGroupsResponse sgResponse = ec2Client.describeSecurityGroups(sgRequest);
		if (sgResponse != null && sgResponse.hasSecurityGroups() && !sgResponse.securityGroups().isEmpty()
				&& sgResponse.securityGroups().get(0).groupName().equals("default")
				&& sgResponse.securityGroups().get(0).description().equals("default VPC security group")) {
			defaultSg = true;
		}
		if (defaultSg) {
			status = ApplicationConstants.DEFAULT;
			message = "This security group is a default security group. You must delete the VPC in order to delete this group.";
			String output = String.format(format, securityGroupName, securityGroupId, status, message);
			print(outputFileName, output, StandardOpenOption.APPEND);
		}
		return defaultSg;
	}

}
