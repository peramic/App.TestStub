package havis.device.test.hardware.provider;

import havis.device.test.hardware.HardwareMgmtException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class HardwareMgmtExceptionMapper implements
		ExceptionMapper<HardwareMgmtException> {

	@Override
	public Response toResponse(HardwareMgmtException e) {
		return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e)
				.type(MediaType.APPLICATION_XML).build();
	}
}