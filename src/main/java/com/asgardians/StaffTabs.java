package com.asgardians;

import static com.asgardians.PanelParts.BAD;
import static com.asgardians.PanelParts.Card;
import static com.asgardians.PanelParts.QUIET;
import static com.asgardians.PanelParts.TEXT;
import static com.asgardians.PanelParts.small;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.Instant;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import lombok.Value;

/**
 * Staff notes, for clan staff only: general notes on the Staff notes page ("I'm holding 100m for Saturday's event"),
 * and notes on a member under their profile. Notes are kept on the clan website, which only answers staff, so they
 * show there too. The plugin only shows this when the website says the member is staff. Swing thread.
 */
final class StaffTabs
{
	/** The longest note the website takes. */
	static final int MAX_LENGTH = 500;

	@Value
	static class Input
	{
		/** Null while loading or when they couldn't be loaded. */
		List<ClanData.StaffNote> notes;
		boolean loading;
		/** Why the notes can't be shown, or null. */
		String error;
		Instant now;
	}

	interface Actions
	{
		/** Adds a note: about a member (rsn), or a general one (rsn null). */
		void add(String rsn, String text);

		void delete(String rsn, ClanData.StaffNote note);

		/** Sets the message of the day; empty clears it. */
		default void setMotd(String text)
		{
		}

		/** Posts an announcement to the News page. */
		default void postNews(String title, String body)
		{
		}
	}

	/** The longest message of the day. */
	static final int MOTD_LENGTH = 300;

	/** A text box styled like the note box. */
	private static JScrollPane box(JTextArea area, int height)
	{
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
		JScrollPane box = new JScrollPane(area);
		box.setBorder(BorderFactory.createLineBorder(PanelParts.LINE));
		box.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		box.setPreferredSize(new Dimension(10, height));
		box.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		return box;
	}

	private static JPanel wide(java.awt.Component c)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(c, BorderLayout.CENTER);
		row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		return row;
	}

	/** Staff set or clear the message of the day that every member sees at login and on Home. */
	static final class MotdForm extends JPanel
	{
		private final JTextArea text = new JTextArea(3, 10);
		private final javax.swing.JLabel status = small(" ", QUIET);

		MotdForm(Actions actions)
		{
			setLayout(new BoxLayout(this));
			setOpaque(false);
			text.setToolTipText("Shown to every member at login and on Home, up to " + MOTD_LENGTH + " characters");
			add(box(text, 54));
			add(Box.createVerticalStrut(4));
			JPanel buttons = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
			buttons.setOpaque(false);
			buttons.add(PanelParts.button("Set message", true, () ->
			{
				String t = clean(text.getText());
				if (t != null)
				{
					actions.setMotd(t.length() > MOTD_LENGTH ? t.substring(0, MOTD_LENGTH) : t);
					status("Saving...", false);
				}
			}));
			buttons.add(PanelParts.button("Clear", () ->
			{
				actions.setMotd("");
				status("Clearing...", false);
			}));
			add(wide(buttons));
			status.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			add(status);
			setAlignmentX(LEFT_ALIGNMENT);
		}

		void status(String message, boolean error)
		{
			status.setText(message);
			status.setForeground(error ? BAD : QUIET);
		}

		/** Puts the current message in the box, unless the member is typing a new one. */
		void current(String motd)
		{
			if (!text.isFocusOwner() && text.getText().isEmpty() && motd != null)
			{
				text.setText(motd);
			}
		}

		void clearText()
		{
			text.setText("");
		}
	}

	/** Staff post an announcement to the News page, as on the website. */
	static final class NewsForm extends JPanel
	{
		private final javax.swing.JTextField title = new javax.swing.JTextField();
		private final JTextArea body = new JTextArea(4, 10);
		private final javax.swing.JLabel status = small(" ", QUIET);

		NewsForm(Actions actions)
		{
			setLayout(new BoxLayout(this));
			setOpaque(false);
			title.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
			add(wide(PanelParts.plain("Title", QUIET)));
			add(wide(title));
			add(Box.createVerticalStrut(4));
			add(wide(PanelParts.plain("Post", QUIET)));
			add(box(body, 72));
			add(Box.createVerticalStrut(4));
			add(wide(PanelParts.button("Post to News", true, () ->
			{
				String t = clean(title.getText());
				String b = clean(body.getText());
				if (t == null || b == null)
				{
					status("Add a title and some words first.", true);
					return;
				}
				actions.postNews(t.length() > 100 ? t.substring(0, 100) : t, b.length() > 4000 ? b.substring(0, 4000) : b);
				status("Posting...", false);
			})));
			status.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			add(status);
			setAlignmentX(LEFT_ALIGNMENT);
		}

		void status(String message, boolean error)
		{
			status.setText(message);
			status.setForeground(error ? BAD : QUIET);
		}

		void clearText()
		{
			title.setText("");
			body.setText("");
		}
	}

	/** A note box and its Add button that stay put while the notes above them are redrawn, so typing isn't lost. */
	static final class Form extends JPanel
	{
		private final JTextArea text = new JTextArea(3, 10);
		private final JButton add;
		private String rsn;

		Form(String hint, Actions actions)
		{
			setLayout(new BoxLayout(this));
			setOpaque(false);
			text.setLineWrap(true);
			text.setWrapStyleWord(true);
			text.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
			text.setToolTipText(hint);
			JScrollPane box = new JScrollPane(text);
			box.setBorder(BorderFactory.createLineBorder(PanelParts.LINE));
			box.setAlignmentX(LEFT_ALIGNMENT);
			box.setPreferredSize(new Dimension(10, 54));
			box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
			add(box);
			add(Box.createVerticalStrut(4));
			add = PanelParts.button("Add note", true, () ->
			{
				String note = clean(text.getText());
				if (note != null)
				{
					actions.add(rsn, note);
					text.setText("");
				}
			});
			JPanel row = new JPanel(new BorderLayout());
			row.setOpaque(false);
			row.add(add, BorderLayout.CENTER);
			row.setAlignmentX(LEFT_ALIGNMENT);
			add(row);
			JPanel hintRow = PanelParts.column();
			hintRow.add(small(hint, QUIET));
			hintRow.setAlignmentX(LEFT_ALIGNMENT);
			add(hintRow);
			setAlignmentX(LEFT_ALIGNMENT);
		}

		/** Which member new notes are about; null for general notes. */
		void about(String rsn)
		{
			if (!java.util.Objects.equals(this.rsn, rsn))
			{
				text.setText("");
			}
			this.rsn = rsn;
		}
	}

	/** BoxLayout along the column. */
	private static final class BoxLayout extends javax.swing.BoxLayout
	{
		BoxLayout(JPanel panel)
		{
			super(panel, Y_AXIS);
		}
	}

	private StaffTabs()
	{
	}

	/** Trimmed, cut to the longest allowed, or null when there's nothing to add. */
	static String clean(String text)
	{
		if (text == null || text.isBlank())
		{
			return null;
		}
		String t = text.trim();
		return t.length() > MAX_LENGTH ? t.substring(0, MAX_LENGTH) : t;
	}

	/** The notes, newest first, into {@code column} (which is cleared first). */
	static void drawNotes(JPanel column, String rsn, Input in, Actions actions)
	{
		column.removeAll();
		if (in.getError() != null)
		{
			column.add(small(in.getError(), BAD));
			return;
		}
		if (in.getNotes() == null)
		{
			column.add(small(in.isLoading() ? "Loading notes..." : "Notes couldn't be loaded.", QUIET));
			return;
		}
		if (in.getNotes().isEmpty())
		{
			column.add(small(rsn != null ? "No notes about " + rsn + " yet." : "No notes yet.", QUIET));
			return;
		}
		boolean first = true;
		for (ClanData.StaffNote note : in.getNotes())
		{
			if (note == null || note.getText() == null)
			{
				continue;
			}
			if (!first)
			{
				column.add(Box.createVerticalStrut(6));
			}
			first = false;
			JPanel one = PanelParts.column();
			one.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, PanelParts.HIGHLIGHT),
				BorderFactory.createEmptyBorder(0, 6, 0, 0)));
			one.add(small(note.getText(), TEXT));
			JPanel foot = PanelParts.spread(PanelParts.plain(byline(note, in.getNow()), QUIET),
				PanelParts.link("Delete", () -> actions.delete(rsn, note)));
			foot.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			one.add(foot);
			one.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			column.add(one);
		}
	}

	/** "Clo · 2h ago". */
	static String byline(ClanData.StaffNote note, Instant now)
	{
		String who = note.getAuthor() != null ? note.getAuthor() : "Staff";
		Instant at = ClanData.parse(note.getAt());
		return at != null && now != null ? who + " · " + ClanView.ago(at, now) : who;
	}

	/** The card for a page or a profile: a heading, the notes, then the form. */
	static Card card(String title, JPanel notes, Form form)
	{
		Card card = new Card(title);
		notes.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		card.add(notes);
		card.gap();
		card.add(form);
		return card;
	}
}
