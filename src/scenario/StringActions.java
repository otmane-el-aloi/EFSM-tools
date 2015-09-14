package scenario;

import java.util.Arrays;

public class StringActions {
	private final String[] actions;

	public StringActions(String str) {
		str = str.trim();
		actions = str.isEmpty() ? new String[0] : str.split(",");
		for (int i = 0; i < actions.length; i++) {
			actions[i] = actions[i].trim();
		}
	}

	public String[] getActions() {
		return actions;
	}

	public int size() {
		return actions.length;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(actions);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		StringActions other = (StringActions) obj;
		if (!Arrays.equals(actions, other.actions))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return String.join(", ", actions);
	}
}