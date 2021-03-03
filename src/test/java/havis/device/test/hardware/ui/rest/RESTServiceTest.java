package havis.device.test.hardware.ui.rest;

import havis.device.test.hardware.HardwareOperationType;
import havis.device.test.hardware.RequestCreateSubscriberType;
import havis.device.test.hardware.RequestCreateType;
import havis.device.test.hardware.RequestType;
import havis.device.test.hardware.RequestUpdateType;
import havis.device.test.hardware.ResponseCreateType;
import havis.device.test.hardware.ResponseDeleteType;
import havis.device.test.hardware.ResponseErrorType;
import havis.device.test.hardware.ResponseReadType;
import havis.device.test.hardware.ResponseUpdateType;
import havis.device.test.hardware.SubscriberInstancePropertyType;
import havis.device.test.hardware.common.io.PathHandler;
import havis.device.test.hardware.common.io.XMLFile;
import havis.device.test.hardware.hardwareMgmt.HardwareMgmtFactory;
import havis.device.test.hardware.hardwareMgmt.xml.XMLHardwareMgmt;
import havis.net.server.http.HttpService;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBException;

import mockit.Mocked;
import mockit.Verifications;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

import test._EnvTest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@SuppressWarnings("restriction")
public class RESTServiceTest {
	private static java.nio.file.Path BASE_RESOURCE_PATH = Paths.get(RESTServiceTest.class.getPackage().getName().replace('.', '/'));

	@Produces({ MediaType.APPLICATION_JSON })
	@Consumes({ MediaType.APPLICATION_JSON })
	public static interface Client {
		@POST
		@Path("")
		HardwareOperationType process(HardwareOperationType request);

		@PUT
		@Path("/{configId}")
		HardwareOperationType createConfig(@PathParam("configId") String configId);

		@GET
		@Path("/{configId}")
		HardwareOperationType getConfig(@PathParam("configId") String configId);

		@GET
		@Path("/{configId}/subscribers")
		HardwareOperationType getSubscribers(@PathParam("configId") String configId);

		@GET
		@Path("/{configId}/subscribers/{subscriberId}")
		HardwareOperationType getSubscriber(@PathParam("configId") String configId, @PathParam("subscriberId") String subscriberId);

		@DELETE
		@Path("/{configId}/subscribers")
		HardwareOperationType deleteSubscribers(@PathParam("configId") String configId);

		@DELETE
		@Path("/{configId}/subscribers/{subscriberId}")
		public HardwareOperationType deleteSubscriber(@PathParam("configId") String configId, @PathParam("subscriberId") String subscriberId);
	}

	@Test
	public void subscribers() throws Exception {
		int port = _EnvTest.SERVER_PORT_1;
		// create hardware management server
		System.setProperty(HttpService.PORT, Integer.toString(port));
		HttpServer server = null;
		HttpService service = null;
		try {
			class Data {
				String response;
			}
			final Data data = new Data();

			final CountDownLatch responseReceived = new CountDownLatch(1);

			server = HttpServer.create(new InetSocketAddress(9999), 0);
			server.createContext("/", new HttpHandler() {

				@Override
				public void handle(HttpExchange exchange) throws IOException {
					StringBuilder requestContentBuf = new StringBuilder();
					InputStreamReader br = new InputStreamReader(exchange.getRequestBody());
					char[] charBuffer = new char[1024];
					int bytesRead = -1;
					while ((bytesRead = br.read(charBuffer)) > 0) {
						requestContentBuf.append(charBuffer, 0, bytesRead);
					}
					data.response = requestContentBuf.toString();
					responseReceived.countDown();
					exchange.sendResponseHeaders(200, -1);
				}

			});
			server.start();

			service = new HttpService();
			service.start();
			service.add(new RESTApplication());
			// create a REST client for the hardware management server
			ResteasyClient resteasyClient = new ResteasyClientBuilder().establishConnectionTimeout(3000, TimeUnit.MILLISECONDS)
					.socketTimeout(3000, TimeUnit.MILLISECONDS).hostnameVerifier(new HostnameVerifier() {

						@Override
						public boolean verify(String hostname, SSLSession session) {
							return true;
						}
					}).build();
			ResteasyWebTarget target = resteasyClient.target("http://admin@localhost:" + port + "/rest/test");

			Client client = target.proxy(Client.class);

			// load the default hardware (PUT)
			String configId = "default";
			HardwareOperationType response = client.createConfig(configId);
			Assert.assertTrue(response.getResponse().get(0).getChoice().get(0) instanceof ResponseCreateType);

			// create a subscriber (POST)
			HardwareOperationType createOp = readXMLOperation("createSubscriberTemplate.xml");
			for (SubscriberInstancePropertyType instanceProperty : ((RequestCreateType) createOp.getRequest().get(0).getChoice().get(0)).getSubscribers()
					.getSubscriber().get(0).getInstanceProperties().getInstanceProperty()) {
				if (instanceProperty.getKey().equals("URL")) {
					// set callback URL
					instanceProperty.setValue("http://localhost:9999");
					break;
				}
			}

			// System.out.println("Waiting");
			// new Scanner(System.in).nextLine();
			response = client.process(createOp);
			String subscriberId = ((ResponseCreateType) response.getResponse().get(0).getChoice().get(0)).getSubscribers().getSubscriberIds().get(0)
					.getSubscriberId();

			// read whole config to get subscribers (GET)
			response = client.getConfig(configId);
			// the properties are returned
			RequestCreateSubscriberType subscriber = ((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getSubscribers().getSubscriber()
					.get(0);
			Assert.assertEquals(subscriber.getSubscriberId(), subscriberId);
			Assert.assertEquals(subscriber.getInstanceProperties().getInstanceProperty().size(), 1);
			Assert.assertEquals(subscriber.getInstanceProperties().getInstanceProperty().get(0).getKey(), "URL");

			// update subscriber (POST)
			HardwareOperationType op = readXMLOperation("updateSubscriberTemplate.xml");
			subscriber = ((RequestUpdateType) op.getRequest().get(0).getChoice().get(0)).getSubscribers().getSubscriber().get(0);
			subscriber.setSubscriberId(subscriberId);
			for (SubscriberInstancePropertyType instanceProperty : subscriber.getInstanceProperties().getInstanceProperty()) {
				if (instanceProperty.getKey().equals("URL")) {
					// set callback URL
					instanceProperty.setValue("http://localhost:9999");
					break;
				}
			}
			response = client.process(op);
			Assert.assertTrue(response.getResponse().get(0).getChoice().get(0) instanceof ResponseUpdateType);

			// read all subscribers (GET)
			response = client.getSubscribers(configId);
			// the properties are returned
			subscriber = ((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getSubscribers().getSubscriber().get(0);
			Assert.assertEquals(subscriber.getSubscriberId(), subscriberId);
			Assert.assertEquals(subscriber.getInstanceProperties().getInstanceProperty().size(), 3);
			Assert.assertEquals(subscriber.getInstanceProperties().getInstanceProperty().get(0).getKey(), "URL");
			// the subscriber has been updated
			Assert.assertEquals(subscriber.getInstanceProperties().getInstanceProperty().get(1).getValue(), "5000");

			// read subscriber with id (GET)
			response = client.getSubscriber(configId, subscriberId);
			// the properties are returned
			subscriber = ((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getSubscribers().getSubscriber().get(0);
			Assert.assertEquals(subscriber.getSubscriberId(), subscriberId);
			Assert.assertEquals(subscriber.getInstanceProperties().getInstanceProperty().get(0).getKey(), "URL");

			// update IO state (POST)
			op = readXMLOperation("updateIO.xml");
			response = client.process(op);
			Assert.assertTrue(response.getResponse().get(0).getChoice().get(0) instanceof ResponseUpdateType);

			// a notification has been received
			responseReceived.await(3000, TimeUnit.MILLISECONDS);
			Assert.assertNotNull(data.response);

			// delete the subscriber (DELETE)
			response = client.deleteSubscriber(configId, subscriberId);
			response = client.deleteSubscriber(configId, subscriberId);
			Assert.assertTrue(response.getResponse().get(0).getChoice().get(0) instanceof ResponseDeleteType);
			response = client.getSubscribers(configId);
			Assert.assertNull(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getSubscribers());

			// create the subscriber again
			response = client.process(createOp);
			Assert.assertFalse(((ResponseCreateType) response.getResponse().get(0).getChoice().get(0)).getSubscribers().getSubscriberIds().isEmpty());

			// delete all subscribers (DELETE)
			response = client.deleteSubscribers(configId);
			Assert.assertNotNull(response.getResponse().get(0).getChoice().get(0) instanceof ResponseDeleteType);
			response = client.getSubscribers(configId);
			Assert.assertNull(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getSubscribers());

			// close the client and the servers
			resteasyClient.close();
		} catch (Exception e) {
			throw e;
		} finally {
			if (server != null) {
				server.stop(0);
			}
			if (service != null) {
				service.stop();
			}
		}
	}

	@Test
	public void setInitialDataDir(@Mocked XMLHardwareMgmt hwMgmt) throws Exception {
		RESTService rs = new RESTService("a");

		new Verifications() {
			{
				new XMLHardwareMgmt("a");
				times = 1;
			}
		};
		releaseService(rs);
	}

	@Test
	public void process() throws Exception {
		RESTService rs = createService(null /* configId */);

		// create a request for creating a config
		HardwareOperationType op = new HardwareOperationType();
		RequestType request = new RequestType();
		request.setConfigId("a");
		request.getChoice().add(new RequestCreateType());
		op.getRequest().add(request);

		HardwareOperationType response = rs.process(op);
		Assert.assertEquals(response.getResponse().get(0).getConfigId(), "a");
		Assert.assertTrue(response.getResponse().get(0).getChoice().get(0) instanceof ResponseCreateType);
		releaseService(rs);
	}

	@Test
	public void createConfig() throws Exception {
		RESTService rs = createService(null /* configId */);
		// create a config
		HardwareOperationType response = rs.createConfig("a");
		Assert.assertEquals(response.getResponse().get(0).getConfigId(), "a");
		Assert.assertTrue(response.getResponse().get(0).getChoice().get(0) instanceof ResponseCreateType);
		// creating the config once again fails
		response = rs.createConfig("a");
		Assert.assertTrue(response.getResponse().get(0).getChoice().get(0) instanceof ResponseErrorType);
		releaseService(rs);
	}

	@Test
	public void deleteConfig() throws Exception {
		RESTService rs = createService("a");
		// delete the config
		HardwareOperationType response = rs.deleteConfig("a");
		Assert.assertEquals(response.getResponse().get(0).getConfigId(), "a");
		Assert.assertTrue(response.getResponse().get(0).getChoice().get(0) instanceof ResponseDeleteType);
		// the config can be created again without exception
		rs.createConfig("a");
		releaseService(rs);
	}

	@Test
	public void deleteAnntennas() throws Exception {
		RESTService rs = createService("hardware");
		// an antenna exists
		HardwareOperationType response = rs.getAntennas("hardware");
		Assert.assertFalse(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getAntennas().getAntenna().isEmpty());
		// delete the antennas
		response = rs.deleteAnntennas("hardware");
		Assert.assertEquals(response.getResponse().get(0).getConfigId(), "hardware");
		Assert.assertTrue(response.getResponse().get(0).getChoice().get(0) instanceof ResponseDeleteType);
		// no antenna exists
		response = rs.getAntennas("hardware");
		Assert.assertNull(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getAntennas());
		releaseService(rs);
	}

	@Test
	public void deleteAnntenna() throws Exception {
		RESTService rs = createService("hardware");
		// an antenna exists
		HardwareOperationType response = rs.getAntennas("hardware");
		Assert.assertFalse(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getAntennas().getAntenna().isEmpty());
		// deleting of an non existing antenna fails
		response = rs.deleteAnntenna("hardware", "123");
		// no antenna has been deleted
		response = rs.getAntennas("hardware");
		Assert.assertFalse(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getAntennas().getAntenna().isEmpty());
		// delete the existing antenna
		response = rs.deleteAnntenna("hardware", "1");
		Assert.assertEquals(response.getResponse().get(0).getConfigId(), "hardware");
		Assert.assertTrue(response.getResponse().get(0).getChoice().get(0) instanceof ResponseDeleteType);
		// no antenna exists
		response = rs.getAntennas("hardware");
		Assert.assertNull(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getAntennas());
		releaseService(rs);
	}

	@Test
	public void deleteTags() throws Exception {
		RESTService rs = createService("hardware");
		// an tag exists
		HardwareOperationType response = rs.getTags("hardware");
		Assert.assertFalse(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getTags().getTag().isEmpty());
		// delete the tags
		response = rs.deleteTags("hardware");
		Assert.assertEquals(response.getResponse().get(0).getConfigId(), "hardware");
		Assert.assertTrue(response.getResponse().get(0).getChoice().get(0) instanceof ResponseDeleteType);
		// no tag exists
		response = rs.getTags("hardware");
		Assert.assertNull(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getTags());
		releaseService(rs);
	}

	@Test
	public void deleteTag() throws Exception {
		RESTService rs = createService("hardware");
		// a tag exists
		HardwareOperationType response = rs.getTags("hardware");
		Assert.assertFalse(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getTags().getTag().isEmpty());
		// deleting of an non existing tag fails
		response = rs.deleteTag("hardware", "123");
		// no tag has been deleted
		response = rs.getTags("hardware");
		Assert.assertFalse(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getTags().getTag().isEmpty());
		String tagId = ((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getTags().getTag().get(0).getTagId();
		// delete the existing tag
		response = rs.deleteTag("hardware", tagId);
		Assert.assertEquals(response.getResponse().get(0).getConfigId(), "hardware");
		Assert.assertTrue(response.getResponse().get(0).getChoice().get(0) instanceof ResponseDeleteType);
		// no tag exists
		response = rs.getTags("hardware");
		Assert.assertNull(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getTags());
		releaseService(rs);
	}

	@Test
	public void deleteIOs() throws Exception {
		RESTService rs = createService("hardware");
		// a IO exists
		HardwareOperationType response = rs.getIOs("hardware");
		Assert.assertFalse(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getIos().getIo().isEmpty());
		// delete the IO
		response = rs.deleteIOs("hardware");
		Assert.assertEquals(response.getResponse().get(0).getConfigId(), "hardware");
		Assert.assertTrue(response.getResponse().get(0).getChoice().get(0) instanceof ResponseDeleteType);
		// no IO exists
		response = rs.getIOs("hardware");
		Assert.assertNull(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getIos());
		releaseService(rs);
	}

	@Test
	public void deleteIO() throws Exception {
		RESTService rs = createService("hardware");
		// a IO exists
		HardwareOperationType response = rs.getIOs("hardware");
		Assert.assertFalse(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getIos().getIo().isEmpty());
		// deleting of an non existing IO fails
		response = rs.deleteIO("hardware", 123);
		// no IO has been deleted
		response = rs.getIOs("hardware");
		Assert.assertFalse(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getIos().getIo().isEmpty());
		int gpioId = ((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getIos().getIo().get(0).getIoId();
		// delete the existing IO
		response = rs.deleteIO("hardware", gpioId);
		Assert.assertEquals(response.getResponse().get(0).getConfigId(), "hardware");
		Assert.assertTrue(response.getResponse().get(0).getChoice().get(0) instanceof ResponseDeleteType);
		// no IO exists
		response = rs.getTags("hardware");
		Assert.assertNull(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getIos());
		releaseService(rs);
	}

	@Test
	public void getConfig() throws Exception {
		RESTService rs = createService("hardware");
		// an antenna and tag exists
		HardwareOperationType response = rs.getConfig("hardware");
		Assert.assertFalse(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getAntennas().getAntenna().isEmpty());
		Assert.assertFalse(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getTags().getTag().isEmpty());
		releaseService(rs);
	}

	@Test
	public void getAnntennas() throws Exception {
		RESTService rs = createService("hardware");
		// an antenna exists
		HardwareOperationType response = rs.getAntennas("hardware");
		Assert.assertFalse(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getAntennas().getAntenna().isEmpty());
		Assert.assertNull(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getTags());
		releaseService(rs);
	}

	@Test
	public void getAnntenna() throws Exception {
		RESTService rs = createService("hardware");
		// try to get an non existing antenna
		HardwareOperationType response = rs.getAntenna("hardware", "123");
		Assert.assertTrue(response.getResponse().get(0).getChoice().get(0) instanceof ResponseErrorType);
		// get existing antenna
		response = rs.getAntenna("hardware", "1");
		Assert.assertFalse(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getAntennas().getAntenna().isEmpty());
		Assert.assertNull(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getTags());
		releaseService(rs);
	}

	@Test
	public void getTags() throws Exception {
		RESTService rs = createService("hardware");
		// a tag exists
		HardwareOperationType response = rs.getTags("hardware");
		Assert.assertFalse(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getTags().getTag().isEmpty());
		Assert.assertNull(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getAntennas());
		releaseService(rs);
	}

	@Test
	public void getTag() throws Exception {
		RESTService rs = createService("hardware");
		// try to get an non existing tag
		HardwareOperationType response = rs.getTag("hardware", "123");
		Assert.assertTrue(response.getResponse().get(0).getChoice().get(0) instanceof ResponseErrorType);
		// get existing tag
		response = rs.getTag("hardware", "t1");
		Assert.assertFalse(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getTags().getTag().isEmpty());
		Assert.assertNull(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getAntennas());
		releaseService(rs);
	}

	@Test
	public void getIOs() throws Exception {
		RESTService rs = createService("hardware");
		// a IO exists
		HardwareOperationType response = rs.getIOs("hardware");
		Assert.assertFalse(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getIos().getIo().isEmpty());
		releaseService(rs);
	}

	@Test
	public void getIO() throws Exception {
		RESTService rs = createService("hardware");
		// try to get an non existing IO
		HardwareOperationType response = rs.getIO("hardware", 123);
		Assert.assertTrue(response.getResponse().get(0).getChoice().get(0) instanceof ResponseErrorType);
		// get existing IO
		response = rs.getIO("hardware", 1);
		Assert.assertFalse(((ResponseReadType) response.getResponse().get(0).getChoice().get(0)).getIos().getIo().isEmpty());
		releaseService(rs);
	}

	private RESTService createService(String configId) throws Exception {
		RESTService rs = new RESTService(new PathHandler().toAbsolutePath(BASE_RESOURCE_PATH).toString());
		if (configId != null) {
			rs.createConfig(configId);
		}
		return rs;
	}

	private void releaseService(RESTService rs) {
		HardwareMgmtFactory.getInstance().clear();
	}

	private HardwareOperationType readXMLOperation(String fileName) throws JAXBException, IOException, SAXException {
		java.nio.file.Path initialPath = new PathHandler().toAbsolutePath(BASE_RESOURCE_PATH.resolve(fileName));
		XMLFile<HardwareOperationType> xmlFile = new XMLFile<HardwareOperationType>(HardwareOperationType.class, initialPath, null /* latestPath */);
		return xmlFile.getContent();
	}
}