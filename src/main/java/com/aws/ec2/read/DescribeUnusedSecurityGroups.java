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
 * Deletes EC2 security groups
 */
public class DescribeUnusedSecurityGroups {
	private static Logger log = Logger.getLogger(DescribeUnusedSecurityGroups.class);
	private static String format = "%s|%s|%s|%s|%s|%s|%s|%s\r\n";
	private static final String header = "Project|SecurityGroupName|SecurityGroupId|SecurityGroupType|SecurityGroupDescription|"
			+ "SecurityGroupRuleProtocol|SecurityGroupRulePortRange|SecurityGroupRuleSource\r\n";

	private static String sgIds = "sg-000b05df87fdab698,sg-001c0d64186da6053,sg-0025922e3fcafd00c,sg-002bf2a4546cfee43,sg-00448b77c4137ab6b,sg-004c6620b1e58437c,sg-004c8e96242b0d0c1,sg-00af84e085885a4c1,sg-00d3c049638da5c06,sg-01131f394804e952c,sg-0115a3afa75e6c387,sg-015a95cb2f30b12f5,sg-019444d92ca0afa05,sg-0198a23264729b2a2,sg-01a690518682158b6,sg-01a717b687ac9a50b,sg-01e068a0466b9dbc3,sg-01ff460de58c7f608,sg-02195651d7a05d171,sg-021c0844863080b49,sg-02224e90fea912beb,sg-023ab38668a9f1301,sg-028b02a7b930fa210,sg-02afe0bbee823aa57,sg-02d86587b3bd4f012,sg-02ea3a7c2a8b0b736,sg-02f8451c304a17fc0,sg-0312a8b95153b8762,sg-0316c393e419758c6,sg-0318b6a388ed28a83,sg-031cd58bf6d3e860d,sg-036b2f01463327c13,sg-037444a1115ab7d62,sg-037a958ffabfedaad,sg-038697c2305ba99ca,sg-03f2d8ce0111fa031,sg-0420fea9cc8e87f34,sg-0459df693a2030b1a,sg-0465df5e35b2bd2f2,sg-0467eaaa0ced43b91,sg-04a284962a3dbc1eb,sg-04c23be89363f8f7e,sg-04e1352738112c0b5,sg-04ea8531df9c2d8d5,sg-04f64f10c13de9713,sg-052f4a528759830ea,sg-054686ddb3f552491,sg-058499b85427da725,sg-0587bfa4169cf2382,sg-058bf760bd3b6853c,sg-059832bdc261930bf,sg-05a51347254f6107c,sg-05af096994396d041,sg-05af414b9d12ca83b,sg-061dad72bc7e16c1b,sg-0635567bf3eba7d13,sg-0655248478be3330a,sg-06c6f001e6d2b00e3,sg-06e14038a825cc984,sg-06e7320dd4c34ef51,sg-06f2cd688b5e89eb3,sg-0716d96e,sg-0733eb0ecb87371dd,sg-075855ed2d4e56f88,sg-076162ad3a9c10d9e,sg-0770c60710a98d92c,sg-07785fbbcdc315606,sg-07b05498224a6c66d,sg-07f6ab15c8f86b364,sg-07fa89db2925d4879,sg-080d28e764d473d96,sg-084642d621d509b5b,sg-0863fca63e80dd121,sg-086b8bf0df3b4d519,sg-089e451a0bd265a2d,sg-08b60fcde07868a94,sg-08e28343cfe1d1363,sg-09047a70c075c1616,sg-0917c47a,sg-09320237b2810e6df,sg-095d006fcbeda8b27,sg-097f10533ecfbc955,sg-09a7f03dd12c4aa35,sg-09be9fb2d89ed918a,sg-09d5ea32ca204f1ed,sg-0a098aaef88e59a36,sg-0a239bbe5539793ec,sg-0a2717b22d1a7102e,sg-0a338dab685b0f165,sg-0a36e6c7ffb16daa8,sg-0a391676905ec8370,sg-0a3f9f5d0f1acd752,sg-0a40fc2c49eede829,sg-0a485355d763b5653,sg-0a58c72b9f9892b90,sg-0a7e7778,sg-0a8cc4961f0ec7505,sg-0a8ce36c7f3384644,sg-0ab289d13a679f950,sg-0ab5d6e525d4742f4,sg-0abe4b47877873c0e,sg-0ae787432aa969133,sg-0ae8a9fc86176a632,sg-0afb872a4ac7e6687,sg-0b1333b85457b76ad,sg-0b22133be12e8912a,sg-0b29d375a2295e1c1,sg-0b5580fe31ce6df19,sg-0b5a847e,sg-0b60ab9cd8cbc867f,sg-0b6160e4dfa0ee85d,sg-0bd5b9186e7d9ee77,sg-0bd6bcc9fe06b8565,sg-0be01170,sg-0bf98c77dc9ea7d6f,sg-0bfc4e558ff760ff9,sg-0c2371e64bb42c0a5,sg-0c5c4cc9126b6dfd8,sg-0c6b9466,sg-0c90f6fce3dd5a5a7,sg-0cc4a12257d61c886,sg-0cc8906ca952a7775,sg-0d007908697ec148d,sg-0d0f72baf1d675f23,sg-0d2d9185313f936b8,sg-0d34490e7f1553aac,sg-0d8e21d1772ca84c3,sg-0dc50480f0fe257ff,sg-0dcad267,sg-0dd8ded95536f0285,sg-0dde03ff50cc9416c,sg-0df2fe6482c40b16f,sg-0e02687b63217c91e,sg-0e08a9e86fa919db7,sg-0e0dff6c2b1facf0b,sg-0e1fbe61743cd0745,sg-0e678ab79cc31dc50,sg-0e9eaffed920535b5,sg-0ea1337bb6a2646eb,sg-0ea5c6bca8694d44b,sg-0ef61d6e4a9441499,sg-0ef637a1ff6d9aa1f,sg-0ef8f37509c49ddbb,sg-0f156269a656a06db,sg-0f47e306b151b2222,sg-0f59a7627051eb03b,sg-0f66320f9acd56d6c,sg-0f6e0cb67e7d0d5c3,sg-0f7b6794d105db4b5,sg-0f82c6ce366b1f3ea,sg-0f83195a03a5b6f68,sg-0f9d89aa1a6f89567,sg-0fa3e43c821f75c34,sg-0fb3f46a,sg-0fb8e3dee093ad656,sg-0fc14fbcea30412e7,sg-0fc22773,sg-0fc6355c8f1792f29,sg-0fd1d2982cef948a8,sg-0fd1ea69f863cb2f6,sg-0fee8e65ba7706163,sg-0fef8e992b11f7f38,sg-0ff5152c645f86163,sg-1185e367,sg-11924965,sg-1833e964,sg-195d477c,sg-198a9467,sg-1bf10964,sg-1db95861,sg-1e823761,sg-25e33d50,sg-27a1855b,sg-287ad64e,sg-2ab4f34f,sg-2c43864a,sg-2ca1b950,sg-30221c48,sg-32a9784d,sg-33b7664c,sg-38909f43,sg-3aa6e945,sg-3d909f46,sg-3dc87f42,sg-3e539b41,sg-3ecc3257,sg-42e4013e,sg-4504783a,sg-4533e939,sg-455f8c36,sg-47438621,sg-4e18c52a,sg-4e59fc27,sg-51700835,sg-582d3f3d,sg-58900024,sg-5cc3fd24,sg-5d4f7639,sg-5f010d38,sg-5f1ac73b,sg-67873218,sg-68c8d002,sg-69e9d513,sg-6b50fdfd,sg-70067a0e,sg-703e5f18,sg-704f7614,sg-7317d81a,sg-77bb620b,sg-77c1820b,sg-7e14c91a,sg-7e2d3f1b,sg-805b3ce9,sg-81774ee5,sg-86702afa,sg-8b26f5f8,sg-8b3cc4f9,sg-8ea084f2,sg-911d04ef,sg-995e90e9,sg-9aecf7fc,sg-9b5d8ee8,sg-9c408eec,sg-9d4750e7,sg-a0cbe8de,sg-a18698df,sg-a2be2feb,sg-a7c2fcdf,sg-aa9857db,sg-ab774ecf,sg-acd32ac5,sg-af8747c6,sg-b2fef7cc,sg-b3b861cf,sg-b92af9ca,sg-bba9d5f3,sg-bc8b72c4,sg-bd418af6,sg-bd8eeac0,sg-be4e63c1,sg-c0702abc,sg-c1cb2ea8,sg-c25987b7,sg-c26bb0ab,sg-c4c896ba,sg-c5c05bbd,sg-c66b52a2,sg-c8b770ac,sg-cb17c7af,sg-cd8997b3,sg-ce7c78b4,sg-ceecf7a8,sg-d1427dab,sg-d25a84a7,sg-d41c05aa,sg-da16e4b1,sg-dab466a5,sg-e016559f,sg-e1320c99,sg-e1904b95,sg-e218c886,sg-e41cf99a,sg-e7b9589b,sg-e8221c90,sg-ecedaf90,sg-ee6b528a,sg-ef889691,sg-f34bad94,sg-f405e69f,sg-f6ad3cbf,sg-f80fd5ee,sg-f90fd5ef,sg-f9bbdf84,sg-fdcdf187";

	public static void main(String[] args) {
		log.info("Started DescribeSecurityGroups process");
		String fileName = ".\\output\\DescribeUnusedSGInventoryList-"
				+ new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss").format(new Date()) + ".csv";
		print(fileName, header, null);
		try {
			Ec2Client ec2Client = Ec2Client.builder()
					.credentialsProvider(ProfileCredentialsProvider.create(AwsPropertiesConfig.AWS_READ_PROFILE))
					.region(Region.US_EAST_1).build();
			String[] securityGroupIds = sgIds.split(",");
			Map<String, Vpc> allVpcs = getAllVpcs(ec2Client);
			if (securityGroupIds != null) {
				for (String sgId : securityGroupIds) {
					describe(allVpcs, fileName, sgId, ec2Client);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		log.info("Completed DescribeSecurityGroups process");
	}

	private static void describe(Map<String, Vpc> allVpcs, String fileName, String securityGroupId, Ec2Client ec2Client)
			throws IOException {
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
						if (securityGroupInUse(securityGroupId, ec2Client)) {
							continue;
						} else {
							Vpc vpc = allVpcs.get(group.vpcId());
							Map<String, String> vpcTagsMap = getTagsMap(vpc != null ? vpc.tags() : null);
							Map<String, String> sgTags = getTagsMap(vpc != null ? group.tags() : null);
							String sgType = group.vpcId() == null ? "EC2-Classic" : "EC2-VPC";
							if (group.ipPermissions() == null || group.ipPermissions().isEmpty()) {
								output = String.format(format,
										sgTags.getOrDefault(ApplicationConstants.PROJECT,
												vpcTagsMap.getOrDefault(ApplicationConstants.PROJECT,
														ApplicationConstants.EMPTY_STRING)),
										group.groupName(), group.groupId(), sgType,
										group.description().replace(",", " & "), ApplicationConstants.EMPTY_STRING,
										ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING);
								print(fileName, output, StandardOpenOption.APPEND);
							} else {
								processOnlySecurityGroupsAndIpPermission(group, vpcTagsMap, sgTags, fileName, sgType);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			log.info(String.format("Exception : Message : %s", e.getMessage()));
		}

	}

	private static boolean securityGroupInUse(String securityGroupId, Ec2Client ec2Client) {
		Filter filter = Filter.builder().name("group-id").values(securityGroupId).build();
		Filter[] filters = { filter };
		DescribeInstancesRequest insRequest = DescribeInstancesRequest.builder().filters(filters).build();
		DescribeInstancesResponse insResponse = ec2Client.describeInstances(insRequest);
		if (insResponse != null && insResponse.reservations() != null && !insResponse.reservations().isEmpty()
				&& insResponse.reservations().get(0).instances() != null
				&& !insResponse.reservations().get(0).instances().isEmpty()) {
			return true;
		}

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
						group.groupName(), group.groupId(), sgType, group.description().replace(",", " & "),
						securityGroupRuleProtocol, securityGroupRulePortRange, ApplicationConstants.EMPTY_STRING);
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
						group.groupName(), group.groupId(), sgType, group.description().replace(",", " & "),
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
