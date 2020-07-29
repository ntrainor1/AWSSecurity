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
 * Describes all unused VPCs
 */
public class DescribeUnusedVpcs {
	private static Logger log = Logger.getLogger(DescribeUnusedVpcs.class);
	private static String format = "%s|%s|%s|%s|%s|%s|%s|%s|%s|%s\r\n";
	private static final String header = "VpcId|VpcName|VpcProject|VpcCIDR|"
			+ "SecurityGroupName|SecurityGroupId|SecurityGroupDescription|SecurityGroupRuleProtocol|"
			+ "SecurityGroupRulePortRange|SecurityGroupRuleSource\r\n";
	private static final String name = "Name";

	public static void main(String[] args) {
		log.info("Started DescribeUnusedVpcs process");
		String fileName = ".\\output\\DescribeUnusedVpcInventoryList-"
				+ new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss").format(new Date()) + ".csv";
		print(fileName, header, null);
		try {
			Ec2Client ec2Client = Ec2Client.builder()
					.credentialsProvider(ProfileCredentialsProvider.create(AwsPropertiesConfig.AWS_READ_PROFILE))
					.region(Region.US_EAST_1).build();
			describe(fileName, ec2Client);

		} catch (Exception e) {
			e.printStackTrace();
		}
		log.info("Completed DescribeUnusedVpcs process");
	}

	private static void describe(String fileName, Ec2Client ec2Client) throws IOException {
		try {
			if (ec2Client != null) {
				DescribeVpcsRequest vpcRequest = DescribeVpcsRequest.builder().build();
				DescribeVpcsResponse vpcResponse = ec2Client.describeVpcs(vpcRequest);
				if (vpcResponse != null && vpcResponse.vpcs() != null) {
					log.debug("Processing unprocessed VpcIds");
					for (Vpc vpc : vpcResponse.vpcs()) {
						log.debug("Processing unprocessed Vpc : " + vpc.vpcId());
						if (!checkIfNetworkInterfacesAttached(vpc, ec2Client)) {
							processAllSecurityGroupInformation(fileName, vpc, ec2Client);
						}
					}
				}
			}
		} catch (Exception e) {
			log.info(String.format("Exception : Message : %s", e.getMessage()));
		}

	}

	/***
	 * Determine if the Vpc has a network interface attached.
	 * 
	 * @param vpc
	 * @param ec2Client
	 */
	private static boolean checkIfNetworkInterfacesAttached(Vpc vpc, Ec2Client ec2Client) {
		Filter filter = Filter.builder().name("vpc-id").values(vpc.vpcId()).build();
		Filter[] filters = { filter };
		DescribeNetworkInterfacesRequest niRequest = DescribeNetworkInterfacesRequest.builder().filters(filters).build();
		DescribeNetworkInterfacesResponse niResponse = ec2Client.describeNetworkInterfaces(niRequest);
		if (niResponse != null && !niResponse.networkInterfaces().isEmpty()) {
			return true;
		}

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
	 * After determining that the Vpc has a network interface attached, retrieve the
	 * Vpc metadata and the security group metadata and write to file.
	 * 
	 * @param vpc
	 * @param ec2Client
	 * @param fileName
	 */
	private static void processAllSecurityGroupInformation(String fileName, Vpc vpc, Ec2Client ec2Client) {
		try {
			String output = ApplicationConstants.EMPTY_STRING;
			Map<String, String> vpcTagsMap = getTagsMap(vpc != null ? vpc.tags() : null);
			Filter filter = Filter.builder().name("vpc-id").values(vpc.vpcId()).build();
			Filter[] filters = { filter };
			DescribeSecurityGroupsRequest sgRequest = DescribeSecurityGroupsRequest.builder().filters(filters).build();
			DescribeSecurityGroupsResponse sgResponse = ec2Client.describeSecurityGroups(sgRequest);
			if (sgResponse != null && sgResponse.securityGroups() != null) {
				for (SecurityGroup group : sgResponse.securityGroups()) {
					log.debug("Processing unprocessed SecurityGroup : " + group.groupId());
					Map<String, String> sgTags = getTagsMap(vpc != null ? group.tags() : null);
					if (group.ipPermissions() == null || group.ipPermissions().isEmpty()) {
						output = String.format(format, vpc.vpcId(), vpcTagsMap.get(name),
								vpcTagsMap.getOrDefault(ApplicationConstants.PROJECT,
										sgTags.getOrDefault(ApplicationConstants.PROJECT,
												ApplicationConstants.EMPTY_STRING)),
								vpc.cidrBlock(), group.groupName().replace(",", " & "), group.groupId(),
								group.description().replace(",", " & "), ApplicationConstants.EMPTY_STRING,
								ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING);
						print(fileName, output, StandardOpenOption.APPEND);
					} else {
						processOnlySecurityGroupsAndIpPermission(group, vpcTagsMap, sgTags, fileName, vpc);
					}
				}
			}
		} catch (Exception e) {
			log.info(String.format("Exception : Message : %s", e.getMessage()));
		}
	}

	/***
	 * process security groups (not attached to EC2 instances) and IpPermissions to
	 * write their metadata to file
	 * 
	 * @param group
	 * @param vpcTagsMap
	 * @param fileName
	 * @param sgType
	 * @throws IOException
	 */
	private static void processOnlySecurityGroupsAndIpPermission(SecurityGroup group, Map<String, String> vpcTagsMap,
			Map<String, String> sgTags, String fileName, Vpc vpc) throws IOException {
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
				output = String.format(format, vpc.vpcId(), vpcTagsMap.get(name),
						vpcTagsMap.getOrDefault(ApplicationConstants.PROJECT,
								sgTags.getOrDefault(ApplicationConstants.PROJECT,
										ApplicationConstants.EMPTY_STRING)),
						vpc.cidrBlock(), group.groupName().replace(",", " & "), group.groupId(),
						group.description().replace(",", " & "), 
						securityGroupRuleProtocol, securityGroupRulePortRange, ApplicationConstants.EMPTY_STRING);
				print(fileName, output, StandardOpenOption.APPEND);
			} else {
				StringBuilder sb = new StringBuilder();
				for (IpRange range : ipPermission.ipRanges()) {
					sb.append(" ").append(range.cidrIp());
				}
				output = String.format(format, vpc.vpcId(), vpcTagsMap.get(name),
						vpcTagsMap.getOrDefault(ApplicationConstants.PROJECT,
								sgTags.getOrDefault(ApplicationConstants.PROJECT,
										ApplicationConstants.EMPTY_STRING)),
						vpc.cidrBlock(), group.groupName().replace(",", " & "), group.groupId(),
						group.description().replace(",", " & "), 
						securityGroupRuleProtocol, securityGroupRulePortRange, sb.toString());
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
}
