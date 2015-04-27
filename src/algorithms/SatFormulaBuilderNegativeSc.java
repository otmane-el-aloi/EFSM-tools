package algorithms;

/**
 * (c) Igor Buzhinsky
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;

import qbf.reduction.BinaryOperation;
import qbf.reduction.BinaryOperations;
import qbf.reduction.BooleanFormula;
import qbf.reduction.BooleanVariable;
import qbf.reduction.FalseFormula;
import qbf.reduction.FormulaList;
import structures.NegativeNode;
import structures.NegativeScenariosTree;
import structures.Node;
import structures.ScenariosTree;
import structures.Transition;
import algorithms.AutomatonCompleter.CompletenessType;

public class SatFormulaBuilderNegativeSc extends FormulaBuilder {
	private final NegativeScenariosTree negativeTree;
	private final List<BooleanFormula> prohibitedFsms;

	public SatFormulaBuilderNegativeSc(ScenariosTree tree, int colorSize,
			List<String> events, List<String> actions,
			CompletenessType completenessType, NegativeScenariosTree negativeTree,
			List<BooleanFormula> prohibitedFsms) {
		super(colorSize, tree, completenessType, events, actions);
		this.negativeTree = negativeTree;
		this.prohibitedFsms = prohibitedFsms;
		
		//System.out.println(negativeTree);
	}

	public Collection<BooleanVariable> nagativeVars() {
		return existVars.stream().filter(v -> v.name.startsWith("xx_")).collect(Collectors.toList());
	}
	
	public static BooleanVariable xxVar(int state, int color) {
		return BooleanVariable.byName("xx", state, color).get();
	}

	public void addNegativeScenarioVars() {
		for (Node node : negativeTree.getNodes()) {
			for (int color = 0; color <= colorSize; color++) {
				if (!BooleanVariable.byName("xx", node.getNumber(), color).isPresent()) {
					existVars.add(new BooleanVariable("xx", node.getNumber(), color));
				}
			}
		}
	}
	
	private void eachNegativeNodeHasOneColorConstraints(List<BooleanFormula> constraints) {
		for (NegativeNode node : negativeTree.getNodes()) {
			final int num = node.getNumber();
			
			if (node == negativeTree.getRoot()) {
				// the root has color 0
				constraints.add(xxVar(0, 0));
				for (int i = 1; i <= colorSize; i++) {
					constraints.add(xxVar(0, i).not());
				}
			} else if (node.strongInvalid()) {
				constraints.add(invalid(node));
				for (int i = 0; i < colorSize; i++) {
					constraints.add(xxVar(num, i).not());
				}
			} else {
				// for each loop, the node after the loop is colored differently
				for (NegativeNode loop : node.loops()) {
					FormulaList options = new FormulaList(BinaryOperations.OR);
					options.add(invalid(node));
					final int loopNum = loop.getNumber();
					for (int i = 0; i < colorSize; i++) {
						options.add(xxVar(num, i).equivalent(xxVar(loopNum, i)).not());
					}
					constraints.add(options.assemble());
				}
				// at least one color
				FormulaList terms = new FormulaList(BinaryOperations.OR);
				for (int color = 0; color <= colorSize; color++) {
					terms.add(xxVar(num, color));
				}
				constraints.add(terms.assemble());
				
				// at most one color
				for (int color1 = 0; color1 <= colorSize; color1++) {
					for (int color2 = 0; color2 < color1; color2++) {
						BooleanVariable v1 = xxVar(num, color1);
						BooleanVariable v2 = xxVar(num, color2);					
						constraints.add(v1.not().or(v2.not()));
					}
				}
			}
		}
	}

	private void properTransitionYConstraints(List<BooleanFormula> constraints) {
		for (NegativeNode node : negativeTree.getNodes()) {
			for (Transition t : node.getTransitions()) {
				NegativeNode child = (NegativeNode) t.getDst();
				for (int nodeColor = 0; nodeColor < colorSize; nodeColor++) {
					BooleanVariable nodeVar = xxVar(node.getNumber(), nodeColor);
					for (int childColor = 0; childColor < colorSize; childColor++) {
						BooleanVariable childVar = xxVar(child.getNumber(), childColor);
						BooleanVariable relationVar = yVar(nodeColor, childColor, t.getEvent());
						constraints.add(BinaryOperation.or(relationVar, nodeVar.not(), childVar.not()));
						constraints.add(BinaryOperation.or(relationVar.not(), nodeVar.not(), childVar, invalid(child)));
					}
				}
			}
		}
	}
	
	private void properTransitionZConstraints(List<BooleanFormula> constraints) {
		for (NegativeNode node : negativeTree.getNodes()) {
			FormulaList options = new FormulaList(BinaryOperations.OR);
			for (int i = 0; i < colorSize; i++) {
				FormulaList zConstraints = new FormulaList(BinaryOperations.AND);
				zConstraints.add(xxVar(node.getNumber(), i));
				for (Transition t : node.getTransitions()) {
					FormulaList innerConstraints = new FormulaList(BinaryOperations.AND);
					List<String> actionSequence = Arrays.asList(t.getActions().getActions());
					for (String action : actions) {
						BooleanFormula f = zVar(i, action, t.getEvent());
						if (!actionSequence.contains(action)) {
							f = f.not();
						}
						innerConstraints.add(f);
					}
					zConstraints.add(invalid(t.getDst()).or(innerConstraints.assemble()));
				}
				options.add(zConstraints.assemble());
			}
			constraints.add(invalid(node).or(options.assemble()));
		}
	}
	
	private void invalidDefinitionConstraints(List<BooleanFormula> constraints) {
		for (NegativeNode parent : negativeTree.getNodes()) {
			for (Transition t : parent.getTransitions()) {
				Node child = t.getDst();
				String event = t.getEvent();
				FormulaList options = new FormulaList(BinaryOperations.OR);
				options.add(invalid(parent));
				for (int colorParent = 0; colorParent < colorSize; colorParent++) {
					// either no transition
					FormulaList innerYConstraints = new FormulaList(BinaryOperations.AND);
					for (int colorChild = 0; colorChild < colorSize; colorChild++) {
						innerYConstraints.add(yVar(colorParent, colorChild, event).not());
					}
					BooleanFormula yFormula = completenessType == CompletenessType.NORMAL ?
							FalseFormula.INSTANCE : innerYConstraints.assemble();
					
					// or actions on the transition are invalid
					FormulaList innerZConstraints = new FormulaList(BinaryOperations.OR);
					for (String action : actions) {
						BooleanFormula v = zVar(colorParent, action, event);
						if (ArrayUtils.contains(t.getActions().getActions(), action)) {
							v = v.not();
						}
						innerZConstraints.add(v);
					}
					
					options.add(xxVar(parent.getNumber(), colorParent)
							.and(innerZConstraints.assemble().or(yFormula)));
				}
				constraints.add(invalid(child).equivalent(options.assemble()));
			}
		}
	}
	
	private BooleanVariable invalid(Node n) {
		return xxVar(n.getNumber(), colorSize);
	}
	
	// for the completeness heuristics
	private void fsmProhibitionConstraints(List<BooleanFormula> constraints) {
		prohibitedFsms.forEach(constraints::add);
	}
	
	public List<BooleanFormula> negativeConstraints() {
		final List<BooleanFormula> constraints = new ArrayList<>();
		eachNegativeNodeHasOneColorConstraints(constraints);
		properTransitionYConstraints(constraints);
		properTransitionZConstraints(constraints);
		invalidDefinitionConstraints(constraints);
		fsmProhibitionConstraints(constraints);
		return constraints;
	}
	
	// should be called only once
	public BooleanFormula getBasicFormula() {
		addColorVars();
		addTransitionVars(true);
		return scenarioConstraints(true).assemble()
				.and(varPresenceConstraints());
	}
	
	public List<BooleanFormula> getNegationConstraints() {
		addNegativeScenarioVars();
		return negativeConstraints();
	}
}
