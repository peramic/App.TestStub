package havis.device.test.hardware;

import havis.device.test.hardware.RequestType;
import havis.device.test.hardware.ResponseType;

import java.util.List;

public interface HardwareMgmt {
	/**
	 * Processes a request.
	 * 
	 * @param request
	 * @return
	 */
	List<ResponseType> process(List<RequestType> request);
}
