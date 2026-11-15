package com.asgardians;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Sent through RuneLite's party server when a member starts a ready check. {@code started} (the starter's clock, in
 * milliseconds) names the check, so answers can say which one they're for.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AsgReadyCheck extends PartyMemberMessage
{
	private long started;
	/** The starter's character name, for members without RuneLite's Party plugin on. */
	private String name;
}
