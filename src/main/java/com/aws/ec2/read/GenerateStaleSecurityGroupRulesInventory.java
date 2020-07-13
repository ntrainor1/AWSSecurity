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
import software.amazon.awssdk.services.ec2.model.DescribeStaleSecurityGroupsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeStaleSecurityGroupsResponse;
import software.amazon.awssdk.services.ec2.model.DescribeVpcsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVpcsResponse;
import software.amazon.awssdk.services.ec2.model.StaleIpPermission;
import software.amazon.awssdk.services.ec2.model.StaleSecurityGroup;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.Vpc;

/**
 * Generates a report of all EC2 instances and security groups associated with
 * an AWS account
 */
public class GenerateStaleSecurityGroupRulesInventory {
	private static Logger log = Logger.getLogger(GenerateStaleSecurityGroupRulesInventory.class);
	private static String format = "%s|%s|%s|%s|%s|%s|%s|%s|%s|%s\r\n";
	private static final String header = "Region|VpcName|VpcId|SecurityGroupName|SecurityGroupId|SecurityGroupType|SecurityGroupDescription|"
			+ "SecurityGroupRuleProtocol|SecurityGroupRulePortRange|SecurityGroupRuleSource\r\n";

	public static void main(String[] args) {
		String fileName = ".\\output\\StaleSecurityGroupRulesInventoryList-"
				+ new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss").format(new Date()) + ".csv";
		print(fileName, header, null);
		for (Region region : Region.regions()) {
			if (region.isGlobalRegion() || !region.id().startsWith("us-") || region.id().startsWith("us-iso")
					|| region.id().startsWith("us-isob") || region.id().startsWith("us-gov")) {
				continue;
			}
			try {
				Ec2Client ec2Client = Ec2Client.builder()
						.credentialsProvider(ProfileCredentialsProvider.create(AwsPropertiesConfig.AWS_READ_PROFILE))
						.region(region).build();
				Map<String, Vpc> allVpcs = getAllVpcs(ec2Client);
				for (Vpc vpc : allVpcs.values()) {
					DescribeStaleSecurityGroupsRequest request = DescribeStaleSecurityGroupsRequest.builder()
							.vpcId(vpc.vpcId()).build();
					DescribeStaleSecurityGroupsResponse response = ec2Client.describeStaleSecurityGroups(request);
					if (response.staleSecurityGroupSet() != null && !response.staleSecurityGroupSet().isEmpty()) {
						log.info("Stale SecurityGroups - Count : " + response.staleSecurityGroupSet().size());
						processOnlyStaleSecurityGroups(response.staleSecurityGroupSet(), allVpcs, fileName, ec2Client,
								region, vpc);
					}
				}
				ec2Client.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		log.info("Process Completed ");
	}

	private static void processOnlyStaleSecurityGroups(List<StaleSecurityGroup> staleSecurityGroups,
			Map<String, Vpc> allVpcs, String fileName, Ec2Client ec2Client, Region region, Vpc vpc) throws IOException {
		String output = ApplicationConstants.EMPTY_STRING;
		Map<String, String> vpcTagsMap = getTagsMap(vpc != null ? vpc.tags() : null);
		for (StaleSecurityGroup staleSecurityGroup : staleSecurityGroups) {
			log.debug("Processing Stale SecurityGroup : " + staleSecurityGroup.groupId());
			String sgType = staleSecurityGroup.vpcId() == null ? "EC2-Classic" : "EC2-VPC";
			if (staleSecurityGroup.staleIpPermissions() == null || staleSecurityGroup.staleIpPermissions().isEmpty()) {
				output = String.format(format, region.id(),
						vpcTagsMap.getOrDefault(ApplicationConstants.NAME, ApplicationConstants.EMPTY_STRING),
						staleSecurityGroup.vpcId(), staleSecurityGroup.groupName(), staleSecurityGroup.groupId(),
						sgType, staleSecurityGroup.description(), ApplicationConstants.EMPTY_STRING,
						ApplicationConstants.EMPTY_STRING);
				print(fileName, output, StandardOpenOption.APPEND);
			} else {
				processOnlySecurityGroupsAndIpPermission(staleSecurityGroup, vpcTagsMap, fileName, sgType, region);
			}
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
	private static void processOnlySecurityGroupsAndIpPermission(StaleSecurityGroup group,
			Map<String, String> vpcTagsMap, String fileName, String sgType, Region region) throws IOException {
		log.debug("Processing Stale Security Group and IpPermissions - Group being processed : " + group.groupId());
		String output = ApplicationConstants.EMPTY_STRING;
		for (StaleIpPermission ipPermission : group.staleIpPermissions()) {
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
				output = String.format(format, region.id(),
						vpcTagsMap.getOrDefault(ApplicationConstants.NAME, ApplicationConstants.EMPTY_STRING),
						group.vpcId(), group.groupName(), group.groupId(), sgType, group.description(),
						securityGroupRuleProtocol, securityGroupRulePortRange, ApplicationConstants.EMPTY_STRING);
				print(fileName, output, StandardOpenOption.APPEND);
			} else {
				output = String.format(format, region.id(),
						vpcTagsMap.getOrDefault(ApplicationConstants.NAME, ApplicationConstants.EMPTY_STRING),
						group.vpcId(), group.groupName(), group.groupId(), sgType, group.description(),
						securityGroupRuleProtocol, securityGroupRulePortRange,
						String.join(",", ipPermission.ipRanges()));
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
}
