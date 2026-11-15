package com.asgardians;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.function.Supplier;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The party's ready check on screen while it's on, and its result for a few seconds after. Members answer by
 * right-clicking the box (Ready / Not ready) or from the panel's Party tab.
 */
class ReadyCheckOverlay extends OverlayPanel
{
	private final Supplier<ReadyCheck.View> view;

	ReadyCheckOverlay(AsgardiansPlugin plugin, Supplier<ReadyCheck.View> view, java.util.function.Consumer<Boolean> answer)
	{
		super(plugin);
		this.view = view;
		setPosition(OverlayPosition.TOP_CENTER);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Ready", "Ready check", e -> answer.accept(true));
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Not ready", "Ready check", e -> answer.accept(false));
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
			panelComponent.getChildren().add(LineComponent.builder().left("Right-click to answer").leftColor(PanelParts.QUIET).build());
		}
		return super.render(graphics);
	}
}
