package com.asgardians;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.runelite.client.party.messages.PartyMemberMessage;

/** A party member's own answer to a ready check: only whether they're ready, nothing else about them. */
@Data
@EqualsAndHashCode(callSuper = true)
public class AsgReadyAnswer extends PartyMemberMessage
{
	/** The check it answers ({@link AsgReadyCheck#getStarted()}). */
	private long started;
	private boolean ready;
	private String name;
}
