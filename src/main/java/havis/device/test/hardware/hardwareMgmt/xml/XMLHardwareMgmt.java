package havis.device.test.hardware.hardwareMgmt.xml;

import havis.device.test.hardware.ErrorCode;
import havis.device.test.hardware.HardwareMgmt;
import havis.device.test.hardware.HardwareMgmtException;
import havis.device.test.hardware.HardwareType;
import havis.device.test.hardware.RequestCreateType;
import havis.device.test.hardware.RequestDeleteType;
import havis.device.test.hardware.RequestReadType;
import havis.device.test.hardware.RequestType;
import havis.device.test.hardware.RequestUpdateType;
import havis.device.test.hardware.ResponseCreateType;
import havis.device.test.hardware.ResponseDeleteType;
import havis.device.test.hardware.ResponseErrorType;
import havis.device.test.hardware.ResponseReadType;
import havis.device.test.hardware.ResponseType;
import havis.device.test.hardware.ResponseUpdateType;
import havis.device.test.hardware.common.io.PathHandler;
import havis.device.test.hardware.common.io.XMLFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class XMLHardwareMgmt implements HardwareMgmt {

	private static final Logger log = Logger.getLogger(XMLHardwareMgmt.class
			.getName());

	private final String DFLT_INITIAL_DATA_DIR = "havis-hardware/data";

	private final String initialDataDir;
	private Path initialDataPath;
	private Map<String, Hardware> hwData = new HashMap<>();
	/**
	 * A copy of the list of {@link Hardware} objects. The objects itself are
	 * not copied because {@link Hardware} provides functionality for
	 * transaction management.
	 */
	private Map<String, Hardware> hwDataCopy = null;

	/**
	 * @param initialDataDir
	 *            The path to directory with XML file with initial data. A
	 *            relative path starts at the class path. If the path is
	 *            <code>null</code> then the default path
	 *            <code>havis-hardware/data</code> is used.
	 */
	public XMLHardwareMgmt(String initialDataDir) {
		this.initialDataDir = initialDataDir == null ? DFLT_INITIAL_DATA_DIR
				: initialDataDir;
	}

	private void commit() {
		if (hwDataCopy != null) {
			for (Hardware hwData : hwDataCopy.values()) {
				hwData.commit();
			}
			hwData = hwDataCopy;
			hwDataCopy = null;
		}
	}

	private void rollback() {
		if (hwDataCopy != null) {
			for (Hardware hwData : hwDataCopy.values()) {
				hwData.rollback();
			}
			hwDataCopy = null;
		}
	}

	@Override
	public synchronized List<ResponseType> process(List<RequestType> requests) {
		hwDataCopy = new HashMap<>(hwData);
		List<ResponseType> responses = new ArrayList<>();
		// for each request
		requests: for (RequestType request : requests) {
			String configId = request.getConfigId();
			// create response structure
			ResponseType response = new ResponseType();
			response.setConfigId(configId);
			List<Object> responseChoiceList = response.getChoice();
			responses.add(response);
			// for each operation
			operations: for (int i = 0; i < request.getChoice().size(); i++) {
				try {
					Object operationChoice = request.getChoice().get(i);
					String operationId = null;
					if (operationChoice instanceof RequestCreateType) {
						RequestCreateType req = (RequestCreateType) operationChoice;
						operationId = req.getOperationId();
						// if empty create operation
						if (req.getAntennas() == null && req.getTags() == null
								&& req.getIos() == null
								&& req.getSubscribers() == null) {
							if (hwDataCopy.containsKey(configId)) {
								throw new HardwareMgmtException(
										ErrorCode.CONFIG_EXISTS,
										"Configuration with identifier "
												+ configId
												+ " for hardware data already exists",
										operationId);
							}
							// create hardware data for configId
							getHwData(configId, operationId);
							// create response
							ResponseCreateType resp = new ResponseCreateType();
							resp.setOperationId(operationId);
							responseChoiceList.add(resp);
							// proceed with next operation
							continue operations;
						}
					} else if (operationChoice instanceof RequestDeleteType) {
						RequestDeleteType req = (RequestDeleteType) operationChoice;
						operationId = req.getOperationId();
						// if empty delete operation
						if (req.getAntennas() == null && req.getTags() == null
								&& req.getIos() == null
								&& req.getSubscribers() == null) {
							// remove existing hardware data for configId
							hwDataCopy.remove(configId);
							// create response
							ResponseDeleteType resp = new ResponseDeleteType();
							resp.setOperationId(operationId);
							responseChoiceList.add(resp);
							// proceed with next operation
							continue operations;
						}
					} else if (operationChoice instanceof RequestReadType) {
						operationId = ((RequestReadType) operationChoice)
								.getOperationId();
					} else if (operationChoice instanceof RequestUpdateType) {
						operationId = ((RequestUpdateType) operationChoice)
								.getOperationId();
					}
					// check if the hardware data exist for the configId
					if (!hwDataCopy.containsKey(configId)) {
						throw new HardwareMgmtException(
								ErrorCode.UNKNOWN_CONFIG,
								"Unknown configuration identifier " + configId
										+ " for hardware data", operationId);
					}
					// get hardware data
					Hardware hwData = getHwData(configId, operationId);
					// execute operation
					if (operationChoice instanceof RequestCreateType) {
						ResponseCreateType req = hwData
								.process((RequestCreateType) operationChoice);
						responseChoiceList.add(req);
					} else if (operationChoice instanceof RequestDeleteType) {
						ResponseDeleteType req = hwData
								.process((RequestDeleteType) operationChoice);
						responseChoiceList.add(req);
					} else if (operationChoice instanceof RequestReadType) {
						ResponseReadType req = hwData
								.process((RequestReadType) operationChoice);
						responseChoiceList.add(req);
					} else if (operationChoice instanceof RequestUpdateType) {
						ResponseUpdateType req = hwData.process(configId,
								(RequestUpdateType) operationChoice);
						responseChoiceList.add(req);
					}
				} catch (HardwareMgmtException e) {
					// create error response
					ResponseErrorType errorResponse = new ResponseErrorType();
					errorResponse.setOperationId(e.getOperationId());
					errorResponse.setCode(e.getErrorCode().getValue());
					errorResponse.setDescription(e.getMessage());
					if (e.getCause() != null) {
						errorResponse.setDescription(errorResponse
								.getDescription()
								+ ": "
								+ e.getCause().getMessage());
					}
					responseChoiceList.add(errorResponse);
					// roll back all changes
					rollback();
					if (log.isLoggable(Level.FINE)) {
						log.log(Level.FINE,
								"Rolled back changes due to error code "
										+ errorResponse.getCode(), e);
					}
					// cancel the processing of requests
					break requests;
				}
			}
		}
		// commit all changes
		commit();
		return responses;
	}

	/**
	 * Gets hardware data. If the data has not been loaded yet then initial data
	 * are created. If a XML file for the given configId exists then the data
	 * from this file are used as initial data.
	 * 
	 * @param configId
	 * @param operationId
	 * @return
	 * @throws HardwareMgmtException
	 */
	private Hardware getHwData(String configId, String operationId)
			throws HardwareMgmtException {
		Hardware hwData = hwDataCopy.get(configId);
		if (hwData == null) {
			Path initialFilePath = null;
			PathHandler ph = new PathHandler();
			if (initialDataPath == null) {
				initialDataPath = ph.toAbsolutePath(initialDataDir);
				if (initialDataPath == null) {
					throw new HardwareMgmtException(
							ErrorCode.READING_OF_HW_DATA_FAILED,
							"Missing directory for initial hardware data: "
									+ initialDataDir, operationId);
				}
			}
			initialFilePath = ph.toAbsolutePath(initialDataPath
					.resolve(configId + ".xml"));
			HardwareType content = null;
			// if initial data for configId does not exist
			if (initialFilePath == null) {
				// create empty initial hardware data
				content = new HardwareType();
				content.setConfigId(configId);
			} else {
				// read initial hardware data from XML file
				try {
					XMLFile<HardwareType> xmlFile = new XMLFile<HardwareType>(
							HardwareType.class, initialFilePath, null /* latestPath */);
					content = xmlFile.getContent();
				} catch (Exception e) {
					throw new HardwareMgmtException(
							ErrorCode.READING_OF_HW_DATA_FAILED,
							"Reading of initial hardware data from "
									+ initialFilePath + " failed", e,
							operationId);
				}
			}
			hwData = new Hardware(content);
			hwDataCopy.put(configId, hwData);
		}
		return hwData;
	}
}
