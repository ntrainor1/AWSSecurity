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
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsResponse;
import software.amazon.awssdk.services.ec2.model.DescribeVpcsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVpcsResponse;
import software.amazon.awssdk.services.ec2.model.GroupIdentifier;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.IpRange;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.SecurityGroup;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.Vpc;

/**
 * Generates a report of all EC2 instances and security groups associated with
 * an AWS account
 */
public class GenerateInstanceAndSecurityGroupInventory {
	private static Logger log = Logger.getLogger(GenerateInstanceAndSecurityGroupInventory.class);
	private static String format = "%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s\r\n";
	private static final String header = "InstanceName|InstanceProject|InstanceId|InstanceType|KeyName|AvailabilityZone|InstanceState|VpcName|VpcId|SubnetId|SecurityGroupName|SecurityGroupId|SecurityGroupType|SecurityGroupVpcId|"
			+ "SecurityGroupRuleProtocol|SecurityGroupRulePortRange|SecurityGroupRuleSource|SecurityGroupRuleDesc|SecurityGroupDescription\r\n";

	public static void main(String[] args) {
		String fileName = ".\\output\\InventoryList-" + new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss").format(new Date())
				+ ".csv";
		Set<String> processedSecurityGroupIds = new HashSet<String>();
		String nextToken = null;
		int count = 0;
		try {
			print(fileName, header, null);
			Ec2Client ec2Client = Ec2Client.builder()
					.credentialsProvider(ProfileCredentialsProvider.create(AwsPropertiesConfig.AWS_READ_PROFILE))
					.build();
			Map<String, SecurityGroup> allSecurityGroups = getAllSecurityGroups(ec2Client);
			Map<String, Vpc> allVpcs = getAllVpcs(ec2Client);
			do {
				DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(6).nextToken(nextToken)
						.build();
				DescribeInstancesResponse response = ec2Client.describeInstances(request);
				if (response.reservations() != null) {
					for (Reservation reservation : response.reservations()) {
						for (Instance instance : reservation.instances()) {
							log.debug("Processing Instance : " + instance.instanceId());
							count++;
							Map<String, String> instanceTagsMap = getTagsMap(instance.tags());
							Vpc vpc = allVpcs.get(instance.vpcId());
							Map<String, String> vpcTagsMap = vpc != null ? getTagsMap(vpc.tags())
									: new HashMap<String, String>();
							if (instance.securityGroups() == null || instance.securityGroups().isEmpty()) {
								processWithoutSecurityGroups(instanceTagsMap, instance, vpcTagsMap, fileName);
							} else {
								processSecurityGroups(instance.securityGroups(), allSecurityGroups, instanceTagsMap,
										instance, vpcTagsMap, fileName, processedSecurityGroupIds);
							}
						}
					}
				}
				nextToken = response.nextToken();
			} while (nextToken != null);

			log.info("Processed Instances - Count : " + count);
			if (!processedSecurityGroupIds.isEmpty() && allSecurityGroups.size() != processedSecurityGroupIds.size()) {
				Set<String> unProcessedSecurityGroupIds = new HashSet<String>(allSecurityGroups.keySet());
				unProcessedSecurityGroupIds.removeAll(processedSecurityGroupIds);
				log.info("SecurityGroups without Ec2 Instances - Count : " + unProcessedSecurityGroupIds.size());
				processOnlySecurityGroups(unProcessedSecurityGroupIds, allSecurityGroups, allVpcs, fileName, ec2Client);
			}
			ec2Client.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		log.info("Process Completed : " + count);
	}

	/***
	 * process the instances to write their metadata to file without security groups
	 * 
	 * @param instanceTagsMap
	 * @param instance
	 * @param vpcTagsMap
	 * @param fileName
	 * @throws IOException
	 */
	private static void processWithoutSecurityGroups(Map<String, String> instanceTagsMap, Instance instance,
			Map<String, String> vpcTagsMap, String fileName) throws IOException {
		log.debug("Processing Instance Without Security Group : " + instance.instanceId());
		String output = String.format(format, instanceTagsMap.get(ApplicationConstants.NAME),
				instanceTagsMap.get(ApplicationConstants.PROJECT), instance.instanceId(),
				instance.instanceTypeAsString(), instance.keyName(), instance.placement().availabilityZone(),
				instance.state().name(), vpcTagsMap.get(ApplicationConstants.NAME), instance.vpcId(),
				instance.subnetId(), ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
				ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
				ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
				ApplicationConstants.EMPTY_STRING);
		print(fileName, output, StandardOpenOption.APPEND);
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
			Map<String, String> vpcTagsMap = vpc != null ? getTagsMap(vpc.tags()) : new HashMap<String, String>();
			String sgType = group.vpcId() == null ? "EC2-Classic" : "EC2-VPC";
			if (group.ipPermissions() == null || group.ipPermissions().isEmpty()) {
				output = String.format(format, ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
						ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
						ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
						ApplicationConstants.EMPTY_STRING, vpcTagsMap.get(ApplicationConstants.NAME),
						ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING, group.groupName(),
						group.groupId(), sgType, group.vpcId(), ApplicationConstants.EMPTY_STRING,
						ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
						ApplicationConstants.EMPTY_STRING, group.description());
				print(fileName, output, StandardOpenOption.APPEND);
			} else {
				processOnlySecurityGroupsAndIpPermission(group, vpcTagsMap, fileName, sgType);
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
			String fileName, String sgType) throws IOException {
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
				output = String.format(format, ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
						ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
						ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
						ApplicationConstants.EMPTY_STRING, vpcTagsMap.get(ApplicationConstants.NAME),
						ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING, group.groupName(),
						group.groupId(), sgType, group.vpcId(), securityGroupRuleProtocol, securityGroupRulePortRange,
						ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING, group.description());
				print(fileName, output, StandardOpenOption.APPEND);
			} else {
				for (IpRange ipRange : ipPermission.ipRanges()) {
					output = String.format(format, ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
							ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
							ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
							ApplicationConstants.EMPTY_STRING, vpcTagsMap.get(ApplicationConstants.NAME),
							ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING, group.groupName(),
							group.groupId(), sgType, group.vpcId(), securityGroupRuleProtocol,
							securityGroupRulePortRange, ipRange.cidrIp(), ipRange.description(), group.description());
					print(fileName, output, StandardOpenOption.APPEND);
				}
			}
		}
	}

	/***
	 * process security groups (for an EC2 instance) to write their metadata to file
	 * 
	 * @param securityGroupIdentifiers
	 * @param allSecurityGroups
	 * @param instanceTagsMap
	 * @param instance
	 * @param vpcTagsMap
	 * @param fileName
	 * @param processedSecurityGroupIds
	 * @throws IOException
	 */
	private static void processSecurityGroups(List<GroupIdentifier> securityGroupIdentifiers,
			Map<String, SecurityGroup> allSecurityGroups, Map<String, String> instanceTagsMap, Instance instance,
			Map<String, String> vpcTagsMap, String fileName, Set<String> processedSecurityGroupIds) throws IOException {
		String output = ApplicationConstants.EMPTY_STRING;
		log.debug("Processing Security Groups for Intance : " + instance.instanceId());
		for (GroupIdentifier sgi : securityGroupIdentifiers) {
			SecurityGroup group = allSecurityGroups.get(sgi.groupId());
			if (group == null) {
				log.debug("Processing Security Groups for Intance : " + instance.instanceId() + " , Group not found : "
						+ sgi.groupId());
				processWithoutSecurityGroups(instanceTagsMap, instance, vpcTagsMap, fileName);
			} else {
				log.debug("Processing Security Groups for Intance : " + instance.instanceId()
						+ " , Group being processed : " + group.groupId());
				processedSecurityGroupIds.add(group.groupId());
				String sgType = group.vpcId() == null ? "EC2-Classic" : "EC2-VPC";
				if (group.ipPermissions() == null || group.ipPermissions().isEmpty()) {
					output = String.format(format, instanceTagsMap.get(ApplicationConstants.NAME),
							instanceTagsMap.get(ApplicationConstants.PROJECT), instance.instanceId(),
							instance.instanceTypeAsString(), instance.keyName(),
							instance.placement().availabilityZone(), instance.state().name(),
							vpcTagsMap.get(ApplicationConstants.NAME), instance.vpcId(), instance.subnetId(),
							group.groupName(), group.groupId(), sgType, group.vpcId(),
							ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
							ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING, group.description());
					print(fileName, output, StandardOpenOption.APPEND);
				} else {
					processGroupsAndIpPermissions(group, instanceTagsMap, instance, vpcTagsMap, fileName, sgType);
				}
			}
		}
	}

	/***
	 * process security groups (for an EC2 instance ) and IpPermissions to write
	 * their metadata to file
	 * 
	 * @param group
	 * @param instanceTagsMap
	 * @param instance
	 * @param vpcTagsMap
	 * @param fileName
	 * @param sgType
	 * @throws IOException
	 */
	private static void processGroupsAndIpPermissions(SecurityGroup group, Map<String, String> instanceTagsMap,
			Instance instance, Map<String, String> vpcTagsMap, String fileName, String sgType) throws IOException {
		String output = ApplicationConstants.EMPTY_STRING;
		log.debug("Processing Security Groups and IpPermissions for Intance : " + instance.instanceId()
				+ " , Group being processed : " + group.groupId());
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
				output = String.format(format, instanceTagsMap.get(ApplicationConstants.NAME),
						instanceTagsMap.get(ApplicationConstants.PROJECT), instance.instanceId(),
						instance.instanceTypeAsString(), instance.keyName(), instance.placement().availabilityZone(),
						instance.state().name(), vpcTagsMap.get(ApplicationConstants.NAME), instance.vpcId(),
						instance.subnetId(), group.groupName(), group.groupId(), sgType, group.vpcId(),
						securityGroupRuleProtocol, securityGroupRulePortRange, ApplicationConstants.EMPTY_STRING,
						ApplicationConstants.EMPTY_STRING, group.description());
				print(fileName, output, StandardOpenOption.APPEND);
			} else {
				for (IpRange ipRange : ipPermission.ipRanges()) {
					output = String.format(format, instanceTagsMap.get(ApplicationConstants.NAME),
							instanceTagsMap.get(ApplicationConstants.PROJECT), instance.instanceId(),
							instance.instanceTypeAsString(), instance.keyName(),
							instance.placement().availabilityZone(), instance.state().name(),
							vpcTagsMap.get(ApplicationConstants.NAME), instance.vpcId(), instance.subnetId(),
							group.groupName(), group.groupId(), sgType, group.vpcId(), securityGroupRuleProtocol,
							securityGroupRulePortRange, ipRange.cidrIp(), ipRange.description(), group.description());
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
