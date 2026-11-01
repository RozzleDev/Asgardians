package com.asgardians;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Supplier;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * While a clan event the member is in is live, shows its codeword and the current UTC time. Overlays are part of
 * RuneLite screenshots, so staff can check a drop screenshot was taken during the event.
 */
class CodewordOverlay extends OverlayPanel
{
	private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("d MMM HH:mm:ss 'UTC'", Locale.UK)
		.withZone(ZoneOffset.UTC);

	private final AsgardiansConfig config;
	private final Supplier<ClanView> view;

	CodewordOverlay(AsgardiansPlugin plugin, AsgardiansConfig config, Supplier<ClanView> view)
	{
		super(plugin);
		this.config = config;
		this.view = view;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showCodeword())
		{
			return null;
		}
		ClanView current = view.get();
		boolean any = false;
		for (ClanView.EventView live : current.live())
		{
			ClanData.Event event = live.getEvent();
			if (event.getCodeword() == null || !event.isIn())
			{
				continue;
			}
			if (!any)
			{
				panelComponent.getChildren().add(TitleComponent.builder().text("Asgardians").color(PanelParts.HIGHLIGHT).build());
				any = true;
			}
			panelComponent.getChildren().add(LineComponent.builder().left(event.getName()).build());
			panelComponent.getChildren().add(LineComponent.builder().left("Codeword:").right(event.getCodeword())
				.rightColor(PanelParts.HIGHLIGHT).build());
		}
		if (!any)
		{
			return null;
		}
		panelComponent.getChildren().add(LineComponent.builder().left(UTC.format(Instant.now())).build());
		return super.render(graphics);
	}
}
