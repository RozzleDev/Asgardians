package com.asgardians;

/** Works out which website the plugin talks to. The -Dasgardians.apiUrl launch flag wins over the config field. */
final class ApiAddress
{
	static final String LAUNCH_FLAG = "asgardians.apiUrl";

	private ApiAddress()
	{
	}

	static String resolve(String configured)
	{
		String flag = System.getProperty(LAUNCH_FLAG);
		String address = flag != null && !flag.isBlank() ? flag : configured;
		if (address == null || address.isBlank())
		{
			address = AsgardiansConfig.DEFAULT_API_ADDRESS;
		}
		address = address.trim();
		while (address.endsWith("/"))
		{
			address = address.substring(0, address.length() - 1);
		}
		return address;
	}

	static boolean isDefault(String address)
	{
		return AsgardiansConfig.DEFAULT_API_ADDRESS.equalsIgnoreCase(address);
	}

	static boolean isInsecure(String address)
	{
		return address.toLowerCase().startsWith("http://");
	}

	static boolean fromLaunchFlag()
	{
		String flag = System.getProperty(LAUNCH_FLAG);
		return flag != null && !flag.isBlank();
	}
}
