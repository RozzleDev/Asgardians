package com.asgardians;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class AsgardiansPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(AsgardiansPlugin.class);
		RuneLite.main(args);
	}
}
