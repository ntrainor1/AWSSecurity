package com.dish.anywhere.aws.ec2.read;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.dish.anywhere.aws.util.ApplicationConstants;
import com.dish.anywhere.aws.util.AwsPropertiesConfig;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeNetworkInterfacesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsResponse;
import software.amazon.awssdk.services.ec2.model.DescribeVpcsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVpcsResponse;
import software.amazon.awssdk.services.ec2.model.GroupIdentifier;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.IpRange;
import software.amazon.awssdk.services.ec2.model.NetworkInterface;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.SecurityGroup;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.Vpc;

/**
 * Generates a report of all EC2 instances and security groups associated with
 * an AWS account
 */
public class GenerateUnusedSecurityGroupInventory {
	private static Logger log = Logger.getLogger(GenerateUnusedSecurityGroupInventory.class);
	private static String format = "%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s\r\n";
	private static final String header = "Project|VpcName|VpcId|SecurityGroupName|SecurityGroupId|SecurityGroupType|SecurityGroupDescription|"
			+ "SecurityGroupRuleProtocol|SecurityGroupRulePortRange|SecurityGroupRuleSource|SecurityGroupRuleDesc\r\n";

	public static void main(String[] args) {
		String fileName = ".\\output\\UnusedSecurityGroupInventoryList-"
				+ new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss").format(new Date()) + ".csv";
		Set<String> usedSecurityGroupIds = new HashSet<String>();
		try {
			print(fileName, header, null);
			Ec2Client ec2Client = Ec2Client.builder()
					.credentialsProvider(ProfileCredentialsProvider.create(AwsPropertiesConfig.AWS_READ_PROFILE))
					.build();
			Map<String, SecurityGroup> allSecurityGroups = getAllSecurityGroups(ec2Client);
			Map<String, Vpc> allVpcs = getAllVpcs(ec2Client);
			String nextToken = null;
			do {
				DescribeInstancesRequest instanceRequest = DescribeInstancesRequest.builder().maxResults(6)
						.nextToken(nextToken).build();
				DescribeInstancesResponse instanceResponse = ec2Client.describeInstances(instanceRequest);
				if (instanceResponse.reservations() != null) {
					for (Reservation reservation : instanceResponse.reservations()) {
						if (reservation.instances() != null) {
							for (Instance instance : reservation.instances()) {
								if (instance.securityGroups() != null) {
									for (GroupIdentifier sgi : instance.securityGroups()) {
										usedSecurityGroupIds.add(sgi.groupId());
									}
								}
							}
						}
					}
				}
				nextToken = instanceResponse.nextToken();
			} while (nextToken != null);

			log.info("Used SecurityGroups - Count : " + usedSecurityGroupIds.size());
			nextToken = null;
			do {
				DescribeNetworkInterfacesRequest interfaceRequest = DescribeNetworkInterfacesRequest.builder()
						.maxResults(6).nextToken(nextToken).build();
				DescribeNetworkInterfacesResponse interfaceResponse = ec2Client
						.describeNetworkInterfaces(interfaceRequest);
				if (interfaceResponse.networkInterfaces() != null) {
					for (NetworkInterface netInterface : interfaceResponse.networkInterfaces()) {
						if (netInterface.groups() != null) {
							for (GroupIdentifier sgi : netInterface.groups()) {
								usedSecurityGroupIds.add(sgi.groupId());
							}
						}
					}
				}
				nextToken = interfaceResponse.nextToken();
			} while (nextToken != null);

			log.info("Used SecurityGroups - Count : " + usedSecurityGroupIds.size());
			if (!usedSecurityGroupIds.isEmpty() && allSecurityGroups.size() != usedSecurityGroupIds.size()) {
				Set<String> unUsedSecurityGroupIds = new HashSet<String>(allSecurityGroups.keySet());
				unUsedSecurityGroupIds.removeAll(usedSecurityGroupIds);
				log.info("Unused SecurityGroups - Count : " + unUsedSecurityGroupIds.size());
				processOnlySecurityGroups(unUsedSecurityGroupIds, allSecurityGroups, allVpcs, fileName, ec2Client);
			}
			ec2Client.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		log.info("Process Completed ");
	}

	/***
	 * process security groups (not attached to EC2 instances) to write their
	 * metadata to file
	 * 
	 * @param unProcessedSecurityGroupIds
	 * @param allSecurityGroups
	 * @param allVpcs
	 * @param fileName
	 * @param ec2Client
	 * @throws IOException
	 */
	private static void processOnlySecurityGroups(Set<String> unProcessedSecurityGroupIds,
			Map<String, SecurityGroup> allSecurityGroups, Map<String, Vpc> allVpcs, String fileName,
			Ec2Client ec2Client) throws IOException {
		String output = ApplicationConstants.EMPTY_STRING;
		log.debug("Processing unprocessed SecurityGroupIds");
		for (String sgId : unProcessedSecurityGroupIds) {
			SecurityGroup group = allSecurityGroups.get(sgId);
			log.debug("Processing unprocessed SecurityGroup : " + group.groupId());
			Vpc vpc = allVpcs.get(group.vpcId());
			Map<String, String> vpcTagsMap = getTagsMap(vpc != null ? vpc.tags() : null);
			Map<String, String> sgTags = getTagsMap(vpc != null ? group.tags() : null);
			String sgType = group.vpcId() == null ? "EC2-Classic" : "EC2-VPC";
			if (group.ipPermissions() == null || group.ipPermissions().isEmpty()) {
				output = String.format(format,
						sgTags.getOrDefault(ApplicationConstants.PROJECT,
								vpcTagsMap.getOrDefault(ApplicationConstants.PROJECT,
										ApplicationConstants.EMPTY_STRING)),
						vpcTagsMap.getOrDefault(ApplicationConstants.NAME, ApplicationConstants.EMPTY_STRING),
						group.vpcId(), group.groupName(), group.groupId(), sgType, group.description(),
						ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
						ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING);
				print(fileName, output, StandardOpenOption.APPEND);
			} else {
				processOnlySecurityGroupsAndIpPermission(group, vpcTagsMap, sgTags, fileName, sgType);
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
	private static void processOnlySecurityGroupsAndIpPermission(SecurityGroup group, Map<String, String> vpcTagsMap,
			Map<String, String> sgTags, String fileName, String sgType) throws IOException {
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
						vpcTagsMap.getOrDefault(ApplicationConstants.NAME, ApplicationConstants.EMPTY_STRING),
						group.vpcId(), group.groupName(), group.groupId(), sgType, group.description(),
						securityGroupRuleProtocol, securityGroupRulePortRange, ApplicationConstants.EMPTY_STRING,
						ApplicationConstants.EMPTY_STRING);
				print(fileName, output, StandardOpenOption.APPEND);
			} else {
				for (IpRange ipRange : ipPermission.ipRanges()) {
					output = String.format(format,
							sgTags.getOrDefault(ApplicationConstants.PROJECT,
									vpcTagsMap.getOrDefault(ApplicationConstants.PROJECT,
											ApplicationConstants.EMPTY_STRING)),
							vpcTagsMap.getOrDefault(ApplicationConstants.NAME, ApplicationConstants.EMPTY_STRING),
							group.vpcId(), group.groupName(), group.groupId(), sgType, group.description(),
							securityGroupRuleProtocol, securityGroupRulePortRange, ipRange.cidrIp(),
							ipRange.description());
					print(fileName, output, StandardOpenOption.APPEND);
				}
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
	private static void print(String fileName, String content, StandardOpenOption standardOpenOption)
			throws IOException {
		if (standardOpenOption != null) {
			Files.write(Paths.get(fileName), content.getBytes(), StandardOpenOption.APPEND);
		} else {
			Files.write(Paths.get(fileName), content.getBytes());
		}
	}

	/***
	 * get all the security groups present in the account
	 * 
	 * @param eEc2Client
	 * @return
	 */
	private static Map<String, SecurityGroup> getAllSecurityGroups(Ec2Client eEc2Client) {
		Map<String, SecurityGroup> allSecurityGroups = new HashMap<String, SecurityGroup>();
		try {
			DescribeSecurityGroupsRequest sgRequest = DescribeSecurityGroupsRequest.builder().build();
			DescribeSecurityGroupsResponse sgResponse = eEc2Client.describeSecurityGroups(sgRequest);
			if (sgResponse != null && sgResponse.securityGroups() != null) {
				for (SecurityGroup group : sgResponse.securityGroups()) {
					allSecurityGroups.put(group.groupId(), group);
				}
			}
		} catch (Exception e) {
			log.error(e);
		}
		log.info("All SecurityGroups - Count : " + allSecurityGroups.size());
		return allSecurityGroups;
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
