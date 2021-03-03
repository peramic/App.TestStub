package havis.device.test.hardware;

import havis.device.test.hardware.ErrorCode;

public enum ErrorCode {
	UNSPECIFIED_ERROR(1),
	//
	UNKNOWN_ANTENNA(100), //
	ANTENNA_EXISTS(101),
	//
	UNKNOWN_TAG(200),
	//
	UNKNOWN_IO(300), //
	IO_EXISTS(301),
	//
	UNKNOWN_CONFIG(500), //
	CONFIG_EXISTS(501), //
	READING_OF_HW_DATA_FAILED(502),
	//
	UNKNOWN_SUBSCRIBER(600), //
	INVALID_SUBSCRIBER_INSTANCE(602), //
	INVALID_SUBSCRIBER_INSTANCE_PROPERTIES(603);

	private final int errorCode;

	private ErrorCode(int errorCode) {
		this.errorCode = errorCode;
	}

	public int getValue() {
		return errorCode;
	}

	public static ErrorCode getErrorCode(int errorCode) {
		for (ErrorCode ec : values()) {
			if (ec.errorCode == errorCode) {
				return ec;
			}
		}
		return null;
	}
}
