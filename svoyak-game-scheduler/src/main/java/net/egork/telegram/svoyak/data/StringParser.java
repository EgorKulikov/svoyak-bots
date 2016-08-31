package net.egork.telegram.svoyak.data;

import java.text.ParseException;

/**
 * @author Egor Kulikov (kulikov@devexperts.com)
 */
public class StringParser implements CharSequence {
	private String underlying;

	public StringParser(String underlying) {
		this.underlying = underlying;
	}

	public int length() {
		return underlying.length();
	}

	public char charAt(int index) {
		return underlying.charAt(index);
	}

	public CharSequence subSequence(int start, int end) {
		return underlying.subSequence(start, end);
	}

	public String advance(boolean toEnd, String...samples) throws ParseException {
		int position = -1;
		String targetSample = null;
		for (String sample : samples) {
			int candidate = underlying.indexOf(sample);
			if (position == -1 || candidate != -1 && candidate < position) {
				position = candidate;
				targetSample = sample;
			}
		}
		if (position == -1)
			throw new ParseException(underlying, -1);
		String result = underlying.substring(0, position);
		if (toEnd)
			underlying = underlying.substring(position + targetSample.length());
		else
			underlying = underlying.substring(position);
		return result;
	}

	public String advanceIfPossible(boolean toEnd, String...samples) {
		try {
			return advance(toEnd, samples);
		} catch (ParseException e) {
			return null;
		}
	}

	public void dropTail(String sample) throws ParseException {
		int position = underlying.indexOf(sample);
		if (position == -1)
			throw new ParseException(underlying, -1);
		underlying = underlying.substring(0, position);
	}

	public void advance(int offset) {
		underlying = underlying.substring(offset);
	}

	public boolean startsWith(String s) {
		return underlying.startsWith(s);
	}
}
