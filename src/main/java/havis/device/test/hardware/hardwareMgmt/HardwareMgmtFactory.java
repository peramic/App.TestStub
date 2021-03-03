package havis.device.test.hardware.hardwareMgmt;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import havis.device.test.hardware.HardwareMgmt;
import havis.device.test.hardware.hardwareMgmt.xml.XMLHardwareMgmt;

/**
 * A factory for the creation of a hardware management instance.
 * <p>
 * The factory is a singleton. The instance is returned by
 * {@link #getInstance()}.
 * </p>
 * <p>
 * Listeners can be added / removed with
 * {@link #addListener(HardwareMgmtFactoryListener)} /
 * {@link #removeListener(HardwareMgmtFactoryListener)}.
 * </p>
 * <p>
 * The hardware management instance is initialized and created with
 * {@link #create(String)} and can be removed with {@link #clear()}. Method
 * {@link #get} returns the current instance.
 * </p>
 */
public class HardwareMgmtFactory {
	private static HardwareMgmtFactory instance = new HardwareMgmtFactory();

	private List<HardwareMgmtFactoryListener> listeners = new CopyOnWriteArrayList<>();

	private final Object lock = new Object();
	private HardwareMgmt hwMgmt;

	public interface HardwareMgmtFactoryListener {
		void created(HardwareMgmt hwMgmt);
	}

	/**
	 * Avoids the external creation of a factory instance.
	 * {@link #getInstance()} must be used instead.
	 */
	private HardwareMgmtFactory() {
	}

	public static HardwareMgmtFactory getInstance() {
		return instance;
	}

	/**
	 * Adds a listener.
	 * 
	 * @param listener
	 */
	public void addListener(HardwareMgmtFactoryListener listener) {
		listeners.add(listener);
	}

	/**
	 * Removes a listener.
	 * 
	 * @param listener
	 */
	public void removeListener(HardwareMgmtFactoryListener listener) {
		listeners.remove(listener);
	}

	/**
	 * Creates a new management instance and replaces an existing one. The
	 * listeners are informed with event
	 * {@link HardwareMgmtFactoryListener#created(HardwareMgmt)}.
	 * 
	 * @param initialDataDir
	 * @return
	 */
	public HardwareMgmt create(String initialDataDir) {
		HardwareMgmt localHwMgmt = hwMgmt;
		synchronized (lock) {
			hwMgmt = new XMLHardwareMgmt(initialDataDir);
			localHwMgmt = hwMgmt;
		}
		for (HardwareMgmtFactoryListener listener : listeners) {
			listener.created(localHwMgmt);
		}
		return localHwMgmt;
	}

	/**
	 * Gets the current management instance. If no instance is available then
	 * <code>null</code> is returned.
	 * 
	 * @return
	 */
	public HardwareMgmt get() {
		synchronized (lock) {
			return hwMgmt;
		}
	}

	/**
	 * Removes the current management instance.
	 */
	public void clear() {
		synchronized (lock) {
			hwMgmt = null;
		}
	}
}
