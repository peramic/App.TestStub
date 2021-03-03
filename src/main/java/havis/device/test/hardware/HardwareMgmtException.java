package havis.device.test.hardware;

import havis.device.test.hardware.ErrorCode;

public class HardwareMgmtException extends Exception {
	private static final long serialVersionUID = 1L;

	private ErrorCode errorCode;
	private String operationId;

	public HardwareMgmtException(ErrorCode errorCode, String message,
			String operationId) {
		super(message);
		this.errorCode = errorCode;
		this.operationId = operationId;
	}

	public HardwareMgmtException(ErrorCode errorCode, String message,
			Throwable cause, String operationId) {
		super(message, cause);
		this.errorCode = errorCode;
		this.operationId = operationId;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	public String getOperationId() {
		return operationId;
	}
}
