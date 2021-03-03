package havis.device.test.hardware;

import havis.device.test.hardware.NotificationType;

public interface HardwareMgmtSubscriber {
	void notify(NotificationType notification);
}
