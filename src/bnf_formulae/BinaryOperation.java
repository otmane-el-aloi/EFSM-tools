package bnf_formulae;

/**
 * (c) Igor Buzhinsky
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BinaryOperation extends BooleanFormula {
	private final List<BooleanFormula> children;
	public final BinaryOperations type;
	private final String comment;
	
	public List<BooleanFormula> copyChildren() {
		return new ArrayList<>(children);
	}
	
	private BinaryOperation(BooleanFormula left, BooleanFormula right, BinaryOperations type) {
		this(Arrays.asList(left, right), type, null);
	}
	
	private BinaryOperation(List<BooleanFormula> children, BinaryOperations type) {
		this(children, type, null);
	}
	
	private BinaryOperation(List<BooleanFormula> children, BinaryOperations type, String comment) {
		for (BooleanFormula f : children) {
			assert f != null;
		}
		if (type == BinaryOperations.EQ || type == BinaryOperations.IMPLIES) {
			assert children.size() == 2;
		}
		this.children = new ArrayList<>(children);
		this.type = type;
		this.comment = comment;
	}
	
	public static BooleanFormula and(List<BooleanFormula> elements) {
		return and(elements, null);
	}
	
	public static BooleanFormula or(List<BooleanFormula> elements) {
		return or(elements, null);
	}
	
	public static BooleanFormula and(List<BooleanFormula> elements, String comment) {
		return new BinaryOperation(elements, BinaryOperations.AND, comment);
	}
	
	public static BooleanFormula or(List<BooleanFormula> elements, String comment) {
		return new BinaryOperation(elements, BinaryOperations.OR, comment);
	}
	
	public static BooleanFormula and(BooleanFormula... elements) {
		return new BinaryOperation(Arrays.asList(elements), BinaryOperations.AND);
	}
	
	public static BooleanFormula or(BooleanFormula... elements) {
		return new BinaryOperation(Arrays.asList(elements), BinaryOperations.OR);
	}
	
	public static BooleanFormula implies(BooleanFormula left, BooleanFormula right) {
		return new BinaryOperation(left, right, BinaryOperations.IMPLIES);
	}
	
	public static BooleanFormula equivalent(BooleanFormula left, BooleanFormula right) {
		return new BinaryOperation(left, right, BinaryOperations.EQ);
	}
	
	@Override
	public String toLimbooleString() {
		assert !children.isEmpty();
		if (children.size() == 1) {
			return children.get(0).toLimbooleString();
		}
		
		final List<String> strChildren = children.stream()
				.map(f -> f.toLimbooleString())
				.collect(Collectors.toList());
		
		// limboole does not like '->' without the left space
		return "(" + String.join((type == BinaryOperations.IMPLIES ? " " : "")
				+ type, strChildren) + ")";
	}
	
	private String commentStringStart() {
		return comment == null ? "" : ("\n/* " + comment + " */\n");
	}
	
	private String commentStringEnd() {
		return comment == null ? "" : ("\n/* " + "end of block" + " */\n");
	}
	
	private String comment(String content) {
		return commentStringStart() + content + commentStringEnd();
	}
	
	@Override
	public String toString() {
		if (children.isEmpty()) {
			switch (type) {
			case AND:
				return comment(TrueFormula.INSTANCE.toString());
			case OR:
				return comment(FalseFormula.INSTANCE.toString());
			default:
				throw new AssertionError();
			}
		} else if (children.size() == 1) {
			return comment(children.get(0).toString());
		}
		
		final List<String> strChildren = children.stream().map(f -> f.toString()).collect(Collectors.toList());
		return "(" + comment(String.join(" " + type + " ", strChildren)) + ")";
	}

	@Override
	public BooleanFormula multipleSubstitute(Map<BooleanVariable, BooleanFormula> replacement) {
		List<BooleanFormula> newChildren = children.stream().map(c ->
			c.multipleSubstitute(replacement)).collect(Collectors.toList());
		return new BinaryOperation(newChildren, type);
	}
	
	@Override
	public BooleanFormula simplify() {
		final List<BooleanFormula> childrenSimpl = children.stream()
				.map(BooleanFormula::simplify).collect(Collectors.toList());
		BooleanFormula left;
		BooleanFormula right;
		switch (type) {
		case AND:
			if (childrenSimpl.contains(FalseFormula.INSTANCE)) {
				return FalseFormula.INSTANCE;
			}
			childrenSimpl.removeIf(elem -> elem == TrueFormula.INSTANCE);
			return childrenSimpl.isEmpty() ? TrueFormula.INSTANCE
				: childrenSimpl.size() == 1 ? childrenSimpl.get(0) : and(childrenSimpl);
		case OR:
			if (childrenSimpl.contains(TrueFormula.INSTANCE)) {
				return TrueFormula.INSTANCE;
			}
			childrenSimpl.removeIf(elem -> elem == FalseFormula.INSTANCE);
			return childrenSimpl.isEmpty() ? FalseFormula.INSTANCE
					: childrenSimpl.size() == 1 ? childrenSimpl.get(0) : or(childrenSimpl);
		case EQ:
			left = childrenSimpl.get(0);
			right = childrenSimpl.get(1);
			if (left == right) { // both TRUE or both FALSE
				return TrueFormula.INSTANCE;
			} else if (left == TrueFormula.INSTANCE && right == FalseFormula.INSTANCE) {
				return FalseFormula.INSTANCE;
			} else if (left == FalseFormula.INSTANCE && right == TrueFormula.INSTANCE) {
				return FalseFormula.INSTANCE;
			} else if (left == TrueFormula.INSTANCE) {
				return right;
			} else if (right == TrueFormula.INSTANCE) {
				return left;
			} else if (left == FalseFormula.INSTANCE) {
				return right.not();
			} else if (right == FalseFormula.INSTANCE) {
				return left.not();
			}
			return new BinaryOperation(childrenSimpl, BinaryOperations.EQ);
		case IMPLIES:
			left = childrenSimpl.get(0);
			right = childrenSimpl.get(1);
			if (left == FalseFormula.INSTANCE || right == TrueFormula.INSTANCE) {
				return TrueFormula.INSTANCE;
			} else if (left == TrueFormula.INSTANCE && right == FalseFormula.INSTANCE) {
				return FalseFormula.INSTANCE;
			} else if (left == TrueFormula.INSTANCE) {
				return right;
			} else if (right == FalseFormula.INSTANCE) {
				return left.not();
			}
			return new BinaryOperation(childrenSimpl, BinaryOperations.IMPLIES);
		}
		throw new AssertionError();
	}

	@Override
	public BooleanFormula removeEqImplConst() {
		if (type == BinaryOperations.EQ) {
			final BooleanFormula left = children.get(0).removeEqImplConst();
			final BooleanFormula right = children.get(1).removeEqImplConst();
			return BinaryOperation.or(left.and(right), left.not().and(right.not()));
		} else if (type == BinaryOperations.IMPLIES) {
			final BooleanFormula left = children.get(0).removeEqImplConst();
			final BooleanFormula right = children.get(1).removeEqImplConst();
			return left.not().or(right);
		} else {
			final List<BooleanFormula> newChildren = children.stream()
					.map(f -> f.removeEqImplConst()).collect(Collectors.toList());
			return new BinaryOperation(newChildren, type, comment);
		}
	}
	
	@Override
	public BooleanFormula propagateNot() {
		final List<BooleanFormula> newChildren = children.stream()
				.map(f -> f.propagateNot()).collect(Collectors.toList());
		return new BinaryOperation(newChildren, type, comment);
	}
	
	// without variable number conversion and the zero in the end
	private static String simplifiedToDimacs(List<BooleanFormula> terms) {
		final List<String> dimacsVars = new ArrayList<>();
		for (BooleanFormula f : terms) {
			final BooleanVariable v;
			final String prefix;
			if (f instanceof BooleanVariable) {
				v = (BooleanVariable) f;
				prefix = "";
			} else if (f instanceof NotOperation) {
				v = (BooleanVariable) (((NotOperation) f).inside);
				prefix = "-";
			} else {
				throw new AssertionError();
			}
			dimacsVars.add(prefix + v.number);
		}
		return String.join(" ", dimacsVars);
	}
	
	public void separateAnd(List<String> cnfConstraints, List<String> limbooleConstraints) {
		if (type != BinaryOperations.AND) {
			throw new AssertionError();
		}
		for (BooleanFormula child : children) {
			boolean add = false;
			if (child instanceof BinaryOperation) {
				final BinaryOperation binChild = (BinaryOperation) child;
				if (binChild.type == BinaryOperations.AND) {
					binChild.separateAnd(cnfConstraints, limbooleConstraints);
				} else {
					add = true;
				}
			} else {
				add = true;
			}
			if (add) {
				final List<BooleanFormula> terms = new ArrayList<>();
				if (child.separateOr(terms)) {
					cnfConstraints.add(simplifiedToDimacs(terms));
				} else {
					limbooleConstraints.add(child.toLimbooleString());
				}
			}
		}
	}
	
	@Override
	public boolean separateOr(List<BooleanFormula> terms) {
		if (type != BinaryOperations.OR) {
			return false;
		}
		for (BooleanFormula child : children) {
			if (child instanceof BinaryOperation) {
				final BinaryOperation binChild = (BinaryOperation) child;
				if (!binChild.separateOr(terms)) {
					return false;
				}
			} else {
				// must be either var or not(var)
				terms.add(child);
			}
		}
		return true;
	}
}
