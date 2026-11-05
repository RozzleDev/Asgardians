package com.asgardians;

import lombok.Data;

/** Settings served by GET /api/v1/plugin/config. The defaults apply until the server answers, or if it can't be reached. */
@Data
class ServerSettings
{
	@Data
	static class Screenshots
	{
		private long minLootValue = 1_000_000;
		private boolean pets = true;
		private boolean clog = true;
		private boolean pbs = true;
		private boolean deaths = true;
		/** Item IDs that always get a screenshot whatever they're worth, such as items a live event is watching for. */
		private java.util.Set<Integer> watch = java.util.Set.of();

		/** Loot gets a screenshot when it's valuable enough or includes a watched item. */
		boolean wantsLoot(Record record)
		{
			return record.totalValue >= minLootValue || watched(record);
		}

		/** The record includes an item on the watch list. */
		boolean watched(Record record)
		{
			if (watch != null && record.getItems() != null)
			{
				for (Record.Item item : record.getItems())
				{
					if (watch.contains(item.getId()))
					{
						return true;
					}
				}
			}
			return false;
		}
	}

	private Screenshots screenshots = new Screenshots();
	/** Stops all sending without a hub update. */
	private boolean killSwitch;
	/** Oldest plugin version the server still accepts. */
	private String minVersion;
}
