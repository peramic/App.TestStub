package havis.device.test.hardware.osgi;

import havis.device.test.hardware.HardwareMgmt;
import havis.device.test.hardware.HardwareOperationType;
import havis.device.test.hardware.RequestDeleteType;
import havis.device.test.hardware.RequestType;
import havis.device.test.hardware.common.serializer.XMLSerializer;
import havis.device.test.hardware.hardwareMgmt.HardwareMgmtFactory;
import havis.device.test.hardware.hardwareMgmt.HardwareMgmtFactory.HardwareMgmtFactoryListener;
import havis.device.test.hardware.osgi.Activator.LoggedHardwareMgmt;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import mockit.Deencapsulation;
import mockit.Delegate;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ActivatorTest {

	@SuppressWarnings("unchecked")
	@Test
	public void start(@Mocked final BundleContext bundleContext,
			@Mocked final Logger log,
			@Mocked final HardwareMgmtFactory hwMgmtFactory,
			@Mocked final HardwareMgmt initialHwMgmt,
			@Mocked final HardwareMgmt hwMgmtByListener,
			@Mocked final ServiceRegistration<HardwareMgmt> serviceReg,
			@Mocked final XMLSerializer<HardwareOperationType> serializer)
			throws Exception {
		class Data {
			String host;
			String port;
			HardwareMgmt hwMgmt;
		}
		final Data data = new Data();
		new Expectations() {
			HardwareMgmtFactoryListener hwMgmtListener;
			{
				bundleContext.getProperty(anyString);
				result = new Delegate<BundleContext>() {
					@SuppressWarnings("unused")
					String getProperty(String key) {
						return key.endsWith("host") ? data.host : data.port;
					}
				};

				hwMgmtFactory
						.addListener(withInstanceOf(HardwareMgmtFactoryListener.class));
				result = new Delegate<HardwareMgmtFactory>() {
					@SuppressWarnings("unused")
					void addListener(HardwareMgmtFactoryListener listener) {
						hwMgmtListener = listener;
					}
				};

				hwMgmtFactory.get();
				result = new Delegate<HardwareMgmtFactory>() {
					@SuppressWarnings("unused")
					HardwareMgmt get() {
						if (data.hwMgmt == null) {
							hwMgmtListener.created(hwMgmtByListener);
						}
						return data.hwMgmt;
					}
				};

				serializer
						.serialize(withInstanceOf(HardwareOperationType.class));
				result = new Exception("bah");

				log.isLoggable(Level.INFO);
				result = true;
			}
		};
		Logger origLog = Deencapsulation.getField(Activator.class, "log");
		Deencapsulation.setField(Activator.class, "log", log);

		try {
			// start the bundle without an initial hwMgmt instance
			data.host = "a";
			data.port = "3";
			data.hwMgmt = null;
			Activator activator = new Activator();
			activator.start(bundleContext);

			new Verifications() {
				{
					// the service has been registered
					LoggedHardwareMgmt loggedHwMgmt;
					Dictionary<String, String> props;
					bundleContext
							.registerService(HardwareMgmt.class,
									loggedHwMgmt = withCapture(),
									props = withCapture());
					times = 1;

					Assert.assertEquals(props.size(), 2);
					Assert.assertEquals(props.get("host"), data.host);
					Assert.assertEquals(props.get("port"), data.port);

					// save the registered service for further processing
					data.hwMgmt = loggedHwMgmt;
				}
			};

			// process an XML request via the registered service
			final List<RequestType> requests = new ArrayList<>();
			RequestType request = new RequestType();
			request.setConfigId("c1");
			RequestDeleteType delete = new RequestDeleteType();
			delete.setOperationId("o1");
			request.getChoice().add(delete);
			requests.add(request);
			data.hwMgmt.process(requests);

			new Verifications() {
				{
					// the request has been processed by the service which was
					// provided by the hwMgmtListener
					List<RequestType> requests;
					hwMgmtByListener.process(requests = withCapture());
					times = 1;

					Assert.assertEquals(requests.get(0).getConfigId(), "c1");

					// the serialization of request and response failed
					log.log(Level.SEVERE,
							withMatch("Cannot serialize request.*"),
							withInstanceOf(Exception.class));
					times = 1;
					log.log(Level.SEVERE,
							withMatch("Cannot serialize response.*"),
							withInstanceOf(Exception.class));
					times = 1;
				}
			};

			// stop the bundle
			activator.stop(bundleContext);
			activator.stop(bundleContext);

			new Verifications() {
				{
					// the service has been unregistered
					serviceReg.unregister();
					times = 1;
				}
			};

			// initialize the hwMgmtFactory with an instance and start the
			// bundle again
			data.hwMgmt = initialHwMgmt;
			activator.start(bundleContext);
			new Verifications() {
				{
					// the service has been registered
					LoggedHardwareMgmt loggedHwMgmt;
					bundleContext.registerService(HardwareMgmt.class,
							loggedHwMgmt = withCapture(),
							withInstanceOf(Dictionary.class));
					times = 2;

					// save the registered service for further processing
					data.hwMgmt = loggedHwMgmt;
				}
			};

			// process a request via the registered service
			data.hwMgmt.process(requests);

			new Verifications() {
				{
					// the request has been processed by the initial hwMgmt
					// instance
					initialHwMgmt.process(requests);
					times = 1;
				}
			};

		} finally {
			Deencapsulation.setField(Activator.class, "log", origLog);
		}
	}

	@Test
	public void startError(@Mocked final BundleContext bundleContext)
			throws Exception {
		new Expectations() {
			{
				bundleContext.getProperty(anyString);
				result = new Delegate<BundleContext>() {
					@SuppressWarnings("unused")
					String getProperty(String key) {
						return null;
					}
				};
			}
		};

		// start the bundle with missing bundle properties
		Activator activator = new Activator();
		try {
			activator.start(bundleContext);
			Assert.fail();
		} catch (MissingPropertyException e) {
			Assert.assertTrue(e
					.getMessage()
					.contains(
							"Missing bundle property 'havis.device.test.hardware.host'"));
		}
	}
}
