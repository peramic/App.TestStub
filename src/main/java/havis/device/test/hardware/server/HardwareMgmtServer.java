package havis.device.test.hardware.server;

import havis.device.test.hardware.ui.rest.RESTApplication;
import havis.net.server.http.HttpService;

import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HardwareMgmtServer {

	private static final Logger log = Logger.getLogger(HardwareMgmtServer.class
			.getName());

	private HttpService jetty;
	private int port;

	/**
	 * Starts the server. The server can be stopped with a hangup signal/
	 * <code>ctrl-c</code>.
	 * 
	 * @param args
	 *            the port to be used (default: 8080)
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		final HardwareMgmtServer server = new HardwareMgmtServer();
		final CountDownLatch isStarted = new CountDownLatch(1);
		// register a shutdown hook for hangup signal/ctrl-c
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				if (log.isLoggable(Level.INFO)) {
					log.info("Hang up signal received");
				}
				try {
					isStarted.await();
					server.stop();
				} catch (Throwable t) {
					log.log(Level.SEVERE, "Stopping of server failed", t);
				}
			}
		});
		server.start();
		isStarted.countDown();
	}

	/**
	 * Starts the server (non-blocking).
	 * 
	 * @param port
	 * @param webAppRootPath
	 *            The path to the root of the web application. A relative path
	 *            starts at the class path.
	 * @throws Exception
	 */
	public void start() throws Exception {
		if (log.isLoggable(Level.INFO)) {
			log.info("Starting server on port " + port);
		}
		jetty = new HttpService();
		jetty.start();
		jetty.add(new RESTApplication());
	}

	/**
	 * Stops the server. The server must have been started before with
	 * {@link #start}.
	 * 
	 * @throws Exception
	 */
	public void stop() throws Exception {
		if (jetty != null) {
			jetty.stop();
		}
	}
}
