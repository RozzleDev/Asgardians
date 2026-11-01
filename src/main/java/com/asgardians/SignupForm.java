package com.asgardians;

import java.awt.Component;
import java.awt.GridLayout;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * The sign-up dialog, built from the questions the website sends for each event. Answers the game already knows
 * (timezone, combat achievement tier) are filled in, and the member can change them. Swing thread only.
 */
final class SignupForm
{
	static final List<String> CA_TIERS = List.of("None", "Easy", "Medium", "Hard", "Elite", "Master", "Grandmaster");

	/** Answers the plugin fills in before the member sees the form. */
	@lombok.Value
	static class Prefill
	{
		String timezone;
		String caTier;
	}

	private SignupForm()
	{
	}

	/**
	 * Shows the form until the answers are valid or the member cancels.
	 *
	 * @return the answers, or null when cancelled
	 */
	static Map<String, Object> ask(Component parent, ClanData.Event event, String rsn, Prefill prefill)
	{
		List<ClanData.Field> fields = ClanView.orEmpty(event.getSignup().getForm());
		Map<String, JComponent> inputs = new LinkedHashMap<>();
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(new JLabel("Signing up " + rsn + " for " + event.getName() + "."));
		if (event.getSignup().getNote() != null)
		{
			panel.add(Box.createVerticalStrut(4));
			panel.add(new JLabel("<html><div style='width:260px'>" + PanelParts.escape(event.getSignup().getNote()) + "</div></html>"));
		}
		if (!fields.isEmpty())
		{
			panel.add(Box.createVerticalStrut(8));
			JPanel grid = new JPanel(new GridLayout(0, 1, 0, 4));
			for (ClanData.Field field : fields)
			{
				JComponent input = input(field, prefill);
				inputs.put(field.getKey(), input);
				grid.add(new JLabel(field.getLabel() + (field.isRequired() ? " *" : "")));
				grid.add(input);
			}
			panel.add(grid);
		}

		while (true)
		{
			int choice = JOptionPane.showConfirmDialog(parent, panel, "Sign up: " + event.getName(),
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
			if (choice != JOptionPane.OK_OPTION)
			{
				return null;
			}
			Map<String, Object> raw = new LinkedHashMap<>();
			inputs.forEach((key, input) -> raw.put(key, read(input)));
			Checked checked = check(fields, raw);
			if (checked.getError() == null)
			{
				return checked.getAnswers();
			}
			JOptionPane.showMessageDialog(parent, checked.getError(), "Check your answers", JOptionPane.WARNING_MESSAGE);
		}
	}

	private static JComponent input(ClanData.Field field, Prefill prefill)
	{
		String type = field.getType() != null ? field.getType() : "text";
		switch (type)
		{
			case "choice":
			{
				List<String> options = new ArrayList<>();
				if (!field.isRequired())
				{
					options.add("");
				}
				options.addAll(ClanView.orEmpty(field.getOptions()));
				return new JComboBox<>(options.toArray(new String[0]));
			}
			case "multi":
			{
				JPanel boxes = new JPanel(new GridLayout(0, 2));
				for (String option : ClanView.orEmpty(field.getOptions()))
				{
					boxes.add(new JCheckBox(option));
				}
				boxes.setBorder(BorderFactory.createEmptyBorder());
				return boxes;
			}
			case "caTier":
			{
				JComboBox<String> tiers = new JComboBox<>(CA_TIERS.toArray(new String[0]));
				if (prefill.getCaTier() != null)
				{
					tiers.setSelectedItem(prefill.getCaTier());
				}
				return tiers;
			}
			case "timezone":
				return new JTextField(prefill.getTimezone() != null ? prefill.getTimezone() : "", 20);
			default:
				return new JTextField(20);
		}
	}

	private static Object read(JComponent input)
	{
		if (input instanceof JTextField)
		{
			return ((JTextField) input).getText().trim();
		}
		if (input instanceof JComboBox)
		{
			Object selected = ((JComboBox<?>) input).getSelectedItem();
			return selected != null ? selected.toString() : "";
		}
		List<String> ticked = new ArrayList<>();
		for (Component child : input.getComponents())
		{
			if (child instanceof JCheckBox && ((JCheckBox) child).isSelected())
			{
				ticked.add(((JCheckBox) child).getText());
			}
		}
		return ticked;
	}

	@lombok.Value
	static class Checked
	{
		Map<String, Object> answers;
		String error;
	}

	/**
	 * Checks raw form values against the questions: required answers, numbers in range, choices from the list, a real
	 * timezone. Empty optional answers are left out; numbers are sent as numbers.
	 */
	static Checked check(List<ClanData.Field> fields, Map<String, Object> raw)
	{
		Map<String, Object> answers = new LinkedHashMap<>();
		for (ClanData.Field field : fields)
		{
			Object value = raw.get(field.getKey());
			boolean empty = value == null || (value instanceof String && ((String) value).isEmpty())
				|| (value instanceof List && ((List<?>) value).isEmpty());
			if (empty)
			{
				if (field.isRequired())
				{
					return new Checked(null, field.getLabel() + " is required.");
				}
				continue;
			}
			String type = field.getType() != null ? field.getType() : "text";
			switch (type)
			{
				case "number":
				{
					double number;
					try
					{
						number = Double.parseDouble(value.toString().replace(",", ""));
					}
					catch (NumberFormatException e)
					{
						return new Checked(null, field.getLabel() + " must be a number.");
					}
					if ((field.getMin() != null && number < field.getMin()) || (field.getMax() != null && number > field.getMax()))
					{
						return new Checked(null, field.getLabel() + " must be between "
							+ trim(field.getMin()) + " and " + trim(field.getMax()) + ".");
					}
					answers.put(field.getKey(), number == Math.floor(number) ? (Object) (long) number : number);
					break;
				}
				case "timezone":
					try
					{
						answers.put(field.getKey(), ZoneId.of(value.toString()).getId());
					}
					catch (DateTimeException e)
					{
						return new Checked(null, field.getLabel() + " isn't a timezone the plugin knows, e.g. Europe/London.");
					}
					break;
				case "choice":
					if (!ClanView.orEmpty(field.getOptions()).contains(value.toString()))
					{
						return new Checked(null, field.getLabel() + ": pick one of the options.");
					}
					answers.put(field.getKey(), value.toString());
					break;
				default:
					answers.put(field.getKey(), value);
			}
		}
		return new Checked(answers, null);
	}

	/** The highest combat achievement tier whose points threshold the member has reached. */
	static String caTier(int points, int[] thresholds)
	{
		String tier = CA_TIERS.get(0);
		for (int i = 0; i < thresholds.length && i + 1 < CA_TIERS.size(); i++)
		{
			if (thresholds[i] > 0 && points >= thresholds[i])
			{
				tier = CA_TIERS.get(i + 1);
			}
		}
		return tier;
	}

	private static String trim(Double value)
	{
		if (value == null)
		{
			return "any";
		}
		return value == Math.floor(value) ? String.valueOf(value.longValue()) : value.toString();
	}
}
