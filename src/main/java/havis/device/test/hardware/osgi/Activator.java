package havis.device.test.hardware.osgi;

import havis.device.test.hardware.HardwareMgmt;
import havis.device.test.hardware.HardwareOperationType;
import havis.device.test.hardware.RequestType;
import havis.device.test.hardware.ResponseType;
import havis.device.test.hardware.common.serializer.XMLSerializer;
import havis.device.test.hardware.hardwareMgmt.HardwareMgmtFactory;
import havis.device.test.hardware.hardwareMgmt.HardwareMgmtFactory.HardwareMgmtFactoryListener;
import havis.device.test.hardware.ui.rest.RESTApplication;

import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.core.Application;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

	private static final Logger log = Logger.getLogger(Activator.class
			.getName());

	private static final String FILE = "bundle.properties";

	private final static Object lock = new Object();
	private ServiceRegistration<HardwareMgmt> serviceReg;
	private ServiceRegistration<Application> applicationServiceRegistration;

	Application application = new RESTApplication();

	class LoggedHardwareMgmt implements HardwareMgmt {

		private final HardwareMgmt hwMgmt;
		private XMLSerializer<HardwareOperationType> serializer;

		public LoggedHardwareMgmt(HardwareMgmt hwMgmt) {
			this.hwMgmt = hwMgmt;
		}

		@Override
		public List<ResponseType> process(List<RequestType> requests) {
			if (log.isLoggable(Level.INFO)) {
				try {
					if (serializer == null) {
						serializer = new XMLSerializer<HardwareOperationType>(
								HardwareOperationType.class);
					}
					HardwareOperationType r = new HardwareOperationType();
					r.getRequest().addAll(requests);
					log.info("Received request: " + serializer.serialize(r));
				} catch (Exception e) {
					log.log(Level.SEVERE,
							"Cannot serialize request for logging", e);
				}
			}
			List<ResponseType> responses = hwMgmt.process(requests);
			if (log.isLoggable(Level.INFO)) {
				try {
					HardwareOperationType r = new HardwareOperationType();
					r.getResponse().addAll(responses);
					log.info("Sending response: " + serializer.serialize(r));
				} catch (Exception e) {
					log.log(Level.SEVERE,
							"Cannot serialize response for logging", e);
				}
			}
			return responses;
		}
	}

	@Override
	public void start(final BundleContext bundleContext) throws Exception {
		long now = new Date().getTime();

		applicationServiceRegistration = bundleContext.registerService(
				Application.class, application, null);

		// load bundle properties file
		Properties bundleProps = null;
		URL propFileURL = bundleContext.getBundle().getResource(
				FILE);
		if (propFileURL != null) {
			bundleProps = new Properties();
			try (InputStream propStream = propFileURL.openStream()) {
				bundleProps.load(propStream);
				if (log.isLoggable(Level.INFO)) {
					log.info("Loaded bundle properties from file "
							+ propFileURL);
				}
			}
		}
		final HardwareMgmtFactory hwMgmtFactory = HardwareMgmtFactory
				.getInstance();
		// listen for the creation of a new hardware management instance
		hwMgmtFactory.addListener(new HardwareMgmtFactoryListener() {

			@Override
			public void created(HardwareMgmt hwMgmt) {
				// publish the new hardware management instance
				registerService(bundleContext, hwMgmt);
			}
		});
		// get an existing hardware management instance and publish it
		registerService(bundleContext, hwMgmtFactory.get());

		java.util.logging.Logger.getGlobal().info(
				"Bundle start took " + (new Date().getTime() - now) + "ms");
	}

	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		if (applicationServiceRegistration != null) {
			applicationServiceRegistration.unregister();
			applicationServiceRegistration = null;
		}
		// unregister a published hardware management instance
		unregisterService(bundleContext);
	}

	/**
	 * Publishes a hardware management instance. If another instance is already
	 * published then it is unregistered before.
	 * 
	 * @param bundleContext
	 * @param hwMgmt
	 */
	private void registerService(BundleContext bundleContext,
			HardwareMgmt hwMgmt) {
		if (hwMgmt == null) {
			return;
		}
		synchronized (lock) {
			// unregister an existing service
			unregisterService(bundleContext);
			// register the new service
			serviceReg = bundleContext.registerService(HardwareMgmt.class,
					new LoggedHardwareMgmt(hwMgmt), null);
			if (log.isLoggable(Level.INFO)) {
				log.info(String.format(
						"Registered service for filter (&(%s=%s))",
						Constants.OBJECTCLASS, HardwareMgmt.class.getName()));
			}
		}
	}

	/**
	 * Unregisters a published hardware management instance.
	 * 
	 * @param bundleContext
	 */
	private void unregisterService(BundleContext bundleContext) {
		synchronized (lock) {
			if (serviceReg != null) {
				serviceReg.unregister();
				serviceReg = null;
				if (log.isLoggable(Level.INFO)) {
					log.info(String.format(
							"Unregistered service for filter (&(%s=%s))",
							Constants.OBJECTCLASS, HardwareMgmt.class.getName()));
				}
			}
		}
	}
}
