package havis.device.test.hardware.ui.rest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Application;

public class RESTApplication extends Application {
	private Set<Object> singletons = new HashSet<Object>();
	private final static String SECURITY = "javax.annotation.security.PermitAll";
	private Map<String, Object> properties = new HashMap<>();

	public RESTApplication() {
		singletons.add(new RESTService());
		properties.put(SECURITY, new String[] { "/rest/test" });
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}

	@Override
	public Map<String, Object> getProperties() {
		return properties;
	}
}