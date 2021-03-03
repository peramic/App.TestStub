package havis.device.test.hardware.ui.rest;

import havis.device.test.hardware.ErrorCode;
import havis.device.test.hardware.HardwareMgmt;
import havis.device.test.hardware.HardwareMgmtException;
import havis.device.test.hardware.HardwareMgmtSubscriber;
import havis.device.test.hardware.HardwareOperationType;
import havis.device.test.hardware.NotificationType;
import havis.device.test.hardware.RequestAbstractType;
import havis.device.test.hardware.RequestCreateSubscriberType;
import havis.device.test.hardware.RequestCreateType;
import havis.device.test.hardware.RequestDeleteAntennasType;
import havis.device.test.hardware.RequestDeleteIOsType;
import havis.device.test.hardware.RequestDeleteSubscribersType;
import havis.device.test.hardware.RequestDeleteTagsType;
import havis.device.test.hardware.RequestDeleteType;
import havis.device.test.hardware.RequestReadType;
import havis.device.test.hardware.RequestType;
import havis.device.test.hardware.RequestUpdateType;
import havis.device.test.hardware.ResponseReadType;
import havis.device.test.hardware.ResponseType;
import havis.device.test.hardware.SubscriberInstancePropertiesType;
import havis.device.test.hardware.SubscriberInstancePropertyType;
import havis.device.test.hardware.SubscriberInstanceType;
import havis.device.test.hardware.common.serializer.XMLSerializer;
import havis.device.test.hardware.hardwareMgmt.HardwareMgmtFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

@Path("test")
// The supported mime types for a response. The HTTP header "Accept" of a
// request provides the mime type for the relating response. A response
// objects is serialized
// with the first matching provider configured in
// web.xml:context-param/resteasy.providers.
// If the HTTP header "Accept" is not available in the request then the first
// mime type of this list is used.
// (JSON is not supported yet: all XML elements of a XSD "choice" are serialized
// to JSON but cannot be deserialized because the generated JiBX classes do not
// allow the setting of more then one element of a choice)
@Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
public class RESTService {

	private static final Logger log = Logger.getLogger(RESTService.class.getName());

	private static final String SUBSCRIBER_PROP_URL = "URL";
	private static final String SUBSCRIBER_PROP_CONNECTION_TIMEOUT = "connectionTimeout";
	private static final String SUBSCRIBER_PROP_SOCKET_TIMEOUT = "socketTimeout";
	private static final long DFLT_TIMEOUT = 3000;

	private long operationIdSequence = 0;
	private HardwareMgmt hwMgmt;

	private XMLSerializer<HardwareOperationType> serializer;

	public static interface MyClient {
		@POST
		@Path("/")
		@Consumes({ MediaType.APPLICATION_JSON })
		void process(HardwareOperationType request);

		@POST
		@Path("/")
		@Consumes({ MediaType.APPLICATION_XML })
		void process(JAXBElement<HardwareOperationType> request);
	}

	private class Subscriber extends SubscriberInstanceType implements HardwareMgmtSubscriber {
		private final List<SubscriberInstancePropertyType> properties;
		private String url;
		@SuppressWarnings("unused")
		private long connectionTimeout;
		@SuppressWarnings("unused")
		private long socketTimeout;

		public Subscriber(String operationId, List<SubscriberInstancePropertyType> properties) throws HardwareMgmtException {
			this.properties = properties;
			// check properties and map them to fields
			Map<String, String> props = new HashMap<>();
			for (SubscriberInstancePropertyType prop : properties) {
				props.put(prop.getKey(), prop.getValue());
			}
			url = props.get(SUBSCRIBER_PROP_URL);
			if (url != null) {
				url = url.trim();
				String connectionTimeoutStr = props.get(SUBSCRIBER_PROP_CONNECTION_TIMEOUT);
				try {
					connectionTimeout = connectionTimeoutStr != null ? Long.valueOf(connectionTimeoutStr.trim()) : DFLT_TIMEOUT;
				} catch (NumberFormatException e) {
					throw new HardwareMgmtException(ErrorCode.INVALID_SUBSCRIBER_INSTANCE_PROPERTIES, "Invalid subscriber instance property '"
							+ SUBSCRIBER_PROP_CONNECTION_TIMEOUT + "': " + connectionTimeoutStr, operationId);
				}
				String socketTimeoutStr = props.get(SUBSCRIBER_PROP_SOCKET_TIMEOUT);
				try {
					socketTimeout = socketTimeoutStr != null ? Long.valueOf(socketTimeoutStr.trim()) : DFLT_TIMEOUT;
				} catch (NumberFormatException e) {
					throw new HardwareMgmtException(ErrorCode.INVALID_SUBSCRIBER_INSTANCE_PROPERTIES, "Invalid subscriber instance property '"
							+ SUBSCRIBER_PROP_CONNECTION_TIMEOUT + "': " + socketTimeoutStr, operationId);
				}
			} else {
				throw new HardwareMgmtException(ErrorCode.INVALID_SUBSCRIBER_INSTANCE_PROPERTIES, "Missing subscriber instance property 'URL'", operationId);
			}
		}

		@Override
		public void notify(NotificationType notification) {
			Client client = ClientBuilder.newClient();
			WebTarget target = client.target(url);

			HardwareOperationType request = new HardwareOperationType();
			request.setNotification(notification);
			if (log.isLoggable(Level.INFO)) {
				try {
					log.info("Sending notification: " + serializer.serialize(request));
				} catch (Exception e) {
					log.log(Level.SEVERE, "Cannot serialize notification for logging", e);
				}
			}

			try {
				target.path("/").request().put(Entity.entity(request, MediaType.APPLICATION_JSON));
			} catch (Exception e) {
				throw e;
			}
			client.close();
		}

		public List<SubscriberInstancePropertyType> getProperties() {
			return properties;
		}
	}

	public RESTService() {
		this(null);
	}

	public RESTService(String initialDataDir) {
		if (hwMgmt == null) {
			// create hardware management object
			hwMgmt = HardwareMgmtFactory.getInstance().create(initialDataDir);
			if (log.isLoggable(Level.INFO)) {
				try {
					serializer = new XMLSerializer<HardwareOperationType>(HardwareOperationType.class);
				} catch (JAXBException e) {
					new RuntimeException(e);
				}
			}
		}
	}

	/**
	 * Processes any request. This method must be used to create or update data.
	 * 
	 * @param request
	 * @return
	 * @throws HardwareMgmtException
	 */
	@POST
	@Path("/")
	@Consumes({ MediaType.APPLICATION_JSON })
	public HardwareOperationType process(HardwareOperationType request) throws HardwareMgmtException {
		if (log.isLoggable(Level.INFO)) {
			try {
				log.info("Received request: " + serializer.serialize(request));
			} catch (Exception e) {
				log.log(Level.SEVERE, "Cannot serialize request for logging", e);
			}
		}
		replaceSubscriberPropertiesWithInstances(request);
		List<ResponseType> responses = hwMgmt.process(request.getRequest());
		HardwareOperationType response = new HardwareOperationType();
		response.getResponse().addAll(responses);
		replaceSubscriberInstancesWithProperties(response);
		if (log.isLoggable(Level.INFO)) {
			try {
				log.info("Sending response: " + serializer.serialize(response));
			} catch (Exception e) {
				log.log(Level.SEVERE, "Cannot serialize response for logging", e);
			}
		}
		return response;
	}

	/**
	 * Processes any request. This method must be used to create or update data.
	 * 
	 * @param request
	 * @return
	 * @throws HardwareMgmtException
	 */
	@POST
	@Path("/")
	@Consumes({ MediaType.APPLICATION_XML })
	public HardwareOperationType process(JAXBElement<HardwareOperationType> request) throws HardwareMgmtException {
		return process(request.getValue());
	}

	/**
	 * Creates an empty hardware configuration.
	 * 
	 * @param configId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@PUT
	@Path("/{configId}")
	public HardwareOperationType createConfig(@PathParam("configId") String configId) throws HardwareMgmtException {
		return process(createRequest(configId, createCreateRequest()));
	}

	/**
	 * Deletes a hardware configuration.
	 * 
	 * @param configId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@DELETE
	@Path("/{configId}")
	public HardwareOperationType deleteConfig(@PathParam("configId") String configId) throws HardwareMgmtException {
		return process(createRequest(configId, createDeleteRequest()));
	}

	/**
	 * Deletes all antennas of a hardware configuration.
	 * 
	 * @param configId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@DELETE
	@Path("/{configId}/antennas")
	public HardwareOperationType deleteAnntennas(@PathParam("configId") String configId) throws HardwareMgmtException {
		RequestDeleteType delete = createDeleteRequest();
		delete.setAntennas(new RequestDeleteAntennasType());
		return process(createRequest(configId, delete));
	}

	/**
	 * Deletes an antenna of a hardware configuration.
	 * 
	 * @param configId
	 * @param antennaId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@DELETE
	@Path("/{configId}/antennas/{antennaId}")
	public HardwareOperationType deleteAnntenna(@PathParam("configId") String configId, @PathParam("antennaId") String antennaId) throws HardwareMgmtException {
		RequestDeleteType delete = createDeleteRequest();
		RequestDeleteAntennasType antennas = new RequestDeleteAntennasType();
		antennas.getAntennaId().add(Integer.valueOf(antennaId));
		delete.setAntennas(antennas);
		return process(createRequest(configId, delete));
	}

	/**
	 * Deletes all tags of a hardware configuration.
	 * 
	 * @param configId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@DELETE
	@Path("/{configId}/tags")
	public HardwareOperationType deleteTags(@PathParam("configId") String configId) throws HardwareMgmtException {
		RequestDeleteType delete = createDeleteRequest();
		delete.setTags(new RequestDeleteTagsType());
		return process(createRequest(configId, delete));
	}

	/**
	 * Deletes a tag of a hardware configuration.
	 * 
	 * @param configId
	 * @param tagId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@DELETE
	@Path("/{configId}/tags/{tagId}")
	public HardwareOperationType deleteTag(@PathParam("configId") String configId, @PathParam("tagId") String tagId) throws HardwareMgmtException {
		RequestDeleteType delete = createDeleteRequest();
		RequestDeleteTagsType tags = new RequestDeleteTagsType();
		tags.getTagId().add(tagId);
		delete.setTags(tags);
		return process(createRequest(configId, delete));
	}

	/**
	 * Deletes all IOs of a hardware configuration.
	 * 
	 * @param configId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@DELETE
	@Path("/{configId}/ios")
	public HardwareOperationType deleteIOs(@PathParam("configId") String configId) throws HardwareMgmtException {
		RequestDeleteType delete = createDeleteRequest();
		delete.setIos(new RequestDeleteIOsType());
		return process(createRequest(configId, delete));
	}

	/**
	 * Deletes a IO of a hardware configuration.
	 * 
	 * @param configId
	 * @param ioId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@DELETE
	@Path("/{configId}/ios/{ioId}")
	public HardwareOperationType deleteIO(@PathParam("configId") String configId, @PathParam("ioId") int ioId) throws HardwareMgmtException {
		RequestDeleteType delete = createDeleteRequest();
		RequestDeleteIOsType ios = new RequestDeleteIOsType();
		ios.getIoId().add(ioId);
		delete.setIos(ios);
		return process(createRequest(configId, delete));
	}

	/**
	 * Deletes all subscribers of a hardware configuration.
	 * 
	 * @param configId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@DELETE
	@Path("/{configId}/subscribers")
	public HardwareOperationType deleteSubscribers(@PathParam("configId") String configId) throws HardwareMgmtException {
		RequestDeleteType delete = createDeleteRequest();
		delete.setSubscribers(new RequestDeleteSubscribersType());
		return process(createRequest(configId, delete));
	}

	/**
	 * Deletes a subscriber of a hardware configuration.
	 * 
	 * @param configId
	 * @param subscriberId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@DELETE
	@Path("/{configId}/subscribers/{subscriberId}")
	public HardwareOperationType deleteSubscriber(@PathParam("configId") String configId, @PathParam("subscriberId") String subscriberId)
			throws HardwareMgmtException {
		RequestDeleteType delete = createDeleteRequest();
		RequestDeleteSubscribersType subscribers = new RequestDeleteSubscribersType();
		subscribers.getSubscriberId().add(subscriberId);
		delete.setSubscribers(subscribers);
		return process(createRequest(configId, delete));
	}

	/**
	 * Gets the data of all antennas, tags and IOs of a hardware configuration.
	 * 
	 * @param configId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@GET
	@Path("/{configId}")
	public HardwareOperationType getConfig(@PathParam("configId") String configId) throws HardwareMgmtException {
		RequestReadType read = createReadRequest();
		read.setAntennas(new RequestDeleteAntennasType());
		read.setTags(new RequestDeleteTagsType());
		read.setIos(new RequestDeleteIOsType());
		read.setSubscribers(new RequestDeleteSubscribersType());
		return process(createRequest(configId, read));
	}

	/**
	 * Gets the data of all antennas of a hardware configuration.
	 * 
	 * @param configId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@GET
	@Path("/{configId}/antennas")
	public HardwareOperationType getAntennas(@PathParam("configId") String configId) throws HardwareMgmtException {
		RequestReadType read = createReadRequest();
		read.setAntennas(new RequestDeleteAntennasType());
		return process(createRequest(configId, read));
	}

	/**
	 * Gets the data of an antenna of a hardware configuration.
	 * 
	 * @param configId
	 * @param antennaId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@GET
	@Path("/{configId}/antennas/{antennaId}")
	public HardwareOperationType getAntenna(@PathParam("configId") String configId, @PathParam("antennaId") String antennaId) throws HardwareMgmtException {
		RequestReadType read = createReadRequest();
		RequestDeleteAntennasType antennas = new RequestDeleteAntennasType();
		antennas.getAntennaId().add(Integer.valueOf(antennaId));
		read.setAntennas(antennas);
		return process(createRequest(configId, read));
	}

	/**
	 * Gets the data of all tags of a hardware configuration.
	 * 
	 * @param configId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@GET
	@Path("/{configId}/tags")
	public HardwareOperationType getTags(@PathParam("configId") String configId) throws HardwareMgmtException {
		RequestReadType read = createReadRequest();
		read.setTags(new RequestDeleteTagsType());
		return process(createRequest(configId, read));
	}

	/**
	 * Gets the data of a tag of a hardware configuration.
	 * 
	 * @param configId
	 * @param tagId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@GET
	@Path("/{configId}/tags/{tagId}")
	public HardwareOperationType getTag(@PathParam("configId") String configId, @PathParam("tagId") String tagId) throws HardwareMgmtException {
		RequestReadType read = createReadRequest();
		RequestDeleteTagsType tags = new RequestDeleteTagsType();
		tags.getTagId().add(tagId);
		read.setTags(tags);
		return process(createRequest(configId, read));
	}

	/**
	 * Gets the data of all IOs of a hardware configuration.
	 * 
	 * @param configId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@GET
	@Path("/{configId}/ios")
	public HardwareOperationType getIOs(@PathParam("configId") String configId) throws HardwareMgmtException {
		RequestReadType read = createReadRequest();
		read.setIos(new RequestDeleteIOsType());
		return process(createRequest(configId, read));
	}

	/**
	 * Gets the data of a IO of a hardware configuration.
	 * 
	 * @param configId
	 * @param ioId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@GET
	@Path("/{configId}/ios/{ioId}")
	public HardwareOperationType getIO(@PathParam("configId") String configId, @PathParam("ioId") int ioId) throws HardwareMgmtException {
		RequestReadType read = createReadRequest();
		RequestDeleteIOsType ios = new RequestDeleteIOsType();
		ios.getIoId().add(ioId);
		read.setIos(ios);
		return process(createRequest(configId, read));
	}

	/**
	 * Gets the data of all subscribers of a hardware configuration.
	 * 
	 * @param configId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@GET
	@Path("/{configId}/subscribers")
	public HardwareOperationType getSubscribers(@PathParam("configId") String configId) throws HardwareMgmtException {
		RequestReadType read = createReadRequest();
		read.setSubscribers(new RequestDeleteSubscribersType());
		return process(createRequest(configId, read));
	}

	/**
	 * Gets the data of a subscriber of a hardware configuration.
	 * 
	 * @param configId
	 * @param ioId
	 * @return
	 * @throws HardwareMgmtException
	 */
	@GET
	@Path("/{configId}/subscribers/{subscriberId}")
	public HardwareOperationType getSubscriber(@PathParam("configId") String configId, @PathParam("subscriberId") String subscriberId)
			throws HardwareMgmtException {
		RequestReadType read = createReadRequest();
		RequestDeleteSubscribersType subscribers = new RequestDeleteSubscribersType();
		subscribers.getSubscriberId().add(subscriberId);
		read.setSubscribers(subscribers);
		return process(createRequest(configId, read));
	}

	private void replaceSubscriberPropertiesWithInstances(HardwareOperationType request) throws HardwareMgmtException {
		// for each request
		for (RequestType req : request.getRequest()) {
			for (Object choice : req.getChoice()) {
				List<RequestCreateSubscriberType> subscribers = null;
				String operationId = null;
				// if a subscriber shall be created
				if (choice instanceof RequestCreateType && ((RequestCreateType) choice).getSubscribers() != null) {
					subscribers = ((RequestCreateType) choice).getSubscribers().getSubscriber();
					operationId = ((RequestCreateType) choice).getOperationId();
				} // else if a subscriber shall be updated
				else if (choice instanceof RequestUpdateType && ((RequestUpdateType) choice).getSubscribers() != null) {
					subscribers = ((RequestUpdateType) choice).getSubscribers().getSubscriber();
					operationId = ((RequestUpdateType) choice).getOperationId();
				}
				if (subscribers != null) {
					// for each subscriber
					for (RequestCreateSubscriberType subscriber : subscribers) {
						SubscriberInstancePropertiesType instanceProps = subscriber.getInstanceProperties();
						// if instance properties exist
						if (instanceProps != null) {
							// replace instance properties with a new subscriber
							// instance
							subscriber.setInstanceProperties(null);
							Subscriber instance = new Subscriber(operationId, instanceProps.getInstanceProperty());
							subscriber.setInstance(instance);
						} else {
							throw new HardwareMgmtException(ErrorCode.INVALID_SUBSCRIBER_INSTANCE_PROPERTIES, "Missing subscriber instance properties",
									((RequestCreateType) choice).getOperationId());
						}
					}
				}
			}
		}
	}

	private void replaceSubscriberInstancesWithProperties(HardwareOperationType response) {
		// for each response
		for (ResponseType resp : response.getResponse()) {
			for (Object choice : resp.getChoice()) {
				if (choice instanceof ResponseReadType && ((ResponseReadType) choice).getSubscribers() != null) {
					// for each subscriber
					for (RequestCreateSubscriberType subscriber : ((ResponseReadType) choice).getSubscribers().getSubscriber()) {
						// if own subscriber
						if (subscriber.getInstance() instanceof Subscriber) {
							// replace instance with subscriber properties
							Subscriber s = (Subscriber) subscriber.getInstance();
							subscriber.setInstance(null);
							SubscriberInstancePropertiesType instanceProps = new SubscriberInstancePropertiesType();
							instanceProps.getInstanceProperty().addAll(s.getProperties());
							subscriber.setInstanceProperties(instanceProps);
						}
					}
				}
			}
		}
	}

	public HardwareOperationType createRequest(String configId, RequestAbstractType choice) {
		HardwareOperationType request = new HardwareOperationType();
		RequestType r = new RequestType();
		r.setConfigId(configId);
		r.getChoice().add(choice);
		request.getRequest().add(r);
		return request;
	}

	public RequestCreateType createCreateRequest() {
		RequestCreateType create = new RequestCreateType();
		create.setOperationId(createOperationId());
		return create;
	}

	public RequestDeleteType createDeleteRequest() {
		RequestDeleteType delete = new RequestDeleteType();
		delete.setOperationId(createOperationId());
		return delete;
	}

	public RequestReadType createReadRequest() {
		RequestReadType read = new RequestReadType();
		read.setOperationId(createOperationId());
		return read;
	}

	private String createOperationId() {
		return String.valueOf(operationIdSequence++);
	}
}