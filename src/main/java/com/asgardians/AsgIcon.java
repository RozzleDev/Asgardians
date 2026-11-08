package com.asgardians;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * The sidebar icon: "ASG" in orange pixel letters inside an orange border, on the panel's dark brown. Drawn pixel by
 * pixel at 16 by 16 so it stays sharp, and scaled up in whole pixels for larger sizes.
 */
final class AsgIcon
{
	private static final Color BACKGROUND = PanelParts.BACKGROUND;
	private static final Color ORANGE = PanelParts.HIGHLIGHT;
	private static final int SIZE = 16;
	private static final String[] A = {".#.", "#.#", "#.#", "###", "#.#", "#.#"};
	private static final String[] S = {".##", "#..", ".#.", "..#", "..#", "##."};
	private static final String[] G = {".##", "#..", "#..", "#.#", "#.#", ".##"};

	private AsgIcon()
	{
	}

	/** The icon at {@code size} pixels: 16, or a whole multiple of 16 for a bigger one. */
	static BufferedImage create(int size)
	{
		int scale = Math.max(1, size / SIZE);
		BufferedImage img = new BufferedImage(SIZE * scale, SIZE * scale, BufferedImage.TYPE_INT_ARGB);
		boolean[][] on = pixels();
		int bg = BACKGROUND.getRGB(), fg = ORANGE.getRGB();
		for (int y = 0; y < SIZE; y++)
		{
			for (int x = 0; x < SIZE; x++)
			{
				boolean corner = (x == 0 || x == SIZE - 1) && (y == 0 || y == SIZE - 1);
				if (corner)
				{
					continue;
				}
				int colour = on[y][x] ? fg : bg;
				for (int dy = 0; dy < scale; dy++)
				{
					for (int dx = 0; dx < scale; dx++)
					{
						img.setRGB(x * scale + dx, y * scale + dy, colour);
					}
				}
			}
		}
		return img;
	}

	/** Which pixels are orange: the border, and the three letters in the middle. */
	static boolean[][] pixels()
	{
		boolean[][] on = new boolean[SIZE][SIZE];
		for (int i = 0; i < SIZE; i++)
		{
			on[0][i] = on[SIZE - 1][i] = on[i][0] = on[i][SIZE - 1] = true;
		}
		String[][] letters = {A, S, G};
		int[] xs = {2, 6, 11};
		for (int l = 0; l < letters.length; l++)
		{
			for (int r = 0; r < letters[l].length; r++)
			{
				for (int c = 0; c < 3; c++)
				{
					if (letters[l][r].charAt(c) == '#')
					{
						on[5 + r][xs[l] + c] = true;
					}
				}
			}
		}
		return on;
	}
}
