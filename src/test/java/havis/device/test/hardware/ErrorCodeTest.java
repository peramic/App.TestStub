package havis.device.test.hardware;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ErrorCodeTest {

	@Test
	public void getErrorCode() {
		Assert.assertNull(ErrorCode.getErrorCode(12345));
		Assert.assertEquals(ErrorCode.getErrorCode(500),
				ErrorCode.UNKNOWN_CONFIG);
	}
}
