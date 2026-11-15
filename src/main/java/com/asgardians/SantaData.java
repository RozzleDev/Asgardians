package com.asgardians;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import lombok.Data;

/**
 * Santa Claws, the clan's Christmas co-op raid, as {@code GET /api/v1/plugin/santa-claws} sends it: the board (the
 * bosses in order, the one the clan is fighting with its HP, bonus, live attacks and debuffs), the drops that hurt the
 * current boss, and where the member stands. Bosses the clan hasn't reached come back wrapped (no name or picture).
 * Field names match the website's JSON; everything may be missing, so callers null-check.
 */
final class SantaData
{
	private SantaData()
	{
	}

	static <T> List<T> orEmpty(List<T> list)
	{
		return list != null ? list : Collections.emptyList();
	}

	@Data
	static class Page
	{
		Board board;
		List<Rope> drops;
		Me me;
		/** The current boss's skilling board; null until the website has worked it out. */
		Skillers skillers;
		/** Question attacks this member has voted on, in the plugin or on Discord: attack id → the option they picked. */
		java.util.Map<Long, String> myVotes;
	}

	/** XP in the current boss's skills since it came up (reported by the clan plugin): the top 10, and the member's own place. */
	@Data
	static class Skillers
	{
		List<Skiller> top;
		List<String> skills;
		String updatedAt;
		Long myXp;
		Integer myRank;
	}

	@Data
	static class Skiller
	{
		String rsn;
		long xp;
	}

	@Data
	static class Board
	{
		Event event;
		String now;
		int activeIndex;
		boolean complete;
		List<Encounter> encounters;
		List<Player> topDamage;
		/** Universal skips the clan has banked; staff spend them to cancel an attack. */
		int skipsLeft;
		/** The latest log entries, newest first (hits are redacted while the clan is blinded). */
		List<LogItem> recent;
		/** The word Discord screenshots must show; the codeword overlay shows it while the event is live. */
		String codeword;

		/** The boss the clan is fighting, or null when it's all over or there's no line-up. */
		Encounter active()
		{
			List<Encounter> list = orEmpty(encounters);
			return activeIndex >= 0 && activeIndex < list.size() ? list.get(activeIndex) : null;
		}
	}

	/** One log entry, as much as the panel needs: a hit is who landed which drop. */
	@Data
	static class LogItem
	{
		long id;
		/** hit, attack, attack-end, defeat… */
		String kind;
		String rsn;
		String itemName;
		/** Damage hidden (the clan is blinded). */
		boolean hidden;
		String at;
		int damage;
		Integer encounterId;
		/** On attack entries: the attack's name, and whether the minions launched it. */
		String attackName;
		boolean minion;
	}

	@Data
	static class Event
	{
		String name;
		/** upcoming, live or complete. */
		String status;
		String startsAt;
		String endsAt;

		boolean isLive()
		{
			return "live".equals(status);
		}

		boolean isUpcoming()
		{
			return "upcoming".equals(status);
		}

		Instant start()
		{
			return ClanData.parse(startsAt);
		}

		Instant end()
		{
			return ClanData.parse(endsAt);
		}
	}

	@Data
	static class Encounter
	{
		int id;
		int sortOrder;
		/** "ACT I" … "FINALE". */
		String actLabel;
		/** locked, active or defeated. */
		String status;
		/** False while it's still a surprise (and for every boss before the start). */
		boolean revealed;
		String name;
		String shortName;
		String blurb;
		/** The website's picture, e.g. /img/santa-claws/frosty.webp (the plugin loads a JPEG copy, see SantaTabs.pluginPicture). */
		String imageUrl;
		Art art;
		List<Unit> units;
		Bonus bonus;
		String prizeText;
		Effects effects;
		/** The boss's attacks and its minions' one, for what a debuff does. */
		List<Attack> attacks;
		Attack minionAttack;
		Pending pendingAttack;
		Pending pendingMinionAttack;
		String nextAttackAt;
		String nextMinionAttackAt;

		boolean isDefeated()
		{
			return "defeated".equals(status);
		}

		boolean isActive()
		{
			return "active".equals(status);
		}

		/** The boss itself (key "boss"), or null. */
		Unit boss()
		{
			for (Unit u : orEmpty(units))
			{
				if ("boss".equals(u.getKey()))
				{
					return u;
				}
			}
			return null;
		}
	}

	/**
	 * Where the picture's own HP bar sits, in % of the picture (the live bar is painted over it), its shape ("1312/1199"),
	 * and the pictures shown as minions fall ({@code stages}) and once the boss is beaten ({@code defeated}).
	 */
	@Data
	static class Art
	{
		String aspect;
		double x;
		double y;
		double w;
		double h;
		List<String> stages;
		String defeated;
	}

	@Data
	static class Unit
	{
		String key;
		String name;
		/** Null while the clan is blinded. */
		Integer hp;
		int maxHp;
		boolean isDecoy;
		/** Where the minion's bar sits on the picture, in % (centre x, top y), like the website; null for the boss. */
		Double x;
		Double y;
	}

	@Data
	static class Bonus
	{
		String skill;
		String name;
		String description;
		long target;
		int multiplier;
		long progress;
		boolean unlocked;
		boolean frozen;
		boolean active;
	}

	@Data
	static class Defence
	{
		/** xp, kc, drops, items or rally. */
		String kind;
		long amount;
		/** The skill for xp, the WOM minigame for kc. */
		String skill;
		List<String> items;
	}

	/** One of a boss's attacks, as listed (not a live one). */
	@Data
	static class Attack
	{
		String key;
		String name;
		String description;
		/** What it does, e.g. bonus-off (freezes the bonus), drop-mod, curse. */
		String effectType;
	}

	/** A live attack: how long the clan has, and how to block it. */
	@Data
	static class Pending
	{
		long id;
		String name;
		String description;
		boolean minion;
		String defendBy;
		Defence defence;
		long progress;
		boolean defended;
		/** What the boss shouts as it charges. */
		String line;
		/** A vote attack's question, options and votes so far (never the answer). */
		Question question;
		/** A screenshot-challenge attack's challenge (proof goes in Discord), and screenshots waiting for staff. */
		String challenge;
		int waiting;
	}

	/** A live vote attack's multiple-choice question: the most-voted option when time's up decides it. */
	@Data
	static class Question
	{
		String question;
		List<String> options;
		List<Integer> votes;
	}

	@Data
	static class Effects
	{
		List<DropMod> dropMods;
		MinionHit minionHit;
		List<Curse> curses;
		int hasteRemaining;
		int hasteFactor;
		boolean blind;
		/** When the blind (Throws Shade) wears off, while blind. */
		String blindUntil;
	}

	@Data
	static class DropMod
	{
		String attackName;
		/** half, lose-highest or minus-per-die. */
		String mode;
		/** For minus-per-die: how much comes off each die. */
		int amount;
		int remaining;
	}

	@Data
	static class MinionHit
	{
		String attackName;
		/** minion-steal, minion-reroll or minion-wrap. */
		String type;
		/** Steal: dice each minion takes. */
		int amount;
		/** How many minions were standing (each one acts). */
		int strength;
	}

	@Data
	static class Curse
	{
		String attackName;
		String rsn;
		/** nullify or half. */
		String mode;
	}

	@Data
	static class Player
	{
		String rsn;
		long damage;
	}

	/** A drop category for the current boss (a raid, a group list, a solo list or a clue tier) and its drops. */
	@Data
	static class Rope
	{
		String ropeKey;
		String name;
		/** raid, group, solo or clue. */
		String kind;
		List<Drop> drops;
	}

	@Data
	static class Drop
	{
		String itemName;
		/** 1 to 5 d20s of damage. */
		int dice;
		String boss;
		/** The wiki picture, when its file name isn't just the item name. Only the preview uses it: RuneLite draws items itself. */
		String wikiImageUrl;
	}

	/** The member's place on the event's top-10 damage board; both null when they're not on it. */
	@Data
	static class Me
	{
		String rsn;
		Long damage;
		Integer rank;
		/** Moderator rank and above: may spend the clan's skips from the panel. */
		boolean canSkip;
	}

	/** After pressing Defend!: how many different members have, out of how many, and whether that blocked it. */
	@Data
	static class Rally
	{
		int rallied;
		int target;
		boolean blocked;
		String rsn;
		String attackName;
	}
}
