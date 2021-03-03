package havis.device.test.hardware.hardwareMgmt.xml;

import havis.device.test.hardware.AirProtocolEnumeration;
import havis.device.test.hardware.ErrorCode;
import havis.device.test.hardware.RequestCreateAntennaType;
import havis.device.test.hardware.RequestCreateAntennasType;
import havis.device.test.hardware.RequestCreateType;
import havis.device.test.hardware.RequestDeleteAntennasType;
import havis.device.test.hardware.RequestDeleteType;
import havis.device.test.hardware.RequestReadType;
import havis.device.test.hardware.RequestType;
import havis.device.test.hardware.RequestUpdateType;
import havis.device.test.hardware.ResponseCreateType;
import havis.device.test.hardware.ResponseDeleteType;
import havis.device.test.hardware.ResponseErrorType;
import havis.device.test.hardware.ResponseReadType;
import havis.device.test.hardware.ResponseType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import mockit.Mocked;
import mockit.Verifications;

import org.testng.Assert;
import org.testng.annotations.Test;

public class XMLHardwareMgmtTest {

	private static Path BASE_RESOURCE_PATH = Paths
			.get(XMLHardwareMgmtTest.class.getPackage().getName()
					.replace('.', '/'));

	@Test
	public void processCreateConfig() {
		// try to create a hardware config with an invalid path to initial
		// hardware data
		// an error "reading of hardware data failed" is returned
		XMLHardwareMgmt mgmt = new XMLHardwareMgmt("abc");
		List<RequestType> requests = new ArrayList<>();
		requests.add(createCreateRequest("c1", "o1"));
		List<ResponseType> responses = mgmt.process(requests);
		Assert.assertEquals(responses.size(), 1);
		ResponseType response = responses.get(0);
		Assert.assertEquals(response.getConfigId(), "c1");
		Assert.assertEquals(response.getChoice().size(), 1);
		ResponseErrorType errorResponse = (ResponseErrorType) response
				.getChoice().get(0);
		Assert.assertEquals(errorResponse.getCode(),
				ErrorCode.READING_OF_HW_DATA_FAILED.getValue());
		Assert.assertEquals(errorResponse.getOperationId(), "o1");
		Assert.assertTrue(errorResponse.getDescription().contains("abc"));

		// create a hardware config without initial data
		mgmt = new XMLHardwareMgmt(BASE_RESOURCE_PATH.toString());
		responses = mgmt.process(requests);
		Assert.assertEquals(responses.size(), 1);
		response = responses.get(0);
		Assert.assertEquals(response.getConfigId(), "c1");
		Assert.assertEquals(response.getChoice().size(), 1);
		ResponseCreateType createResponse = (ResponseCreateType) response
				.getChoice().get(0);
		Assert.assertEquals(createResponse.getOperationId(), "o1");

		// try to create the hardware config again
		// an error "config already exists" is returned
		responses = mgmt.process(requests);
		response = responses.get(0);
		Assert.assertEquals(response.getConfigId(), "c1");
		Assert.assertEquals(response.getChoice().size(), 1);
		errorResponse = (ResponseErrorType) response.getChoice().get(0);
		Assert.assertEquals(errorResponse.getCode(),
				ErrorCode.CONFIG_EXISTS.getValue());
		Assert.assertEquals(errorResponse.getOperationId(), "o1");
		Assert.assertTrue(errorResponse.getDescription().contains("c1"));

		// create a hardware config with initial data from a valid XML file
		mgmt = new XMLHardwareMgmt(BASE_RESOURCE_PATH.toString());
		requests = new ArrayList<>();
		requests.add(createCreateRequest("hardware", "op1"));
		responses = mgmt.process(requests);
		Assert.assertEquals(responses.size(), 1);
		response = responses.get(0);
		Assert.assertEquals(response.getConfigId(), "hardware");
		Assert.assertEquals(response.getChoice().size(), 1);
		createResponse = (ResponseCreateType) response.getChoice().get(0);
		Assert.assertEquals(createResponse.getOperationId(), "op1");

		// the changes have been committed
		// a read request must not fail
		requests = new ArrayList<>();
		requests.add(createReadRequest("hardware", "op1"));
		responses = mgmt.process(requests);
		Assert.assertEquals(responses.size(), 1);
		response = responses.get(0);
		Assert.assertTrue(response.getChoice().get(0) instanceof ResponseReadType);

		// create a hardware config with initial data from an invalid XML file
		mgmt = new XMLHardwareMgmt(BASE_RESOURCE_PATH.toString());
		requests = new ArrayList<>();
		requests.add(createCreateRequest("invalidHardware", "op1"));
		responses = mgmt.process(requests);
		Assert.assertEquals(responses.size(), 1);
		response = responses.get(0);
		Assert.assertEquals(response.getConfigId(), "invalidHardware");
		Assert.assertEquals(response.getChoice().size(), 1);
		errorResponse = (ResponseErrorType) response.getChoice().get(0);
		Assert.assertEquals(errorResponse.getCode(),
				ErrorCode.READING_OF_HW_DATA_FAILED.getValue());
		Assert.assertEquals(errorResponse.getOperationId(), "op1");
		Assert.assertTrue(errorResponse.getDescription().contains(
				"invalidHardware")
				&& errorResponse.getDescription().contains("Not a number"));
	}

	@Test
	public void processCRUD(@Mocked final Hardware hwData) throws Exception {
		XMLHardwareMgmt mgmt = new XMLHardwareMgmt(
				BASE_RESOURCE_PATH.toString());
		List<RequestType> requests = new ArrayList<>();
		// create a create operation for a config
		final String configId = "c1";
		RequestType req = createCreateRequest(configId, "o1");

		// add a create operation for an antenna
		RequestCreateType create = addCreateOperation(req, "o2");
		RequestCreateAntennaType createAntenna = new RequestCreateAntennaType();
		createAntenna
				.setAirProtocol(AirProtocolEnumeration.EPC_GLOBAL_CLASS_1_GEN_2);
		RequestCreateAntennasType createAntennas = new RequestCreateAntennasType();
		createAntennas.getAntenna().add(createAntenna);
		create.setAntennas(createAntennas);

		// add a read + update operation
		addReadOperation(req, "o3");
		addUpdateOperation(req, "o4");
		requests.add(req);

		// add a delete request for the created antenna
		req = createDeleteRequest("c1", "o5");
		RequestDeleteType delete = (RequestDeleteType) req.getChoice().get(0);
		RequestDeleteAntennasType antennas = new RequestDeleteAntennasType();
		antennas.getAntennaId().add(3 /* antennaId */);
		delete.setAntennas(antennas);
		requests.add(req);

		// add a read request for a config which has not been created
		requests.add(createReadRequest("c2", "op1"));

		// process the request
		List<ResponseType> responses = mgmt.process(requests);
		Assert.assertEquals(responses.size(), 3);

		new Verifications() {
			{
				RequestCreateType create;
				hwData.process(create = withCapture());
				times = 1;
				Assert.assertEquals(create.getOperationId(), "o2");

				RequestReadType read;
				hwData.process(read = withCapture());
				times = 1;
				Assert.assertEquals(read.getOperationId(), "o3");

				RequestUpdateType update;
				hwData.process(configId, update = withCapture());
				times = 1;
				Assert.assertEquals(update.getOperationId(), "o4");

				RequestDeleteType delete;
				hwData.process(delete = withCapture());
				times = 1;
				Assert.assertEquals(delete.getOperationId(), "o5");
			}
		};
		ResponseErrorType errorResponse = (ResponseErrorType) responses
				.get(responses.size() - 1).getChoice().get(0);
		Assert.assertEquals(errorResponse.getCode(),
				ErrorCode.UNKNOWN_CONFIG.getValue());
		Assert.assertTrue(errorResponse.getDescription().contains("c2"));

		// all changes must have been rolled back
		// the read request fails
		requests = new ArrayList<>();
		requests.add(createReadRequest("c1", "o3"));
		responses = mgmt.process(requests);
		Assert.assertEquals(responses.size(), 1);
		errorResponse = (ResponseErrorType) responses.get(0).getChoice().get(0);
		Assert.assertEquals(errorResponse.getCode(),
				ErrorCode.UNKNOWN_CONFIG.getValue());
		Assert.assertTrue(errorResponse.getDescription().contains("c1"));
	}

	@Test
	public void processDeleteConfig() {
		// delete + create + delete + create a config
		XMLHardwareMgmt mgmt = new XMLHardwareMgmt(
				BASE_RESOURCE_PATH.toString());
		List<RequestType> requests = new ArrayList<>();
		RequestType req = createDeleteRequest("c1", "o1");
		addCreateOperation(req, "o2");
		requests.add(req);
		requests.add(createDeleteRequest("c1", "o3"));
		requests.add(createCreateRequest("c1", "o4"));
		List<ResponseType> responses = mgmt.process(requests);
		Assert.assertEquals(responses.size(), 3);

		ResponseType response = responses.get(0);
		Assert.assertEquals(response.getConfigId(), "c1");
		Assert.assertEquals(response.getChoice().size(), 2);
		ResponseDeleteType deleteResponse = (ResponseDeleteType) response
				.getChoice().get(0);
		Assert.assertEquals(deleteResponse.getOperationId(), "o1");
		ResponseCreateType createResponse = (ResponseCreateType) response
				.getChoice().get(1);
		Assert.assertEquals(createResponse.getOperationId(), "o2");

		response = responses.get(1);
		Assert.assertEquals(response.getConfigId(), "c1");
		Assert.assertEquals(response.getChoice().size(), 1);
		deleteResponse = (ResponseDeleteType) response.getChoice().get(0);
		Assert.assertEquals(deleteResponse.getOperationId(), "o3");

		response = responses.get(2);
		Assert.assertEquals(response.getConfigId(), "c1");
		Assert.assertEquals(response.getChoice().size(), 1);
		createResponse = (ResponseCreateType) response.getChoice().get(0);
		Assert.assertEquals(createResponse.getOperationId(), "o4");
	}

	private RequestType createRequest(String configId) {
		RequestType request = new RequestType();
		request.setConfigId(configId);
		return request;
	}

	private RequestType createCreateRequest(String configId, String operationId) {
		RequestType request = createRequest(configId);
		addCreateOperation(request, operationId);
		return request;
	}

	private RequestCreateType addCreateOperation(RequestType request,
			String operationId) {
		RequestCreateType create = new RequestCreateType();
		create.setOperationId(operationId);
		request.getChoice().add(create);
		return create;
	}

	private RequestType createDeleteRequest(String configId, String operationId) {
		RequestType request = createRequest(configId);
		addDeleteOperation(request, operationId);
		return request;
	}

	private RequestDeleteType addDeleteOperation(RequestType request,
			String operationId) {
		RequestDeleteType delete = new RequestDeleteType();
		delete.setOperationId(operationId);
		request.getChoice().add(delete);
		return delete;
	}

	private RequestType createReadRequest(String configId, String operationId) {
		RequestType request = createRequest(configId);
		addReadOperation(request, operationId);
		return request;
	}

	private RequestReadType addReadOperation(RequestType request,
			String operationId) {
		RequestReadType read = new RequestReadType();
		read.setOperationId(operationId);
		request.getChoice().add(read);
		return read;
	}

	private RequestUpdateType addUpdateOperation(RequestType request,
			String operationId) {
		RequestUpdateType update = new RequestUpdateType();
		update.setOperationId(operationId);
		request.getChoice().add(update);
		return update;
	}
}
