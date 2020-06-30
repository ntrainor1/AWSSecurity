package com.dish.anywhere.aws.ec2.read;

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

import com.dish.anywhere.aws.util.ApplicationConstants;
import com.dish.anywhere.aws.util.AwsPropertiesConfig;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsResponse;
import software.amazon.awssdk.services.ec2.model.DescribeVpcsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVpcsResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.IpRange;
import software.amazon.awssdk.services.ec2.model.SecurityGroup;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.Vpc;

/**
 * Deletes EC2 security groups
 */
public class DescribeSecurityGroups {
	private static Logger log = Logger.getLogger(DescribeSecurityGroups.class);
	private static String format = "%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s\r\n";
	private static final String header = "Project|SecurityGroupName|SecurityGroupId|SecurityGroupRemediationAction|"
			+ "Instances|NetworkInterfaces|Used|VpcName|VpcId|SecurityGroupType|SecurityGroupDescription|"
			+ "SecurityGroupRuleProtocol|SecurityGroupRulePortRange|SecurityGroupRuleSource|VpcInstances|VpcNetworkInterfaces|VpcUsed\r\n";
	private static final String name = "Name";

	public static void main(String[] args) {
		log.info("Started DescribeSecurityGroups process");
		String inputFileName = ".\\input\\DescribeSecurityGroups.csv";
		String outputFileName = ".\\output\\DescribeSecurityGroups-"
				+ new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss").format(new Date()) + ".csv";
		print(outputFileName, header, null);

		try {
			Ec2Client ec2Client = Ec2Client.builder()
					.credentialsProvider(ProfileCredentialsProvider.create(AwsPropertiesConfig.AWS_READ_PROFILE))
					.region(Region.US_EAST_1).build();
			BufferedReader csvReader = new BufferedReader(new FileReader(inputFileName));
			String row = null;
			Map<String, Vpc> allVpcs = getAllVpcs(ec2Client);
			while ((row = csvReader.readLine()) != null) {
				String[] data = row.split(ApplicationConstants.COMMA);
				if (data != null) {
					String securityGroupId = data[0];
					String securityGroupAction = data[1];
					describeSecurityGroups(allVpcs, outputFileName, securityGroupId, securityGroupAction, ec2Client);
				}
			}
			csvReader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		log.info("Completed DescribeSecurityGroups process");
	}

	private static void describeSecurityGroups(Map<String, Vpc> allVpcs, String fileName, String securityGroupId,
			String securityGroupAction, Ec2Client ec2Client) throws IOException {
		try {
			if (ec2Client != null) {
				DescribeSecurityGroupsRequest sgRequest = DescribeSecurityGroupsRequest.builder()
						.groupIds(securityGroupId).build();
				DescribeSecurityGroupsResponse sgResponse = ec2Client.describeSecurityGroups(sgRequest);
				if (sgResponse != null && sgResponse.securityGroups() != null) {
					String output = ApplicationConstants.EMPTY_STRING;
					log.debug("Processing unprocessed SecurityGroupIds");
					for (SecurityGroup group : sgResponse.securityGroups()) {
						log.debug("Processing unprocessed SecurityGroup : " + group.groupId());
						Vpc vpc = allVpcs.get(group.vpcId());
						Map<String, String> vpcTagsMap = getTagsMap(vpc != null ? vpc.tags() : null);
						Map<String, String> sgTags = getTagsMap(vpc != null ? group.tags() : null);
						String sgType = group.vpcId() == null ? "EC2-Classic" : "EC2-VPC";
						String inUseVpcNetworkInterface = "";
						String inUseVpcInstance = "";
						String inUseVpc = "";
						if (sgType == "EC2-VPC") {
								inUseVpcNetworkInterface = checkIfVpcUseNetworkInterface(vpc, ec2Client) ? "Y": "N";
								inUseVpcInstance = checkIfVpcUseInstance(vpc, ec2Client) ? "Y": "N";
								inUseVpc = inUseVpcInstance == "Y" ? "Y": inUseVpcNetworkInterface == "Y" ? "Y" : "N";
						}
						
						String inUseInstance = securityGroupInUseInstance(securityGroupId, ec2Client) ? "Y": "N";
						String inUseNetworkInterface = securityGroupInUseNetworkInterface(securityGroupId, ec2Client) ? "Y": "N";
						String inUse = inUseInstance == "Y" ? "Y": inUseNetworkInterface == "Y" ? "Y" : "N";
						
						
						if (group.ipPermissions() == null || group.ipPermissions().isEmpty()) {
							output = String.format(format,
									sgTags.getOrDefault(ApplicationConstants.PROJECT,
											vpcTagsMap.getOrDefault(ApplicationConstants.PROJECT,
													ApplicationConstants.EMPTY_STRING)),
									group.groupName().replace(",", " & "), group.groupId(), securityGroupAction, inUseInstance, inUseNetworkInterface,
									inUse, vpcTagsMap.get(name), group.vpcId(), sgType, group.description().replace(",", " & "),
									ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
									ApplicationConstants.EMPTY_STRING, inUseVpcInstance, inUseVpcNetworkInterface, inUseVpc);
							print(fileName, output, StandardOpenOption.APPEND);
						} else {
							processOnlySecurityGroupsAndIpPermission(group, vpcTagsMap, sgTags, fileName, securityGroupAction, inUseInstance, inUseNetworkInterface,
									inUse, sgType, inUseVpcInstance, inUseVpcNetworkInterface, inUseVpc);
						}
					}
				}
			}
		} catch (Exception e) {
			log.info(String.format("Exception : Message : %s", e.getMessage()));
		}
	}

	/***
	 * process security groups and determine if network interface attached
	 * 
	 * @param securityGroupId
	 * @param ec2Client
	 */
	private static boolean securityGroupInUseNetworkInterface(String securityGroupId, Ec2Client ec2Client) {
		Filter filter = Filter.builder().name("group-id").values(securityGroupId).build();
		Filter[] filters = { filter };
		DescribeNetworkInterfacesRequest intfRequest = DescribeNetworkInterfacesRequest.builder().filters(filters)
				.build();
		DescribeNetworkInterfacesResponse intfResponse = ec2Client.describeNetworkInterfaces(intfRequest);
		if (intfResponse != null && intfResponse.networkInterfaces() != null
				&& !intfResponse.networkInterfaces().isEmpty()) {
			return true;
		}
		return false;
	}

	/***
	 * process security groups and determine if EC2 instance attached
	 * 
	 * @param securityGroupId
	 * @param ec2Client
	 */
	private static boolean securityGroupInUseInstance(String securityGroupId, Ec2Client ec2Client) {
		Filter filter = Filter.builder().name("group-id").values(securityGroupId).build();
		Filter[] filters = { filter };
		DescribeInstancesRequest insRequest = DescribeInstancesRequest.builder().filters(filters).build();
		DescribeInstancesResponse insResponse = ec2Client.describeInstances(insRequest);
		if (insResponse != null && insResponse.reservations() != null && !insResponse.reservations().isEmpty()
				&& insResponse.reservations().get(0).instances() != null
				&& !insResponse.reservations().get(0).instances().isEmpty()) {
			return true;
		}
		return false;
	}

	/***
	 * process security groups (not attached to EC2 instances) and IpPermissions to
	 * write their metadata to file
	 * 
	 * @param group
	 * @param vpcTagsMap
	 * @param fileName
	 * @param sgType
	 * @param sgType 
	 * @param inUse 
	 * @param inUseNetworkInterface 
	 * @param inUseInstance 
	 * @throws IOException
	 */
	private static void processOnlySecurityGroupsAndIpPermission(SecurityGroup group, Map<String, String> vpcTagsMap,
			Map<String, String> sgTags, String fileName, String securityGroupAction, String inUseInstance, String inUseNetworkInterface, String inUse, String sgType,
			String inUseVpcInstance, String inUseVpcNetworkInterface, String inUseVpc) throws IOException {
		log.debug(
				"Processing unprocessed Security Group and IpPermissions - Group being processed : " + group.groupId());
		String output = ApplicationConstants.EMPTY_STRING;
		for (IpPermission ipPermission : group.ipPermissions()) {
			String securityGroupRuleProtocol = ipPermission.ipProtocol();
			String securityGroupRulePortRange = ApplicationConstants.EMPTY_STRING;
			if ((ipPermission.fromPort() == null && ipPermission.toPort() == null
					&& "-1".equals(ipPermission.ipProtocol()))) {
				// log.info("port is null");
				securityGroupRuleProtocol = ApplicationConstants.ALL;
				securityGroupRulePortRange = ApplicationConstants.ALL;
			} else if (ipPermission.fromPort().intValue() == ipPermission.toPort().intValue()
					&& ipPermission.fromPort().intValue() == -1) {
				securityGroupRulePortRange = "NA";
			} else if (ipPermission.fromPort().intValue() == ipPermission.toPort().intValue()) {
				securityGroupRulePortRange = ipPermission.fromPort().toString();
			} else {
				securityGroupRulePortRange = ipPermission.fromPort().toString() + "-"
						+ ipPermission.toPort().toString();
			}

			if (ipPermission.ipRanges() == null || ipPermission.ipRanges().isEmpty()) {
				output = String.format(format,
						sgTags.getOrDefault(ApplicationConstants.PROJECT,
								vpcTagsMap.getOrDefault(ApplicationConstants.PROJECT,
										ApplicationConstants.EMPTY_STRING)),
						group.groupName().replace(",", " & "), group.groupId(), securityGroupAction, inUseInstance, inUseNetworkInterface,
						inUse, vpcTagsMap.get(name), group.vpcId(), sgType, group.description().replace(",", " & "),
						securityGroupRuleProtocol, securityGroupRulePortRange, ApplicationConstants.EMPTY_STRING, inUseVpcInstance, inUseVpcNetworkInterface, inUseVpc);
				print(fileName, output, StandardOpenOption.APPEND);
			} else {
				StringBuilder sb = new StringBuilder();
				for (IpRange range : ipPermission.ipRanges()) {
					sb.append("&").append(range.cidrIp());
				}
				output = String.format(format,
						sgTags.getOrDefault(ApplicationConstants.PROJECT,
								vpcTagsMap.getOrDefault(ApplicationConstants.PROJECT,
										ApplicationConstants.EMPTY_STRING)),
						group.groupName().replace(",", " & "), group.groupId(), securityGroupAction, inUseInstance, inUseNetworkInterface,
						inUse, vpcTagsMap.get(name), group.vpcId(), sgType, group.description().replace(",", " & "),
						securityGroupRuleProtocol, securityGroupRulePortRange, sb.toString(), inUseVpcInstance, inUseVpcNetworkInterface, inUseVpc);
				print(fileName, output, StandardOpenOption.APPEND);

			}
		}
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

	/***
	 * get all the VPCs present in the account
	 * 
	 * @param eEc2Client
	 * @return
	 */
	private static Map<String, Vpc> getAllVpcs(Ec2Client eEc2Client) {
		Map<String, Vpc> allAllVpcs = new HashMap<String, Vpc>();
		try {
			DescribeVpcsRequest request = DescribeVpcsRequest.builder().build();
			DescribeVpcsResponse response = eEc2Client.describeVpcs(request);
			if (response != null && response.vpcs() != null) {
				for (Vpc vpc : response.vpcs()) {
					allAllVpcs.put(vpc.vpcId(), vpc);
				}
			}
		} catch (Exception e) {
			log.error(e);
		}
		log.info("All VPCs - Count : " + allAllVpcs.size());
		return allAllVpcs;
	}
	
	/***
	 * Determine if the Vpc has a network interface attached.
	 * 
	 * @param vpc
	 * @param ec2Client
	 */
	private static boolean checkIfVpcUseNetworkInterface(Vpc vpc, Ec2Client ec2Client) {
		Filter filter = Filter.builder().name("vpc-id").values(vpc.vpcId()).build();
		Filter[] filters = { filter };
		DescribeNetworkInterfacesRequest niRequest = DescribeNetworkInterfacesRequest.builder().filters(filters).build();
		DescribeNetworkInterfacesResponse niResponse = ec2Client.describeNetworkInterfaces(niRequest);
		if (niResponse != null && !niResponse.networkInterfaces().isEmpty()) {
			return true;
		}
		return false;
	}

	private static boolean checkIfVpcUseInstance(Vpc vpc, Ec2Client ec2Client) {
		Filter filter = Filter.builder().name("vpc-id").values(vpc.vpcId()).build();
		Filter[] filters = { filter };
		DescribeInstancesRequest insRequest = DescribeInstancesRequest.builder().filters(filters).build();
		DescribeInstancesResponse insResponse = ec2Client.describeInstances(insRequest);
		if (insResponse != null && insResponse.reservations() != null && !insResponse.reservations().isEmpty()
				&& insResponse.reservations().get(0).instances() != null
				&& !insResponse.reservations().get(0).instances().isEmpty()) {
			return true;
		}
		return false;
	}
}
