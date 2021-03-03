package havis.device.test.hardware.hardwareMgmt.xml;

import havis.device.test.hardware.AirProtocolEnumeration;
import havis.device.test.hardware.ErrorCode;
import havis.device.test.hardware.HardwareMgmtException;
import havis.device.test.hardware.HardwareMgmtSubscriber;
import havis.device.test.hardware.HardwareOperationType;
import havis.device.test.hardware.HardwareType;
import havis.device.test.hardware.IODirectionEnumeration;
import havis.device.test.hardware.IOStateEnumeration;
import havis.device.test.hardware.MemoryBankNameEnumeration;
import havis.device.test.hardware.NotificationIOType;
import havis.device.test.hardware.NotificationType;
import havis.device.test.hardware.RequestCreateIOType;
import havis.device.test.hardware.RequestCreateSubscriberType;
import havis.device.test.hardware.RequestCreateTagAntennaType;
import havis.device.test.hardware.RequestCreateTagType;
import havis.device.test.hardware.RequestCreateType;
import havis.device.test.hardware.RequestDeleteType;
import havis.device.test.hardware.RequestReadType;
import havis.device.test.hardware.RequestUpdateType;
import havis.device.test.hardware.ResponseCreateSubscriberIdsType;
import havis.device.test.hardware.ResponseCreateTagIdsType;
import havis.device.test.hardware.ResponseCreateType;
import havis.device.test.hardware.ResponseDeleteType;
import havis.device.test.hardware.ResponseReadAntennaType;
import havis.device.test.hardware.ResponseReadType;
import havis.device.test.hardware.ResponseUpdateType;
import havis.device.test.hardware.SubscriberIOType;
import havis.device.test.hardware.SubscriberIOsType;
import havis.device.test.hardware.SubscriberInstanceType;
import havis.device.test.hardware.SubscriberType;
import havis.device.test.hardware.common.io.PathHandler;
import havis.device.test.hardware.common.io.XMLFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.bind.JAXBException;

import mockit.Deencapsulation;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

public class HardwareTest {

	private static Path BASE_RESOURCE_PATH = Paths.get(HardwareTest.class
			.getPackage().getName().replace('.', '/'));

	public class SubscriberInstance extends SubscriberInstanceType implements
			HardwareMgmtSubscriber {

		private NotificationType notification;

		@Override
		public void notify(NotificationType notification) {
			this.notification = notification;
		}

		public NotificationType getNotification() {
			return notification;
		}
	}

	@Test
	public void createAntennasTags() throws Exception {
		// create an antenna
		HardwareType initialData = new HardwareType();
		initialData.setConfigId("c1");
		Hardware hwData = new Hardware(initialData);

		HardwareOperationType op = readXMLOperation("createAntenna.xml");
		RequestCreateType antennaRequest = (RequestCreateType) op.getRequest()
				.get(0).getChoice().get(0);
		ResponseCreateType antennaCreateResponse = hwData
				.process(antennaRequest);

		Assert.assertEquals(antennaCreateResponse.getOperationId(), "o1");
		Assert.assertNull(antennaCreateResponse.getTags());

		// get internal data structure
		HardwareType dataCopy = Deencapsulation.getField(hwData, "dataCopy");
		// no tag is assigned to the antenna
		Assert.assertNull(dataCopy.getAntennas().getAntenna().get(0).getTags());

		// create a tag and assign it to the antenna
		op = readXMLOperation("createTag.xml");
		RequestCreateType tagRequest = (RequestCreateType) op.getRequest()
				.get(0).getChoice().get(0);
		ResponseCreateType tagCreateResponse = hwData.process(tagRequest);

		Assert.assertEquals(tagCreateResponse.getOperationId(), "o2");
		ResponseCreateTagIdsType tagIds = tagCreateResponse.getTags()
				.getTagIds().get(0);
		Assert.assertEquals(tagIds.getClientTagId(), "t1");
		// the response contains a generated tagId
		Assert.assertNotNull(tagIds.getTagId());
		Assert.assertNotEquals(tagIds.getClientTagId(), tagIds.getTagId());

		// the tag is assigned to the antenna with the generated tagId
		Assert.assertEquals(dataCopy.getAntennas().getAntenna().get(0)
				.getTags().getTag().get(0).getTagId(), tagIds.getTagId());

		// try to create a tag for an unknown antenna
		tagRequest.getTags().getTag().get(0).getAntennas().getAntenna().get(0)
				.setAntennaId(2);
		try {
			hwData.process(tagRequest);
			Assert.fail();
		} catch (HardwareMgmtException e) {
			Assert.assertEquals(e.getErrorCode(), ErrorCode.UNKNOWN_ANTENNA);
			Assert.assertTrue(e.getMessage().contains(
					"Unknown antenna identifier 2"));
		}

		// roll back all changes
		hwData.rollback();
		// the antenna can be created again
		hwData.process(antennaRequest);

		// commit the adding of the antenna
		hwData.commit();
		// try to create the antenna again
		try {
			hwData.process(antennaRequest);
			Assert.fail();
		} catch (HardwareMgmtException e) {
			Assert.assertEquals(e.getErrorCode(), ErrorCode.ANTENNA_EXISTS);
			Assert.assertTrue(e.getMessage().contains("1 already exists"));
		}
	}

	@Test
	public void createIOsSubscribers() throws Exception {
		// create an antenna
		HardwareType initialData = new HardwareType();
		initialData.setConfigId("c1");
		Hardware hwData = new Hardware(initialData);

		// create IOIO
		HardwareOperationType op = readXMLOperation("createIO.xml");
		RequestCreateType gpioRequest = (RequestCreateType) op.getRequest()
				.get(0).getChoice().get(0);
		ResponseCreateType gpioCreateResponse = hwData.process(gpioRequest);

		Assert.assertEquals(gpioCreateResponse.getOperationId(), "o3");

		// try to create the IO again
		try {
			hwData.process(gpioRequest);
			Assert.fail();
		} catch (HardwareMgmtException e) {
			Assert.assertEquals(e.getErrorCode(), ErrorCode.IO_EXISTS);
			Assert.assertTrue(e.getMessage().contains("10 already exists"));
		}

		// try to create a subscriber with an invalid subscriber instance
		op = readXMLOperation("createSubscriberTemplate.xml");
		RequestCreateType subscriberRequest = (RequestCreateType) op
				.getRequest().get(0).getChoice().get(0);
		subscriberRequest.getSubscribers().getSubscriber().get(0)
				.setInstance(new SubscriberInstanceType());
		try {
			gpioCreateResponse = hwData.process(subscriberRequest);
			Assert.fail();
		} catch (HardwareMgmtException e) {
			Assert.assertTrue(e.getMessage().contains(
					"Subscriber instance does not"));
		}

		// create a subscriber with a valid subscriber instance
		SubscriberInstanceType subscriberInstance = new SubscriberInstance();
		subscriberRequest.getSubscribers().getSubscriber().get(0)
				.setInstance(subscriberInstance);
		ResponseCreateType subscriberCreateResponse = hwData
				.process(subscriberRequest);

		Assert.assertEquals(subscriberCreateResponse.getOperationId(), "o4");

		ResponseCreateSubscriberIdsType subscriberIds = subscriberCreateResponse
				.getSubscribers().getSubscriberIds().get(0);
		Assert.assertEquals(subscriberIds.getClientSubscriberId(), "s1");
		// the response contains a generated subscriberId
		Assert.assertNotNull(subscriberIds.getSubscriberId());
		Assert.assertNotEquals(subscriberIds.getClientSubscriberId(),
				subscriberIds.getSubscriberId());

		// commit the adding of the subscriber
		hwData.commit();

		// create a subscriber again
		hwData.process(subscriberRequest);

		// the subscriber instance has not been copied
		HardwareType data = Deencapsulation.getField(hwData, "data");
		Assert.assertEquals(data.getRuntime().getSubscribers().getSubscriber()
				.get(0).getInstance(), subscriberInstance);
		HardwareType dataCopy = Deencapsulation.getField(hwData, "dataCopy");
		Assert.assertEquals(dataCopy.getRuntime().getSubscribers()
				.getSubscriber().size(), 2);
		for (SubscriberType subscriber : dataCopy.getRuntime().getSubscribers()
				.getSubscriber()) {
			Assert.assertEquals(subscriber.getInstance(), subscriberInstance);
		}
	}

	@Test
	public void deleteAntennasTags() throws Exception {
		// create an antenna and an assigned tag
		HardwareType initialData = new HardwareType();
		initialData.setConfigId("c1");
		Hardware hwData = new Hardware(initialData);

		HardwareOperationType op = readXMLOperation("createAntenna.xml");
		RequestCreateType antennaCreateRequest = (RequestCreateType) op
				.getRequest().get(0).getChoice().get(0);
		hwData.process(antennaCreateRequest);

		op = readXMLOperation("createTag.xml");
		RequestCreateType tagCreateRequest = (RequestCreateType) op
				.getRequest().get(0).getChoice().get(0);
		hwData.process(tagCreateRequest);

		// delete the antenna
		op = readXMLOperation("deleteAntenna.xml");
		RequestDeleteType antennaDeleteRequest = (RequestDeleteType) op
				.getRequest().get(0).getChoice().get(0);
		ResponseDeleteType antennaDeleteResponse = hwData
				.process(antennaDeleteRequest);
		Assert.assertEquals(antennaDeleteResponse.getOperationId(),
				antennaDeleteRequest.getOperationId());

		// the antenna and tag can be created again
		hwData.process(antennaCreateRequest);
		ResponseCreateType tagCreateResponse = hwData.process(tagCreateRequest);
		String tagId = tagCreateResponse.getTags().getTagIds().get(0)
				.getTagId();

		// delete all antennas
		op = readXMLOperation("deleteAntenna.xml");
		antennaDeleteRequest = (RequestDeleteType) op.getRequest().get(0)
				.getChoice().get(0);
		antennaDeleteRequest.getAntennas().getAntennaId().clear();
		antennaDeleteResponse = hwData.process(antennaDeleteRequest);

		// the antenna and tag can be created again
		hwData.process(antennaCreateRequest);
		tagCreateResponse = hwData.process(tagCreateRequest);
		tagId = tagCreateResponse.getTags().getTagIds().get(0).getTagId();

		// get internal data structure
		HardwareType dataCopy = Deencapsulation.getField(hwData, "dataCopy");
		// the tag is assigned to the antenna
		Assert.assertEquals(dataCopy.getAntennas().getAntenna().get(0)
				.getTags().getTag().get(0).getTagId(), tagId);

		// delete the tag
		op = readXMLOperation("deleteTag.xml");
		RequestDeleteType tagDeleteRequest = (RequestDeleteType) op
				.getRequest().get(0).getChoice().get(0);
		tagDeleteRequest.getTags().getTagId().set(0, tagId);
		hwData.process(tagDeleteRequest);

		Assert.assertNull(dataCopy.getTags());

		// the antenna has no tag assigned anymore
		Assert.assertNull(dataCopy.getAntennas().getAntenna().get(0).getTags());

		// the tag can be created again
		tagCreateResponse = hwData.process(tagCreateRequest);
		tagId = tagCreateResponse.getTags().getTagIds().get(0).getTagId();

		// the tag is assigned to the antenna
		Assert.assertEquals(dataCopy.getAntennas().getAntenna().get(0)
				.getTags().getTag().get(0).getTagId(), tagId);

		// create further tags
		tagCreateResponse = hwData.process(tagCreateRequest);
		tagId = tagCreateResponse.getTags().getTagIds().get(0).getTagId();
		hwData.process(tagCreateRequest);

		Assert.assertEquals(dataCopy.getTags().getTag().size(), 3);

		// delete one tag
		op = readXMLOperation("deleteTag.xml");
		tagDeleteRequest = (RequestDeleteType) op.getRequest().get(0)
				.getChoice().get(0);
		tagDeleteRequest.getTags().getTagId().set(0, tagId);
		hwData.process(tagDeleteRequest);

		Assert.assertEquals(dataCopy.getTags().getTag().size(), 2);

		// delete all remaining tags
		op = readXMLOperation("deleteTag.xml");
		tagDeleteRequest = (RequestDeleteType) op.getRequest().get(0)
				.getChoice().get(0);
		tagDeleteRequest.getTags().getTagId().clear();
		hwData.process(tagDeleteRequest);

		Assert.assertNull(dataCopy.getTags());

		// the antenna has no tag assigned anymore
		Assert.assertNull(dataCopy.getAntennas().getAntenna().get(0).getTags());
	}

	@Test
	public void deleteIOsSubscribers() throws Exception {
		HardwareType initialData = new HardwareType();
		initialData.setConfigId("c1");
		Hardware hwData = new Hardware(initialData);

		// create a IO
		HardwareOperationType op = readXMLOperation("createIO.xml");
		RequestCreateType gpioCreateRequest = (RequestCreateType) op
				.getRequest().get(0).getChoice().get(0);
		hwData.process(gpioCreateRequest);

		// get internal data structure
		HardwareType dataCopy = Deencapsulation.getField(hwData, "dataCopy");

		// delete the IO
		op = readXMLOperation("deleteIO.xml");
		RequestDeleteType gpioDeleteRequest = (RequestDeleteType) op
				.getRequest().get(0).getChoice().get(0);
		ResponseDeleteType gpioDeleteResponse = hwData
				.process(gpioDeleteRequest);
		Assert.assertEquals(gpioDeleteResponse.getOperationId(),
				gpioDeleteRequest.getOperationId());

		Assert.assertNull(dataCopy.getIos());

		// the IO can be created again
		hwData.process(gpioCreateRequest);

		// delete all IOs
		op = readXMLOperation("deleteIO.xml");
		gpioDeleteRequest = (RequestDeleteType) op.getRequest().get(0)
				.getChoice().get(0);
		gpioDeleteRequest.getIos().getIoId().clear();
		hwData.process(gpioDeleteRequest);

		Assert.assertNull(dataCopy.getIos());

		// create a subscriber
		op = readXMLOperation("createSubscriberTemplate.xml");
		RequestCreateType subscriberCreateRequest = (RequestCreateType) op
				.getRequest().get(0).getChoice().get(0);
		subscriberCreateRequest.getSubscribers().getSubscriber().get(0)
				.setInstance(new SubscriberInstance());
		ResponseCreateType subscriberCreateResponse = hwData
				.process(subscriberCreateRequest);
		String subscriberId = subscriberCreateResponse.getSubscribers()
				.getSubscriberIds().get(0).getSubscriberId();

		// delete the subscriber
		op = readXMLOperation("deleteSubscriber.xml");
		RequestDeleteType subscriberDeleteRequest = (RequestDeleteType) op
				.getRequest().get(0).getChoice().get(0);
		subscriberDeleteRequest.getSubscribers().getSubscriberId()
				.set(0, subscriberId);
		ResponseDeleteType subscriberDeleteResponse = hwData
				.process(subscriberDeleteRequest);
		Assert.assertEquals(subscriberDeleteResponse.getOperationId(),
				subscriberDeleteRequest.getOperationId());

		Assert.assertNull(dataCopy.getRuntime());

		// the subscriber can be created again
		hwData.process(subscriberCreateRequest);
		// create further subscribers
		subscriberCreateResponse = hwData.process(subscriberCreateRequest);
		subscriberId = subscriberCreateResponse.getSubscribers()
				.getSubscriberIds().get(0).getSubscriberId();
		hwData.process(subscriberCreateRequest);

		Assert.assertEquals(dataCopy.getRuntime().getSubscribers()
				.getSubscriber().size(), 3);

		// delete one subscriber
		op = readXMLOperation("deleteSubscriber.xml");
		subscriberDeleteRequest = (RequestDeleteType) op.getRequest().get(0)
				.getChoice().get(0);
		subscriberDeleteRequest.getSubscribers().getSubscriberId()
				.set(0, subscriberId);
		hwData.process(subscriberDeleteRequest);

		Assert.assertEquals(dataCopy.getRuntime().getSubscribers()
				.getSubscriber().size(), 2);

		// delete all remaining subscribers
		op = readXMLOperation("deleteSubscriber.xml");
		subscriberDeleteRequest = (RequestDeleteType) op.getRequest().get(0)
				.getChoice().get(0);
		subscriberDeleteRequest.getSubscribers().getSubscriberId().clear();
		subscriberDeleteResponse = hwData.process(subscriberDeleteRequest);

		Assert.assertNull(dataCopy.getRuntime());
	}

	@Test
	public void readAntennasTags() throws Exception {
		// create an antenna
		HardwareType initialData = new HardwareType();
		initialData.setConfigId("c1");
		Hardware hwData = new Hardware(initialData);

		HardwareOperationType op = readXMLOperation("createAntenna.xml");
		RequestCreateType antennaCreateRequest = (RequestCreateType) op
				.getRequest().get(0).getChoice().get(0);
		hwData.process(antennaCreateRequest);

		// try to read from a non-existing antenna
		op = readXMLOperation("readAntennaTag.xml");
		RequestReadType readRequest = (RequestReadType) op.getRequest().get(0)
				.getChoice().get(0);
		readRequest.getAntennas().getAntennaId().set(0, 2);
		try {
			hwData.process(readRequest);
			Assert.fail();
		} catch (HardwareMgmtException e) {
			Assert.assertEquals(e.getErrorCode(), ErrorCode.UNKNOWN_ANTENNA);
			Assert.assertTrue(e.getMessage().contains(
					"Unknown antenna identifier 2"));
		}

		// try to read antenna and tag with valid antennaId
		// the tag has not been created up to now and cannot be read
		readRequest.getAntennas().getAntennaId().set(0, 1);
		try {
			hwData.process(readRequest);
			Assert.fail();
		} catch (HardwareMgmtException e) {
			Assert.assertEquals(e.getErrorCode(), ErrorCode.UNKNOWN_TAG);
			Assert.assertTrue(e.getMessage().contains(
					"Unknown tag identifier t1"));
		}

		// create the missing tag
		op = readXMLOperation("createTag.xml");
		RequestCreateType tagCreateRequest = (RequestCreateType) op
				.getRequest().get(0).getChoice().get(0);
		ResponseCreateType tagCreateResponse = hwData.process(tagCreateRequest);
		String tagId = tagCreateResponse.getTags().getTagIds().get(0)
				.getTagId();

		// read antenna and tag
		readRequest.getTags().getTagId().set(0, tagId);
		ResponseReadType readResponse = hwData.process(readRequest);
		Assert.assertEquals(readResponse.getOperationId(), "o1");
		ResponseReadAntennaType antenna = readResponse.getAntennas()
				.getAntenna().get(0);
		Assert.assertEquals(antenna.getAntennaId(), 1);
		Assert.assertEquals(antenna.getAirProtocol(),
				AirProtocolEnumeration.EPC_GLOBAL_CLASS_1_GEN_2);
		RequestCreateTagType tag = readResponse.getTags().getTag().get(0);
		Assert.assertEquals(tag.getTagId(), tagId);
		Assert.assertEquals(tag.getMemoryBanks().getMemoryBank().get(0)
				.getName(), MemoryBankNameEnumeration.EPC_BANK);
		RequestCreateTagAntennaType tagAntenna = tag.getAntennas().getAntenna()
				.get(0);
		Assert.assertEquals(tagAntenna.getAntennaId().longValue(),
				antenna.getAntennaId());
		Assert.assertEquals(tagAntenna.getPeakRSSI().longValue(), 3);

		// read all antennas and tags using empty "antennas" and "tags" tags
		// the created antenna and the assigned tag are returned
		readRequest.getAntennas().getAntennaId().clear();
		readRequest.getTags().getTagId().clear();
		readResponse = hwData.process(readRequest);
		Assert.assertEquals(readResponse.getAntennas().getAntenna().size(), 1);
		Assert.assertEquals(readResponse.getTags().getTag().size(), 1);

		// read all antennas and tags by reading the whole hardware config
		// the created antenna and the assigned tag are returned
		readRequest.setAntennas(null);
		readRequest.setTags(null);
		readResponse = hwData.process(readRequest);
		Assert.assertEquals(readResponse.getAntennas().getAntenna().size(), 1);
		Assert.assertEquals(readResponse.getTags().getTag().size(), 1);

		// delete antenna and tag
		op = readXMLOperation("deleteAntenna.xml");
		RequestDeleteType antennaDeleteRequest = (RequestDeleteType) op
				.getRequest().get(0).getChoice().get(0);
		hwData.process(antennaDeleteRequest);

		// read all antennas and tags
		// no antenna or tag exist
		readResponse = hwData.process(readRequest);
		Assert.assertNull(readResponse.getAntennas());
		Assert.assertNull(readResponse.getTags());
	}

	@Test
	public void readIOsSubscribers() throws Exception {
		HardwareType initialData = new HardwareType();
		initialData.setConfigId("c1");
		Hardware hwData = new Hardware(initialData);

		// create a IO
		HardwareOperationType op = readXMLOperation("createIO.xml");
		RequestCreateType gpioCreateRequest = (RequestCreateType) op
				.getRequest().get(0).getChoice().get(0);
		hwData.process(gpioCreateRequest);

		// try to read from a non-existing IO
		op = readXMLOperation("readIO.xml");
		RequestReadType gpioReadRequest = (RequestReadType) op.getRequest()
				.get(0).getChoice().get(0);
		gpioReadRequest.getIos().getIoId().set(0, 2);
		try {
			hwData.process(gpioReadRequest);
			Assert.fail();
		} catch (HardwareMgmtException e) {
			Assert.assertEquals(e.getErrorCode(), ErrorCode.UNKNOWN_IO);
			Assert.assertTrue(e.getMessage().contains(
					"Unknown IO identifier 2"));
		}

		// read IO data
		op = readXMLOperation("readIO.xml");
		gpioReadRequest = (RequestReadType) op.getRequest().get(0).getChoice()
				.get(0);
		ResponseReadType gpioReadResponse = hwData.process(gpioReadRequest);

		Assert.assertEquals(gpioReadResponse.getOperationId(),
				gpioReadRequest.getOperationId());
		RequestCreateIOType gpio = gpioReadResponse.getIos().getIo().get(0);
		Assert.assertEquals(gpio.getIoId(), gpioReadRequest.getIos().getIoId()
				.get(0).intValue());
		Assert.assertEquals(gpio.getDirection(), IODirectionEnumeration.INPUT);
		Assert.assertEquals(gpio.getState(), IOStateEnumeration.LOW);

		// read all IOs using empty "IOs" tag
		op = readXMLOperation("readIO.xml");
		gpioReadRequest = (RequestReadType) op.getRequest().get(0).getChoice()
				.get(0);
		gpioReadRequest.getIos().getIoId().clear();
		gpioReadResponse = hwData.process(gpioReadRequest);

		Assert.assertEquals(gpioReadResponse.getIos().getIo().size(), 1);

		// read all IOs by reading the whole hardware config
		op = readXMLOperation("readIO.xml");
		gpioReadRequest = (RequestReadType) op.getRequest().get(0).getChoice()
				.get(0);
		gpioReadRequest.setIos(null);
		gpioReadResponse = hwData.process(gpioReadRequest);

		Assert.assertEquals(gpioReadResponse.getIos().getIo().size(), 1);

		// create a subscriber
		op = readXMLOperation("createSubscriberTemplate.xml");
		RequestCreateType subscriberCreateRequest = (RequestCreateType) op
				.getRequest().get(0).getChoice().get(0);
		SubscriberInstance subscriberInstance = new SubscriberInstance();
		subscriberCreateRequest.getSubscribers().getSubscriber().get(0)
				.setInstance(subscriberInstance);
		ResponseCreateType subscriberCreateResponse = hwData
				.process(subscriberCreateRequest);
		String subscriberId = subscriberCreateResponse.getSubscribers()
				.getSubscriberIds().get(0).getSubscriberId();

		// try to read from a non-existing subscriber
		op = readXMLOperation("readSubscriber.xml");
		RequestReadType subscriberReadRequest = (RequestReadType) op
				.getRequest().get(0).getChoice().get(0);
		subscriberReadRequest.getSubscribers().getSubscriberId().set(0, "a");
		try {
			hwData.process(subscriberReadRequest);
			Assert.fail();
		} catch (HardwareMgmtException e) {
			Assert.assertEquals(e.getErrorCode(), ErrorCode.UNKNOWN_SUBSCRIBER);
			Assert.assertTrue(e.getMessage().contains(
					"Unknown subscriber identifier a"));
		}

		// read subscriber data
		op = readXMLOperation("readSubscriber.xml");
		subscriberReadRequest = (RequestReadType) op.getRequest().get(0)
				.getChoice().get(0);
		subscriberReadRequest.getSubscribers().getSubscriberId()
				.set(0, subscriberId);
		ResponseReadType subscriberReadResponse = hwData
				.process(subscriberReadRequest);

		Assert.assertEquals(subscriberReadRequest.getOperationId(),
				subscriberReadRequest.getOperationId());
		RequestCreateSubscriberType subscriber = subscriberReadResponse
				.getSubscribers().getSubscriber().get(0);
		Assert.assertEquals(subscriber.getSubscriberId(), subscriberReadRequest
				.getSubscribers().getSubscriberId().get(0));
		Assert.assertTrue(subscriber.getInstance() == subscriberInstance);

		// read all subscribers using empty "subscribers" tag
		op = readXMLOperation("readSubscriber.xml");
		subscriberReadRequest = (RequestReadType) op.getRequest().get(0)
				.getChoice().get(0);
		subscriberReadRequest.getSubscribers().getSubscriberId().clear();
		subscriberReadResponse = hwData.process(subscriberReadRequest);

		Assert.assertEquals(subscriberReadResponse.getSubscribers()
				.getSubscriber().size(), 1);

		// read all subscribers by reading the whole hardware config
		op = readXMLOperation("readSubscriber.xml");
		subscriberReadRequest = (RequestReadType) op.getRequest().get(0)
				.getChoice().get(0);
		subscriberReadRequest.setSubscribers(null);
		subscriberReadResponse = hwData.process(subscriberReadRequest);

		Assert.assertEquals(subscriberReadResponse.getSubscribers()
				.getSubscriber().size(), 1);
	}

	@Test
	public void updateAntennasTags() throws Exception {
		HardwareType initialData = new HardwareType();
		initialData.setConfigId("c1");
		Hardware hwData = new Hardware(initialData);

		// create an antenna
		HardwareOperationType op = readXMLOperation("createAntenna.xml");
		String configId = op.getRequest().get(0).getConfigId();
		RequestCreateType antennaCreateRequest = (RequestCreateType) op
				.getRequest().get(0).getChoice().get(0);
		hwData.process(antennaCreateRequest);

		// try to update a non-existing antenna
		op = readXMLOperation("updateAntennaTag.xml");
		RequestUpdateType antennaUpdateRequest = (RequestUpdateType) op
				.getRequest().get(0).getChoice().get(0);
		antennaUpdateRequest.getAntennas().getAntenna().get(0).setAntennaId(2);
		try {
			hwData.process(configId, antennaUpdateRequest);
			Assert.fail();
		} catch (HardwareMgmtException e) {
			Assert.assertEquals(e.getErrorCode(), ErrorCode.UNKNOWN_ANTENNA);
			Assert.assertTrue(e.getMessage().contains(
					"Unknown antenna identifier 2"));
		}

		// try to update antenna and tag with valid antennaId
		// the tag has not been created up to now and cannot be updated
		op = readXMLOperation("updateAntennaTag.xml");
		antennaUpdateRequest = (RequestUpdateType) op.getRequest().get(0)
				.getChoice().get(0);
		try {
			hwData.process(configId, antennaUpdateRequest);
			Assert.fail();
		} catch (HardwareMgmtException e) {
			Assert.assertEquals(e.getErrorCode(), ErrorCode.UNKNOWN_TAG);
			Assert.assertTrue(e.getMessage().contains(
					"Unknown tag identifier t1"));
		}

		// create the missing tag
		op = readXMLOperation("createTag.xml");
		RequestCreateType tagCreateRequest = (RequestCreateType) op
				.getRequest().get(0).getChoice().get(0);
		ResponseCreateType tagCreateResponse = hwData.process(tagCreateRequest);
		String tagId = tagCreateResponse.getTags().getTagIds().get(0)
				.getTagId();

		// get internal data structure
		HardwareType dataCopy = Deencapsulation.getField(hwData, "dataCopy");
		Assert.assertEquals(dataCopy.getTags().getTag().get(0).getMemoryBanks()
				.getMemoryBank().get(0).getName(),
				MemoryBankNameEnumeration.EPC_BANK);
		Assert.assertEquals(dataCopy.getAntennas().getAntenna().get(0)
				.getTags().getTag().get(0).getPeakRSSI().intValue(), 3);

		// update antenna and tag
		antennaUpdateRequest.getTags().getTag().get(0).setTagId(tagId);
		ResponseUpdateType updateResponse = hwData.process(configId,
				antennaUpdateRequest);
		Assert.assertEquals(updateResponse.getOperationId(),
				antennaUpdateRequest.getOperationId());
		// the tag properties have been updated
		Assert.assertEquals(dataCopy.getTags().getTag().get(0).getMemoryBanks()
				.getMemoryBank().get(0).getName(),
				MemoryBankNameEnumeration.TID_BANK);
		// the tag properties for the assigned antenna have been updated
		Assert.assertEquals(dataCopy.getAntennas().getAntenna().get(0)
				.getTags().getTag().get(0).getPeakRSSI().intValue(), 4);

		// try to update tag with invalid antennaId
		antennaUpdateRequest.getTags().getTag().get(0).getAntennas()
				.getAntenna().get(0).setAntennaId(2);
		try {
			hwData.process(configId, antennaUpdateRequest);
			Assert.fail();
		} catch (HardwareMgmtException e) {
			Assert.assertEquals(e.getErrorCode(), ErrorCode.UNKNOWN_ANTENNA);
			Assert.assertTrue(e.getMessage().contains(
					"Unknown antenna identifier 2"));
		}

		// update tag with valid antennaId but with missing tag assignment in
		// antenna structure
		antennaUpdateRequest.getTags().getTag().get(0).getAntennas()
				.getAntenna().get(0).setAntennaId(1);
		dataCopy.getAntennas().getAntenna().get(0).setTags(null);
		hwData.process(configId, antennaUpdateRequest);
		// the tag assignment has been created
		Assert.assertEquals(dataCopy.getAntennas().getAntenna().get(0)
				.getTags().getTag().get(0).getPeakRSSI().intValue(), 4);
	}

	@Test
	public void updateIOsSubscribers() throws Exception {
		HardwareType initialData = new HardwareType();
		initialData.setConfigId("c1");
		Hardware hwData = new Hardware(initialData);

		// create a IO
		HardwareOperationType op = readXMLOperation("createIO.xml");
		RequestCreateType gpioCreateRequest = (RequestCreateType) op
				.getRequest().get(0).getChoice().get(0);
		hwData.process(gpioCreateRequest);

		// try to update a non-existing IO
		op = readXMLOperation("updateIO.xml");
		String configId = op.getRequest().get(0).getConfigId();
		RequestUpdateType gpioUpdateRequest = (RequestUpdateType) op
				.getRequest().get(0).getChoice().get(0);
		gpioUpdateRequest.getIos().getIo().get(0).setIoId(2);
		try {
			hwData.process(configId, gpioUpdateRequest);
			Assert.fail();
		} catch (HardwareMgmtException e) {
			Assert.assertEquals(e.getErrorCode(), ErrorCode.UNKNOWN_IO);
			Assert.assertTrue(e.getMessage().contains(
					"Unknown IO identifier 2"));
		}

		// update IO
		op = readXMLOperation("updateIO.xml");
		gpioUpdateRequest = (RequestUpdateType) op.getRequest().get(0)
				.getChoice().get(0);
		ResponseUpdateType gpioUpdateResponse = hwData.process(configId,
				gpioUpdateRequest);
		Assert.assertEquals(gpioUpdateResponse.getOperationId(),
				gpioUpdateRequest.getOperationId());

		// get internal data structure
		HardwareType dataCopy = Deencapsulation.getField(hwData, "dataCopy");
		// the IO properties have been updated
		Assert.assertEquals(dataCopy.getIos().getIo().get(0).getState(),
				IOStateEnumeration.HIGH);

		// create a subscriber
		op = readXMLOperation("createSubscriberTemplate.xml");
		RequestCreateType subscriberCreateRequest = (RequestCreateType) op
				.getRequest().get(0).getChoice().get(0);
		subscriberCreateRequest.getSubscribers().getSubscriber().get(0)
				.setInstance(new SubscriberInstance());
		ResponseCreateType subscriberCreateResponse = hwData
				.process(subscriberCreateRequest);
		String subscriberId = subscriberCreateResponse.getSubscribers()
				.getSubscriberIds().get(0).getSubscriberId();

		// try to update a non-existing subscriber
		op = readXMLOperation("updateSubscriberTemplate.xml");
		RequestUpdateType subscriberUpdateRequest = (RequestUpdateType) op
				.getRequest().get(0).getChoice().get(0);
		subscriberUpdateRequest.getSubscribers().getSubscriber().get(0)
				.setSubscriberId("a");
		try {
			hwData.process(configId, subscriberUpdateRequest);
			Assert.fail();
		} catch (HardwareMgmtException e) {
			Assert.assertEquals(e.getErrorCode(), ErrorCode.UNKNOWN_SUBSCRIBER);
			Assert.assertTrue(e.getMessage().contains(
					"Unknown subscriber identifier a"));
		}

		// update subscriber
		op = readXMLOperation("updateSubscriberTemplate.xml");
		subscriberUpdateRequest = (RequestUpdateType) op.getRequest().get(0)
				.getChoice().get(0);
		subscriberUpdateRequest.getSubscribers().getSubscriber().get(0)
				.setSubscriberId(subscriberId);
		subscriberCreateRequest.getSubscribers().getSubscriber().get(0)
				.setInstance(new SubscriberInstance());
		ResponseUpdateType subscriberUpdateResponse = hwData.process(configId,
				subscriberUpdateRequest);
		Assert.assertEquals(subscriberUpdateResponse.getOperationId(),
				subscriberUpdateRequest.getOperationId());

		// the subscriber properties have been updated
		RequestCreateSubscriberType oldProps = subscriberUpdateRequest
				.getSubscribers().getSubscriber().get(0);
		SubscriberType newProps = dataCopy.getRuntime().getSubscribers()
				.getSubscriber().get(0);
		Assert.assertEquals(newProps.getIos().getIo().get(0).getIoId(),
				oldProps.getIos().getIo().get(0).getIoId());
		Assert.assertTrue(newProps.getInstance() == oldProps.getInstance());
	}

	@Test
	public void gpioEvent() throws Exception {
		HardwareType initialData = new HardwareType();
		initialData.setConfigId("c1");
		Hardware hwData = new Hardware(initialData);

		// create a IO
		HardwareOperationType op = readXMLOperation("createIO.xml");
		RequestCreateType gpioCreateRequest = (RequestCreateType) op
				.getRequest().get(0).getChoice().get(0);
		hwData.process(gpioCreateRequest);
		int gpioId = gpioCreateRequest.getIos().getIo().get(0).getIoId();

		// create a subscriber which observes the IO
		op = readXMLOperation("createSubscriberTemplate.xml");
		RequestCreateType subscriberCreateRequest = (RequestCreateType) op
				.getRequest().get(0).getChoice().get(0);
		RequestCreateSubscriberType subscriberProps = subscriberCreateRequest
				.getSubscribers().getSubscriber().get(0);
		SubscriberInstance subscriberInstance1 = new SubscriberInstance();
		subscriberProps.setInstance(subscriberInstance1);
		SubscriberIOsType gpios = new SubscriberIOsType();
		SubscriberIOType subscriberIO = new SubscriberIOType();
		subscriberIO.setIoId(gpioId);
		subscriberIO.setStateChanged(true);
		gpios.getIo().add(subscriberIO);
		subscriberProps.setIos(gpios);
		hwData.process(subscriberCreateRequest);

		// create a subscriber which does NOT observe the IO
		op = readXMLOperation("createSubscriberTemplate.xml");
		subscriberCreateRequest = (RequestCreateType) op.getRequest().get(0)
				.getChoice().get(0);
		subscriberProps = subscriberCreateRequest.getSubscribers()
				.getSubscriber().get(0);
		SubscriberInstance subscriberInstance2 = new SubscriberInstance();
		subscriberProps.setInstance(subscriberInstance2);
		subscriberProps.getIos().getIo().get(0).setStateChanged(false);
		hwData.process(subscriberCreateRequest);

		// update the IO
		op = readXMLOperation("updateIO.xml");
		String configId = op.getRequest().get(0).getConfigId();
		RequestUpdateType gpioUpdateRequest = (RequestUpdateType) op
				.getRequest().get(0).getChoice().get(0);
		hwData.process(configId, gpioUpdateRequest);

		// a IO event is sent to the first subscriber
		Assert.assertEquals(
				subscriberInstance1.getNotification().getConfigId(), configId);
		NotificationIOType notificationIO = subscriberInstance1
				.getNotification().getIos().getIo().get(0);
		Assert.assertEquals(notificationIO.getIoId(), gpioCreateRequest
				.getIos().getIo().get(0).getIoId());
		Assert.assertEquals(notificationIO.getStateChanged().getOld(),
				gpioCreateRequest.getIos().getIo().get(0).getState());
		Assert.assertEquals(notificationIO.getStateChanged().getNew(),
				gpioUpdateRequest.getIos().getIo().get(0).getState());
		// the second subscriber has not been informed about the change
		Assert.assertNull(subscriberInstance2.getNotification());
	}

	private HardwareOperationType readXMLOperation(String fileName)
			throws JAXBException, IOException, SAXException {
		Path initialPath = new PathHandler().toAbsolutePath(BASE_RESOURCE_PATH
				.resolve(fileName));
		XMLFile<HardwareOperationType> xmlFile = new XMLFile<HardwareOperationType>(
				HardwareOperationType.class, initialPath, null /* latestPath */);
		return xmlFile.getContent();
	}

}
