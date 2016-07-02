package structures;

import scenario.StringActions;
import bool.MyBooleanExpression;

public class Transition {
    private final String event;
	private final MyBooleanExpression expr;
	private final StringActions actions;
	private final MealyNode src;
	private final MealyNode dst;

	public Transition(MealyNode src, MealyNode dst, String event, MyBooleanExpression expr, StringActions actions) {
		this.src = src;
		this.dst = dst;
		this.event = event;
		this.expr = expr;
		this.actions = actions;
	}

	public String event() {
		return event;
	}

	public MealyNode src() {
		return src;
	}

	public MealyNode dst() {
		return dst;
	}

	public StringActions actions() {
		return actions;
	}

	public MyBooleanExpression expr() {
		return expr;
	}
	
	@Override
	public String toString() {
		return src.number() + " -> " + dst.number()
				+ "  " + event + " [" + expr.toString() + "] " + actions.toString();
	}

    /*public static boolean isCompatibile(Transition first, Transition second) {
		if (first.event().equals(second.event())) {
			if (first.expr() == second.expr()) {
				if (!first.actions().equals(second.actions())) {
					return false;
				}
			} else if (first.expr().hasSolutionWith(second.expr())) {
				return false;
			}
		}
		return true;
	}*/
}
