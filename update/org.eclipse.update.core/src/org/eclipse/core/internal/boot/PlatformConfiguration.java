package org.eclipse.core.internal.boot;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownServiceException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.StringTokenizer;

import org.eclipse.core.boot.BootLoader;
import org.eclipse.core.boot.IPlatformConfiguration;
import org.eclipse.core.boot.IPlatformConfiguration.ISiteEntry;
import org.eclipse.core.boot.IPlatformConfiguration.ISitePolicy;

public class PlatformConfiguration implements IPlatformConfiguration {

	private URL configLocation;
	private HashMap sites;
	private HashMap nativeSites;

	public static boolean DEBUG = true;

	private static final String PLUGINS = "plugins";
	private static final String INSTALL = "install";
	private static final String CONFIG_FILE = "platform.cfg";
	private static final String FEATURES = INSTALL + "/features";
	private static final String LINKS = INSTALL + "/links";
	private static final String PLUGIN_XML = "plugin.xml";
	private static final String FRAGMENT_XML = "fragment.xml";
	private static final String FEATURE_XML = "feature.xml";

	private static final String CFG_SITE = "site";
	private static final String CFG_URL = "url";
	private static final String CFG_POLICY = "policy";
	private static final String[] CFG_POLICY_TYPE = {"USER-INCLUDE", "USER-EXCLUDE", "SITE-INCLUDE"};
	private static final String CFG_POLICY_TYPE_UNKNOWN = "UNKNOWN";
	private static final String CFG_LIST = "list";
	private static final String CFG_VERSION = "version";
	private static final String VERSION = "1.0";
	private static final String EOF = "eof";
	private static final int CFG_LIST_LENGTH = 10;
	
	private static final int DEFAULT_POLICY_TYPE = ISitePolicy.USER_EXCLUDE;
	private static final String[] DEFAULT_POLICY_LIST = new String[0];

	public class SiteEntry implements IPlatformConfiguration.ISiteEntry {

		private URL url;
		private ISitePolicy policy;
		private ArrayList features;
		private ArrayList plugins;

		private SiteEntry() {
		}
		private SiteEntry(URL url, ISitePolicy policy) {
			if (url==null)
				throw new IllegalArgumentException();
				
			if (policy==null)
				throw new IllegalArgumentException();
				
			this.url = url;
			this.policy = policy;
			this.features = null;
			this.plugins = null;
		}

		/*
		 * @see ISiteEntry#getURL()
		 */
		public URL getURL() {
			return url;
		}

		/*
		* @see ISiteEntry#getSitePolicy()
		*/
		public ISitePolicy getSitePolicy() {
			return policy;
		}

		/*
		 * @see ISiteEntry#setSitePolicy(ISitePolicy)
		 */
		public void setSitePolicy(ISitePolicy policy) {				
			if (policy==null)
				throw new IllegalArgumentException();
			this.policy = policy;
		}
		
		private String[] detectFeatures() {
			if (!supportsDetection())
				return new String[0];

			// locate feature entries on site
			features = new ArrayList();
			File root =
				new File(url.getFile().replace('/', File.separatorChar) + FEATURES);
			String[] list = root.list();
			String path;
			File plugin;
			for (int i = 0; list != null && i < list.length; i++) {
				path = list[i] + File.separator + FEATURE_XML;
				plugin = new File(root, path);
				if (!plugin.exists()) {
					continue;
				}
				features.add(FEATURES + "/" + path.replace(File.separatorChar, '/'));
			}				
				
			return (String[])features.toArray(new String[0]);
		}
		
		private String[] detectPlugins() {
			if (!supportsDetection())
				return new String[0];
								
			// locate plugin entries on site
			plugins = new ArrayList();
			File root =
				new File(url.getFile().replace('/', File.separatorChar) + PLUGINS);
			String[] list = root.list();
			String path;
			File plugin;
			for (int i = 0; list != null && i < list.length; i++) {
				path = list[i] + File.separator + PLUGIN_XML;
				plugin = new File(root, path);
				if (!plugin.exists()) {
					path = list[i] + File.separator + FRAGMENT_XML;
					plugin = new File(root, path);
					if (!plugin.exists())
						continue;
				}
				plugins.add(PLUGINS + "/" + path.replace(File.separatorChar, '/'));
			}				
				
			return (String[])plugins.toArray(new String[0]);
		}
		
		private boolean supportsDetection() {
			return url.getProtocol().equals("file");
		}
		
		private String[] getDetectedFeatures() {
			if (features == null)
				return detectFeatures();
			else
				return (String[])features.toArray(new String[0]);
		}
		
		private String[] getDetectedPlugins() {
			if (plugins == null)
				return detectPlugins();
			else 
				return (String[])plugins.toArray(new String[0]);
		}
	}

	public class SitePolicy implements IPlatformConfiguration.ISitePolicy {

		private int type;
		private String[] list;

		private SitePolicy() {
		}
		private SitePolicy(int type, String[] list) {
			if (type != ISitePolicy.USER_INCLUDE
				&& type != ISitePolicy.USER_EXCLUDE
				&& type != ISitePolicy.SITE_INCLUDE)
				throw new IllegalArgumentException();
			this.type = type;

			if (list == null)
				this.list = new String[0];
			else
				this.list = list;
		}

		/*
		 * @see ISitePolicy#getType()
		 */
		public int getType() {
			return type;
		}

		/*
		* @see ISitePolicy#getList()
		*/
		public String[] getList() {
			return list;
		}

		/*
		 * @see ISitePolicy#setList(String[])
		 */
		public void setList(String[] list) {
			if (list == null)
				this.list = new String[0];
			else
				this.list = list;
		}

	}

	PlatformConfiguration() throws IOException {
		this.sites = new HashMap();
		this.nativeSites = new HashMap();
		initializeCurrent();
	}
	
	PlatformConfiguration(URL url) throws IOException {
		this.sites = new HashMap();
		this.nativeSites = new HashMap();
		initialize(url);
	}

	/*
	 * @see IPlatformConfiguration#createSiteEntry(URL, ISitePolicy)
	 */
	public ISiteEntry createSiteEntry(URL url, ISitePolicy policy) {
		return new PlatformConfiguration.SiteEntry(url, policy);
	}

	/*
	 * @see IPlatformConfiguration#createSitePolicy(int, String[])
	 */
	public ISitePolicy createSitePolicy(int type, String[] list) {
		return new PlatformConfiguration.SitePolicy(type, list);
	}

	/*
	 * @see IPlatformConfiguration#configureSite(ISiteEntry)
	 */
	public void configureSite(ISiteEntry entry) {
		configureSite(entry, false);
	}

	/*
	 * @see IPlatformConfiguration#configureSite(ISiteEntry, boolean)
	 */
	public void configureSite(ISiteEntry entry, boolean replace) {

		if (entry == null)
			return;

		URL key = entry.getURL();
		if (key == null)
			return;

		if (sites.containsKey(key) && !replace)
			return;

		sites.put(key, entry);
	}

	/*
	 * @see IPlatformConfiguration#unconfigureSite(ISiteEntry)
	 */
	public void unconfigureSite(ISiteEntry entry) {
		if (entry == null)
			return;

		URL key = entry.getURL();
		if (key == null)
			return;

		sites.remove(key);
	}

	/*
	 * @see IPlatformConfiguration#getConfiguredSites()
	 */
	public ISiteEntry[] getConfiguredSites() {
		if (sites.size() == 0)
			return new ISiteEntry[0];

		return (ISiteEntry[]) sites.values().toArray(new ISiteEntry[0]);
	}

	/*
	 * @see IPlatformConfiguration#findConfiguredSite(URL)
	 */
	public ISiteEntry findConfiguredSite(URL url) {
		if (url == null)
			return null;

		return (ISiteEntry) sites.get(url);
	}

	/*
	 * @see IPlatformConfiguration#getConfigurationLocation()
	 */
	public URL getConfigurationLocation() {
		return configLocation;
	}

	/*
	 * @see IPlatformConfiguration#save()
	 */
	public void save() throws IOException {
		save(configLocation);
	}
	
	/*
	 * @see IPlatformConfiguration#save(URL)
	 */
	public void save(URL url) throws IOException {		
		if (url == null)
			throw new IOException(Policy.bind("cfig.unableToSave.noURL"));

		URLConnection uc = url.openConnection();
		uc.setDoOutput(true);
		OutputStream os = null;
		try {
			os = uc.getOutputStream();
		} catch (UnknownServiceException e) {
			// retry with direct file i/o
			if (!url.getProtocol().equals("file"))
				throw e;
			os = new FileOutputStream(url.getFile());
		}
		PrintWriter w = new PrintWriter(os);
		try {
			write(w);
		} finally {
			w.close();
		}
	}

	private void initializeCurrent() throws IOException {

		// FIXME:
		// check for safe mode
		// flag: -safe		come up in safe mode (loads configuration
		//                  but compute "safe" plugin path

		// determine configuration to use
		// flag: -config COMMON | HOME | CURRENT | <path>
		//        COMMON	in <eclipse>/install/<cfig>
		//        HOME		in <user.home>/eclipse/install/<cfig>
		//        CURRENT	in <user.dir>/eclipse/install/<cfig>
		//        <path>	as specififed
		//        SAFE		come up in SAFE mode
		// if none specified, search order for configuration is
		// CURRENT, HOME, COMMON
		// if none found new configuration is created in the first
		// r/w location in order of COMMON, HOME, CURRENT	

		// load configuration. 

		// if none found, do default setup
		setupDefaultConfiguration();
		
		// detect links

		// detect changes

		// if (feature changes)
		// 	trigger alternate startup (into update app)
		// else 
		//	process any plugin-only changes
	}
	
	private void initialize(URL url) throws IOException {
		if (url == null) 
			return;
			
		load(url);
		configLocation = url;
	}
	
	private void load(URL url) throws IOException {		
		
		if (url == null) 
			throw new IOException(Policy.bind("cfig.unableToLoad.noURL"));
		
		// try to load saved properties file
		Properties props = new Properties();
		InputStream is = null;
		try {
			is = url.openStream();
			props.load(is);
			// check to see if we have complete config file
			if (!EOF.equals(props.getProperty(EOF))) {
				if (DEBUG)
					debug("skipping incomplete configuration file " + url.toString());
				throw new IOException(Policy.bind("cfig.unableToLoad.incomplete",url.toString()));
			}
		} finally {
			if (is!=null) {
				try {
					is.close();
				} catch(IOException e) {
				}
			}
		}
		
		if (DEBUG)
			debug("using configuration file " + url.toString());
		
		// load simple properties 
		// FIXME: currently none
		
		// load site properties
		ISiteEntry se = loadSite(props, CFG_SITE+".0", null);					
		for (int i=1; se != null; i++) {
			configureSite(se);
			se = loadSite(props, CFG_SITE+"."+i, null);	
		}
	}
	
	private ISiteEntry loadSite(Properties props, String name, ISiteEntry dflt) {

		String urlString = loadAttribute(props, name+"."+CFG_URL, null);
		if (urlString == null)
			return dflt;
			
		URL url = null;
		try {
			url = new URL(urlString);
		} catch(MalformedURLException e) {
			return dflt;
		}
			
		int policyType;
		String[] policyList;
		String typeString = loadAttribute(props, name+"."+CFG_POLICY, null);
		if (typeString == null) {
			policyType = DEFAULT_POLICY_TYPE;
			policyList = DEFAULT_POLICY_LIST;
		} else {
			int i;
			for (i=0; i<CFG_POLICY_TYPE.length; i++) {
				if (typeString.equals(CFG_POLICY_TYPE[i])) {
					break;
				}
			}
			if (i>=CFG_POLICY_TYPE.length) {
				policyType = DEFAULT_POLICY_TYPE;
				policyList = DEFAULT_POLICY_LIST;
			} else {
				policyType = i;
				policyList = loadListAttribute(props, name+"."+CFG_LIST, new String[0]);
			}
		}

		ISitePolicy sp = createSitePolicy(policyType, policyList);
		return createSiteEntry(url,sp);
	}
	
	private String[] loadListAttribute(Properties props, String name, String[] dflt) {
		ArrayList list = new ArrayList();
		String value = loadAttribute(props, name+".0",null);
		if (value == null)
			return dflt;
			
		for (int i=1; value != null; i++) {
			loadListAttributeSegment(list, value);
			value = loadAttribute(props, name+"."+i, null);
		}
		return (String[])list.toArray(new String[0]);
	}
	
	private void loadListAttributeSegment(ArrayList list,String value) {
		
		if (value==null) return;
	
		StringTokenizer tokens = new StringTokenizer(value, ",");
		String token;
		while (tokens.hasMoreTokens()) {
			token = tokens.nextToken().trim();
			if (!token.equals("")) 
				list.add(token);
		}
		return;
	}
		
	private String loadAttribute(Properties props, String name, String dflt) {
		String prop = props.getProperty(name);
		if (prop == null)
			return dflt;
		else
			return prop.trim();
	}
	
	private void setupDefaultConfiguration() throws IOException {
		// default initial startup configuration:
		// site: current install, policy: USER_EXCLUDE, empty list
		ISitePolicy sp = createSitePolicy(DEFAULT_POLICY_TYPE, DEFAULT_POLICY_LIST);
		ISiteEntry se =
			createSiteEntry(
				BootLoader.getInstallURL(), sp);
		configureSite(se);
		configLocation =
				new URL(BootLoader.getInstallURL(), INSTALL + "/" + CONFIG_FILE);
		if (DEBUG)
			debug("creating default configuration " + configLocation.getFile());
	}

	private void write(PrintWriter w) {
		writeAttribute(w, CFG_VERSION, VERSION);
		SiteEntry[] list = (SiteEntry[]) sites.values().toArray(new SiteEntry[0]);
		if (list.length == 0)
			return;

		for (int i = 0; i < list.length; i++) {
			writeSite(w, CFG_SITE + "." + Integer.toString(i), list[i]);
		}
		writeAttribute(w, EOF, EOF);
	}

	private void writeSite(PrintWriter w, String id, SiteEntry entry) {
		writeAttribute(w, id + "." + CFG_URL, entry.getURL().toString());
		int type = entry.getSitePolicy().getType();
		String typeString = CFG_POLICY_TYPE_UNKNOWN;
		try {
			typeString = CFG_POLICY_TYPE[type];
		} catch (IndexOutOfBoundsException e) {
		}
		writeAttribute(w, id + "." + CFG_POLICY, typeString);
		writeListAttribute(w, id + "." + CFG_LIST, entry.getSitePolicy().getList());
	}
	
	private void writeListAttribute(PrintWriter w, String id, String[] list) {
		if (list == null || list.length == 0)
			return;
			
		String value = "";
		int listLen = 0;
		int listIndex = 0;
		for (int i = 0; i < list.length; i++) {
			if (listLen != 0)
				value += ",";
			else
				value = "";
			value += list[i];

			if (++listLen >= CFG_LIST_LENGTH) {
				writeAttribute(w, id + "." + Integer.toString(listIndex++), value);
				listLen = 0;
			}
		}
		if (listLen != 0)
			writeAttribute(w, id + "." + Integer.toString(listIndex), value);
	}

	private void writeAttribute(PrintWriter w, String id, String value) {
		w.println(id + "=" + escapedValue(value));
	}

	private String escapedValue(String value) {
		// FIXME: implement escaping for property file
		return value;
	}
	
	private static void debug(String s) {
		System.out.println("PlatformConfiguration: " + s);
	}
}