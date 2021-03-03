package havis.device.test.hardware.hardwareMgmt.xml;

import havis.device.test.hardware.AntennaTagType;
import havis.device.test.hardware.AntennaTagsType;
import havis.device.test.hardware.AntennaType;
import havis.device.test.hardware.AntennasType;
import havis.device.test.hardware.Cloner;
import havis.device.test.hardware.ErrorCode;
import havis.device.test.hardware.HardwareMgmtException;
import havis.device.test.hardware.HardwareMgmtSubscriber;
import havis.device.test.hardware.HardwareType;
import havis.device.test.hardware.IOStateEnumeration;
import havis.device.test.hardware.IOType;
import havis.device.test.hardware.IOsType;
import havis.device.test.hardware.NotificationIOType;
import havis.device.test.hardware.NotificationIOsStateChangedType;
import havis.device.test.hardware.NotificationIOsType;
import havis.device.test.hardware.NotificationType;
import havis.device.test.hardware.RequestCreateAntennaType;
import havis.device.test.hardware.RequestCreateAntennasType;
import havis.device.test.hardware.RequestCreateIOType;
import havis.device.test.hardware.RequestCreateIOsType;
import havis.device.test.hardware.RequestCreateSubscriberType;
import havis.device.test.hardware.RequestCreateSubscribersType;
import havis.device.test.hardware.RequestCreateTagAntennaType;
import havis.device.test.hardware.RequestCreateTagAntennasType;
import havis.device.test.hardware.RequestCreateTagType;
import havis.device.test.hardware.RequestCreateTagsType;
import havis.device.test.hardware.RequestCreateType;
import havis.device.test.hardware.RequestDeleteAntennasType;
import havis.device.test.hardware.RequestDeleteIOsType;
import havis.device.test.hardware.RequestDeleteSubscribersType;
import havis.device.test.hardware.RequestDeleteTagsType;
import havis.device.test.hardware.RequestDeleteType;
import havis.device.test.hardware.RequestReadType;
import havis.device.test.hardware.RequestUpdateType;
import havis.device.test.hardware.ResponseCreateSubscriberIdsType;
import havis.device.test.hardware.ResponseCreateSubscribersType;
import havis.device.test.hardware.ResponseCreateTagIdsType;
import havis.device.test.hardware.ResponseCreateTagsType;
import havis.device.test.hardware.ResponseCreateType;
import havis.device.test.hardware.ResponseDeleteType;
import havis.device.test.hardware.ResponseReadAntennaTagsType;
import havis.device.test.hardware.ResponseReadAntennaType;
import havis.device.test.hardware.ResponseReadAntennasType;
import havis.device.test.hardware.ResponseReadType;
import havis.device.test.hardware.ResponseUpdateType;
import havis.device.test.hardware.RuntimeType;
import havis.device.test.hardware.SubscriberIOType;
import havis.device.test.hardware.SubscriberInstanceType;
import havis.device.test.hardware.SubscriberType;
import havis.device.test.hardware.SubscribersType;
import havis.device.test.hardware.TagType;
import havis.device.test.hardware.TagsType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Hardware {
	private final Cloner cloner = new Cloner();
	private long idSequence = 0;
	private HardwareType data;
	private HardwareType dataCopy = null;

	public Hardware(HardwareType initialData) {
		this.data = initialData;
	}

	public void commit() {
		if (dataCopy != null) {
			// replace data with modified copy
			data = dataCopy;
			dataCopy = null;
		}
	}

	public void rollback() {
		// removed modified copy
		dataCopy = null;
	}

	public ResponseCreateType process(RequestCreateType request)
			throws HardwareMgmtException {
		dataCopy = getDataCopy();
		ResponseCreateType response = new ResponseCreateType();
		response.setOperationId(request.getOperationId());

		// create antennas
		createAntennas(request);
		// create tags and add the tagId mapping to the response
		createTags(request, response);
		// create IOs
		createIOs(request);
		// create subscribers
		createSubscribers(request, response);
		return response;
	}

	private void createAntennas(RequestCreateType request)
			throws HardwareMgmtException {
		RequestCreateAntennasType antennasRequest = request.getAntennas();
		// if an antenna shall be created
		if (antennasRequest != null) {
			// create structure for antennas if it does not exist yet
			AntennasType antennas = dataCopy.getAntennas();
			if (antennas == null) {
				antennas = new AntennasType();
				dataCopy.setAntennas(antennas);
			}
			// for each new antenna
			for (RequestCreateAntennaType antennaRequest : antennasRequest
					.getAntenna()) {
				// check for existing antenna
				for (AntennaType antenna : antennas.getAntenna()) {
					if (antenna.getAntennaId() == antennaRequest.getAntennaId()) {
						throw new HardwareMgmtException(
								ErrorCode.ANTENNA_EXISTS, "Antenna identifier "
										+ antennaRequest.getAntennaId()
										+ " already exists",
								request.getOperationId());
					}
				}
				// create antenna
				AntennaType antenna = new AntennaType();
				antenna.setAntennaId(antennaRequest.getAntennaId());
				antenna.setAirProtocol(antennaRequest.getAirProtocol());
				antennas.getAntenna().add(antenna);
			}
		}
	}

	private void createTags(RequestCreateType request,
			ResponseCreateType response) throws HardwareMgmtException {
		RequestCreateTagsType tagsRequest = request.getTags();
		// if tags shall be created
		if (tagsRequest != null) {
			// create structure for tags if it does not exist yet
			TagsType tags = dataCopy.getTags();
			if (tags == null) {
				tags = new TagsType();
				dataCopy.setTags(tags);
			}
			// for each new tag
			for (RequestCreateTagType tagRequest : tagsRequest.getTag()) {
				// create a tag with an own identifier
				TagType tag = new TagType();
				tag.setTagId(String.valueOf(idSequence++));
				
				// clone tagPropertiesElemGroup
				tag.setMemoryBanks(cloner.deepClone(tagRequest.getMemoryBanks()));
				tag.setLocks(cloner.deepClone(tagRequest.getLocks()));		
				tag.setKilled(tagRequest.isKilled());
				
				tags.getTag().add(tag);

				// for each antenna assignment of request
				for (RequestCreateTagAntennaType tagAntenna : tagRequest
						.getAntennas().getAntenna()) {
					AntennaType antenna = null;
					if (dataCopy.getAntennas() != null) {
						// get antenna structure
						for (AntennaType a : dataCopy.getAntennas()
								.getAntenna()) {
							if (a.getAntennaId() == tagAntenna.getAntennaId()) {
								antenna = a;
								break;
							}
						}
					}
					if (antenna == null) {
						throw new HardwareMgmtException(
								ErrorCode.UNKNOWN_ANTENNA,
								"Unknown antenna identifier "
										+ tagAntenna.getAntennaId(),
								request.getOperationId());
					}
					// create structure for tag assignment if it does
					// not exist yet
					AntennaTagsType antennaTags = antenna.getTags();
					if (antennaTags == null) {
						antennaTags = new AntennaTagsType();
						antenna.setTags(antennaTags);
					}
					// assign the tag to the antenna
					AntennaTagType antennaTag = new AntennaTagType();
					antennaTag.setTagId(tag.getTagId());
					
					// clone antennaTagPropertiesElemGroup
					antennaTag.setPeakRSSI(cloner.deepClone(tagAntenna.getPeakRSSI()));
					antennaTag.setError(cloner.deepClone(tagAntenna.getError()));
					
					antennaTags.getTag().add(antennaTag);
				}

				// create response part
				ResponseCreateTagsType tagsResponse = response.getTags();
				if (tagsResponse == null) {
					tagsResponse = new ResponseCreateTagsType();
					response.setTags(tagsResponse);
				}
				ResponseCreateTagIdsType tagIds = new ResponseCreateTagIdsType();
				tagIds.setClientTagId(tagRequest.getTagId());
				tagIds.setTagId(tag.getTagId());
				tagsResponse.getTagIds().add(tagIds);
			}
		}
	}

	private void createIOs(RequestCreateType request)
			throws HardwareMgmtException {
		RequestCreateIOsType gpiosRequest = request.getIos();
		// if a IO shall be created
		if (gpiosRequest != null) {
			// create structure for IOs if it does not exist yet
			IOsType gpios = dataCopy.getIos();
			if (gpios == null) {
				gpios = new IOsType();
				dataCopy.setIos(gpios);
			}
			// for each new IO
			for (RequestCreateIOType gpioRequest : gpiosRequest.getIo()) {
				// check for existing IO
				for (IOType gpio : gpios.getIo()) {
					if (gpio.getIoId() == gpioRequest.getIoId()) {
						throw new HardwareMgmtException(ErrorCode.IO_EXISTS,
								"IO identifier " + gpioRequest.getIoId()
										+ " already exists",
								request.getOperationId());
					}
				}
				// create IO
				IOType gpio = new IOType();
				gpio.setIoId(gpioRequest.getIoId());
				
				// clone gpioPropertiesElemGroup
				gpio.setDirection(cloner.deepClone(gpioRequest.getDirection()));
				gpio.setState(cloner.deepClone(gpioRequest.getState()));
				
				gpios.getIo().add(gpio);
			}
		}
	}

	private void createSubscribers(RequestCreateType request,
			ResponseCreateType response) throws HardwareMgmtException {
		RequestCreateSubscribersType subscribersRequest = request
				.getSubscribers();
		// if a subscriber shall be created
		if (subscribersRequest != null) {
			// create structure for subscribers if it does not exist yet
			RuntimeType runtime = dataCopy.getRuntime();
			if (runtime == null) {
				runtime = new RuntimeType();
				dataCopy.setRuntime(runtime);
			}
			SubscribersType subscribers = runtime.getSubscribers();
			if (subscribers == null) {
				subscribers = new SubscribersType();
				runtime.setSubscribers(subscribers);
			}
			// for each new subscriber
			for (RequestCreateSubscriberType subscriberRequest : subscribersRequest
					.getSubscriber()) {
				SubscriberInstanceType instance = subscriberRequest.getInstance();
				if (instance != null
						&& !(instance instanceof HardwareMgmtSubscriber)) {
					throw new HardwareMgmtException(
							ErrorCode.INVALID_SUBSCRIBER_INSTANCE,
							"Subscriber instance does not implement the interface "
									+ HardwareMgmtSubscriber.class.getName(),
							request.getOperationId());
				}
				// create a subscriber with an own identifier and the original
				// instance, the other properties are copied
				SubscriberType subscriber = new SubscriberType();
				subscriber.setSubscriberId(String.valueOf(idSequence++));
				
				subscriber.setInstance(subscriberRequest.getInstance());
				subscriber.setIos(cloner.deepClone(subscriberRequest.getIos()));
				
				subscribers.getSubscriber().add(subscriber);

				// create response part
				ResponseCreateSubscribersType subscribersResponse = response
						.getSubscribers();
				if (subscribersResponse == null) {
					subscribersResponse = new ResponseCreateSubscribersType();
					response.setSubscribers(subscribersResponse);
				}
				ResponseCreateSubscriberIdsType subscriberIds = new ResponseCreateSubscriberIdsType();
				subscriberIds.setClientSubscriberId(subscriberRequest
						.getSubscriberId());
				subscriberIds.setSubscriberId(subscriber.getSubscriberId());
				subscribersResponse.getSubscriberIds().add(subscriberIds);
			}
		}
	}

	public ResponseDeleteType process(RequestDeleteType request) {
		dataCopy = getDataCopy();
		ResponseDeleteType response = new ResponseDeleteType();
		response.setOperationId(request.getOperationId());

		// released tagId -> 0 (released), 1 (deleted)
		final int RELEASED_TAG = 0;
		final int DELETED_TAG = 1;
		Map<String, Integer> releasedTagIds = new HashMap<>();

		// delete antennas and get released tags
		deleteAntennas(request, releasedTagIds, RELEASED_TAG);
		// add tags which shall be deleted to the list of released tags
		getTags4deletion(request, releasedTagIds, DELETED_TAG);
		// delete the assignments from the released tags to the antennas
		deleteAntennaTagAssignments(releasedTagIds, RELEASED_TAG);
		// delete the released tags
		deleteTags(releasedTagIds);
		// delete IOs
		deleteIOs(request);
		// delete subscribers
		deleteSubscribers(request);
		return response;
	}

	private void deleteAntennas(RequestDeleteType request,
			Map<String, Integer> releasedTagIds, final int RELEASED_TAG) {
		RequestDeleteAntennasType antennasRequest = request.getAntennas();
		// if antennas shall be deleted and antennas exist
		if (antennasRequest != null && dataCopy.getAntennas() != null) {
			if (antennasRequest.getAntennaId() == null
					|| antennasRequest.getAntennaId().isEmpty()) {
				// delete all existing antennas and tags
				dataCopy.setAntennas(null);
				dataCopy.setTags(null);
				return;
			}
			List<AntennaType> antennaList = dataCopy.getAntennas()
					.getAntenna();
			// for each antenna of request
			for (int antennaId : antennasRequest.getAntennaId()) {
				// get antenna
				AntennaType antenna = null;
				for (AntennaType a : antennaList) {
					if (a.getAntennaId() == antennaId) {
						antenna = a;
						break;
					}
				}
				if (antenna != null) {
					// if tags are assigned to the antenna
					if (antenna.getTags() != null) {
						// for each assigned tag
						for (AntennaTagType tag : antenna.getTags()
								.getTag()) {
							// add tagId to released tagIds
							releasedTagIds.put(tag.getTagId(), RELEASED_TAG);
						}
					}
					// remove antenna
					if (antennaList.size() > 1) {
						antennaList.remove(antenna);
					} else {
						dataCopy.setAntennas(null);
					}
				}
			}
		}
	}

	private void getTags4deletion(RequestDeleteType request,
			Map<String, Integer> releasedTagIds, final int DELETED_TAG) {
		RequestDeleteTagsType tagsRequest = request.getTags();
		// if tags shall be deleted and tags exist
		if (tagsRequest != null && dataCopy.getTags() != null) {
			if (tagsRequest.getTagId() == null
					|| tagsRequest.getTagId().isEmpty()) {
				// add all existing tagIds to released tagIds
				for (TagType tag : dataCopy.getTags().getTag()) {
					releasedTagIds.put(tag.getTagId(), DELETED_TAG);
				}
			} else {
				// add tagIds of request to released tagIds
				for (String tagId : tagsRequest.getTagId()) {
					releasedTagIds.put(tagId, DELETED_TAG);
				}
			}
		}
	}

	private void deleteAntennaTagAssignments(
			Map<String, Integer> releasedTagIds, final int RELEASED_TAG) {
		if (dataCopy.getAntennas() != null) {
			// for each existing antenna
			for (AntennaType antenna : dataCopy.getAntennas().getAntenna()) {
				// if tags are assigned to the antenna
				if (antenna.getTags() != null) {
					List<AntennaTagType> deleted = new ArrayList<>();
					// for each assigned tag
					List<AntennaTagType> antennaTagList = antenna.getTags()
							.getTag();
					for (AntennaTagType antennaTag : antennaTagList) {
						Integer state = releasedTagIds.get(antennaTag
								.getTagId());
						// if assigned tag is in list
						if (state != null) {
							if (state == RELEASED_TAG) {
								// the tag is in use by this antenna =>
								// remove the tag from the list with the
								// released tags
								releasedTagIds.remove(antennaTag.getTagId());
							} else {
								// delete the assignment
								deleted.add(antennaTag);
							}
						}
					}
					// delete the assignments to the antenna
					if (antennaTagList.size() > deleted.size()) {
						antennaTagList.removeAll(deleted);
					} else {
						antenna.setTags(null);
					}
				}
			}
		}
	}

	private void deleteTags(Map<String, Integer> releasedTagIds) {
		// if tags exist
		if (dataCopy.getTags() != null) {
			List<TagType> tagList = dataCopy.getTags().getTag();
			// for each released tagId
			for (String tagId : releasedTagIds.keySet()) {
				TagType releasedTag = null;
				// get tag
				for (TagType tag : tagList) {
					if (tag.getTagId().equals(tagId)) {
						releasedTag = tag;
						break;
					}
				}
				if (releasedTag != null) {
					// delete tag
					if (tagList.size() > 1) {
						tagList.remove(releasedTag);
					} else {
						dataCopy.setTags(null);
					}
				}
			}
		}
	}

	private void deleteIOs(RequestDeleteType request) {
		RequestDeleteIOsType gpiosRequest = request.getIos();
		// if IOs shall be deleted and IOs exist
		if (gpiosRequest != null && dataCopy.getIos() != null) {
			if (gpiosRequest.getIoId() == null
					|| gpiosRequest.getIoId().isEmpty()) {
				// delete all existing IOs
				dataCopy.setIos(null);
				return;
			}
			List<IOType> gpiosList = dataCopy.getIos().getIo();
			// for each IO of request
			for (int gpioId : gpiosRequest.getIoId()) {
				// get IO
				IOType gpio = null;
				for (IOType g : gpiosList) {
					if (g.getIoId() == gpioId) {
						gpio = g;
						break;
					}
				}
				if (gpio != null) {
					// remove IO
					if (gpiosList.size() > 1) {
						gpiosList.remove(gpio);
					} else {
						dataCopy.setIos(null);
					}
				}
			}
		}
	}

	private void deleteSubscribers(RequestDeleteType request) {
		RequestDeleteSubscribersType subscribersRequest = request
				.getSubscribers();
		// if subscribers shall be deleted and subscribers exist
		if (subscribersRequest != null && dataCopy.getRuntime() != null
				&& dataCopy.getRuntime().getSubscribers() != null) {
			if (subscribersRequest.getSubscriberId() == null
					|| subscribersRequest.getSubscriberId().isEmpty()) {
				// delete all existing subscribers
				dataCopy.setRuntime(null);
				return;
			}
			List<SubscriberType> subscriberList = dataCopy.getRuntime()
					.getSubscribers().getSubscriber();
			// for each subscriber of request
			for (String subscriberId : subscribersRequest.getSubscriberId()) {
				// get subscriber
				SubscriberType subscriber = null;
				for (SubscriberType s : subscriberList) {
					if (s.getSubscriberId().equals(subscriberId)) {
						subscriber = s;
						break;
					}
				}
				if (subscriber != null) {
					// remove subscriber
					if (subscriberList.size() > 1) {
						subscriberList.remove(subscriber);
					} else {
						dataCopy.setRuntime(null);
					}
				}
			}
		}
	}

	public ResponseReadType process(RequestReadType request)
			throws HardwareMgmtException {
		dataCopy = getDataCopy();
		ResponseReadType response = new ResponseReadType();
		response.setOperationId(request.getOperationId());

		if (request.getAntennas() == null && request.getTags() == null
				&& request.getIos() == null
				&& request.getSubscribers() == null) {
			request.setAntennas(new RequestDeleteAntennasType());
			request.setTags(new RequestDeleteTagsType());
			request.setIos(new RequestDeleteIOsType());
			request.setSubscribers(new RequestDeleteSubscribersType());
		}
		// read the antennas and add them to the response
		readAntennas(request, response);
		// read tags and the assignments to antennas and add them to the
		// response
		readTags(request, response);
		// read IO data and add them to the response
		readIOs(request, response);
		// read subscriber data and add them to the response
		readSubscribers(request, response);
		return response;
	}

	private void readAntennas(RequestReadType request, ResponseReadType response)
			throws HardwareMgmtException {
		RequestDeleteAntennasType antennasRequest = request.getAntennas();
		// if antennas shall be read
		if (antennasRequest != null) {
			// get antennaIds
			List<Integer> antennaIds;
			if (antennasRequest.getAntennaId() == null
					|| antennasRequest.getAntennaId().isEmpty()) {
				// get all existing antennaIds
				antennaIds = new ArrayList<>();
				if (dataCopy.getAntennas() != null) {
					for (AntennaType antenna : dataCopy.getAntennas()
							.getAntenna()) {
						antennaIds.add(antenna.getAntennaId());
					}
				}
			} else {
				// get requested antennaIds
				antennaIds = antennasRequest.getAntennaId();
			}
			if (!antennaIds.isEmpty()) {
				ResponseReadAntennasType antennas = new ResponseReadAntennasType();
				response.setAntennas(antennas);
				// for each requested antennaId
				for (int antennaId : antennaIds) {
					// get antenna
					AntennaType antenna = null;
					if (dataCopy.getAntennas() != null) {
						for (AntennaType a : dataCopy.getAntennas()
								.getAntenna()) {
							if (a.getAntennaId() == antennaId) {
								antenna = a;
								break;
							}
						}
					}
					if (antenna == null) {
						throw new HardwareMgmtException(
								ErrorCode.UNKNOWN_ANTENNA,
								"Unknown antenna identifier " + antennaId,
								request.getOperationId());
					}
					// create response part
					ResponseReadAntennaType read = new ResponseReadAntennaType();
					read.setAntennaId(antennaId);
					read.setAirProtocol(antenna.getAirProtocol());
					// get tagIds if tags are assigned to the antenna
					if (antenna.getTags() != null
							&& antenna.getTags().getTag().size() > 0) {
						ResponseReadAntennaTagsType tags = new ResponseReadAntennaTagsType();
						for (AntennaTagType tag : antenna.getTags()
								.getTag()) {
							tags.getTagId().add(tag.getTagId());
						}
						read.setTags(tags);
					}
					antennas.getAntenna().add(read);
				}
			}
		}
	}

	private void readTags(RequestReadType request, ResponseReadType response)
			throws HardwareMgmtException {
		RequestDeleteTagsType tagsRequest = request.getTags();
		// if tags shall be read
		if (tagsRequest != null) {
			// get tagIds
			List<String> tagIds;
			if (tagsRequest.getTagId() == null
					|| tagsRequest.getTagId().isEmpty()) {
				// get all existing tagIds
				tagIds = new ArrayList<>();
				if (dataCopy.getTags() != null) {
					for (TagType tag : dataCopy.getTags().getTag()) {
						tagIds.add(tag.getTagId());
					}
				}
			} else {
				// get requested tagIds
				tagIds = tagsRequest.getTagId();
			}
			if (!tagIds.isEmpty()) {
				RequestCreateTagsType tags = new RequestCreateTagsType();
				response.setTags(tags);
				// for each requested tagId
				for (String tagId : tagIds) {
					// get tag
					TagType tag = null;
					if (dataCopy.getTags() != null) {
						for (TagType t : dataCopy.getTags().getTag()) {
							if (t.getTagId().equals(tagId)) {
								tag = t;
								break;
							}
						}
					}
					if (tag == null) {
						throw new HardwareMgmtException(ErrorCode.UNKNOWN_TAG,
								"Unknown tag identifier " + tagId,
								request.getOperationId());
					}
					// create response part
					RequestCreateTagType read = new RequestCreateTagType();
					read.setTagId(tagId);
					
					// clone tagPropertiesElemGroup
					read.setMemoryBanks(cloner.deepClone(tag.getMemoryBanks()));
					read.setLocks(cloner.deepClone(tag.getLocks()));
					read.setKilled(tag.isKilled());
					
					// get antennaIds if the tag is assigned to antennas
					RequestCreateTagAntennasType antennas = new RequestCreateTagAntennasType();
					read.setAntennas(antennas);
					if (dataCopy.getAntennas() != null) {
						// for each antenna
						for (AntennaType antenna : dataCopy.getAntennas()
								.getAntenna()) {
							if (antenna.getTags() != null) {
								// for each assigned tag
								for (AntennaTagType antennaTag : antenna
										.getTags().getTag()) {
									// if tag is assigned to antenna
									if (antennaTag.getTagId().equals(tagId)) {
										// add antenna tag properties to
										// response list
										RequestCreateTagAntennaType a = new RequestCreateTagAntennaType();
										a.setAntennaId(antenna.getAntennaId());
										
										// clone antennaTagPropertiesElemGroup
										a.setPeakRSSI(antennaTag.getPeakRSSI());
										a.setError(cloner.deepClone(antennaTag.getError()));
										
										antennas.getAntenna().add(a);
										break;
									}
								}
							}
						}
					}
					tags.getTag().add(read);
				}
			}
		}
	}

	private void readIOs(RequestReadType request, ResponseReadType response)
			throws HardwareMgmtException {
		RequestDeleteIOsType gpiosRequest = request.getIos();
		// if IOs shall be read
		if (gpiosRequest != null) {
			// get gpioIds
			List<Integer> gpioIds;
			if (gpiosRequest.getIoId() == null
					|| gpiosRequest.getIoId().isEmpty()) {
				// get all existing gpioIds
				gpioIds = new ArrayList<>();
				if (dataCopy.getIos() != null) {
					for (IOType gpio : dataCopy.getIos().getIo()) {
						gpioIds.add(gpio.getIoId());
					}
				}
			} else {
				// get requested gpioIds
				gpioIds = gpiosRequest.getIoId();
			}
			if (!gpioIds.isEmpty()) {
				RequestCreateIOsType gpios = new RequestCreateIOsType();
				response.setIos(gpios);
				// for each requested gpioId
				for (int gpioId : gpioIds) {
					// get IO
					IOType gpio = null;
					if (dataCopy.getIos() != null) {
						for (IOType g : dataCopy.getIos().getIo()) {
							if (g.getIoId() == gpioId) {
								gpio = g;
								break;
							}
						}
					}
					if (gpio == null) {
						throw new HardwareMgmtException(ErrorCode.UNKNOWN_IO,
								"Unknown IO identifier " + gpioId,
								request.getOperationId());
					}
					// create response part
					RequestCreateIOType read = new RequestCreateIOType();
					read.setIoId(gpioId);
					
					read.setDirection(gpio.getDirection());
					read.setState(gpio.getState());
					
					gpios.getIo().add(read);
				}
			}
		}
	}

	private void readSubscribers(RequestReadType request,
			ResponseReadType response) throws HardwareMgmtException {
		RequestDeleteSubscribersType subscribersRequest = request
				.getSubscribers();
		// if subscribers shall be read
		if (subscribersRequest != null) {
			// get subscriberIds
			List<String> subscriberIds;
			if (subscribersRequest.getSubscriberId() == null
					|| subscribersRequest.getSubscriberId().isEmpty()) {
				// get all existing subscriberIds
				subscriberIds = new ArrayList<>();
				if (dataCopy.getRuntime() != null
						&& dataCopy.getRuntime().getSubscribers() != null) {
					for (SubscriberType subscriber : dataCopy.getRuntime()
							.getSubscribers().getSubscriber()) {
						subscriberIds.add(subscriber.getSubscriberId());
					}
				}
			} else {
				// get requested subscriberIds
				subscriberIds = subscribersRequest.getSubscriberId();
			}
			if (!subscriberIds.isEmpty()) {
				RequestCreateSubscribersType subscribers = new RequestCreateSubscribersType();
				response.setSubscribers(subscribers);
				// for each requested subscriberId
				for (String subscriberId : subscriberIds) {
					// get subscriber
					SubscriberType subscriber = null;
					if (dataCopy.getRuntime() != null
							&& dataCopy.getRuntime().getSubscribers() != null) {
						for (SubscriberType s : dataCopy.getRuntime()
								.getSubscribers().getSubscriber()) {
							if (s.getSubscriberId().equals(subscriberId)) {
								subscriber = s;
								break;
							}
						}
					}
					if (subscriber == null) {
						throw new HardwareMgmtException(
								ErrorCode.UNKNOWN_SUBSCRIBER,
								"Unknown subscriber identifier " + subscriberId,
								request.getOperationId());
					}
					// create response part with original subscriber instance,
					// the other properties are copied
					RequestCreateSubscriberType read = new RequestCreateSubscriberType();
					read.setSubscriberId(subscriberId);
					
					read.setInstance(subscriber.getInstance());
					read.setIos(cloner.deepClone(subscriber.getIos()));
					subscribers.getSubscriber().add(read);
				}
			}
		}
	}

	public ResponseUpdateType process(String configId, RequestUpdateType request)
			throws HardwareMgmtException {
		dataCopy = getDataCopy();
		ResponseUpdateType response = new ResponseUpdateType();
		response.setOperationId(request.getOperationId());
		// update antennas
		updateAntennas(request);
		// update tags and the assignments to the antennas
		updateTags(request);
		// update IOs
		updateIOs(configId, request);
		// update subscribers
		updateSubscribers(request);
		return response;
	}

	private void updateAntennas(RequestUpdateType request)
			throws HardwareMgmtException {
		RequestCreateAntennasType antennasRequest = request.getAntennas();
		// if antennas shall be updated
		if (antennasRequest != null) {
			for (RequestCreateAntennaType antennaRequest : antennasRequest
					.getAntenna()) {
				// get antenna
				AntennaType antenna = null;
				if (dataCopy.getAntennas() != null) {
					for (AntennaType a : dataCopy.getAntennas()
							.getAntenna()) {
						if (a.getAntennaId() == antennaRequest.getAntennaId()) {
							antenna = a;
							break;
						}
					}
				}
				if (antenna == null) {
					throw new HardwareMgmtException(ErrorCode.UNKNOWN_ANTENNA,
							"Unknown antenna identifier "
									+ antennaRequest.getAntennaId(),
							request.getOperationId());
				}
				// update antenna properties
				antenna.setAirProtocol(antennaRequest.getAirProtocol());
			}
		}
	}

	private void updateTags(RequestUpdateType request)
			throws HardwareMgmtException {
		RequestCreateTagsType tagsRequest = request.getTags();
		// if tags shall be updated
		if (tagsRequest != null) {
			// delete assignments to antennas
			final int RELEASED_TAG = 0;
			final int DELETED_TAG = 1;
			Map<String, Integer> releasedTagIds = new HashMap<>();
			for (RequestCreateTagType tagRequest : tagsRequest.getTag()) {
				releasedTagIds.put(tagRequest.getTagId(), DELETED_TAG);
			}
			deleteAntennaTagAssignments(releasedTagIds, RELEASED_TAG);
			// for each tag of the request
			for (RequestCreateTagType tagRequest : tagsRequest.getTag()) {
				// get tag
				TagType tag = null;
				if (dataCopy.getTags() != null) {
					for (TagType t : dataCopy.getTags().getTag()) {
						if (t.getTagId().equals(tagRequest.getTagId())) {
							tag = t;
							break;
						}
					}
				}
				if (tag == null) {
					throw new HardwareMgmtException(ErrorCode.UNKNOWN_TAG,
							"Unknown tag identifier " + tagRequest.getTagId(),
							request.getOperationId());
				}
				// update tag properties
				tag.setMemoryBanks(cloner.deepClone(tagRequest.getMemoryBanks()));
				tag.setLocks(cloner.deepClone(tagRequest.getLocks()));
				tag.setKilled(tagRequest.isKilled());

				// for each assigned antenna of the request
				for (RequestCreateTagAntennaType tagAntenna : tagRequest
						.getAntennas().getAntenna()) {
					// get antenna
					AntennaType antenna = null;
					if (dataCopy.getAntennas() != null) {
						for (AntennaType a : dataCopy.getAntennas()
								.getAntenna()) {
							if (a.getAntennaId() == tagAntenna.getAntennaId()) {
								antenna = a;
								break;
							}
						}
					}
					if (antenna == null) {
						throw new HardwareMgmtException(
								ErrorCode.UNKNOWN_ANTENNA,
								"Unknown antenna identifier "
										+ tagAntenna.getAntennaId(),
								request.getOperationId());
					}

					AntennaTagType antennaTag = null;
					AntennaTagsType antennaTags = antenna.getTags();
					// if structure for tag assignments does not exist yet
					if (antennaTags == null) {
						// create it
						antennaTags = new AntennaTagsType();
						antenna.setTags(antennaTags);
					} else {
						// get assignment for current tag
						for (AntennaTagType at : antennaTags.getTag()) {
							if (at.getTagId().equals(tag.getTagId())) {
								antennaTag = at;
								break;
							}
						}
					}
					// if tag assignment does not exist for the current tag
					if (antennaTag == null) {
						// create the structure
						antennaTag = new AntennaTagType();
						antennaTag.setTagId(tag.getTagId());
						antennaTags.getTag().add(antennaTag);
					}
					// update tag properties relating to the antenna
					antennaTag.setPeakRSSI(tagAntenna.getPeakRSSI());
					antennaTag.setError(cloner.deepClone(tagAntenna.getError()));
				}
			}
		}
	}

	private void updateIOs(String configId, RequestUpdateType request)
			throws HardwareMgmtException {
		RequestCreateIOsType gpiosRequest = request.getIos();
		// if IOs shall be updated
		if (gpiosRequest != null) {
			for (RequestCreateIOType gpioRequest : gpiosRequest.getIo()) {
				// get IO
				IOType gpio = null;
				if (dataCopy.getIos() != null) {
					for (IOType g : dataCopy.getIos().getIo()) {
						if (g.getIoId() == gpioRequest.getIoId()) {
							gpio = g;
							break;
						}
					}
				}
				if (gpio == null) {
					throw new HardwareMgmtException(ErrorCode.UNKNOWN_IO,
							"Unknown IO identifier "
									+ gpioRequest.getIoId(),
							request.getOperationId());
				}

				// if state has changed
				if (gpio.getDirection().equals(gpioRequest.getDirection())
						&& !gpio.getState().equals(gpioRequest.getState())) {
					// fire event
					fireIOEvent(configId, gpio.getIoId(),
							gpio.getState(), gpioRequest.getState());
				}

				// update IO properties, the properties are replaced with a
				// copy
				gpio.setDirection(gpioRequest.getDirection());
				gpio.setState(gpioRequest.getState());			
			}
		}
	}

	private void fireIOEvent(String configId, int gpioId,
			IOStateEnumeration oldState, IOStateEnumeration newState) {
		if (dataCopy.getRuntime() != null
				&& dataCopy.getRuntime().getSubscribers() != null) {
			// for each subscriber
			for (SubscriberType subscriber : dataCopy.getRuntime()
					.getSubscribers().getSubscriber()) {
				// if the subscriber observes IOs
				if (subscriber.getIos() != null) {
					// for each observed IO
					for (SubscriberIOType gpio : subscriber.getIos()
							.getIo()) {
						// if gpioId matches and a change event shall be sent
						if (gpio.getIoId() == gpioId
								&& gpio.isStateChanged() != null
								&& gpio.isStateChanged()) {
							// create notification
							NotificationType notification = new NotificationType();
							notification.setConfigId(configId);
							NotificationIOsType gpios = new NotificationIOsType();
							NotificationIOType g = new NotificationIOType();
							g.setIoId(gpioId);
							NotificationIOsStateChangedType stateChanged = new NotificationIOsStateChangedType();
							stateChanged.setOld(oldState);
							stateChanged.setNew(newState);
							g.setStateChanged(stateChanged);
							gpios.getIo().add(g);
							notification.setIos(gpios);
							// send notification to subscriber
							HardwareMgmtSubscriber subscriberInstance = (HardwareMgmtSubscriber) subscriber.getInstance();
							subscriberInstance.notify(notification);
							break;
						}
					}
				}
			}
		}
	}

	private void updateSubscribers(RequestUpdateType request)
			throws HardwareMgmtException {
		RequestCreateSubscribersType subscribersRequest = request
				.getSubscribers();
		// if subscribers shall be updated
		if (subscribersRequest != null) {
			for (RequestCreateSubscriberType subscriberRequest : subscribersRequest
					.getSubscriber()) {
				// get subscriber
				SubscriberType subscriber = null;
				if (dataCopy.getRuntime() != null
						&& dataCopy.getRuntime().getSubscribers() != null) {
					for (SubscriberType s : dataCopy.getRuntime()
							.getSubscribers().getSubscriber()) {
						if (s.getSubscriberId().equals(
								subscriberRequest.getSubscriberId())) {
							subscriber = s;
							break;
						}
					}
				}
				if (subscriber == null) {
					throw new HardwareMgmtException(
							ErrorCode.UNKNOWN_SUBSCRIBER,
							"Unknown subscriber identifier "
									+ subscriberRequest.getSubscriberId(),
							request.getOperationId());
				}
				// update subscriber properties, the properties are copied excl.
				// the subscriber instance

				subscriber.setInstance(subscriberRequest.getInstance());
				subscriber.setIos(cloner.deepClone(subscriberRequest.getIos()));
			}
		}
	}

	private HardwareType getDataCopy() {
		if (dataCopy == null) {
			// remove subscriber instances
			Map<String, SubscriberInstanceType> subscriberInstances = new HashMap<>();
			if (data.getRuntime() != null
					&& data.getRuntime().getSubscribers() != null) {
				// for each subscriber
				for (SubscriberType subscriber : data.getRuntime()
						.getSubscribers().getSubscriber()) {
					if (subscriber.getInstance() != null) {
						subscriberInstances.put(subscriber.getSubscriberId(),
								subscriber.getInstance());
						subscriber.setInstance(null);
					}
				}
			}
			// clone the data structure
			dataCopy = cloner.deepClone(data);
			if (!subscriberInstances.isEmpty()) {
				// set the subscriber instances
				setSubscriberInstances(subscriberInstances, data);
				setSubscriberInstances(subscriberInstances, dataCopy);
			}
		}
		return dataCopy;
	}

	private void setSubscriberInstances(
			Map<String, SubscriberInstanceType> subscriberInstances,
			HardwareType data) {
		if (data.getRuntime() != null
				&& data.getRuntime().getSubscribers() != null) {
			// for each subscriber
			for (SubscriberType subscriber : data.getRuntime().getSubscribers()
					.getSubscriber()) {
				SubscriberInstanceType instance = subscriberInstances
						.get(subscriber.getSubscriberId());
				if (instance != null) {
					subscriber.setInstance(
							instance);
				}
			}
		}
	}
}
