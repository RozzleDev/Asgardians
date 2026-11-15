package com.asgardians;

import com.google.gson.Gson;
import java.awt.Dimension;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import okhttp3.OkHttpClient;

/**
 * Opens the real side panel on the Santa Claws page, without RuneLite or a login.
 * <ul>
 * <li>No arguments: sample data ({@code santa-sample.json}: Frosty with a Defend! attack and a minion attack
 * charging). Pressing Defend! just pretends.</li>
 * <li>{@code --live <api address> <rsn> <account hash>}: the real page from a (local) clan website, reloaded every
 * 10 seconds, as that clan member. Defend! really presses. The RSN must be on that website's clan roster, e.g.
 * {@code --live http://localhost:3005 "Frigg Rune" 7000000000000000002} on a local copy.</li>
 * </ul>
 * Run with {@code ./gradlew santaPreview} (add {@code --args="--live ..."} for live).
 */
public class SantaPreview
{
	private static final Gson GSON = new Gson();
	/** The website's pictures on this machine (the plugin loads the same files from the website). */
	private static final java.io.File SITE = new java.io.File(System.getProperty("santa.site", "C:/Users/clo/src/asgardians-v2/frontend/public"));
	private static final OkHttpClient HTTP = new OkHttpClient();
	private static final java.util.Map<String, java.awt.image.BufferedImage> ITEMS = new java.util.concurrent.ConcurrentHashMap<>();

	public static void main(String[] args) throws Exception
	{
		boolean live = args.length >= 4 && args[0].equals("--live");
		if (args.length >= 2 && args[0].equals("--icons"))
		{
			// Every act icon, big then at panel size, beaten (faded) underneath, to check the drawings.
			SantaIcons.Kind[] kinds = SantaIcons.Kind.values();
			java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(kinds.length * 70, 120, java.awt.image.BufferedImage.TYPE_INT_RGB);
			java.awt.Graphics2D g = img.createGraphics();
			g.setColor(PanelParts.BACKGROUND);
			g.fillRect(0, 0, img.getWidth(), img.getHeight());
			for (int i = 0; i < kinds.length; i++)
			{
				int[][] spots = {{64, 2, 0}, {13, 70, 0}, {13, 95, 1}};
				for (int[] s : spots)
				{
					javax.swing.JComponent c = SantaIcons.icon(kinds[i], s[0], s[2] == 1);
					c.setSize(s[0], s[0]);
					java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create(i * 70 + (70 - s[0]) / 2, s[1], s[0], s[0]);
					c.paint(g2);
					g2.dispose();
				}
			}
			g.dispose();
			javax.imageio.ImageIO.write(img, "png", new java.io.File(args[1]));
			System.exit(0);
			return;
		}
		if (args.length >= 2 && args[0].equals("--png"))
		{
			// A picture of the sample page (tall enough for everything, with the first drop category open).
			SwingUtilities.invokeAndWait(() ->
			{
				AsgardiansPanel panel = PanelPreview.panel();
				JFrame frame = new JFrame();
				frame.add(panel);
				panel.setPreferredSize(new Dimension(242, Integer.parseInt(args.length >= 3 ? args[2] : "2000")));
				frame.pack();
				panel.setSantaImages(images(true));
				sample(panel);
				for (String key : System.getProperty("santa.open", "").split(","))
				{
					if (!key.isEmpty())
					{
						panel.santaOpenForPreview(key);
					}
				}
				panel.show(AsgardiansPanel.SANTA);
				if (args.length >= 4 && args[3].equals("bottom"))
				{
					// The page is taller than a screen: scroll to the end to picture the drops.
					frame.validate();
					javax.swing.JScrollPane scroll = (javax.swing.JScrollPane) SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class,
						panel.getComponent(1) instanceof javax.swing.JScrollPane ? ((javax.swing.JScrollPane) panel.getComponent(1)).getViewport().getView()
							: panel.getComponent(1));
					for (java.awt.Component c : panel.getComponents())
					{
						if (c instanceof javax.swing.JScrollPane)
						{
							scroll = (javax.swing.JScrollPane) c;
						}
					}
					if (scroll != null)
					{
						scroll.getViewport().validate();
						javax.swing.JViewport port = scroll.getViewport();
						port.setViewPosition(new java.awt.Point(0, Math.max(0, port.getView().getPreferredSize().height - port.getHeight())));
					}
				}
				PanelPreview.save(frame, panel, new java.io.File(args[1]));
				frame.dispose();
			});
			System.exit(0);
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			AsgardiansPanel panel = PanelPreview.panel();
			JFrame frame = new JFrame(live ? "Santa Claws panel (live: " + args[1] + " as " + args[2] + ")" : "Santa Claws panel (sample data)");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.getContentPane().setBackground(PanelParts.BACKGROUND);
			frame.add(panel);
			panel.setPreferredSize(new Dimension(242, 820));
			frame.pack();
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
			panel.setSantaImages(images(false));
			if (live)
			{
				live(panel, args[1], args[2], Long.parseLong(args[3]));
			}
			else
			{
				sample(panel);
			}
			panel.onActivate();
			panel.show(AsgardiansPanel.SANTA);
		});
	}

	/**
	 * Boss pictures from the website's folder on this machine; drop pictures from the OSRS wiki (in game RuneLite draws
	 * items itself). {@code now}: load before returning, for saving a picture of the page.
	 */
	private static SantaPictures.Images images(boolean now)
	{
		return new SantaPictures.Images()
		{
			@Override
			public void picture(String path, java.util.function.Consumer<java.awt.image.BufferedImage> done)
			{
				try
				{
					java.io.File file = new java.io.File(SITE, path);
					if (file.exists())
					{
						done.accept(javax.imageio.ImageIO.read(file));
					}
				}
				catch (java.io.IOException ignored)
				{
					// No picture: the box stays empty.
				}
			}

			@Override
			public void item(SantaData.Drop drop, java.util.function.Consumer<java.awt.image.BufferedImage> done)
			{
				java.awt.image.BufferedImage have = ITEMS.get(drop.getItemName());
				if (have != null)
				{
					done.accept(have);
					return;
				}
				Runnable fetch = () ->
				{
					java.awt.image.BufferedImage img = wiki(drop);
					if (img != null)
					{
						ITEMS.put(drop.getItemName(), img);
						if (now)
						{
							done.accept(img);
						}
						else
						{
							SwingUtilities.invokeLater(() -> done.accept(img));
						}
					}
				};
				if (now)
				{
					fetch.run();
				}
				else
				{
					new Thread(fetch, "santa-preview-icon").start();
				}
			}
		};
	}

	/** The item's wiki picture, like the website: its own address when set, else the item name as the file name. */
	private static java.awt.image.BufferedImage wiki(SantaData.Drop drop)
	{
		String url = drop.getWikiImageUrl();
		if (url == null)
		{
			String name = drop.getItemName().replaceAll("\\s*\\(from [^)]*\\)\\s*$", "").trim().replace(' ', '_');
			url = "https://oldschool.runescape.wiki/images/" + okhttp3.HttpUrl.parse("https://x/" + name + ".png").encodedPath().substring(1);
		}
		try (okhttp3.Response r = HTTP.newCall(new okhttp3.Request.Builder().url(url).header("User-Agent", "Asgardians clan plugin preview").build()).execute())
		{
			return r.isSuccessful() && r.body() != null ? javax.imageio.ImageIO.read(r.body().byteStream()) : null;
		}
		catch (java.io.IOException | IllegalArgumentException e)
		{
			return null;
		}
	}

	/** The saved sample page. Defend! counts up locally so the button can be tried. */
	private static void sample(AsgardiansPanel panel)
	{
		SantaData.Page page = load();
		// The sample was saved on one day; keep its clocks running from now.
		java.time.Instant then = java.time.Instant.parse(page.getBoard().getNow());
		java.time.Duration shift = java.time.Duration.between(then, java.time.Instant.now());
		page.getBoard().setNow(java.time.Instant.now().toString());
		if (Boolean.getBoolean("santa.decoy"))
		{
			// -Dsanta.decoy=true: Seeing Double has landed, so a fake boss (100 of 150 HP left) soaks drops first.
			SantaData.Unit fake = new SantaData.Unit();
			fake.setKey("decoy");
			fake.setName("Frosty?");
			fake.setHp(100);
			fake.setMaxHp(150);
			fake.setDecoy(true);
			java.util.List<SantaData.Unit> units = new java.util.ArrayList<>(page.getBoard().active().getUnits());
			units.add(0, fake);
			page.getBoard().active().setUnits(units);
		}
		if (Boolean.getBoolean("santa.nutshot"))
		{
			// -Dsanta.nutshot=true: Nut Shot has landed on two of the top damage dealers.
			java.util.List<SantaData.Curse> curses = new java.util.ArrayList<>();
			for (String rsn : new String[]{"Frigg Rune", "Asgard Odin"})
			{
				SantaData.Curse c = new SantaData.Curse();
				c.setAttackName("Nut Shot");
				c.setRsn(rsn);
				c.setMode("half");
				curses.add(c);
			}
			page.getBoard().active().getEffects().setCurses(curses);
		}
		if (Boolean.getBoolean("santa.blind"))
		{
			// -Dsanta.blind=true: Throws Shade has landed (3h 12m left), with one hit since.
			SantaData.Effects fx = page.getBoard().active().getEffects();
			fx.setBlind(true);
			fx.setBlindUntil(java.time.Instant.now().plusSeconds(3 * 3600 + 12 * 60).toString());
			page.getBoard().active().getUnits().forEach(u -> u.setHp(null));
			SantaData.LogItem hit = new SantaData.LogItem();
			hit.setKind("hit");
			hit.setRsn("Asgard Odin");
			hit.setItemName("Horn of Bran");
			hit.setHidden(true);
			page.getBoard().setRecent(java.util.List.of(hit));
		}
		for (SantaData.Pending p : new SantaData.Pending[]{page.getBoard().active().getPendingAttack(), page.getBoard().active().getPendingMinionAttack()})
		{
			if (p != null && p.getDefendBy() != null)
			{
				p.setDefendBy(java.time.Instant.parse(p.getDefendBy()).plus(shift).toString());
			}
		}
		panel.setSantaActions(() -> panel.updateSanta(page, null, null, Instant.now()), id ->
		{
			SantaData.Pending p = page.getBoard().active().getPendingAttack();
			if (p != null && p.getId() == id)
			{
				p.setProgress(p.getProgress() + 1);
				p.setDefended(p.getProgress() >= p.getDefence().getAmount());
			}
			// What the website says back, after a moment like a real call.
			Timer reply = new Timer(400, e -> panel.santaDefended(id, p != null && p.isDefended() ? "Blocked! You made it "
				+ p.getProgress() + " / " + p.getDefence().getAmount() + "." : "You're defending! " + (p != null ? p.getProgress() : 0)
				+ " / 25 so far. Get your clanmates in! (sample)"));
			reply.setRepeats(false);
			reply.start();
		}, id ->
		{
			// Skipping cancels the attack and spends a skip, like the website.
			SantaData.Encounter e = page.getBoard().active();
			if (e.getPendingAttack() != null && e.getPendingAttack().getId() == id)
			{
				e.setPendingAttack(null);
			}
			if (e.getPendingMinionAttack() != null && e.getPendingMinionAttack().getId() == id)
			{
				e.setPendingMinionAttack(null);
			}
			page.getBoard().setSkipsLeft(Math.max(0, page.getBoard().getSkipsLeft() - 1));
			Timer reply = new Timer(400, ev -> panel.santaDefended(id, "Skipped! That attack is cancelled. (sample)"));
			reply.setRepeats(false);
			reply.start();
		});
		panel.updateSanta(page, null, null, Instant.now());
	}

	private static SantaData.Page load()
	{
		try (InputStreamReader in = new InputStreamReader(SantaPreview.class.getResourceAsStream("santa-sample.json"), StandardCharsets.UTF_8))
		{
			return GSON.fromJson(in, SantaData.Page.class);
		}
		catch (java.io.IOException e)
		{
			throw new IllegalStateException("santa-sample.json is missing", e);
		}
	}

	/** The real page from a clan website, as a roster member, like the plugin does in game. */
	private static void live(AsgardiansPanel panel, String address, String rsn, long accountHash)
	{
		ApiClient api = new ApiClient(new OkHttpClient(), GSON);
		String[] key = {null};
		Runnable refresh = () ->
		{
			if (key[0] == null)
			{
				return;
			}
			api.get(ApiClient.endpoint(address, "santa-claws"), key[0], SantaData.Page.class, result -> SwingUtilities.invokeLater(() ->
			{
				if (result.getStatus() == 204 || result.getStatus() == 404)
				{
					panel.updateSanta(null, null, null, Instant.now());
				}
				else if (result.ok())
				{
					panel.updateSanta(result.getBody(), null, null, Instant.now());
				}
				else
				{
					panel.updateSanta(null, null, "Santa Claws didn't load (" + result.getStatus() + "): " + result.getError(), Instant.now());
				}
			}));
		};
		panel.setSantaActions(refresh, id -> api.post(ApiClient.endpoint(address, "santa-claws/attacks/" + id + "/defend"), key[0],
			new com.google.gson.JsonObject(), SantaData.Rally.class, result -> SwingUtilities.invokeLater(() -> panel.santaDefended(id,
				result.ok() ? (result.getBody().isBlocked() ? "Blocked! You made it " : "You're defending! ")
					+ result.getBody().getRallied() + " / " + result.getBody().getTarget()
					: SantaTabs.problemText(GSON, result.getError())))),
			id -> api.post(ApiClient.endpoint(address, "santa-claws/attacks/" + id + "/skip"), key[0], new com.google.gson.JsonObject(),
				com.google.gson.JsonObject.class, result -> SwingUtilities.invokeLater(() -> panel.santaDefended(id,
					result.ok() ? "Skipped! That attack is cancelled." : SantaTabs.problemText(GSON, result.getError())))),
			(id, option) ->
			{
				com.google.gson.JsonObject body = new com.google.gson.JsonObject();
				body.addProperty("option", option);
				api.post(ApiClient.endpoint(address, "santa-claws/attacks/" + id + "/vote"), key[0], body, com.google.gson.JsonObject.class,
					result -> SwingUtilities.invokeLater(() ->
					{
						if (result.ok() || result.getStatus() == 409)
						{
							panel.santaVoted(id, result.ok() ? option : null, result.ok()
								? "You voted " + option + ". The most-voted answer when time's up decides it."
								: SantaTabs.problemText(GSON, result.getError()));
						}
						else
						{
							panel.santaDefended(id, SantaTabs.problemText(GSON, result.getError()));
						}
					}));
			});
		panel.updateSanta(null, "Connecting to " + address + " as " + rsn + "…", null, Instant.now());
		api.register(address, rsn, accountHash, "STANDARD", result -> SwingUtilities.invokeLater(() ->
		{
			if (result.getInstallKey() == null)
			{
				panel.updateSanta(null, "Couldn't register " + rsn + " (" + result.getStatus() + "): " + result.getError()
					+ ". Is the API running, and is the RSN on its clan roster?", null, Instant.now());
				return;
			}
			key[0] = result.getInstallKey();
			refresh.run();
		}));
		new Timer(10_000, e -> refresh.run()).start();
	}
}
