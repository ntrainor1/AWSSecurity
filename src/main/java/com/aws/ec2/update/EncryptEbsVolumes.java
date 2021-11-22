package com.dish.anywhere.aws.ec2.update;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
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
public class EncryptEbsVolumes {
	private static Logger log = Logger.getLogger(EncryptEbsVolumes.class);
	private static String format = "%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s\r\n";
	private static final String header = "Original Volume|Encrypted Volume|Instance Id|Instance Name|Instance Project|Status|Start Time|End Time|Total Time|Original Size|Encrypted Size\r\n";

	public static void main(String[] args) {
		log.info("Started EncryptEbsVolumes process");
		String inputFileName = ".\\input\\EncryptEbsVolumesInput.csv";
		String outputFileName = ".\\output\\EncryptEbsVolumesOutput-"
				+ new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss").format(new Date()) + ".csv";
		print(outputFileName, header, null);

		Region region = Region.US_EAST_2;
		Ec2Client ec2Client = Ec2Client.builder()
				.credentialsProvider(ProfileCredentialsProvider.create(AwsPropertiesConfig.AWS_BETSOL_PROFILE))
				.region(region).build();
		BufferedReader csvReader;
		try {
			csvReader = new BufferedReader(new FileReader(inputFileName));
			String row = null;
			while ((row = csvReader.readLine()) != null) {
				String[] data = row.split(ApplicationConstants.COMMA);
				if (data != null) {
					String startTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
					String volumeId = data[0];
					Volume unencrypted = describeVolume(volumeId, ec2Client);

					// Determine that the volume is not already encrypted
					if (!unencrypted.encrypted()) {
						List<VolumeAttachment> instances = null;

						// Fetch instances, stop instances, and detach the unencrypted volume
						instances = unencrypted.attachments();
						for (VolumeAttachment volumeAttachment : instances) {
							Instance instance = describeInstance(volumeAttachment.instanceId(), ec2Client);
							stopInstance(instance, ec2Client);
							while (!describeInstance(instance.instanceId(), ec2Client).state().nameAsString().equals("stopped")) {
								Thread.sleep(1000);
							}
							detachUnencryptedInstance(instance, unencrypted, ec2Client);
						}

						// Take snapshot and create encrypted volume from snapshot
						Snapshot snapshot = takeSnapshot(unencrypted, ec2Client);
						while (!inspectSnapshot(snapshot.snapshotId(), ec2Client).stateAsString().equals("completed")) {
							Thread.sleep(1000);
						}
						Volume encrypted = createEncryptedVolume(unencrypted, snapshot, ec2Client);
						while (!describeVolume(encrypted.volumeId(), ec2Client).stateAsString().equals("available")) {
							Thread.sleep(1000);
						}
						
						// Reattach encrypted volume
						if (instances.size() == 0) {
							outputData(unencrypted, encrypted, null, "Complete", startTime, outputFileName, ec2Client);
						} else {
							for (VolumeAttachment volumeAttachment : instances) {
								attachEncryptedVolume(volumeAttachment, encrypted, ec2Client);
								startInstance(volumeAttachment, ec2Client);
								outputData(unencrypted, encrypted, volumeAttachment, "Complete", startTime, outputFileName, ec2Client);
							}
						}
					} else {
						outputData(unencrypted, null, null, "Already Encrypted", startTime, outputFileName, ec2Client);
					}
				}
			}
			csvReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		log.info("Finished ListEbsVolumes process");
	}

	/***
	 * returns a Map of the tag key values
	 * 
	 * @param ec2Client
	 * @param tags
	 * @return Volume
	 */
	private static Volume describeVolume(String volumeId, Ec2Client ec2Client) {
		Filter filter = Filter.builder().name("volume-id").values(volumeId).build();
		Filter[] filters = { filter };
		DescribeVolumesRequest describeVolumesRequest = DescribeVolumesRequest.builder().filters(filters).build();
		Volume volume = ec2Client.describeVolumes(describeVolumesRequest).volumes().get(0);
		return volume;
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
	 * @return Instance
	 */
	private static Instance describeInstance(String instanceId, Ec2Client ec2Client) {
		Filter filter = Filter.builder().name("instance-id").values(instanceId).build();
		Filter[] filters = { filter };
		DescribeInstancesRequest diRequest = DescribeInstancesRequest.builder().filters(filters).build();
		DescribeInstancesResponse diResponse = ec2Client.describeInstances(diRequest);
		Instance instance = diResponse.reservations().get(0).instances().get(0);
		return instance;
	}

	/***
	 * Stop the instance before detaching volume
	 * 
	 * @param instance
	 * @param ec2Client
	 * @return
	 */
	private static void stopInstance(Instance instance, Ec2Client ec2client) {
		if (!instance.state().equals("stopped")) {
			String[] instanceIds = { instance.instanceId() };
			StopInstancesRequest stopInstanceRequest = StopInstancesRequest.builder().instanceIds(instanceIds).build();
			ec2client.stopInstances(stopInstanceRequest);
		}
	}

	/***
	 * Stop the instance before detaching volume
	 * 
	 * @param instance
	 * @param volume
	 * @param ec2Client
	 * @return
	 */
	private static void detachUnencryptedInstance(Instance instance, Volume volume, Ec2Client ec2client) {
		DetachVolumeRequest detachVolumeRequest = DetachVolumeRequest.builder().instanceId(instance.instanceId())
				.volumeId(volume.volumeId()).build();
		ec2client.detachVolume(detachVolumeRequest);
	}

	/***
	 * Stop the instance before detaching volume
	 * 
	 * @param volume
	 * @param ec2Client
	 * @return
	 */
	private static Snapshot takeSnapshot(Volume volume, Ec2Client ec2Client) {
		CreateSnapshotRequest createSnapshotRequest = CreateSnapshotRequest.builder().volumeId(volume.volumeId())
				.build();
		String snapshotId = ec2Client.createSnapshot(createSnapshotRequest).snapshotId();
		String[] snapshotIds = { snapshotId };
		DescribeSnapshotsRequest describeSnapshotsRequest = DescribeSnapshotsRequest.builder().snapshotIds(snapshotIds)
				.build();
		Snapshot snapshot = ec2Client.describeSnapshots(describeSnapshotsRequest).snapshots().get(0);
		return snapshot;
	}

	/***
	 * Stop the instance before detaching volume
	 * 
	 * @param volume
	 * @param ec2Client
	 * @return
	 */
	private static Snapshot inspectSnapshot(String snapshotId, Ec2Client ec2Client) {
		String[] snapshotIds = { snapshotId };
		DescribeSnapshotsRequest describeSnapshotsRequest = DescribeSnapshotsRequest.builder().snapshotIds(snapshotIds)
				.build();
		Snapshot snapshot = ec2Client.describeSnapshots(describeSnapshotsRequest).snapshots().get(0);
		return snapshot;
	}
	/***
	 * Stop the instance before detaching volume
	 * 
	 * @param snapshot
	 * @param ec2Client
	 * @return
	 */
	private static Volume createEncryptedVolume(Volume volume, Snapshot snapshot, Ec2Client ec2Client) {
		VolumeType volumeType = volume.volumeType();
		String availabilityZone = volume.availabilityZone();
		Integer size = volume.size();

		CreateVolumeRequest volumeRequest = CreateVolumeRequest.builder().snapshotId(snapshot.snapshotId())
				.volumeType(volumeType).availabilityZone(availabilityZone).size(size).encrypted(true).build();
//		CreateVolumeRequest volumeRequest = CreateVolumeRequest.builder().snapshotId(snapshot.snapshotId())
//				.volumeType(volumeType).availabilityZone(availabilityZone).size(size).encrypted(true).kmsKeyId(kmsKeyId).build();
		String encryptedVolumeId = ec2Client.createVolume(volumeRequest).volumeId();

		return describeVolume(encryptedVolumeId, ec2Client);
	}

	/***
	 * Write the content to the file
	 * 
	 * @param volumeAttachment
	 * @param encrypted
	 * @param ec2Client
	 */
	private static void attachEncryptedVolume(VolumeAttachment volumeAttachment, Volume encrypted,
			Ec2Client ec2Client) {
		AttachVolumeRequest attachVolumeRequest = AttachVolumeRequest.builder().volumeId(encrypted.volumeId())
				.instanceId(volumeAttachment.instanceId()).device(volumeAttachment.device()).build();
		ec2Client.attachVolume(attachVolumeRequest);
	}

	/***
	 * Stop the instance before detaching volume
	 * 
	 * @param volumeAttachment
	 * @param ec2Client
	 * @return
	 */
	private static void startInstance(VolumeAttachment volumeAttachment, Ec2Client ec2client) {
		if (!volumeAttachment.state().equals("stopped")) {
			String[] instanceIds = { volumeAttachment.instanceId() };
			StartInstancesRequest startInstanceRequest = StartInstancesRequest.builder().instanceIds(instanceIds)
					.build();
			ec2client.startInstances(startInstanceRequest);
		}
	}

	/***
	 * Write the content to the file
	 * 
	 * @param unencrypted
	 * @param encrypted
	 * @param volumeAttachment
	 * @param volumeAttachment
	 */
	private static void outputData(Volume unencrypted, Volume encrypted, VolumeAttachment volumeAttachment,
			String status, String startTime, String outputFileName, Ec2Client ec2Client) {
		String output = ApplicationConstants.EMPTY_STRING;
		String endTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
		
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
		String duration = ApplicationConstants.EMPTY_STRING;
		try {
			Date date1 = simpleDateFormat.parse(startTime);
			Date date2 = simpleDateFormat.parse(endTime);
			Long difference = (date2.getTime() - date1.getTime())/1000;
			duration = difference.toString() + "s";
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		
		if (encrypted != null) {
			if (volumeAttachment != null) {
				Instance instance = describeInstance(volumeAttachment.instanceId(), ec2Client);
				Map<String, String> instanceTags = getTagsMap(instance != null ? instance.tags() : null);

				if (instanceTags != null) {
					output = String.format(format, unencrypted.volumeId(), encrypted.volumeId(),
							volumeAttachment.instanceId(), instanceTags.get(ApplicationConstants.NAME),
							instanceTags.getOrDefault(ApplicationConstants.PROJECT, instanceTags
									.getOrDefault(ApplicationConstants.PROJECT, ApplicationConstants.EMPTY_STRING)),
							status, startTime, endTime, duration, unencrypted.size(), encrypted.size());
				} else {
					output = String.format(format, unencrypted.volumeId(), encrypted.volumeId(),
							volumeAttachment.instanceId(), ApplicationConstants.EMPTY_STRING,
							ApplicationConstants.EMPTY_STRING, status, startTime, endTime, duration, unencrypted.size(), encrypted.size());
				}
			} else {
				output = String.format(format, unencrypted.volumeId(), encrypted.volumeId(),
						ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
						ApplicationConstants.EMPTY_STRING, status, startTime, endTime, duration, unencrypted.size(), encrypted.size());
			}
		} else {
			output = String.format(format, unencrypted.volumeId(), ApplicationConstants.EMPTY_STRING,
					ApplicationConstants.EMPTY_STRING, ApplicationConstants.EMPTY_STRING,
					ApplicationConstants.EMPTY_STRING, status, startTime, endTime, duration, unencrypted.size(), ApplicationConstants.EMPTY_STRING);
		}
		
		System.out.println(output);
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
