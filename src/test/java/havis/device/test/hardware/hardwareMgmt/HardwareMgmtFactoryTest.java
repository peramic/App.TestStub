package havis.device.test.hardware.hardwareMgmt;

import havis.device.test.hardware.HardwareMgmt;
import havis.device.test.hardware.hardwareMgmt.HardwareMgmtFactory.HardwareMgmtFactoryListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.testng.Assert;
import org.testng.annotations.Test;

public class HardwareMgmtFactoryTest {

	@Test
	public void all() {
		HardwareMgmtFactory factory = HardwareMgmtFactory.getInstance();
		// register a listener
		final List<HardwareMgmt> hwMgmts = new ArrayList<>();
		HardwareMgmtFactoryListener listener = new HardwareMgmtFactoryListener() {

			@Override
			public void created(HardwareMgmt hwMgmt) {
				hwMgmts.add(hwMgmt);
			}
		};
		factory.addListener(listener);
		// try to get a hardware management object
		Assert.assertNull(factory.get());
		// create a hardware management object
		HardwareMgmt createdHwMgmt = factory.create("initialDataDir");
		// the listener has been informed about the new object
		Assert.assertEquals(createdHwMgmt, hwMgmts.get(0));

		// create a hardware management object
		createdHwMgmt = factory.create("initialDataDir");
		// the listener has been informed about the new object again
		Assert.assertEquals(createdHwMgmt, hwMgmts.get(1));

		// remove the listener and create a hardware management object
		factory.removeListener(listener);
		createdHwMgmt = factory.create("initialDataDir");
		// the listener has NOT been informed about the new object again
		Assert.assertEquals(hwMgmts.size(), 2);

		// clear the factory and try to get an object
		factory.clear();
		Assert.assertNull(factory.get());
	}

	@Test
	public void threads() throws Exception {
		ExecutorService threadPool = Executors.newFixedThreadPool(1);

		// add a listener to get created hardware management objects
		HardwareMgmtFactory factory = HardwareMgmtFactory.getInstance();
		final List<HardwareMgmt> getHwMgmts = new Vector<>();
		factory.addListener(new HardwareMgmtFactoryListener() {

			@Override
			public void created(HardwareMgmt hwMgmt) {
				getHwMgmts.add(hwMgmt);
			}
		});

		// start the creating of hardware management objects
		final List<HardwareMgmt> createdHwMgmts = new Vector<>();
		final CountDownLatch start = new CountDownLatch(1);
		Future<?> future = threadPool.submit(new Runnable() {

			@Override
			public void run() {
				for (int i = 0; i < 1000; i++) {
					HardwareMgmtFactory factory = HardwareMgmtFactory
							.getInstance();
					createdHwMgmts.add(factory.create("a"));
					if (i == 0) {
						start.countDown();
					}
				}
			}
		});
		// get hardware management objects while they are being created
		start.await(3000, TimeUnit.MILLISECONDS);
		while (!future.isDone()) {
			Assert.assertNotNull(factory.get());
		}
		Assert.assertTrue(createdHwMgmts.equals(getHwMgmts));

		// clear the factory
		HardwareMgmtFactory.getInstance().clear();
	}
}
