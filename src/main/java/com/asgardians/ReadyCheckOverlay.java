package com.asgardians;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.function.Supplier;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The party's ready check on screen while it's on, and its result for a few seconds after. Members answer on the
 * panel's Party page or by typing "!asg r" / "!asg nr".
 */
class ReadyCheckOverlay extends OverlayPanel
{
	private final Supplier<ReadyCheck.View> view;

	ReadyCheckOverlay(AsgardiansPlugin plugin, Supplier<ReadyCheck.View> view)
	{
		super(plugin);
		this.view = view;
		setPosition(OverlayPosition.TOP_CENTER);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		ReadyCheck.View v = view.get();
		if (v == null)
		{
			return null;
		}
		panelComponent.setPreferredSize(new Dimension(150, 0));
		panelComponent.getChildren().add(TitleComponent.builder()
			.text(v.isOver() ? "Ready check over" : "Ready check · " + v.getSecondsLeft() + "s")
			.color(PanelParts.HIGHLIGHT).build());
		for (ReadyCheck.Line l : v.getLines())
		{
			panelComponent.getChildren().add(LineComponent.builder().left(l.getName())
				.right(l.getAnswer() == ReadyCheck.Answer.READY ? "Ready" : l.getAnswer() == ReadyCheck.Answer.NOT_READY ? "Not ready" : "...")
				.rightColor(PartyTabs.colour(l.getAnswer())).build());
		}
		if (!v.isOver() && v.getMine() == ReadyCheck.Answer.WAITING)
		{
			panelComponent.getChildren().add(LineComponent.builder().left("Type !asg r to ready up").leftColor(PanelParts.QUIET).build());
		}
		return super.render(graphics);
	}
}
