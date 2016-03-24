package main.plant.apros;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.Pair;

import main.plant.PlantBuilderMain;
import structures.plant.MooreNode;
import structures.plant.MooreTransition;
import structures.plant.NondetMooreAutomaton;

public class CompositionalBuilder {
	// Block-based composition
	final static Parameter reacRelPower = new Parameter(
			"YC00B001#NR1_POWER", "reac_rel_power", 0.1, 0.5, 0.95, 1.0, 1.1);
	final static Parameter trip = new Parameter(
			"YZ10U404FL01#FF_OUTPUT_VALUE", "trip", 0.5);
	final static Parameter rodPosition2 = new Parameter(
			"YC00B001_RA1#RA_RE_RODP2", "rod_position2_", 1.0);
	final static Parameter rodPosition1 = new Parameter(
			"YC00B001_RA1#RA_RE_RODP", "rod_position1_", 1.0);
	
	final static Configuration CONF_REAC = new Configuration(
			1.0, Arrays.asList(reacRelPower),
			Arrays.asList(trip, rodPosition1, rodPosition2));
	
	final static Parameter pressurizerWaterLevel = new Parameter(
			"YP10B001#PR11_LIQ_LEVEL", "pressurizer_water_level", 2.3, 2.8,
			3.705);
	final static Parameter pressurizerPressure = new Parameter(
			"YP10B001_NO8#NO6_PRESSURE", "pressurizer_pressure", 8e6, 9e6, 10e6, 11e6, 12e6, 13e6);
	final static Parameter valveE51_preslevco = new Parameter(
			"TE51S002_VA1#V_POSITION_SET_VALUE", "valveE51", 0.5);
	final static Parameter valveK52_preslevco = new Parameter(
			"TK52S002_VA1#V_POSITION_SET_VALUE", "valveK52", 0.5);
	final static Parameter valveK53_preslevco = new Parameter(
			"TK53S002_VA1#V_POSITION_SET_VALUE", "valveK53", 0.5);
	
	final static Configuration CONF_PRESSURIZER = new Configuration(
			1.0, Arrays.asList(pressurizerWaterLevel, pressurizerPressure),
			Arrays.asList(valveE51_preslevco, valveK52_preslevco, valveK53_preslevco));

	final static Parameter pressureInLowerPlenum = new Parameter(
			"YC00J005#TA11_PRESSURE", "pressure_lower_plenum", 3.5, 8.0, 10.0);
	final static Parameter liveSteamPressure = new Parameter(
			"RA00J010#PO11_PRESSURE", "pressure_live_steam", 3.5);
	
	final static Configuration CONF_MISC = new Configuration(
			1.0, Arrays.asList(pressureInLowerPlenum, liveSteamPressure),
			Arrays.asList());
	
	final static List<Configuration> CONF_STRUCTURE = Arrays.asList(CONF_PRESSURIZER, CONF_REAC, CONF_MISC);

	// Control diagram-based composition
	final static List<Configuration> CONF_NETWORK = Arrays.asList(
			TraceTranslator.CONFIGURATION_PROTECTION1,
			TraceTranslator.CONFIGURATION_PROTECTION5,
			TraceTranslator.CONFIGURATION_PROTECTION7);
	
	final static Configuration CONF1 = new Configuration(
			1.0, Arrays.asList(
			TraceTranslator.pressureInLowerPlenum),
			Arrays.asList(TraceTranslator.tripSignal));
	
	final static Configuration CONF2 = new Configuration(
			1.0, Arrays.asList(TraceTranslator.pressurizerWaterLevel),
			Arrays.asList(TraceTranslator.tripSignal));
	
	final static Configuration CONF3 = new Configuration(
			1.0, Arrays.asList(TraceTranslator.reacRelPower_entirePlant),
			Arrays.asList(TraceTranslator.prot5valve41open));
	
	final static List<Configuration> CONF_TEST = Arrays.asList(CONF1, CONF2, CONF3);
	
	/*******************************************/
	
	final static List<Configuration> CONFS = CONF_STRUCTURE;
	final static int FAST_THRESHOLD = 0;
	final static boolean ALL_EVENT_COMBINATIONS = false;
	final static String TRACE_LOCATION = TraceTranslator.INPUT_DIRECTORY;
	
	/*******************************************/
	
	// assuming that we have at most 10 intervals
	static boolean isProperAction(String action, String prefix) {
		return action.startsWith(prefix)
				&& action.length() == prefix.length() + 1
				&& Character.isDigit(action.charAt(action.length() - 1));
	}
	
	static int actionIntervalIndex(String[] actions, String prefix) {
		int i;
		for (i = 0; i < actions.length; i++) {
			if (isProperAction(actions[i], prefix)) {
				break;
			}
		}
		return Integer.parseInt(String.valueOf(actions[i].charAt(actions[i].length() - 1)));
	}
	
	static boolean isConsistentWithInputs(MooreNode node, String outgoingEvent,
			Match match, boolean isOutputInput) {
		if (isOutputInput) {
			for (Pair<Parameter, Integer> pair : match.outputInputPairs) {
				final int firstIndex = actionIntervalIndex(node.actions().getActions(),
						pair.getLeft().traceName());
				final int secondIndex = Integer.parseInt(String.valueOf(
						outgoingEvent.charAt(pair.getRight() + 1)));
				if (firstIndex != secondIndex) {
					return false;
				}
			}
		} else {
			for (Pair<Integer, Parameter> pair : match.inputOutputPairs) {
				final int secondIndex = actionIntervalIndex(node.actions().getActions(),
						pair.getRight().traceName());
				final int firstIndex = Integer.parseInt(String.valueOf(
						outgoingEvent.charAt(pair.getLeft() + 1)));
				if (firstIndex != secondIndex) {
					return false;
				}
			}
		}
		return true;
	}
	
	private static Configuration composeConfigurations(Configuration c1, Configuration c2, Match match) {
		// outputs
		final Map<String, Parameter> traceNameToParam = new TreeMap<>();
		final Consumer<Parameter> process = p -> {
			traceNameToParam.putIfAbsent(p.traceName(), p);
		};
		c1.outputParameters.forEach(process);
		c2.outputParameters.forEach(process);
		for (String prefix : match.badActionPrefixes) {
			traceNameToParam.remove(prefix);
		}
		final List<Parameter> outputs = new ArrayList<>(traceNameToParam.values());
		
		// inputs
		final List<Parameter> inputs = new ArrayList<>();
		for (int i = 0; i < c1.inputParameters.size(); i++) {
			if (!match.badFirstIndices.contains(i)) {
				inputs.add(c1.inputParameters.get(i));
			}
		}
		for (int i = 0; i < c2.inputParameters.size(); i++) {
			if (!match.badSecondIndices.contains(i)) {
				inputs.add(c2.inputParameters.get(i));
			}
		}
		
		return new Configuration(c1.intervalSec, outputs, inputs);
	}
	
	private static NondetMooreAutomaton compose(NondetMooreAutomaton a1, NondetMooreAutomaton a2,
			Match match, Set<List<String>> allActionCombinationsSorted) throws FileNotFoundException {
		final List<MooreNode> compositeStates = new ArrayList<>();
		
		final Deque<Pair<StatePair, MooreNode>> q = new ArrayDeque<>();
		final Map<Set<String>, MooreNode> allEnqueudOutputCombinations = new HashMap<>();
		for (int initial1 : a1.initialStates()) {
			for (int initial2 : a2.initialStates()) {
				final MooreNode state1 = a1.state(initial1);
				final MooreNode state2 = a2.state(initial2);
				final StatePair p = new StatePair(state1, state2);
				if (p.isConsistent(match) && p.isPresentInTraces(allActionCombinationsSorted)) {
					final MooreNode node = p.toMooreNode(compositeStates.size(), match);
					allEnqueudOutputCombinations.put(p.actionSet(match), node);
					q.add(Pair.of(p, node));
					compositeStates.add(node);
				}
			}
		}
		final int initialStateNum = q.size();
		
		while (!q.isEmpty()) {
			final Pair<StatePair, MooreNode> retrieved = q.removeLast();
			final StatePair pair = retrieved.getLeft();
			final MooreNode src = retrieved.getRight();
			
			for (MooreTransition t1 : pair.first.transitions()) {
				final String e1 = t1.event();
				
				l: for (MooreTransition t2 : pair.second.transitions()) {
					final String e2 = t2.event();

					// **** The event must be consistent with the NEXT state
					// **** If it is required to be consistent with the FIRST state,
					// **** then there are some semantical problems
					
					// internal connection consistency
					if (!isConsistentWithInputs(t2.dst(), e1, match, false)) {
						continue;
					}
					if (!isConsistentWithInputs(t1.dst(), e2, match, true)) {
						continue;
					}
					
					for (Pair<Integer, Integer> ip : match.inputPairs) {
						if (e1.charAt(ip.getLeft() + 1) != e2.charAt(ip.getRight() + 1)) {
							continue l;
						}
					}
					
					final StatePair p = new StatePair(t1.dst(), t2.dst());
					if (p.isConsistent(match) && p.isPresentInTraces(allActionCombinationsSorted)) {
						final StringBuilder event = new StringBuilder("A");
						for (int i = 1; i < e1.length(); i++) {
							if (!match.badFirstIndices.contains(i - 1)) {
								event.append(e1.charAt(i));
							}
						}
						for (int i = 1; i < e2.length(); i++) {
							if (!match.badSecondIndices.contains(i - 1)) {
								event.append(e2.charAt(i));
							}
						}
						
						final Set<String> actionSet = p.actionSet(match);
						MooreNode dst = allEnqueudOutputCombinations.get(actionSet);
						if (dst == null) {
							dst = p.toMooreNode(compositeStates.size(), match);
							q.add(Pair.of(p, dst));
							compositeStates.add(dst);
							allEnqueudOutputCombinations.put(actionSet, dst);
						}
						
						final String e = event.toString();
						if (!src.allDst(e).contains(dst)) {
							src.addTransition(event.toString(), dst);
						}
					}
				}
			}
		}

		/*
		 * queue Q <- all consistent pairs of initial states
		 * while !Q.isEmpty()
		 *   q = (q_1, q_2) <- Q
		 *   foreach pair of consistent outgoing inputs
		 *   	if there is no internal connection conflict
		 *         (current output and outgoing transition input
		 *   	   AND the destination present in the entire trace set
		 *   		 Q <- q
		 * remove internal connections (duplicate inputs and outputs)
		 */
		
		final List<Boolean> isInitial = new ArrayList<>();
		isInitial.addAll(Collections.nCopies(initialStateNum, true));
		isInitial.addAll(Collections.nCopies(compositeStates.size() - initialStateNum, false));
		return new NondetMooreAutomaton(compositeStates, isInitial);
	}
	
	private static Configuration outputConfigurationComposition(Configuration c1, Configuration c2) {
		final List<Parameter> outputParams = new ArrayList<>(c1.outputParameters);
		final Set<String> allParamNames = new HashSet<>();
		for (Parameter p : c1.outputParameters) {
			allParamNames.add(p.aprosName());
		}
		for (Parameter p : c2.outputParameters) {
			if (allParamNames.add(p.aprosName())) {
				outputParams.add(p);
			}
		}
		return new Configuration(c1.intervalSec, outputParams, Collections.emptyList());
	}
	
	static class Match {
		final List<Pair<Parameter, Parameter>> outputPairs = new ArrayList<>();
		final List<Pair<Integer, Integer>> inputPairs = new ArrayList<>();
		final List<Pair<Integer, Parameter>> inputOutputPairs = new ArrayList<>();
		final List<Pair<Parameter, Integer>> outputInputPairs = new ArrayList<>();
		final Set<Integer> badFirstIndices = new TreeSet<>();
		final Set<Integer> badSecondIndices = new TreeSet<>();
		final Set<String> badActionPrefixes = new TreeSet<>();
		
		Match(Configuration c1, Configuration c2) {
			for (Parameter p : c1.outputParameters) {
				for (Parameter q : c2.outputParameters) {
					if (Parameter.unify(p, q)) {
						outputPairs.add(Pair.of(p, q));
					}
				}
			}
			for (int i = 0; i < c1.inputParameters.size(); i++) {
				final Parameter p = c1.inputParameters.get(i);
				for (int j = 0; j < c2.inputParameters.size(); j++) {
					final Parameter q = c2.inputParameters.get(j);
					if (Parameter.unify(p, q)) {
						inputPairs.add(Pair.of(i, j));
					}
				}
			}
			
			for (Parameter p : c1.outputParameters) {
				for (int j = 0; j < c2.inputParameters.size(); j++) {
					final Parameter q = c2.inputParameters.get(j);
					if (Parameter.unify(p, q)) {
						outputInputPairs.add(Pair.of(p, j));
					}
				}
			}
			
			for (int i = 0; i < c1.inputParameters.size(); i++) {
				final Parameter p = c1.inputParameters.get(i);
				for (Parameter q : c2.outputParameters) {
					if (Parameter.unify(p, q)) {
						inputOutputPairs.add(Pair.of(i, q));
					}
				}
			}
			
			// internal connections
			for (Pair<Integer, Parameter> iop : inputOutputPairs) {
				badFirstIndices.add(iop.getLeft());
			}
			// duplications
			for (Pair<Integer, Integer> ip : inputPairs) {
				badSecondIndices.add(ip.getRight());
			}
			// internal connections
			for (Pair<Parameter, Integer> oip : outputInputPairs) {
				badSecondIndices.add(oip.getRight());
			}
			
			for (Pair<Parameter, Integer> p : outputInputPairs) {
				badActionPrefixes.add(p.getLeft().traceName());
			}
			for (Pair<Integer, Parameter> p : inputOutputPairs) {
				badActionPrefixes.add(p.getRight().traceName());
			}
		}
	}
	
	public static void main(String[] args) throws FileNotFoundException {		
		// 1. Unification of all configuration pairs:
		System.out.println("*** UNIFICATION");
		for (int i = 0; i < CONFS.size(); i++) {
			for (int j = 0; j < i; j++) {
				new Match(CONFS.get(i), CONFS.get(j));
			}
		}
		if (CONFS.stream().map(s -> s.intervalSec).distinct().count() > 1) {
			System.err.println("Incompatible intervals, stopping.");
			return;
		}
		
		// 2. Load the dataset
		System.out.println("*** LOADING THE DATASET");
		final Dataset ds = new Dataset(CONFS.get(0).intervalSec, TRACE_LOCATION);
		
		// 3. Build all the basic plants
		final List<NondetMooreAutomaton> automata = new ArrayList<>();
		for (int i = 0; i < CONFS.size(); i++) {
			System.out.println("*** BUILDING BASIC PLANTS, STAGE " + (i + 1));
			final Configuration conf = CONFS.get(i);
			System.out.println(conf);
			System.out.println();
			final String namePrefix = "automaton" + i + ".";
			
			final List<String> params = TraceTranslator.generateScenarios(conf,
					ds, new HashSet<>(), namePrefix + "gv", namePrefix + "smv",
					false, FAST_THRESHOLD, ALL_EVENT_COMBINATIONS);
			System.out.println();
			final PlantBuilderMain builder = new PlantBuilderMain();
			builder.run(params.toArray(new String[params.size()]));
			if (!builder.resultAutomaton().isPresent()) {
				System.err.println("Basic plant model constuction failed; "
						+ "is the number of states sufficient?");
				return;
			}
			final NondetMooreAutomaton a = builder.resultAutomaton().get();
			dumpAutomaton(a, conf, namePrefix);
			automata.add(a);
			System.out.println();
		}
		
		// 4. Iteratively compose automata
		Configuration lastConf = CONFS.get(0);
		NondetMooreAutomaton lastAuto = automata.get(0);
		for (int i = 1; i < CONFS.size(); i++) {
			System.out.println("*** COMPOSING, STAGE " + i);
			final Configuration conf1 = lastConf;
			final Configuration conf2 = CONFS.get(i);
			final Match match = new Match(conf1, conf2);
			final String namePrefix = "automaton_comp" + i + ".";
			
			// Obtain the set of all possible composite actions
			final Set<List<String>> allActionCombinations = new HashSet<>();
			TraceTranslator.generateScenarios(outputConfigurationComposition(conf1, conf2),
					ds, allActionCombinations, "", "", false, FAST_THRESHOLD,
					ALL_EVENT_COMBINATIONS);
			final Set<List<String>> allActionCombinationsSorted = new HashSet<>();
			for (List<String> actionCombination : allActionCombinations) {
				final List<String> copy = new ArrayList<>(actionCombination);
				Collections.sort(copy);
				allActionCombinationsSorted.add(copy);
			}

			// Compose
			System.out.println();
			System.out.println("Composing...");
			lastAuto = compose(lastAuto, automata.get(i), match,
					allActionCombinationsSorted);
			lastConf = composeConfigurations(conf1, conf2, match);
			dumpAutomaton(lastAuto, lastConf, namePrefix);
			System.out.println(lastConf);
			System.out.println();
		}
	}
	
	// assuming completeness and checking only state 0
	private static List<String> eventsFromAutomaton(NondetMooreAutomaton a) {
		final Set<String> result = new TreeSet<>();
		for (MooreTransition t : a.state(0).transitions()) {
			result.add(t.event());
		}
		return new ArrayList<>(result);
	}
	
	private static void dumpAutomaton(NondetMooreAutomaton a, Configuration conf,
			String namePrefix) throws FileNotFoundException {
		conf.annotate(a);
		try (PrintWriter pw = new PrintWriter(namePrefix + "gv")) {
			pw.println(a);
		}
		try (PrintWriter pw = new PrintWriter(namePrefix + "smv")) {
			pw.println(a.toNuSMVString(eventsFromAutomaton(a), conf.actions()));
		}
	}
}
